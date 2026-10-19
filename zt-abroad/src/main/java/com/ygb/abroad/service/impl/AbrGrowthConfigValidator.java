package com.ygb.abroad.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import com.ygb.abroad.domain.AbrCalendarEntry;
import com.ygb.abroad.domain.AbrGrowthTrigger;
import com.ygb.abroad.domain.AbrPlaybook;
import com.ygb.abroad.growth.CronSchedule;
import com.ygb.abroad.growth.EventFilter;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 营销编排器配置校验（剧本、营销日历、触发规则）：不合法时抛 ServiceException，合法时把 JSON 字段规范化后写回
 *
 * 可选值（学段、国家 / 地区、钩子、内容类型、Agent）由调用方从字典读取后传入，本类不访问数据库。
 *
 * @author ygb
 */
public final class AbrGrowthConfigValidator
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 剧本文字中不允许出现的价格写法（价格只来自产品配置，见《获客系统设计》） */
    private static final Pattern PRICE = Pattern.compile("[¥￥$]|\\d+\\s*元");

    /** 编码类字段（节点编码、产品编码、事件类型）格式 */
    private static final String CODE_PATTERN = "^[a-z][a-z0-9_]{1,63}$";

    /** 备用选题最多条数与单条长度 */
    private static final int MAX_TOPICS = 30;

    private static final int MAX_TOPIC_CHARS = 100;

    /** 主推产品最多个数 */
    private static final int MAX_PRODUCTS = 20;

    /** 动作参数 JSON 最大长度 */
    private static final int MAX_PARAMS_CHARS = 4000;

    private AbrGrowthConfigValidator()
    {
    }

    /**
     * 校验剧本并规范化 JSON 字段
     *
     * @param p 剧本
     * @param degrees 可选学段
     * @param countries 可选国家 / 地区
     * @param hookTools 可选钩子
     * @param contentKinds 可选内容类型
     */
    public static void checkPlaybook(AbrPlaybook p, Set<String> degrees, Set<String> countries, Set<String> hookTools, Set<String> contentKinds)
    {
        p.setDegrees(codeArray(p.getDegrees(), "适用学段", degrees, 10));
        p.setCountries(codeArray(p.getCountries(), "主推国家 / 地区", countries, 20));
        p.setHookTools(codeArray(p.getHookTools(), "主推钩子", hookTools, 10));
        p.setProducts(codeArray(p.getProducts(), "主推产品", null, MAX_PRODUCTS));
        p.setContentMix(contentMix(p.getContentMix(), contentKinds));
        p.setBackupTopics(topics(p.getBackupTopics()));
        p.setStatus(status(p.getStatus()));
        for (String text : new String[] { p.getAudience(), p.getContentFocus(), p.getNurtureActions(), p.getBackupTopics() })
        {
            if (StringUtils.isNotBlank(text) && PRICE.matcher(text).find())
            {
                throw new ServiceException("剧本中不要写价格（价格在产品配置中维护，Agent 只引用产品配置）");
            }
        }
        noTags("剧本", p.getPlaybookName(), p.getAudience(), p.getContentFocus(), p.getNurtureActions(), p.getBackupTopics(), p.getRemark());
    }

    /**
     * 校验日历条目（节点的结束日期置为开始日期）
     *
     * @param e 日历条目
     * @param playbookExists 时间窗所选剧本是否存在
     */
    public static void checkCalendarEntry(AbrCalendarEntry e, boolean playbookExists)
    {
        noTags("日历条目", e.getEntryName(), e.getRemark());
        boolean repeat = !"N".equals(e.getRepeatYearly());
        e.setRepeatYearly(repeat ? "Y" : "N");
        e.setStatus(status(e.getStatus()));
        LocalDate start = MarketingCalendar.toLocalDate(e.getStartDate());
        if (start == null)
        {
            throw new ServiceException("开始日期不能为空");
        }
        if (AbrCalendarEntry.TYPE_WINDOW.equals(e.getEntryType()))
        {
            if (e.getPlaybookId() == null || !playbookExists)
            {
                throw new ServiceException("时间窗必须选择一个存在的剧本");
            }
            e.setNodeCode(null);
            LocalDate end = MarketingCalendar.toLocalDate(e.getEndDate());
            if (end == null)
            {
                throw new ServiceException("时间窗的结束日期不能为空");
            }
            if (!repeat && end.isBefore(start))
            {
                throw new ServiceException("结束日期不能早于开始日期");
            }
            if (repeat && (end.isBefore(start) || end.isAfter(start.plusYears(1).minusDays(1))))
            {
                throw new ServiceException("每年重复的时间窗：结束日期须在开始日期之后一年以内（跨年时结束日期填次年，如 2026-10-01 至 2027-03-31）");
            }
            if (repeat && isLeapDay(start))
            {
                throw new ServiceException("每年重复的时间窗不能从 2 月 29 日开始");
            }
        }
        else if (AbrCalendarEntry.TYPE_NODE.equals(e.getEntryType()))
        {
            if (StringUtils.isBlank(e.getNodeCode()) || !e.getNodeCode().matches(CODE_PATTERN))
            {
                throw new ServiceException("节点编码必填，只能用小写字母、数字和下划线，以字母开头，2–64 位（触发规则按它过滤）");
            }
            if (e.getPlaybookId() != null && !playbookExists)
            {
                throw new ServiceException("所选剧本不存在");
            }
            if (repeat && isLeapDay(start))
            {
                throw new ServiceException("每年重复的节点不能设在 2 月 29 日");
            }
            e.setEndDate(e.getStartDate());
        }
        else
        {
            throw new ServiceException("日历条目类型只能是时间窗（window）或节点（node）");
        }
    }

    /**
     * 校验触发规则并规范化 JSON 字段
     *
     * @param t 触发规则
     * @param agents 可选 Agent
     * @param playbookExists 限定剧本是否存在（未限定时传 true）
     */
    public static void checkTrigger(AbrGrowthTrigger t, Set<String> agents, boolean playbookExists)
    {
        if (!agents.contains(t.getAgentCode()))
        {
            throw new ServiceException("Agent “" + t.getAgentCode() + "”不在字典 abr_growth_agent 中");
        }
        if (t.getPlaybookId() != null && !playbookExists)
        {
            throw new ServiceException("限定的剧本不存在");
        }
        t.setStatus(status(t.getStatus()));
        if (AbrGrowthTrigger.TYPE_EVENT.equals(t.getTriggerType()))
        {
            if (StringUtils.isBlank(t.getEventType()) || !t.getEventType().matches(CODE_PATTERN))
            {
                throw new ServiceException("事件规则必须填写事件类型（小写字母、数字和下划线，如 plan_moment）");
            }
            JsonNode filter = parse(t.getEventFilter(), "事件过滤", "{}");
            String error = EventFilter.validate(filter);
            if (error != null)
            {
                throw new ServiceException(error);
            }
            t.setEventFilter(MAPPER.writeValueAsString(filter));
            t.setCronExpression(null);
        }
        else if (AbrGrowthTrigger.TYPE_SCHEDULE.equals(t.getTriggerType()))
        {
            String error = CronSchedule.validate(t.getCronExpression());
            if (error != null)
            {
                throw new ServiceException(error);
            }
            t.setCronExpression(t.getCronExpression().trim().replaceAll("\\s+", " "));
            t.setEventType(null);
            t.setEventFilter("{}");
        }
        else
        {
            throw new ServiceException("触发类型只能是事件（event）或定时（schedule）");
        }
        JsonNode params = parse(t.getActionParams(), "动作参数", "{}");
        if (!params.isObject())
        {
            throw new ServiceException("动作参数必须是 JSON 对象");
        }
        String text = MAPPER.writeValueAsString(params);
        if (text.length() > MAX_PARAMS_CHARS)
        {
            throw new ServiceException("动作参数最多 " + MAX_PARAMS_CHARS + " 个字符");
        }
        t.setActionParams(text);
        noTags("触发规则", t.getTriggerName(), t.getEventFilter(), text, t.getRemark());
    }

    /**
     * 编码数组：去重、校验取值
     */
    private static String codeArray(String json, String label, Set<String> allowed, int max)
    {
        JsonNode node = parse(json, label, "[]");
        if (!node.isArray())
        {
            throw new ServiceException(label + "必须是 JSON 数组");
        }
        Set<String> codes = new LinkedHashSet<>();
        for (JsonNode n : node)
        {
            String code = n.isString() ? n.asString().trim() : "";
            if (allowed != null ? !allowed.contains(code) : !code.matches(CODE_PATTERN))
            {
                throw new ServiceException(label + "中的“" + (n.isString() ? n.asString() : n.toString()) + "”不是可选值");
            }
            codes.add(code);
        }
        if (codes.size() > max)
        {
            throw new ServiceException(label + "最多 " + max + " 个");
        }
        ArrayNode out = MAPPER.createArrayNode();
        codes.forEach(out::add);
        return MAPPER.writeValueAsString(out);
    }

    /**
     * 内容配比：各类型 0–100 的整数，合计 100；空对象表示沿用默认
     */
    private static String contentMix(String json, Set<String> kinds)
    {
        JsonNode node = parse(json, "内容配比", "{}");
        if (!node.isObject())
        {
            throw new ServiceException("内容配比必须是 JSON 对象（内容类型 → 百分比）");
        }
        if (node.isEmpty())
        {
            return "{}";
        }
        ObjectNode out = MAPPER.createObjectNode();
        int sum = 0;
        for (Map.Entry<String, JsonNode> e : node.properties())
        {
            if (!kinds.contains(e.getKey()))
            {
                throw new ServiceException("内容配比中的“" + e.getKey() + "”不是可选的内容类型");
            }
            JsonNode v = e.getValue();
            if (!v.isIntegralNumber() || v.asInt() < 0 || v.asInt() > 100)
            {
                throw new ServiceException("内容配比“" + e.getKey() + "”须为 0–100 的整数");
            }
            sum += v.asInt();
            out.put(e.getKey(), v.asInt());
        }
        if (sum != 100)
        {
            throw new ServiceException("内容配比合计须为 100，当前为 " + sum);
        }
        return MAPPER.writeValueAsString(out);
    }

    /**
     * 备用选题：非空字符串数组
     */
    private static String topics(String json)
    {
        JsonNode node = parse(json, "备用选题", "[]");
        if (!node.isArray())
        {
            throw new ServiceException("备用选题必须是 JSON 字符串数组");
        }
        List<String> list = new ArrayList<>();
        for (JsonNode n : node)
        {
            String topic = n.isString() ? n.asString().trim() : "";
            if (topic.isEmpty() || topic.length() > MAX_TOPIC_CHARS)
            {
                throw new ServiceException("备用选题每条须为 1–" + MAX_TOPIC_CHARS + " 个字符的文字");
            }
            if (!list.contains(topic))
            {
                list.add(topic);
            }
        }
        if (list.size() > MAX_TOPICS)
        {
            throw new ServiceException("备用选题最多 " + MAX_TOPICS + " 条");
        }
        ArrayNode out = MAPPER.createArrayNode();
        list.forEach(out::add);
        return MAPPER.writeValueAsString(out);
    }

    private static JsonNode parse(String json, String label, String empty)
    {
        try
        {
            return MAPPER.readTree(StringUtils.isBlank(json) ? empty : json);
        }
        catch (JacksonException e)
        {
            throw new ServiceException(label + "不是合法的 JSON");
        }
    }

    /**
     * 管理端编排器接口不经 XSS 过滤（避免把 > 改成 &gt; 后进入 Agent 提示词），由这里拒绝类似 HTML 标签的写法
     */
    private static void noTags(String label, String... texts)
    {
        for (String text : texts)
        {
            if (StringUtils.isNotBlank(text) && AbrAssessmentAnswers.containsTag(JsonNodeFactory.instance.stringNode(text)))
            {
                throw new ServiceException(label + "中不能包含 < 加字母的写法（类似 HTML 标签）");
            }
        }
    }

    private static String status(String status)
    {
        if (StringUtils.isBlank(status))
        {
            return "0";
        }
        if (!"0".equals(status) && !"1".equals(status))
        {
            throw new ServiceException("状态只能是 0（正常）或 1（停用）");
        }
        return status;
    }

    private static boolean isLeapDay(LocalDate d)
    {
        return d.getMonthValue() == 2 && d.getDayOfMonth() == 29;
    }
}
