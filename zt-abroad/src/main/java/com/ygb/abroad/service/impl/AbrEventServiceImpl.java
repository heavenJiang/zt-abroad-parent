package com.ygb.abroad.service.impl;

import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrEvent;
import com.ygb.abroad.domain.AbrMomentRule;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppPlanVo;
import com.ygb.abroad.growth.event.PlanMomentDetected;
import com.ygb.abroad.mapper.AbrAssessmentMapper;
import com.ygb.abroad.mapper.AbrEventMapper;
import com.ygb.abroad.mapper.AbrMomentRuleMapper;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 业务事件 服务层实现
 *
 * 转化时刻每类每人只记录一次（abr_event 去重键为时刻编码）；推荐在下次打开选校单时展示一次，两次推荐至少间隔
 * abroad.moment.min-interval-hours 小时；未成年人只记录事件、不展示推荐（营销触达只面向成年人）。
 *
 * @author ygb
 */
@Service
public class AbrEventServiceImpl implements IAbrEventService
{
    private static final Logger log = LoggerFactory.getLogger(AbrEventServiceImpl.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private AbrEventMapper eventMapper;

    @Autowired
    private AbrMomentRuleMapper ruleMapper;

    @Autowired
    private AbrAssessmentMapper assessmentMapper;

    @Autowired
    private ApplicationEventPublisher publisher;

    /** 两次推荐展示的最小间隔（小时） */
    @Value("${abroad.moment.min-interval-hours:24}")
    private int minIntervalHours;

    @Override
    public void recordAction(AbrVisitor visitor, String eventType, String refType, Long refId, Map<String, Object> payload)
    {
        AbrEvent e = new AbrEvent();
        e.setEventType(eventType);
        e.setVisitorId(visitor.getVisitorId());
        e.setAppUserId(visitor.getAppUserId());
        e.setRefType(refType);
        e.setRefId(refId);
        e.setPayload(payload == null ? null : MAPPER.writeValueAsString(payload));
        try
        {
            eventMapper.insertAbrEvent(e);
        }
        catch (RuntimeException ex)
        {
            // 事件只用于分析与订阅，写入失败不影响学生的操作
            log.warn("业务事件写入失败 type={} visitorId={}：{}", eventType, visitor.getVisitorId(), ex.getMessage());
        }
    }

    @Override
    public Map<String, JsonNode> activeMomentParams()
    {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        activeRules().forEach((code, rule) -> map.put(code, AbrAssessmentAnswers.object(rule.getRuleParams())));
        return map;
    }

    @Override
    public Set<String> recordedMoments(Long visitorId)
    {
        return new HashSet<>(eventMapper.selectMomentCodes(visitorId));
    }

    @Override
    public boolean recordMoment(AbrVisitor visitor, String moment, Map<String, Object> payload)
    {
        AbrMomentRule rule = activeRules().get(moment);
        if (rule == null)
        {
            return false;
        }
        AbrEvent e = new AbrEvent();
        e.setEventType(AbrEvent.TYPE_PLAN_MOMENT);
        e.setVisitorId(visitor.getVisitorId());
        e.setAppUserId(visitor.getAppUserId());
        e.setDedupKey(moment);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("moment", moment);
        if (payload != null)
        {
            body.putAll(payload);
        }
        e.setPayload(MAPPER.writeValueAsString(body));
        if (StringUtils.isBlank(rule.getRecommendLabel()))
        {
            e.setRecommendStatus(AbrEvent.RECOMMEND_NONE);
        }
        else
        {
            e.setRecommendStatus(minor(visitor) ? AbrEvent.RECOMMEND_SUPPRESSED : AbrEvent.RECOMMEND_PENDING);
            e.setRecommendLabel(rule.getRecommendLabel());
        }
        if (eventMapper.insertAbrEvent(e) == 0)
        {
            return false;
        }
        publisher.publishEvent(new PlanMomentDetected(e.getEventId(), visitor.getVisitorId(), visitor.getAppUserId(), moment,
                StringUtils.trimToNull(rule.getRecommendProduct())));
        return true;
    }

    @Override
    public AppPlanVo.Recommendation takeRecommendation(AbrVisitor visitor)
    {
        AbrEvent pending = eventMapper.selectPendingRecommendation(visitor.getVisitorId());
        if (pending == null)
        {
            return null;
        }
        Date last = eventMapper.selectLastShownTime(visitor.getVisitorId());
        if (last != null && System.currentTimeMillis() - last.getTime() < minIntervalHours * 3600_000L)
        {
            return null;
        }
        if (minor(visitor))
        {
            return null;
        }
        AbrMomentRule rule = activeRules().get(pending.getDedupKey());
        if (rule == null || StringUtils.isBlank(rule.getRecommendLabel()) || eventMapper.markRecommendationShown(pending.getEventId()) == 0)
        {
            return null;
        }
        return new AppPlanVo.Recommendation(pending.getEventId(), pending.getDedupKey(), rule.getRecommendLabel(),
                StringUtils.trimToNull(rule.getRecommendText()), StringUtils.trimToNull(rule.getRecommendProduct()));
    }

    /**
     * 启用的规则（时刻编码 → 规则）
     */
    private Map<String, AbrMomentRule> activeRules()
    {
        AbrMomentRule query = new AbrMomentRule();
        query.setStatus("0");
        Map<String, AbrMomentRule> map = new LinkedHashMap<>();
        ruleMapper.selectAbrMomentRuleList(query).forEach(r -> map.put(r.getMomentCode(), r));
        return map;
    }

    /**
     * 访客是否为未成年人（任一测评标记了 is_minor）
     */
    private boolean minor(AbrVisitor visitor)
    {
        return assessmentMapper.countMinorAssessments(visitor.getVisitorId()) > 0;
    }
}
