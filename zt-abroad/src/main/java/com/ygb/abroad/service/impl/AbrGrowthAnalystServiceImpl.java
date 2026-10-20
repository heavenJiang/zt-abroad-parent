package com.ygb.abroad.service.impl;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.ai.AiContext;
import com.ygb.abroad.ai.AiGateway;
import com.ygb.abroad.ai.AiResult;
import com.ygb.abroad.domain.AbrExperiment;
import com.ygb.abroad.domain.AbrGrowthAgent;
import com.ygb.abroad.domain.AbrGrowthParamLog;
import com.ygb.abroad.domain.AbrGrowthReport;
import com.ygb.abroad.domain.vo.AbrAttributionRow;
import com.ygb.abroad.mapper.AbrGrowthAgentMapper;
import com.ygb.abroad.mapper.AbrGrowthAnalystMapper;
import com.ygb.abroad.mapper.AbrMessageMapper;
import com.ygb.abroad.service.IAbrAttributionService;
import com.ygb.abroad.service.IAbrExperimentService;
import com.ygb.abroad.service.IAbrGrowthAnalystService;
import com.ygb.abroad.service.IAbrGrowthGuardService;
import com.ygb.abroad.service.IAbrLeadAssignService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * growth_analyst 服务层实现：数字全部由系统计算；大模型只写摘要与建议（内部可见），数字须来自数据，否则用模板
 * 
 * @author ygb
 */
@Service
public class AbrGrowthAnalystServiceImpl implements IAbrGrowthAnalystService
{
    private static final Logger log = LoggerFactory.getLogger(AbrGrowthAnalystServiceImpl.class);

    static final String AGENT = "growth_analyst";

    static final String PARAM_MIX = "content_mix";

    static final String PERM_REPORT = "abroad:growthReport:list";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private AbrGrowthAnalystMapper analystMapper;

    @Autowired
    private AbrGrowthAgentMapper growthAgentMapper;

    @Autowired
    private AbrMessageMapper messageMapper;

    @Autowired
    private IAbrAttributionService attributionService;

    @Autowired
    private IAbrExperimentService experimentService;

    @Autowired
    private IAbrGrowthGuardService guardService;

    @Autowired
    private IAbrLeadAssignService assignService;

    @Autowired
    private AiGateway gateway;

    /** 内容配比边界（类型:最小-最大） */
    @Value("${abroad.growth-analyst.mix-bounds:case:15-50,flash:15-50,longtail:10-40,invite:5-25}")
    private String mixBounds;

    /** 每次最多调几个百分点 */
    @Value("${abroad.growth-analyst.mix-step:5}")
    private int mixStep;

    /** 看最近几天的表现 */
    @Value("${abroad.growth-analyst.mix-window-days:28}")
    private int mixWindowDays;

    /** 每类最少发布数 */
    @Value("${abroad.growth-analyst.mix-min-contents:3}")
    private int mixMinContents;

    /** 相对整体平均的差异门槛 */
    @Value("${abroad.growth-analyst.mix-threshold:0.2}")
    private double mixThreshold;

    @Override
    public AbrGrowthReport weekly(String weekStart, boolean tune, String operator)
    {
        LocalDate today = LocalDate.now(AbrLookupTools.BEIJING);
        LocalDate begin = StringUtils.isBlank(weekStart) ? AbrAttributionRules.weekStart(today).minusWeeks(1) : parseMonday(weekStart);
        if (!begin.plusDays(6).isBefore(today))
        {
            throw new ServiceException("只能生成已经过完的周");
        }
        LocalDate end = begin.plusDays(6);
        AbrGrowthReport report = analystMapper.selectReportByWeek(begin.toString());
        boolean created = report == null;
        if (created)
        {
            report = new AbrGrowthReport();
            report.setWeekStart(Date.valueOf(begin));
            report.setData("{}");
            report.setCreateBy(operator);
            analystMapper.insertReport(report);
        }
        List<Map<String, Object>> params = new ArrayList<>();
        if (tune && analystMapper.countParamLogs(report.getReportId(), PARAM_MIX) == 0)
        {
            params.addAll(tuneMix(end, report.getReportId(), operator));
        }
        if (params.isEmpty())
        {
            AbrGrowthParamLog q = new AbrGrowthParamLog();
            q.setReportId(report.getReportId());
            for (AbrGrowthParamLog l : analystMapper.selectParamLogList(q))
            {
                params.add(paramRow(l.getScope(), l.getScopeName(), l.getOldValue(), l.getNewValue(), l.getStatus(), l.getReason()));
            }
        }
        Map<String, Object> data = data(begin, end, params);
        String dataJson = MAPPER.writeValueAsString(data);
        Map<String, Object> narrative = narrative(data, dataJson, report, operator);
        report.setData(dataJson);
        report.setNarrative(MAPPER.writeValueAsString(narrative));
        analystMapper.updateReport(report);
        if (created)
        {
            notifyStaff(begin, narrative);
        }
        return analystMapper.selectReportById(report.getReportId());
    }

    private static LocalDate parseMonday(String s)
    {
        try
        {
            LocalDate d = LocalDate.parse(s.trim());
            return AbrAttributionRules.weekStart(d);
        }
        catch (RuntimeException e)
        {
            throw new ServiceException("日期格式应为 yyyy-MM-dd");
        }
    }

    /**
     * 周报数据：本周与上周漏斗、每周活跃、内容排行、类型表现、熔断、实验、调参
     */
    private Map<String, Object> data(LocalDate begin, LocalDate end, List<Map<String, Object>> params)
    {
        Map<String, Object> thisFunnel = attributionService.funnel(begin.toString(), end.toString(), "content");
        Map<String, Object> lastFunnel = attributionService.funnel(begin.minusWeeks(1).toString(), end.minusWeeks(1).toString(), "channel");
        @SuppressWarnings("unchecked")
        List<AbrAttributionRow> rows = (List<AbrAttributionRow>) thisFunnel.get("rows");
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> week = new LinkedHashMap<>();
        week.put("begin", begin.toString());
        week.put("end", end.toString());
        data.put("week", week);
        Map<String, Object> funnel = new LinkedHashMap<>();
        funnel.put("thisWeek", totals((AbrAttributionRow) thisFunnel.get("total")));
        funnel.put("lastWeek", totals((AbrAttributionRow) lastFunnel.get("total")));
        data.put("funnel", funnel);
        data.put("weeklyActive", weeklyActive(begin));
        List<AbrAttributionRow> contents = rows.stream().filter(r -> StringUtils.isNotBlank(r.getGroupKey())).toList();
        List<AbrAttributionRow> top = contents.stream().filter(r -> r.getVisitors() > 0)
                .sorted(Comparator.comparing(AbrAttributionRow::getToolUsers).thenComparing(AbrAttributionRow::getVisitors).reversed()).limit(3).toList();
        List<AbrAttributionRow> bottom = contents.stream().filter(r -> r.getPublished() > 0 && !top.contains(r))
                .sorted(Comparator.comparing(AbrAttributionRow::getVisitors).thenComparing(AbrAttributionRow::getToolUsers)
                        .thenComparing(AbrAttributionRow::getViews))
                .limit(3).toList();
        data.put("topContents", top.stream().map(AbrGrowthAnalystServiceImpl::contentRow).toList());
        data.put("bottomContents", bottom.stream().map(AbrGrowthAnalystServiceImpl::contentRow).toList());
        data.put("kinds", kindRows(end));
        data.put("breakers", analystMapper.selectBreakerTrips(begin.toString(), end.toString()));
        data.put("experiments", experimentRows(begin, end));
        data.put("params", params);
        return data;
    }

    private static Map<String, Object> totals(AbrAttributionRow t)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("visitors", t.getVisitors());
        m.put("toolUsers", t.getToolUsers());
        m.put("planUsers", t.getPlanUsers());
        m.put("loginUsers", t.getLoginUsers());
        m.put("momentUsers", t.getMomentUsers());
        m.put("bookedUsers", t.getBookedUsers());
        m.put("signedUsers", t.getSignedUsers());
        m.put("published", t.getPublished());
        m.put("views", t.getViews());
        return m;
    }

    private static Map<String, Object> contentRow(AbrAttributionRow r)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contentCode", r.getGroupKey());
        m.put("title", StringUtils.defaultString(r.getGroupLabel()));
        m.put("visitors", r.getVisitors());
        m.put("toolUsers", r.getToolUsers());
        m.put("views", r.getViews());
        return m;
    }

    private Map<String, Object> weeklyActive(LocalDate begin)
    {
        Map<String, Object> product = attributionService.product(AbrAttributionRules.MAX_WEEKS);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> weeks = (List<Map<String, Object>>) product.get("weeks");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("thisWeek", 0L);
        m.put("lastWeek", 0L);
        for (Map<String, Object> w : weeks)
        {
            if (begin.toString().equals(w.get("week")))
            {
                m.put("thisWeek", w.get("active"));
            }
            if (begin.minusWeeks(1).toString().equals(w.get("week")))
            {
                m.put("lastWeek", w.get("active"));
            }
        }
        return m;
    }

    private List<Map<String, Object>> kindRows(LocalDate end)
    {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, AbrMixTuner.Perf> e : kindPerformance(end).entrySet())
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", e.getKey());
            m.put("contents", e.getValue().contents());
            m.put("toolUsers", e.getValue().toolUsers());
            m.put("perContent", AbrExperimentRules.round(e.getValue().perContent(), 2));
            out.add(m);
        }
        return out;
    }

    private Map<String, AbrMixTuner.Perf> kindPerformance(LocalDate end)
    {
        Map<String, AbrMixTuner.Perf> perf = new LinkedHashMap<>();
        for (Map<String, Object> r : analystMapper.selectKindPerformance(end.minusDays(mixWindowDays - 1L).toString(), end.toString()))
        {
            perf.put(String.valueOf(r.get("content_kind")),
                    new AbrMixTuner.Perf(AbrAttributionRules.num(r.get("contents")), AbrAttributionRules.num(r.get("tool_users"))));
        }
        return perf;
    }

    private List<Map<String, Object>> experimentRows(LocalDate begin, LocalDate end)
    {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AbrExperiment e : experimentService.selectExperimentList(new AbrExperiment()))
        {
            boolean active = AbrExperimentRules.STATUS_RUNNING.equals(e.getStatus()) || AbrExperimentRules.STATUS_READY.equals(e.getStatus());
            boolean endedThisWeek = e.getEndTime() != null && !e.getEndTime().toInstant().atZone(AbrLookupTools.BEIJING).toLocalDate().isBefore(begin)
                    && !e.getEndTime().toInstant().atZone(AbrLookupTools.BEIJING).toLocalDate().isAfter(end);
            if (!active && !endedThisWeek)
            {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getExperimentName());
            m.put("platform", e.getPlatform());
            m.put("status", e.getStatus());
            m.put("winner", e.getWinnerCode());
            m.put("reason", StringUtils.defaultString(e.getConclusionReason()));
            out.add(m);
        }
        return out;
    }

    /**
     * 按近 N 天各类型表现调整启用剧本的配比；执行模式为自动时生效，人工确认时只写建议；暂停或出错时保持上周参数
     */
    private List<Map<String, Object>> tuneMix(LocalDate end, Long reportId, String operator)
    {
        List<Map<String, Object>> out = new ArrayList<>();
        AbrGrowthAgent agent = growthAgentMapper.selectAbrGrowthAgentByCode(AGENT);
        String mode = agent == null ? "off" : agent.getExecMode();
        if ("off".equals(mode) || guardService.agentBlockReason(AGENT) != null)
        {
            return out;
        }
        boolean apply = !"confirm".equals(mode);
        Map<String, AbrMixTuner.Perf> perf;
        try
        {
            perf = kindPerformance(end);
        }
        catch (RuntimeException e)
        {
            log.warn("内容类型表现统计失败，保持上周配比：{}", e.getMessage());
            return out;
        }
        Map<String, int[]> bounds = AbrMixTuner.bounds(mixBounds);
        for (Map<String, Object> p : analystMapper.selectPlaybookMixes())
        {
            String code = String.valueOf(p.get("playbook_code"));
            Map<String, Integer> current = mixOf(String.valueOf(p.get("content_mix")));
            if (current.isEmpty())
            {
                continue;
            }
            AbrMixTuner.Result r = AbrMixTuner.tune(current, perf, bounds, mixStep, mixMinContents, mixThreshold);
            if (!r.changed())
            {
                continue;
            }
            AbrGrowthParamLog l = new AbrGrowthParamLog();
            l.setParamKey(PARAM_MIX);
            l.setScope(code);
            l.setOldValue(MAPPER.writeValueAsString(current));
            l.setNewValue(MAPPER.writeValueAsString(r.mix()));
            l.setReason(StringUtils.substring(r.reason(), 0, 500));
            l.setStatus(apply ? "applied" : "suggested");
            l.setReportId(reportId);
            l.setCreateBy(operator);
            analystMapper.insertParamLog(l);
            if (apply)
            {
                analystMapper.updatePlaybookMix(code, l.getNewValue(), AGENT);
            }
            out.add(paramRow(code, String.valueOf(p.get("playbook_name")), l.getOldValue(), l.getNewValue(), l.getStatus(), l.getReason()));
        }
        return out;
    }

    private static Map<String, Object> paramRow(String scope, String name, String oldValue, String newValue, String status, String reason)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("playbook", scope);
        m.put("playbookName", StringUtils.defaultString(name));
        m.put("old", MAPPER.readTree(oldValue));
        m.put("new", MAPPER.readTree(newValue));
        m.put("status", status);
        m.put("reason", StringUtils.defaultString(reason));
        return m;
    }

    private static Map<String, Integer> mixOf(String json)
    {
        Map<String, Integer> m = new LinkedHashMap<>();
        JsonNode n = MAPPER.readTree(StringUtils.defaultIfBlank(json, "{}"));
        n.properties().forEach(e -> m.put(e.getKey(), e.getValue().asInt(0)));
        return m;
    }

    /**
     * 摘要：调用 growth_analyst（内部可见），校验数字都来自数据；失败或不合格用模板
     */
    private Map<String, Object> narrative(Map<String, Object> data, String dataJson, AbrGrowthReport report, String operator)
    {
        @SuppressWarnings("unchecked")
        Map<String, Object> funnel = (Map<String, Object>) data.get("funnel");
        @SuppressWarnings("unchecked")
        Map<String, Object> active = (Map<String, Object>) data.get("weeklyActive");
        @SuppressWarnings("unchecked")
        Map<String, Object> thisWeek = (Map<String, Object>) funnel.get("thisWeek");
        @SuppressWarnings("unchecked")
        Map<String, Object> lastWeek = (Map<String, Object>) funnel.get("lastWeek");
        Map<String, Object> fallback = AbrGrowthReportRules.template(thisWeek, lastWeek, AbrAttributionRules.num(active.get("thisWeek")),
                AbrAttributionRules.num(active.get("lastWeek")), highlights(data));
        report.setAiCallId(null);
        try
        {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("data", data);
            AiResult ai = gateway.invoke(AGENT, input, AiContext.of("growth_report", String.valueOf(report.getReportId()), operator));
            report.setAiCallId(ai.getCallId());
            if (!ai.isSuccess())
            {
                report.setAiStatus(StringUtils.substring(ai.getStatus(), 0, 16));
                return fallback;
            }
            Set<String> allowed = AbrGrowthReportRules.numbers(dataJson);
            List<String> errors = AbrGrowthReportRules.check(ai.getOutput(), allowed);
            if (!errors.isEmpty())
            {
                log.info("周报摘要未通过校验，用模板：{}", errors);
                report.setAiStatus("fallback");
                return fallback;
            }
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("summary", ai.getOutput().path("summary").asString("").trim());
            n.put("highlights", texts(ai.getOutput().path("highlights")));
            n.put("suggestions", texts(ai.getOutput().path("suggestions")));
            n.put("source", "ai");
            report.setAiStatus("success");
            return n;
        }
        catch (RuntimeException e)
        {
            log.warn("周报摘要生成失败，用模板：{}", e.getMessage());
            report.setAiStatus("error");
            return fallback;
        }
    }

    private static List<String> texts(JsonNode arr)
    {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray())
        {
            arr.forEach(t -> out.add(t.asString("").trim()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<String> highlights(Map<String, Object> data)
    {
        List<String> out = new ArrayList<>();
        List<Map<String, Object>> top = (List<Map<String, Object>>) data.get("topContents");
        if (!top.isEmpty())
        {
            Map<String, Object> t = top.get(0);
            out.add("带来工具使用最多的内容：" + t.get("title") + "（" + t.get("contentCode") + "，访客 " + t.get("visitors") + "，用过工具 " + t.get("toolUsers") + "）");
        }
        List<Map<String, Object>> breakers = (List<Map<String, Object>>) data.get("breakers");
        if (!breakers.isEmpty())
        {
            out.add("本周熔断 / 预算闸 " + breakers.size() + " 次");
        }
        for (Map<String, Object> e : (List<Map<String, Object>>) data.get("experiments"))
        {
            out.add("实验「" + e.get("name") + "」：" + e.get("status") + (e.get("winner") != null ? "，胜出组 " + e.get("winner") : ""));
        }
        List<Map<String, Object>> params = (List<Map<String, Object>>) data.get("params");
        if (!params.isEmpty())
        {
            out.add("内容配比调整 " + params.size() + " 个剧本");
        }
        return out;
    }

    private void notifyStaff(LocalDate begin, Map<String, Object> narrative)
    {
        try
        {
            String title = "增长周报（" + begin + " 起）";
            String content = StringUtils.substring(String.valueOf(narrative.get("summary")), 0, 900);
            for (Long u : messageMapper.selectUsersWithPerm(PERM_REPORT))
            {
                assignService.notify(u, "growth_report", title, content, null, "growth_report:" + begin);
            }
        }
        catch (RuntimeException e)
        {
            log.warn("周报提醒发送失败：{}", e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> evaluateExperiments(String operator)
    {
        AbrGrowthAgent agent = growthAgentMapper.selectAbrGrowthAgentByCode(AGENT);
        boolean auto = agent != null && !"confirm".equals(agent.getExecMode()) && !"off".equals(agent.getExecMode());
        return experimentService.evaluateAll(auto, operator);
    }

    @Override
    public List<AbrGrowthReport> selectReportList()
    {
        return analystMapper.selectReportList();
    }

    @Override
    public AbrGrowthReport selectReportById(Long reportId)
    {
        return analystMapper.selectReportById(reportId);
    }

    @Override
    public List<AbrGrowthParamLog> selectParamLogList(AbrGrowthParamLog log)
    {
        return analystMapper.selectParamLogList(log);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int revertParam(Long logId, String operator)
    {
        AbrGrowthParamLog l = requireLog(logId, "applied", "只有已生效的调整可以撤销");
        requireCurrent(l.getScope(), l.getNewValue(), "配比已被修改过，不能自动撤销，请在营销剧本中手动调整");
        analystMapper.updatePlaybookMix(l.getScope(), l.getOldValue(), operator);
        return analystMapper.changeParamLogStatus(logId, "applied", "reverted", operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int applyParam(Long logId, String operator)
    {
        AbrGrowthParamLog l = requireLog(logId, "suggested", "只有待确认的建议可以采纳");
        requireCurrent(l.getScope(), l.getOldValue(), "配比已被修改过，建议已过时，请忽略");
        analystMapper.updatePlaybookMix(l.getScope(), l.getNewValue(), operator);
        return analystMapper.changeParamLogStatus(logId, "suggested", "applied", operator);
    }

    @Override
    public int dismissParam(Long logId, String operator)
    {
        requireLog(logId, "suggested", "只有待确认的建议可以忽略");
        return analystMapper.changeParamLogStatus(logId, "suggested", "dismissed", operator);
    }

    private AbrGrowthParamLog requireLog(Long id, String status, String message)
    {
        AbrGrowthParamLog l = id == null ? null : analystMapper.selectParamLogById(id);
        if (l == null || !status.equals(l.getStatus()))
        {
            throw new ServiceException(message);
        }
        return l;
    }

    private void requireCurrent(String playbook, String expected, String message)
    {
        if (!mixOf(analystMapper.selectPlaybookMix(playbook)).equals(mixOf(expected)))
        {
            throw new ServiceException(message);
        }
    }
}
