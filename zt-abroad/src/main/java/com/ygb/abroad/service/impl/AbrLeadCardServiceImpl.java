package com.ygb.abroad.service.impl;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.ai.AiContext;
import com.ygb.abroad.ai.AiGateway;
import com.ygb.abroad.ai.AiResult;
import com.ygb.abroad.domain.AbrLead;
import com.ygb.abroad.domain.AbrLeadCard;
import com.ygb.abroad.domain.AbrLeadFollow;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.abroad.mapper.AbrLeadCardMapper;
import com.ygb.abroad.service.IAbrLeadCardService;
import com.ygb.abroad.service.IAbrLeadService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.DictUtils;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 线索卡片 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrLeadCardServiceImpl implements IAbrLeadCardService
{
    private static final Logger log = LoggerFactory.getLogger(AbrLeadCardServiceImpl.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 带给模型的跟进记录条数 */
    static final int FOLLOW_UPS = 3;

    @Autowired
    private AbrLeadCardMapper cardMapper;

    @Autowired
    private IAbrLeadService leadService;

    @Autowired
    private AiGateway gateway;

    @Override
    public AbrLeadCard generate(Long leadId, String trigger, boolean force, String operator)
    {
        AbrLead lead = leadService.selectAbrLeadById(leadId);
        if (lead == null)
        {
            throw new ServiceException("线索不存在");
        }
        Map<String, Object> basics = basics(lead);
        Map<String, Object> input = input(lead, basics);
        String inputJson = MAPPER.writeValueAsString(input);
        String digest = AbrVisitorServiceImpl.hash(inputJson);
        AbrLeadCard before = cardMapper.selectByLead(lead.getLeadId());
        AbrLeadCard card = new AbrLeadCard();
        card.setLeadId(lead.getLeadId());
        card.setBasics(MAPPER.writeValueAsString(basics));
        card.setInputDigest(digest);
        card.setTriggerType(trigger);
        card.setCreateBy(operator);
        if (!force && before != null && AbrLeadCard.STATUS_DONE.equals(before.getCardStatus()) && digest.equals(before.getInputDigest()))
        {
            // 输入没有变化：沿用上次的 AI 内容，只刷新基础信息
            card.setCardStatus(before.getCardStatus());
            card.setAiContent(before.getAiContent());
            card.setAiCallId(before.getAiCallId());
            card.setAiStatus(before.getAiStatus());
            cardMapper.upsertCard(card);
            return cardMapper.selectByLead(lead.getLeadId());
        }
        card.setCardStatus(AbrLeadCard.STATUS_FALLBACK);
        try
        {
            AiContext ctx = AiContext.of("lead", String.valueOf(lead.getLeadId()), operator);
            if (StringUtils.isNotBlank(lead.getDisplayName()))
            {
                ctx.setKnownNames(List.of(lead.getDisplayName()));
            }
            AiResult ai = gateway.invoke(AGENT, input, ctx);
            card.setAiCallId(ai.getCallId());
            card.setAiStatus(ai.getStatus());
            if (ai.isSuccess())
            {
                String error = AbrLeadCardRules.validate(ai.getOutput());
                if (error == null)
                {
                    card.setCardStatus(AbrLeadCard.STATUS_DONE);
                    card.setAiContent(ai.getOutput().toString());
                }
                else
                {
                    card.setErrorMsg(StringUtils.substring("未通过校验：" + error, 0, 500));
                }
            }
            else
            {
                card.setErrorMsg(StringUtils.substring(ai.getMessage(), 0, 500));
            }
        }
        catch (RuntimeException e)
        {
            log.warn("线索卡片生成失败 leadId={}：{}", lead.getLeadId(), e.getMessage());
            card.setAiStatus("error");
            card.setErrorMsg(StringUtils.substring(e.getMessage(), 0, 500));
        }
        cardMapper.upsertCard(card);
        return cardMapper.selectByLead(lead.getLeadId());
    }

    @Override
    public AbrLeadCard selectByLead(Long leadId)
    {
        AbrLead lead = leadService.selectAbrLeadById(leadId);
        return lead == null ? null : cardMapper.selectByLead(lead.getLeadId());
    }

    /**
     * 基础信息：规则取数，始终展示给顾问
     */
    Map<String, Object> basics(AbrLead lead)
    {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("grade", lead.getGrade());
        b.put("score", lead.getScore());
        b.put("scoreReasons", reasons(lead.getScoreReasons()));
        b.put("stage", label("abr_lead_stage", lead.getStage()));
        b.put("source", label("abr_lead_source", lead.getFirstSource()));
        b.put("channel", StringUtils.defaultString(lead.getFirstChannel()));
        b.put("minor", "Y".equals(lead.getMinor()));
        b.put("owner", StringUtils.defaultString(lead.getOwnerName()));
        b.put("lastTouch", lead.getLastTouchTime() == null ? "" : DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, lead.getLastTouchTime()));
        b.put("daysSinceLastTouch", lead.getLastTouchTime() == null ? null
                : ChronoUnit.DAYS.between(MarketingCalendar.toLocalDate(lead.getLastTouchTime()), MarketingCalendar.today()));
        Long v = lead.getVisitorId();
        Map<String, Object> profile = v == null ? null : cardMapper.selectLatestProfile(v);
        Map<String, Object> p = new LinkedHashMap<>();
        if (profile != null)
        {
            p.put("degree", label("abr_degree", str(profile.get("degree"))));
            p.put("field", str(profile.get("field")));
            p.put("countries", countries(str(profile.get("countries"))));
            p.put("undergradTier", str(profile.get("undergradTier")));
            p.put("gpa", profile.get("gpa"));
            p.put("english", AbrLeadCardRules.english(profile.get("ieltsTotal"), profile.get("ieltsMinSub"), str(profile.get("englishStatus"))));
            p.put("monthsToIntake", profile.get("monthsToIntake"));
        }
        b.put("profile", p);
        Map<String, Object> result = v == null ? null : cardMapper.selectLatestResult(v);
        Map<String, Object> r = new LinkedHashMap<>();
        if (result != null)
        {
            r.put("reach", result.get("reach"));
            r.put("match", result.get("match"));
            r.put("safety", result.get("safety"));
            r.put("needsImprovement", result.get("needsImprovement"));
            List<Object[]> reps = AbrLeadCardRules.representatives(json(str(result.get("programs"))));
            List<Long> ids = new ArrayList<>();
            reps.forEach(x -> ids.add((Long) x[0]));
            Map<Long, Map<String, Object>> names = new LinkedHashMap<>();
            if (!ids.isEmpty())
            {
                cardMapper.selectProgramNames(ids).forEach(n -> names.put(((Number) n.get("programId")).longValue(), n));
            }
            List<Map<String, Object>> programs = new ArrayList<>();
            for (Object[] x : reps)
            {
                Map<String, Object> n = names.get((Long) x[0]);
                if (n != null)
                {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("school", n.get("school"));
                    m.put("program", n.get("program"));
                    m.put("tier", x[1]);
                    m.put("missing", x[2]);
                    programs.add(m);
                }
            }
            r.put("programs", programs);
        }
        b.put("result", r);
        List<String> plan = new ArrayList<>();
        if (v != null)
        {
            cardMapper.selectPlanPrograms(v).forEach(x -> plan.add(x.get("school") + " " + x.get("program")));
        }
        b.put("planItems", plan);
        List<String> moments = new ArrayList<>();
        if (v != null)
        {
            cardMapper.selectMoments(v).forEach(m -> moments.add(AbrLeadRules.MOMENTS.getOrDefault(m, m)));
        }
        b.put("moments", moments);
        List<String> tools = new ArrayList<>();
        if (v != null)
        {
            cardMapper.selectToolCounts(v).forEach(t -> tools.add(AbrLeadRules.TOOL_LABELS.getOrDefault(str(t.get("tool")), str(t.get("tool"))) + " "
                    + t.get("count") + " 次"));
        }
        b.put("tools", tools);
        return b;
    }

    /**
     * 模型输入：基础信息 + 最近几条跟进（不含手机号；称呼作为已知姓名去标识化）
     */
    private Map<String, Object> input(AbrLead lead, Map<String, Object> b)
    {
        Map<String, Object> in = new LinkedHashMap<>();
        Map<String, Object> l = new LinkedHashMap<>();
        for (String k : new String[] { "grade", "stage", "source", "channel", "minor", "daysSinceLastTouch" })
        {
            l.put(k, b.get(k));
        }
        List<String> reasons = new ArrayList<>();
        for (Object o : (List<?>) b.get("scoreReasons"))
        {
            reasons.add(String.valueOf(o));
        }
        l.put("scoreReasons", reasons);
        in.put("lead", l);
        in.put("profile", b.get("profile"));
        in.put("result", b.get("result"));
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("items", b.get("planItems"));
        plan.put("moments", b.get("moments"));
        in.put("plan", plan);
        in.put("tools", b.get("tools"));
        List<Map<String, Object>> follows = new ArrayList<>();
        for (AbrLeadFollow f : leadService.follows(lead.getLeadId()))
        {
            if (AbrLeadFollow.KIND_FOLLOW.equals(f.getFollowKind()) && follows.size() < FOLLOW_UPS)
            {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("method", label("abr_follow_method", f.getMethod()));
                m.put("content", StringUtils.substring(f.getContent(), 0, 300));
                follows.add(m);
            }
        }
        in.put("followUps", follows);
        return in;
    }

    private static List<String> reasons(String json)
    {
        List<String> out = new ArrayList<>();
        for (JsonNode r : json(json))
        {
            out.add(r.path("label").asString("") + "：" + r.path("detail").asString("") + "（" + r.path("points").asInt(0) + " 分）");
        }
        return out;
    }

    /**
     * 目标国家 / 地区：档案中为逗号分隔的编码（如 UK,US）
     */
    private static List<String> countries(String codes)
    {
        List<String> out = new ArrayList<>();
        for (String c : StringUtils.defaultString(codes).split(","))
        {
            if (StringUtils.isNotBlank(c))
            {
                out.add(label("abr_country", c.trim()));
            }
        }
        return out;
    }

    private static JsonNode json(String text)
    {
        try
        {
            return MAPPER.readTree(StringUtils.isBlank(text) ? "[]" : text);
        }
        catch (JacksonException e)
        {
            return MAPPER.createArrayNode();
        }
    }

    private static String str(Object o)
    {
        return o == null ? null : String.valueOf(o);
    }

    private static String label(String dictType, String value)
    {
        String l = value == null ? null : DictUtils.getDictLabel(dictType, value);
        return StringUtils.isBlank(l) ? StringUtils.defaultString(value) : l;
    }
}
