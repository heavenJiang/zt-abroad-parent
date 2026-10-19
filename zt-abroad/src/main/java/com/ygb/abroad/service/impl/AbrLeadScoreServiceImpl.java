package com.ygb.abroad.service.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrLead;
import com.ygb.abroad.domain.AbrLeadScoreRule;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.abroad.mapper.AbrLeadMapper;
import com.ygb.abroad.mapper.AbrLeadScoreRuleMapper;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.abroad.service.IAbrLeadScoreService;
import com.ygb.abroad.service.IAbrLeadService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 线索评分 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrLeadScoreServiceImpl implements IAbrLeadScoreService
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    static final Set<String> GRADES = Set.of("A", "B", "C");

    @Autowired
    private AbrLeadMapper leadMapper;

    @Autowired
    private AbrLeadScoreRuleMapper ruleMapper;

    @Autowired
    private IAbrLeadService leadService;

    @Autowired
    private IAbrEventService eventService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbrLead score(Long leadId, String operator)
    {
        AbrLead lead = root(leadId);
        Map<String, JsonNode> rules = activeRules();
        LocalDate today = MarketingCalendar.today();
        int days = rules.containsKey(AbrLeadScorer.TOOLS) ? rules.get(AbrLeadScorer.TOOLS).path("days").asInt(30) : 30;
        Map<String, Object> f = leadMapper.selectScoreFeatures(lead.getLeadId(), lead.getVisitorId(), lead.getAppUserId(),
                Date.from(today.minusDays(days).atStartOfDay(MarketingCalendar.ZONE).toInstant()));
        AbrLeadScorer.Result r = AbrLeadScorer.score(features(lead, f, today), rules);
        String before = lead.getGrade();
        leadMapper.updateScore(lead.getLeadId(), r.score(), r.grade(), MAPPER.writeValueAsString(r.reasons()));
        boolean overridden = "Y".equals(lead.getGradeOverride());
        String after = overridden ? before : r.grade();
        if (!r.grade().equals(lead.getRuleGrade()) || !StringUtils.equals(before, after))
        {
            leadMapper.insertScoreLog(lead.getLeadId(), before, after, r.score(), r.grade(), "rule", overridden ? "顾问已修改等级，只更新规则等级" : "", operator);
        }
        if (!StringUtils.equals(before, after))
        {
            gradeChanged(lead.getLeadId(), before, after, "rule");
        }
        return leadService.selectAbrLeadById(lead.getLeadId());
    }

    @Override
    public AbrLead scoreByVisitor(Long visitorId, Long appUserId, String operator)
    {
        if (visitorId == null && appUserId == null)
        {
            return null;
        }
        Long id = leadMapper.selectLeadIdByVisitor(visitorId, appUserId);
        return id == null ? null : score(id, operator);
    }

    @Override
    public Map<String, Object> rescoreAll(String operator)
    {
        int count = 0;
        int changed = 0;
        for (Long id : leadMapper.selectScorableLeadIds())
        {
            AbrLead before = leadMapper.selectAbrLeadById(id);
            AbrLead after = score(id, operator);
            count++;
            changed += StringUtils.equals(before.getGrade(), after.getGrade()) ? 0 : 1;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scored", count);
        m.put("changed", changed);
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbrLead override(Long leadId, String grade, String reason, String operator)
    {
        AbrLead lead = root(leadId);
        String g = StringUtils.trimToEmpty(grade).toUpperCase();
        String text = StringUtils.trimToEmpty(reason);
        if (text.length() > 500)
        {
            throw new ServiceException("原因最多 500 个字符");
        }
        if (g.isEmpty())
        {
            if (!"Y".equals(lead.getGradeOverride()))
            {
                throw new ServiceException("这条线索的等级没有被修改过");
            }
            leadMapper.clearOverride(lead.getLeadId(), operator);
            leadMapper.insertScoreLog(lead.getLeadId(), lead.getGrade(), lead.getRuleGrade(), lead.getScore(), lead.getRuleGrade(), "reset",
                    StringUtils.defaultIfBlank(text, "恢复规则等级"), operator);
            if (!StringUtils.equals(lead.getGrade(), lead.getRuleGrade()))
            {
                gradeChanged(lead.getLeadId(), lead.getGrade(), lead.getRuleGrade(), "reset");
            }
            return leadService.selectAbrLeadById(lead.getLeadId());
        }
        if (!GRADES.contains(g))
        {
            throw new ServiceException("意向度只能是 A / B / C");
        }
        if (text.isEmpty())
        {
            throw new ServiceException("修改意向度须填写原因");
        }
        leadMapper.setOverride(lead.getLeadId(), g, text, operator);
        leadMapper.insertScoreLog(lead.getLeadId(), lead.getGrade(), g, lead.getScore(), lead.getRuleGrade(), "manual", text, operator);
        if (!StringUtils.equals(lead.getGrade(), g))
        {
            gradeChanged(lead.getLeadId(), lead.getGrade(), g, "manual");
        }
        return leadService.selectAbrLeadById(lead.getLeadId());
    }

    @Override
    public List<Map<String, Object>> scoreLog(Long leadId)
    {
        return leadMapper.selectScoreLog(root(leadId).getLeadId());
    }

    @Override
    public List<AbrLeadScoreRule> selectRules()
    {
        return ruleMapper.selectAbrLeadScoreRuleList();
    }

    @Override
    public int updateRule(AbrLeadScoreRule rule)
    {
        AbrLeadScoreRule before = rule.getRuleId() == null ? null : ruleMapper.selectAbrLeadScoreRuleById(rule.getRuleId());
        if (before == null)
        {
            throw new ServiceException("规则不存在");
        }
        JsonNode params;
        try
        {
            params = MAPPER.readTree(StringUtils.defaultIfBlank(rule.getRuleParams(), "{}"));
        }
        catch (JacksonException e)
        {
            throw new ServiceException("参数不是合法的 JSON");
        }
        String error = AbrLeadScorer.validate(before.getRuleCode(), params);
        if (error != null)
        {
            throw new ServiceException(error);
        }
        String status = "1".equals(rule.getStatus()) ? "1" : "0";
        if (AbrLeadScorer.GRADE.equals(before.getRuleCode()) && "1".equals(status))
        {
            throw new ServiceException("分级线不能停用");
        }
        rule.setRuleParams(params.toString());
        rule.setStatus(status);
        rule.setRemark(StringUtils.defaultIfBlank(rule.getRemark(), before.getRemark()));
        return ruleMapper.updateAbrLeadScoreRule(rule);
    }

    private void gradeChanged(Long leadId, String from, String to, String type)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lead_id", leadId);
        payload.put("from", from);
        payload.put("to", to);
        payload.put("type", type);
        eventService.recordSystemEvent(EVENT_GRADE_CHANGED, null, payload);
    }

    private AbrLead root(Long leadId)
    {
        AbrLead l = leadId == null ? null : leadMapper.selectAbrLeadById(leadId);
        for (int i = 0; i < AbrLeadServiceImpl.MAX_HOPS && l != null && l.getMergedInto() != null; i++)
        {
            l = leadMapper.selectAbrLeadById(l.getMergedInto());
        }
        if (l == null || l.getMergedInto() != null)
        {
            throw new ServiceException("线索不存在");
        }
        return l;
    }

    private Map<String, JsonNode> activeRules()
    {
        Map<String, JsonNode> m = new LinkedHashMap<>();
        for (AbrLeadScoreRule r : ruleMapper.selectAbrLeadScoreRuleList())
        {
            if ("0".equals(r.getStatus()) || AbrLeadScorer.GRADE.equals(r.getRuleCode()))
            {
                try
                {
                    m.put(r.getRuleCode(), MAPPER.readTree(StringUtils.defaultIfBlank(r.getRuleParams(), "{}")));
                }
                catch (JacksonException e)
                {
                    // 库中参数格式不对的规则跳过
                }
            }
        }
        return m;
    }

    /**
     * 由线索与查询结果得到特征；距入学月数优先按档案中的入学年份（9 月入学）计算
     */
    static AbrLeadScorer.Features features(AbrLead lead, Map<String, Object> f, LocalDate today)
    {
        Set<String> moments = new LinkedHashSet<>(split(f.get("moments")));
        Integer months = null;
        String year = f.get("intakeYear") == null ? "" : String.valueOf(f.get("intakeYear")).replace("\"", "");
        if (year.matches("^20\\d{2}$"))
        {
            months = (int) ChronoUnit.MONTHS.between(today.withDayOfMonth(1), LocalDate.of(Integer.parseInt(year), 9, 1));
        }
        else if (f.get("monthsToIntake") instanceof Number n)
        {
            months = n.intValue();
        }
        Date last = lead.getLastTouchTime();
        long days = last == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(MarketingCalendar.toLocalDate(last), today);
        return new AbrLeadScorer.Features(StringUtils.isNotBlank(lead.getPhoneHash()), lead.getAppUserId() != null, moments, months,
                num(f.get("submitted")) > 0, (int) num(f.get("toolRuns")), (int) num(f.get("planItems")), num(f.get("shares")) > 0,
                num(f.get("shareViews")) > 0, new LinkedHashSet<>(split(f.get("sources"))), days);
    }

    private static List<String> split(Object v)
    {
        return v == null ? List.of() : Arrays.stream(String.valueOf(v).split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static long num(Object v)
    {
        return v instanceof Number n ? n.longValue() : 0;
    }
}
