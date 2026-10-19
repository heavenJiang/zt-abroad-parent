package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.ai.AiCallGuard;
import com.ygb.abroad.domain.AbrBreakerRule;
import com.ygb.abroad.domain.AbrBreakerTrip;
import com.ygb.abroad.domain.AbrEvent;
import com.ygb.abroad.domain.AbrGrowthAgent;
import com.ygb.abroad.domain.AbrGrowthSetting;
import com.ygb.abroad.domain.AbrGrowthSignal;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.domain.AbrTouchLog;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.abroad.growth.OutboundDecision;
import com.ygb.abroad.growth.TouchDecision;
import com.ygb.abroad.growth.TouchPolicy;
import com.ygb.abroad.growth.TouchRequest;
import com.ygb.abroad.mapper.AbrAssessmentMapper;
import com.ygb.abroad.mapper.AbrBreakerRuleMapper;
import com.ygb.abroad.mapper.AbrBreakerTripMapper;
import com.ygb.abroad.mapper.AbrGrowthAgentMapper;
import com.ygb.abroad.mapper.AbrGrowthSettingMapper;
import com.ygb.abroad.mapper.AbrGrowthSignalMapper;
import com.ygb.abroad.mapper.AbrTouchLogMapper;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.abroad.service.IAbrGrowthGuardService;
import com.ygb.common.utils.StringUtils;

/**
 * 获客执行保障 服务层实现；同时作为 AI 网关的调用前放行检查（只管获客 Agent，其他 Agent 如 report_explainer 不受影响）
 *
 * 预算闸按北京时间自然日统计 abr_ai_call_log（不含管理端试用与评测）：超出时当天拦下、次日自动恢复，每个对象每天记一条触发记录。
 * 熔断触发即暂停（持久），须员工确认后手动恢复；恢复后只统计恢复之后的信号，避免立即再次触发。
 *
 * @author ygb
 */
@Service
public class AbrGrowthGuardServiceImpl implements IAbrGrowthGuardService, AiCallGuard
{
    private static final Logger log = LoggerFactory.getLogger(AbrGrowthGuardServiceImpl.class);

    /** 熔断暂停人 */
    static final String BREAKER = "breaker";

    /** 退订范围：营销消息 */
    static final String SCOPE_MARKETING = "marketing";

    /** 退订归因：最近几天内触达过该对象的 Agent */
    private static final int OPTOUT_ATTRIBUTION_DAYS = 7;

    @Autowired
    private AbrGrowthSettingMapper settingMapper;

    @Autowired
    private AbrGrowthAgentMapper agentMapper;

    @Autowired
    private AbrTouchLogMapper touchMapper;

    @Autowired
    private AbrGrowthSignalMapper signalMapper;

    @Autowired
    private AbrBreakerRuleMapper ruleMapper;

    @Autowired
    private AbrBreakerTripMapper tripMapper;

    @Autowired
    private AbrAssessmentMapper assessmentMapper;

    @Autowired
    private IAbrEventService eventService;

    @Override
    public String blockReason(String agentCode)
    {
        if (agentMapper.selectAbrGrowthAgentByCode(agentCode) == null)
        {
            return null;
        }
        return agentBlockReason(agentCode);
    }

    @Override
    public String agentBlockReason(String agentCode)
    {
        return blockReason(setting(), agentMapper.selectAbrGrowthAgentByCode(agentCode), aiUsageToday(), true);
    }

    @Override
    public Map<String, String> blockedAgents()
    {
        AbrGrowthSetting setting = setting();
        Map<String, BigDecimal[]> usage = aiUsageToday();
        Map<String, String> map = new LinkedHashMap<>();
        for (AbrGrowthAgent a : agentMapper.selectAbrGrowthAgentList(new AbrGrowthAgent()))
        {
            String reason = blockReason(setting, a, usage, true);
            if (reason != null)
            {
                map.put(a.getAgentCode(), reason);
            }
        }
        return map;
    }

    @Override
    public boolean modeOff(String agentCode)
    {
        AbrGrowthAgent a = agentMapper.selectAbrGrowthAgentByCode(agentCode);
        return a != null && AbrGrowthAgent.MODE_OFF.equals(a.getExecMode());
    }

    @Override
    public OutboundDecision outbound(String agentCode, String sampleKey)
    {
        AbrGrowthAgent a = agentMapper.selectAbrGrowthAgentByCode(agentCode);
        if (blockReason(setting(), a, aiUsageToday(), true) != null)
        {
            return OutboundDecision.BLOCKED;
        }
        return switch (a.getExecMode())
        {
            case AbrGrowthAgent.MODE_CONFIRM -> OutboundDecision.CONFIRM;
            case AbrGrowthAgent.MODE_AUTO_SAMPLE -> TouchPolicy.sampled(agentCode + ":" + sampleKey, a.getSampleRate())
                    ? OutboundDecision.DIRECT_SAMPLED : OutboundDecision.DIRECT;
            default -> OutboundDecision.DIRECT;
        };
    }

    @Override
    public TouchDecision checkTouch(TouchRequest req)
    {
        AbrGrowthSetting setting = setting();
        String reason = blockReason(setting, agentMapper.selectAbrGrowthAgentByCode(req.agentCode()), null, false);
        if (reason == null && AbrTouchLog.CATEGORY_MARKETING.equals(req.category()))
        {
            reason = marketingBlockReason(setting, req);
        }
        if (reason != null)
        {
            insertTouch(req, AbrTouchLog.STATUS_BLOCKED, reason);
            return TouchDecision.deny(reason);
        }
        ZonedDateTime end = TouchPolicy.quietEnd(ZonedDateTime.now(MarketingCalendar.ZONE), TouchPolicy.parseTime(setting.getQuietStart()),
                TouchPolicy.parseTime(setting.getQuietEnd()));
        if (end != null)
        {
            return TouchDecision.defer(Date.from(end.toInstant()), "免打扰时段（" + setting.getQuietStart() + "–" + setting.getQuietEnd() + "）");
        }
        return TouchDecision.allow();
    }

    @Override
    public void recordTouch(TouchRequest req, String status, String reason)
    {
        insertTouch(req, status, reason);
    }

    @Override
    public void reportSignal(AbrGrowthSignal signal)
    {
        if (StringUtils.isBlank(signal.getSource()))
        {
            signal.setSource("system");
        }
        signalMapper.insertAbrGrowthSignal(signal);
    }

    @Override
    public boolean marketingOptedOut(String targetType, Long targetId)
    {
        return touchMapper.existsOptout(targetType, targetId, SCOPE_MARKETING);
    }

    @Override
    public boolean setMarketingOptout(String targetType, Long targetId, boolean optout, String source, String operator, String reason)
    {
        if (!optout)
        {
            return touchMapper.deleteOptout(targetType, targetId, SCOPE_MARKETING) > 0;
        }
        if (touchMapper.insertOptout(targetType, targetId, SCOPE_MARKETING, source, StringUtils.substring(reason, 0, 500), operator) == 0)
        {
            return false;
        }
        if ("user".equals(source))
        {
            AbrGrowthSignal s = new AbrGrowthSignal();
            s.setSignalType(AbrGrowthSignal.TYPE_UNSUBSCRIBE);
            s.setAgentCode(touchMapper.selectLastTouchAgent(targetType, targetId, daysAgo(OPTOUT_ATTRIBUTION_DAYS)));
            s.setTargetType(targetType);
            s.setTargetId(targetId);
            s.setSource("system");
            signalMapper.insertAbrGrowthSignal(s);
        }
        return true;
    }

    @Override
    public int evaluateBreakers()
    {
        AbrBreakerRule query = new AbrBreakerRule();
        query.setStatus("0");
        List<AbrBreakerRule> rules = ruleMapper.selectAbrBreakerRuleList(query);
        if (rules.isEmpty())
        {
            return 0;
        }
        AbrGrowthSetting setting = setting();
        Map<String, AbrGrowthAgent> agents = new LinkedHashMap<>();
        agentMapper.selectAbrGrowthAgentList(new AbrGrowthAgent()).forEach(a -> agents.put(a.getAgentCode(), a));
        int trips = 0;
        for (AbrBreakerRule rule : rules)
        {
            try
            {
                trips += AbrBreakerRule.SCOPE_GLOBAL.equals(rule.getScope()) ? evaluateGlobal(rule, setting) : evaluateAgents(rule, agents);
            }
            catch (RuntimeException e)
            {
                log.warn("熔断规则 {} 评估失败：{}", rule.getRuleCode(), e.getMessage());
            }
        }
        return trips;
    }

    /**
     * 全局规则：全部 Agent 合计，达到阈值时全局暂停
     */
    private int evaluateGlobal(AbrBreakerRule rule, AbrGrowthSetting setting)
    {
        if ("Y".equals(setting.getPaused()))
        {
            return 0;
        }
        Date since = windowStart(rule, setting.getResumeTime());
        long[] m = total(measure(rule.getMetric(), since));
        BigDecimal value = TouchPolicy.breach(ratio(rule.getMetric()), m[0], m[1], rule.getThreshold(), minSample(rule));
        if (value == null)
        {
            return 0;
        }
        String reason = "熔断：" + rule.getRuleName() + "（当前 " + value.stripTrailingZeros().toPlainString() + "）";
        if (settingMapper.pauseGlobal(reason, BREAKER) == 0)
        {
            return 0;
        }
        trip(rule, null, value, m[1], since, "全局暂停");
        return 1;
    }

    /**
     * 按 Agent 规则：逐个 Agent 统计，达到阈值时暂停该 Agent
     */
    private int evaluateAgents(AbrBreakerRule rule, Map<String, AbrGrowthAgent> agents)
    {
        Date windowStart = windowStart(rule, null);
        Map<String, long[]> measured = measure(rule.getMetric(), windowStart);
        int trips = 0;
        for (Map.Entry<String, long[]> e : measured.entrySet())
        {
            AbrGrowthAgent agent = agents.get(e.getKey());
            if (agent == null || "Y".equals(agent.getPaused())
                    || (StringUtils.isNotBlank(rule.getAgentCode()) && !rule.getAgentCode().equals(agent.getAgentCode())))
            {
                continue;
            }
            long[] m = e.getValue();
            Date since = windowStart;
            if (agent.getResumeTime() != null && agent.getResumeTime().after(windowStart))
            {
                since = agent.getResumeTime();
                m = measure(rule.getMetric(), since).getOrDefault(agent.getAgentCode(), new long[2]);
            }
            BigDecimal value = TouchPolicy.breach(ratio(rule.getMetric()), m[0], m[1], rule.getThreshold(), minSample(rule));
            if (value == null)
            {
                continue;
            }
            String reason = "熔断：" + rule.getRuleName() + "（当前 " + value.stripTrailingZeros().toPlainString() + "）";
            if (agentMapper.pauseAgent(agent.getAgentCode(), reason, BREAKER) > 0)
            {
                trip(rule, agent.getAgentCode(), value, m[1], since, "暂停 " + agent.getAgentCode());
                trips++;
            }
        }
        return trips;
    }

    /**
     * 统计指标：Agent 编码（无 Agent 为空串）→ [分子, 分母]（计数类分母为 0）
     */
    Map<String, long[]> measure(String metric, Date since)
    {
        Map<String, long[]> map = new HashMap<>();
        switch (metric)
        {
            case "unsubscribe_rate" ->
            {
                add(map, signalMapper.countSignalsByAgent(AbrGrowthSignal.TYPE_UNSUBSCRIBE, since), 0);
                add(map, touchMapper.countMarketingSentByAgent(since), 1);
            }
            case "compliance_block_rate" ->
            {
                add(map, signalMapper.countSignalsByAgent(AbrGrowthSignal.TYPE_COMPLIANCE_BLOCK, since), 0);
                add(map, signalMapper.countSignalsByAgent(AbrGrowthSignal.TYPE_COMPLIANCE_BLOCK, since), 1);
                add(map, signalMapper.countSignalsByAgent(AbrGrowthSignal.TYPE_COMPLIANCE_PASS, since), 1);
            }
            default -> add(map, signalMapper.countSignalsByAgent(metric, since), 0);
        }
        return map;
    }

    private static void add(Map<String, long[]> map, List<Map<String, Object>> rows, int index)
    {
        for (Map<String, Object> row : rows)
        {
            String agent = row.get("agentCode") == null ? "" : String.valueOf(row.get("agentCode"));
            map.computeIfAbsent(agent, k -> new long[2])[index] += ((Number) row.get("count")).longValue();
        }
    }

    private static long[] total(Map<String, long[]> map)
    {
        long[] t = new long[2];
        map.values().forEach(v -> {
            t[0] += v[0];
            t[1] += v[1];
        });
        return t;
    }

    static boolean ratio(String metric)
    {
        return metric != null && metric.endsWith("_rate");
    }

    private static int minSample(AbrBreakerRule rule)
    {
        return rule.getMinSample() == null ? 0 : rule.getMinSample();
    }

    private static Date windowStart(AbrBreakerRule rule, Date resumeTime)
    {
        Date start = new Date(System.currentTimeMillis() - (rule.getWindowHours() == null ? 24 : rule.getWindowHours()) * 3600_000L);
        return resumeTime != null && resumeTime.after(start) ? resumeTime : start;
    }

    private void trip(AbrBreakerRule rule, String agentCode, BigDecimal value, long sample, Date since, String action)
    {
        AbrBreakerTrip t = new AbrBreakerTrip();
        t.setRuleId(rule.getRuleId());
        t.setRuleCode(rule.getRuleCode());
        t.setMetric(rule.getMetric());
        t.setScope(rule.getScope());
        t.setAgentCode(agentCode);
        t.setMetricValue(value);
        t.setSampleSize(ratio(rule.getMetric()) ? (int) sample : null);
        t.setThreshold(rule.getThreshold());
        t.setWindowStart(since);
        t.setAction(action);
        tripMapper.insertAbrBreakerTrip(t);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trip_id", t.getTripId());
        payload.put("rule_code", rule.getRuleCode());
        payload.put("metric", rule.getMetric());
        payload.put("scope", rule.getScope());
        payload.put("agent_code", agentCode);
        payload.put("value", value);
        payload.put("threshold", rule.getThreshold());
        payload.put("action", action);
        eventService.recordSystemEvent(AbrEvent.TYPE_CIRCUIT_BREAKER_TRIPPED, "trip:" + t.getTripId(), payload);
        log.warn("熔断触发：规则 {}，{}，指标 {} = {}（阈值 {}）", rule.getRuleCode(), action, rule.getMetric(), value, rule.getThreshold());
    }

    /**
     * 不能运行的原因
     *
     * @param usage 今天的 AI 用量（为 null 时不检查预算：触达判断不受 AI 预算影响）
     * @param recordBudget 超出预算时是否写触发记录
     */
    private String blockReason(AbrGrowthSetting setting, AbrGrowthAgent agent, Map<String, BigDecimal[]> usage, boolean recordBudget)
    {
        if ("Y".equals(setting.getPaused()))
        {
            return "获客自动化已全局暂停：" + StringUtils.defaultString(setting.getPauseReason());
        }
        if (agent == null)
        {
            return "未配置运行参数（abr_growth_agent）";
        }
        if (AbrGrowthAgent.MODE_OFF.equals(agent.getExecMode()))
        {
            return "执行模式为停用";
        }
        if ("Y".equals(agent.getPaused()))
        {
            return "已暂停：" + StringUtils.defaultString(agent.getPauseReason());
        }
        if (usage == null)
        {
            return null;
        }
        BigDecimal[] mine = usage.getOrDefault(agent.getAgentCode(), new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
        if (TouchPolicy.overBudget(mine[0], agent.getDailyCostBudget())
                || TouchPolicy.overBudget(mine[1], agent.getDailyTokenBudget() == null ? null : BigDecimal.valueOf(agent.getDailyTokenBudget())))
        {
            budgetTrip("budget_agent", agent.getAgentCode(), mine, agent.getDailyCostBudget(), agent.getDailyTokenBudget(), recordBudget);
            return "超出该 Agent 今天的 AI 预算（次日自动恢复）";
        }
        BigDecimal[] all = usage.getOrDefault("", new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
        if (TouchPolicy.overBudget(all[0], setting.getDailyCostBudget())
                || TouchPolicy.overBudget(all[1], setting.getDailyTokenBudget() == null ? null : BigDecimal.valueOf(setting.getDailyTokenBudget())))
        {
            budgetTrip("budget_global", null, all, setting.getDailyCostBudget(), setting.getDailyTokenBudget(), recordBudget);
            return "超出获客 Agent 今天的 AI 总预算（次日自动恢复）";
        }
        return null;
    }

    /**
     * 预算闸触发记录：每个对象每天一条
     */
    private void budgetTrip(String code, String agentCode, BigDecimal[] used, BigDecimal costBudget, Long tokenBudget, boolean record)
    {
        if (!record)
        {
            return;
        }
        boolean byCost = TouchPolicy.overBudget(used[0], costBudget);
        AbrBreakerTrip t = new AbrBreakerTrip();
        t.setRuleCode(code);
        t.setMetric(byCost ? "daily_cost" : "daily_tokens");
        t.setScope(agentCode == null ? AbrBreakerRule.SCOPE_GLOBAL : AbrBreakerRule.SCOPE_AGENT);
        t.setAgentCode(agentCode);
        t.setMetricValue(byCost ? used[0] : used[1]);
        t.setThreshold(byCost ? costBudget : BigDecimal.valueOf(tokenBudget));
        t.setWindowStart(todayStart());
        t.setAction("当天停止调用模型，次日自动恢复");
        t.setDedupKey(code + ":" + StringUtils.defaultString(agentCode) + ":" + MarketingCalendar.today());
        if (tripMapper.insertAbrBreakerTrip(t) > 0)
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("trip_id", t.getTripId());
            payload.put("scope", t.getScope());
            payload.put("agent_code", agentCode);
            payload.put("metric", t.getMetric());
            payload.put("value", t.getMetricValue());
            payload.put("budget", t.getThreshold());
            eventService.recordSystemEvent(AbrEvent.TYPE_BUDGET_EXCEEDED, t.getDedupKey(), payload);
            log.warn("预算闸：{} 今天的 AI 用量 {} 已达上限 {}", agentCode == null ? "全部获客 Agent" : agentCode, t.getMetricValue(), t.getThreshold());
        }
    }

    /**
     * 营销触达的限制：退订、未成年人（或无法核验年龄）、每 7 天上限
     */
    private String marketingBlockReason(AbrGrowthSetting setting, TouchRequest req)
    {
        if (touchMapper.existsOptout(req.targetType(), req.targetId(), SCOPE_MARKETING))
        {
            return "已退订营销消息";
        }
        if (AbrGrowthTask.TARGET_VISITOR.equals(req.targetType()))
        {
            if (assessmentMapper.countMinorAssessments(req.targetId()) > 0)
            {
                return "未成年人不接收营销消息";
            }
        }
        else
        {
            // 学生账号、线索的年龄与家长关系在 M4-15 / M5-01 接入；接入前无法核验是否成年，营销消息一律不发
            return "暂无法核验该对象是否成年（" + req.targetType() + "），营销消息不发";
        }
        int cap = setting.getWeeklyTouchCap() == null ? 0 : setting.getWeeklyTouchCap();
        if (touchMapper.countMarketingSent(req.targetType(), req.targetId(), daysAgo(7)) >= cap)
        {
            return "频控：7 天内营销触达已达 " + cap + " 次";
        }
        return null;
    }

    private void insertTouch(TouchRequest req, String status, String reason)
    {
        AbrTouchLog t = new AbrTouchLog();
        t.setTaskId(req.taskId());
        t.setAgentCode(req.agentCode());
        t.setTargetType(req.targetType());
        t.setTargetId(req.targetId());
        t.setChannel(req.channel());
        t.setCategory(StringUtils.defaultIfBlank(req.category(), AbrTouchLog.CATEGORY_MARKETING));
        t.setStatus(status);
        t.setReason(StringUtils.substring(reason, 0, 500));
        t.setContentRef(req.contentRef());
        touchMapper.insertAbrTouchLog(t);
    }

    /**
     * 今天各获客 Agent 的 AI 用量：Agent 编码 → [费用, token]；空串键为全部获客 Agent 合计
     */
    @Override
    public Map<String, BigDecimal[]> aiUsageToday()
    {
        Map<String, BigDecimal[]> map = new HashMap<>();
        BigDecimal[] total = { BigDecimal.ZERO, BigDecimal.ZERO };
        Set<String> growth = new HashSet<>();
        agentMapper.selectAbrGrowthAgentList(new AbrGrowthAgent()).forEach(a -> growth.add(a.getAgentCode()));
        for (Map<String, Object> row : agentMapper.selectAiUsageSince(todayStart(), null))
        {
            String code = String.valueOf(row.get("agentCode"));
            if (!growth.contains(code))
            {
                continue;
            }
            BigDecimal cost = new BigDecimal(String.valueOf(row.get("cost")));
            BigDecimal tokens = new BigDecimal(String.valueOf(row.get("tokens")));
            map.put(code, new BigDecimal[] { cost, tokens });
            total[0] = total[0].add(cost);
            total[1] = total[1].add(tokens);
        }
        map.put("", total);
        return map;
    }

    private AbrGrowthSetting setting()
    {
        AbrGrowthSetting s = settingMapper.selectAbrGrowthSetting();
        if (s == null)
        {
            // 设置行缺失时按全局暂停处理（安全优先）
            s = new AbrGrowthSetting();
            s.setPaused("Y");
            s.setPauseReason("全局设置缺失（abr_growth_setting）");
        }
        return s;
    }

    static Date todayStart()
    {
        return Date.from(MarketingCalendar.today().atStartOfDay(MarketingCalendar.ZONE).toInstant());
    }

    private static Date daysAgo(int days)
    {
        return new Date(System.currentTimeMillis() - days * 86_400_000L);
    }

    /**
     * 免打扰时段（供管理端展示）
     */
    static boolean quietNow(AbrGrowthSetting setting)
    {
        LocalTime now = ZonedDateTime.now(MarketingCalendar.ZONE).toLocalTime();
        return TouchPolicy.inQuietHours(now, TouchPolicy.parseTime(setting.getQuietStart()), TouchPolicy.parseTime(setting.getQuietEnd()));
    }
}
