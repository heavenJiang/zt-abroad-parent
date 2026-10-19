package com.ygb.abroad.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.ygb.abroad.domain.AbrCalendarEntry;
import com.ygb.abroad.domain.AbrEvent;
import com.ygb.abroad.domain.AbrGrowthAgent;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.domain.AbrGrowthTrigger;
import com.ygb.abroad.domain.AbrPlaybook;
import com.ygb.abroad.growth.CronSchedule;
import com.ygb.abroad.growth.EventFilter;
import com.ygb.abroad.growth.GrowthAgentHandler;
import com.ygb.abroad.growth.GrowthTaskOutcome;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.abroad.growth.OrchestratorRound;
import com.ygb.abroad.mapper.AbrCalendarEntryMapper;
import com.ygb.abroad.mapper.AbrEventMapper;
import com.ygb.abroad.mapper.AbrGrowthAgentMapper;
import com.ygb.abroad.mapper.AbrGrowthCursorMapper;
import com.ygb.abroad.mapper.AbrGrowthTaskMapper;
import com.ygb.abroad.mapper.AbrGrowthTriggerMapper;
import com.ygb.abroad.mapper.AbrPlaybookMapper;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.abroad.service.IAbrGrowthGuardService;
import com.ygb.abroad.service.IAbrOrchestratorService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 营销编排器 服务层实现
 *
 * 每轮先在游标行锁内处理日历与事件（多实例或上一轮未结束时直接跳过），再处理定时规则、过期与超时，最后执行到期任务：
 * <ol>
 * <li>日历：按北京时间算今天生效的剧本，与上次不同时写入 playbook_switched 事件；今天的节点写入 calendar_node 事件（每个节点每天一次）。</li>
 * <li>事件：读取游标之后的 abr_event，逐条匹配启用的事件规则（类型、过滤、限定剧本）生成任务，去重键为“规则 + 事件”。
 * 事件ID可能因并发事务提交先后出现空洞，游标只推进到连续的或已静置的事件为止，之后的事件下一轮会再读一次（去重键保证不重复生成）。</li>
 * <li>定时：启用的定时规则在上次触发之后、当前时刻之前有计划时刻的，生成一条任务（停机错过的只补最近一次，且不早于补触发窗口）。</li>
 * <li>执行：只认领有处理器的 Agent 的任务，逐条交给处理器；失败按 5、10、20、40、60 分钟重试，达到最多次数为失败。
 * 没有处理器的 Agent（未上线）任务保持待执行，到期后标为过期；全局或 Agent 暂停、超出 AI 预算时不认领（M6-02），
 * 执行模式为停用的 Agent 不生成任务。处理器返回推迟（如免打扰）时按指定时刻重排，不计执行次数。</li>
 * <li>熔断：每轮评估熔断规则，达到阈值时暂停对应 Agent 或全局。</li>
 * </ol>
 *
 * @author ygb
 */
@Service
public class AbrOrchestratorServiceImpl implements IAbrOrchestratorService
{
    private static final Logger log = LoggerFactory.getLogger(AbrOrchestratorServiceImpl.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 游标名 */
    static final String CURSOR = "orchestrator";

    /** 任务创建者：规则生成 */
    private static final String SYSTEM = "system";

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    @Autowired
    private AbrPlaybookMapper playbookMapper;

    @Autowired
    private AbrCalendarEntryMapper entryMapper;

    @Autowired
    private AbrGrowthTriggerMapper triggerMapper;

    @Autowired
    private AbrGrowthTaskMapper taskMapper;

    @Autowired
    private AbrGrowthCursorMapper cursorMapper;

    @Autowired
    private AbrEventMapper eventMapper;

    @Autowired
    private IAbrEventService eventService;

    @Autowired
    private IAbrGrowthGuardService guardService;

    @Autowired
    private AbrGrowthAgentMapper agentMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired(required = false)
    private List<GrowthAgentHandler> handlers;

    /** 每批读取的事件数 */
    @Value("${abroad.growth.event-batch:500}")
    private int eventBatch;

    /** 每轮最多读取的事件批数 */
    @Value("${abroad.growth.event-batches-per-round:5}")
    private int eventBatchesPerRound;

    /** 事件写入后多少秒视为已静置（游标可越过它之前的ID空洞） */
    @Value("${abroad.growth.event-settle-seconds:120}")
    private int eventSettleSeconds;

    /** 定时规则停机后补触发的窗口（分钟） */
    @Value("${abroad.growth.schedule-catch-up-minutes:10}")
    private int catchUpMinutes;

    /** 每轮最多执行的任务数 */
    @Value("${abroad.growth.batch-size:10}")
    private int batchSize;

    /** 执行中超过多少分钟视为中断 */
    @Value("${abroad.growth.stale-minutes:15}")
    private int staleMinutes;

    @Override
    public OrchestratorRound dispatch()
    {
        int[] counts = transactionTemplate.execute(status -> calendarAndEvents());
        if (counts == null)
        {
            return null;
        }
        int trips = guardService.evaluateBreakers();
        int created = counts[3] + fireSchedules();
        int expired = taskMapper.expireAbrGrowthTasks();
        int recovered = taskMapper.recoverStaleAbrGrowthTasks(staleMinutes);
        if (recovered > 0)
        {
            log.warn("编排任务执行超时 {} 条，已恢复为待执行或标为失败", recovered);
        }
        int run = runDueTasks();
        OrchestratorRound round = new OrchestratorRound(counts[0] > 0, counts[1], counts[2], created, run, expired, trips);
        if (round.busy())
        {
            log.info("编排器一轮：{}", round);
        }
        return round;
    }

    @Override
    public List<AbrPlaybook> activePlaybooks(LocalDate date)
    {
        return MarketingCalendar.activePlaybooks(allPlaybooks(), allEntries(), date);
    }

    @Override
    public Map<String, Object> current()
    {
        LocalDate today = MarketingCalendar.today();
        List<AbrCalendarEntry> entries = allEntries();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("date", today.toString());
        map.put("playbooks", MarketingCalendar.activePlaybooks(allPlaybooks(), entries, today).stream().map(this::playbookBrief).toList());
        map.put("nodes", MarketingCalendar.nodesOn(entries, today).stream().map(e -> nodeBrief(today, e)).toList());
        map.put("handlerAgents", handlerMap().keySet());
        map.put("blockedAgents", guardService.blockedAgents());
        Map<String, Long> tasks = new LinkedHashMap<>();
        for (Map<String, Object> row : taskMapper.countAbrGrowthTasksByStatus())
        {
            tasks.put(String.valueOf(row.get("status")), ((Number) row.get("count")).longValue());
        }
        map.put("taskCounts", tasks);
        List<Map<String, Object>> schedules = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now(MarketingCalendar.ZONE);
        for (AbrGrowthTrigger t : enabledTriggers(AbrGrowthTrigger.TYPE_SCHEDULE))
        {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("triggerId", t.getTriggerId());
            s.put("triggerName", t.getTriggerName());
            s.put("agentCode", t.getAgentCode());
            ZonedDateTime next = CronSchedule.next(t.getCronExpression(), now);
            s.put("nextFireTime", next == null ? null : next.toLocalDateTime().toString().replace('T', ' '));
            schedules.add(s);
        }
        map.put("schedules", schedules);
        return map;
    }

    @Override
    public Map<String, Object> preview(LocalDate from, int days)
    {
        if (from == null || days < 1 || days > 400)
        {
            throw new ServiceException("预览天数为 1–400");
        }
        List<AbrPlaybook> playbooks = allPlaybooks();
        List<AbrCalendarEntry> entries = allEntries();
        Map<String, String> names = new LinkedHashMap<>();
        playbooks.forEach(p -> names.put(p.getPlaybookCode(), p.getPlaybookName()));
        List<Map<String, Object>> segments = new ArrayList<>();
        for (MarketingCalendar.Segment s : MarketingCalendar.segments(playbooks, entries, from, days))
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", s.from().toString());
            m.put("to", s.to().toString());
            m.put("playbooks", s.playbookCodes().stream().map(c -> Map.of("code", c, "name", names.getOrDefault(c, c))).toList());
            segments.add(m);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("segments", segments);
        map.put("nodes", MarketingCalendar.nodes(entries, from, days).stream().map(h -> nodeBrief(h.date(), h.entry())).toList());
        return map;
    }

    @Override
    public AbrGrowthTask fireTrigger(Long triggerId, String operator)
    {
        AbrGrowthTrigger t = triggerMapper.selectAbrGrowthTriggerById(triggerId);
        if (t == null)
        {
            throw new ServiceException("触发规则不存在");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("manual", true);
        payload.put("params", object(t.getActionParams()));
        Date now = new Date();
        AbrGrowthTask task = newTask(t, null, payload, codes(MarketingCalendar.today()), now);
        task.setDedupKey("t" + t.getTriggerId() + ":m" + UUID.randomUUID().toString().replace("-", ""));
        task.setCreateBy(operator);
        taskMapper.insertAbrGrowthTask(task);
        return taskMapper.selectAbrGrowthTaskById(task.getTaskId());
    }

    /**
     * 游标行锁内：日历 + 事件触发
     *
     * @return [是否切换剧本, 节点事件数, 读取事件数, 生成任务数]；另一轮持有锁时返回 null
     */
    private int[] calendarAndEvents()
    {
        Map<String, Object> cursor = cursorMapper.lockCursor(CURSOR);
        if (cursor == null)
        {
            return null;
        }
        long lastEventId = ((Number) cursor.get("lastEventId")).longValue();
        JsonNode state = object((String) cursor.get("state"));

        LocalDate today = MarketingCalendar.today();
        List<AbrCalendarEntry> entries = allEntries();
        List<AbrPlaybook> active = MarketingCalendar.activePlaybooks(allPlaybooks(), entries, today);
        List<String> activeCodes = MarketingCalendar.codes(active);
        List<String> before = new ArrayList<>();
        state.path("playbooks").forEach(n -> before.add(n.asString()));
        boolean switched = false;
        if (!state.has("playbooks") || !MarketingCalendar.sameSet(before, activeCodes))
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("date", today.toString());
            payload.put("playbooks", activeCodes);
            payload.putAll(MarketingCalendar.diff(before, activeCodes));
            switched = eventService.recordSystemEvent(AbrEvent.TYPE_PLAYBOOK_SWITCHED, today + ":" + digest(String.join(",", activeCodes)), payload);
        }
        int nodes = 0;
        for (AbrCalendarEntry e : MarketingCalendar.nodesOn(entries, today))
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("date", today.toString());
            payload.put("entry_id", e.getEntryId());
            payload.put("node_code", e.getNodeCode());
            payload.put("name", e.getEntryName());
            payload.put("playbook_code", e.getPlaybookCode());
            if (eventService.recordSystemEvent(AbrEvent.TYPE_CALENDAR_NODE, e.getEntryId() + ":" + today, payload))
            {
                nodes++;
            }
        }

        Map<String, List<AbrGrowthTrigger>> byType = new LinkedHashMap<>();
        for (AbrGrowthTrigger t : enabledTriggers(AbrGrowthTrigger.TYPE_EVENT))
        {
            byType.computeIfAbsent(t.getEventType(), k -> new ArrayList<>()).add(t);
        }
        int read = 0;
        int created = 0;
        long cursorId = lastEventId;
        long scanFrom = lastEventId;
        for (int batch = 0; batch < eventBatchesPerRound; batch++)
        {
            List<AbrEvent> events = eventMapper.selectEventsAfter(scanFrom, eventSettleSeconds, eventBatch);
            boolean contiguous = cursorId == scanFrom;
            for (AbrEvent ev : events)
            {
                read++;
                created += matchEvent(ev, byType.get(ev.getEventType()), activeCodes);
                if (contiguous && (ev.getEventId() == cursorId + 1 || Boolean.TRUE.equals(ev.getSettled())))
                {
                    cursorId = ev.getEventId();
                }
                else
                {
                    contiguous = false;
                }
                scanFrom = ev.getEventId();
            }
            if (events.size() < eventBatch)
            {
                break;
            }
        }
        Map<String, Object> newState = new LinkedHashMap<>();
        newState.put("playbooks", activeCodes);
        newState.put("date", today.toString());
        cursorMapper.updateCursor(CURSOR, cursorId, MAPPER.writeValueAsString(newState));
        return new int[] { switched ? 1 : 0, nodes, read, created };
    }

    /**
     * 一条事件匹配同类型的事件规则，生成任务
     *
     * @return 生成的任务数
     */
    private int matchEvent(AbrEvent ev, List<AbrGrowthTrigger> triggers, List<String> activeCodes)
    {
        if (triggers == null)
        {
            return 0;
        }
        JsonNode payload = object(ev.getPayload());
        int created = 0;
        for (AbrGrowthTrigger t : triggers)
        {
            if (!EventFilter.matches(object(t.getEventFilter()), payload) || !playbookAllowed(t, activeCodes))
            {
                continue;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("event_type", ev.getEventType());
            body.put("event", payload);
            body.put("ref_type", ev.getRefType());
            body.put("ref_id", ev.getRefId());
            body.put("visitor_id", ev.getVisitorId());
            body.put("app_user_id", ev.getAppUserId());
            body.put("event_time", ev.getCreateTime() == null ? null : DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, ev.getCreateTime()));
            body.put("params", object(t.getActionParams()));
            AbrGrowthTask task = newTask(t, ev.getEventId(), body, activeCodes, new Date());
            if (ev.getAppUserId() != null)
            {
                task.setTargetType(AbrGrowthTask.TARGET_APP_USER);
                task.setTargetId(ev.getAppUserId());
            }
            else if (ev.getVisitorId() != null)
            {
                task.setTargetType(AbrGrowthTask.TARGET_VISITOR);
                task.setTargetId(ev.getVisitorId());
            }
            task.setDedupKey("t" + t.getTriggerId() + ":e" + ev.getEventId());
            created += taskMapper.insertAbrGrowthTask(task);
        }
        return created;
    }

    /**
     * 定时规则：生成到点的任务
     *
     * @return 生成的任务数
     */
    private int fireSchedules()
    {
        List<AbrGrowthTrigger> triggers = enabledTriggers(AbrGrowthTrigger.TYPE_SCHEDULE);
        if (triggers.isEmpty())
        {
            return 0;
        }
        ZonedDateTime now = ZonedDateTime.now(MarketingCalendar.ZONE);
        ZonedDateTime earliest = now.minusMinutes(catchUpMinutes);
        List<String> activeCodes = codes(now.toLocalDate());
        int created = 0;
        for (AbrGrowthTrigger t : triggers)
        {
            Date base = t.getLastFireTime() != null ? t.getLastFireTime() : t.getUpdateTime() != null ? t.getUpdateTime() : t.getCreateTime();
            ZonedDateTime since = base == null ? earliest : base.toInstant().atZone(MarketingCalendar.ZONE);
            if (since.isBefore(earliest))
            {
                since = earliest;
            }
            ZonedDateTime due;
            try
            {
                due = CronSchedule.latestDue(t.getCronExpression(), since, now);
            }
            catch (IllegalArgumentException e)
            {
                log.warn("定时规则 {} 的表达式无法解析：{}", t.getTriggerId(), e.getMessage());
                continue;
            }
            if (due == null)
            {
                continue;
            }
            Date fireTime = Date.from(due.toInstant());
            if (triggerMapper.updateLastFireTime(t.getTriggerId(), fireTime) == 0 || !playbookAllowed(t, activeCodes))
            {
                continue;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fire_time", due.toLocalDateTime().toString().replace('T', ' '));
            body.put("params", object(t.getActionParams()));
            AbrGrowthTask task = newTask(t, null, body, activeCodes, fireTime);
            task.setDedupKey("t" + t.getTriggerId() + ":s" + due.format(MINUTE));
            created += taskMapper.insertAbrGrowthTask(task);
        }
        return created;
    }

    /**
     * 执行到期任务
     *
     * @return 执行的任务数
     */
    private int runDueTasks()
    {
        Map<String, GrowthAgentHandler> map = handlerMap();
        Map<String, String> blocked = guardService.blockedAgents();
        map.keySet().removeAll(blocked.keySet());
        if (map.isEmpty())
        {
            return 0;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        if (taskMapper.claimAbrGrowthTasks(new ArrayList<>(map.keySet()), token, batchSize) == 0)
        {
            return 0;
        }
        List<AbrGrowthTask> tasks = taskMapper.selectAbrGrowthTasksByClaim(token);
        for (AbrGrowthTask task : tasks)
        {
            try
            {
                execute(task, map.get(task.getAgentCode()));
            }
            catch (RuntimeException e)
            {
                // 写回结果失败：任务保持执行中，超时后由 recoverStaleAbrGrowthTasks 恢复；不影响本批其他任务
                log.error("编排任务 {} 写回结果失败", task.getTaskId(), e);
            }
        }
        return tasks.size();
    }

    /**
     * 执行一条已认领的任务并写回结果
     */
    void execute(AbrGrowthTask task, GrowthAgentHandler handler)
    {
        GrowthTaskOutcome outcome;
        try
        {
            outcome = handler.handle(task);
        }
        catch (RuntimeException e)
        {
            log.warn("编排任务 {}（{} / {}）执行失败：{}", task.getTaskId(), task.getAgentCode(), task.getAction(), e.getMessage(), e);
            outcome = GrowthTaskOutcome.retry(StringUtils.substring("执行异常：" + e.getMessage(), 0, 1900));
        }
        if (outcome == null)
        {
            outcome = GrowthTaskOutcome.done(null);
        }
        if (outcome.kind() == GrowthTaskOutcome.Kind.DEFER && outcome.deferUntil() != null)
        {
            taskMapper.deferAbrGrowthTask(task.getTaskId(), task.getClaimToken(), outcome.deferUntil(), StringUtils.substring(outcome.message(), 0, 1900));
            return;
        }
        if (outcome.kind() == GrowthTaskOutcome.Kind.RETRY || outcome.kind() == GrowthTaskOutcome.Kind.DEFER)
        {
            int attempts = task.getAttempts() == null ? 1 : task.getAttempts();
            taskMapper.retryAbrGrowthTask(task.getTaskId(), task.getClaimToken(), GrowthTaskOutcome.retryDelayMinutes(attempts),
                    StringUtils.substring(outcome.message(), 0, 1900));
            return;
        }
        AbrGrowthTask done = new AbrGrowthTask();
        done.setTaskId(task.getTaskId());
        done.setClaimToken(task.getClaimToken());
        done.setStatus(outcome.kind() == GrowthTaskOutcome.Kind.DONE ? AbrGrowthTask.STATUS_DONE : AbrGrowthTask.STATUS_SKIPPED);
        done.setResult(outcome.result() == null ? null : MAPPER.writeValueAsString(outcome.result()));
        done.setErrorMsg(StringUtils.substring(outcome.message(), 0, 1900));
        taskMapper.finishAbrGrowthTask(done);
    }

    /**
     * 按规则生成一条任务（未写入）
     */
    private AbrGrowthTask newTask(AbrGrowthTrigger t, Long eventId, Map<String, Object> payload, List<String> activeCodes, Date base)
    {
        Date scheduled = new Date(Math.max(base.getTime(), System.currentTimeMillis()) + (t.getDelayMinutes() == null ? 0 : t.getDelayMinutes()) * 60_000L);
        AbrGrowthTask task = new AbrGrowthTask();
        task.setTriggerId(t.getTriggerId());
        task.setAgentCode(t.getAgentCode());
        task.setAction(t.getAction());
        task.setSourceEventId(eventId);
        task.setPlaybookCodes(MAPPER.writeValueAsString(activeCodes));
        task.setPayload(MAPPER.writeValueAsString(payload));
        task.setScheduledTime(scheduled);
        task.setExpireTime(new Date(scheduled.getTime() + (t.getTtlHours() == null ? 72 : t.getTtlHours()) * 3600_000L));
        task.setMaxAttempts(t.getMaxAttempts());
        task.setCreateBy(SYSTEM);
        return task;
    }

    /**
     * 规则限定了剧本时，只在该剧本生效期间触发
     */
    private static boolean playbookAllowed(AbrGrowthTrigger t, List<String> activeCodes)
    {
        return t.getPlaybookId() == null || (t.getPlaybookCode() != null && activeCodes.contains(t.getPlaybookCode()));
    }

    private Map<String, GrowthAgentHandler> handlerMap()
    {
        Map<String, GrowthAgentHandler> map = new LinkedHashMap<>();
        if (handlers != null)
        {
            for (GrowthAgentHandler h : handlers)
            {
                if (map.putIfAbsent(h.agentCode(), h) != null)
                {
                    log.warn("Agent {} 有多个编排任务处理器，只使用第一个", h.agentCode());
                }
            }
        }
        return map;
    }

    private List<String> codes(LocalDate date)
    {
        return MarketingCalendar.codes(activePlaybooks(date));
    }

    private List<AbrPlaybook> allPlaybooks()
    {
        return playbookMapper.selectAbrPlaybookList(new AbrPlaybook());
    }

    private List<AbrCalendarEntry> allEntries()
    {
        return entryMapper.selectAbrCalendarEntryList(new AbrCalendarEntry());
    }

    /**
     * 启用的规则（执行模式为停用的 Agent 的规则不生成任务）
     */
    private List<AbrGrowthTrigger> enabledTriggers(String type)
    {
        AbrGrowthTrigger query = new AbrGrowthTrigger();
        query.setTriggerType(type);
        query.setStatus("0");
        AbrGrowthAgent off = new AbrGrowthAgent();
        off.setExecMode(AbrGrowthAgent.MODE_OFF);
        Set<String> offAgents = new HashSet<>();
        agentMapper.selectAbrGrowthAgentList(off).forEach(a -> offAgents.add(a.getAgentCode()));
        List<AbrGrowthTrigger> list = triggerMapper.selectAbrGrowthTriggerList(query);
        list.removeIf(t -> offAgents.contains(t.getAgentCode()));
        return list;
    }

    private Map<String, Object> playbookBrief(AbrPlaybook p)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("playbookId", p.getPlaybookId());
        m.put("code", p.getPlaybookCode());
        m.put("name", p.getPlaybookName());
        m.put("priority", p.getPriority());
        m.put("hookTools", object(p.getHookTools()));
        m.put("contentMix", object(p.getContentMix()));
        return m;
    }

    private static Map<String, Object> nodeBrief(LocalDate date, AbrCalendarEntry e)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date.toString());
        m.put("entryId", e.getEntryId());
        m.put("nodeCode", e.getNodeCode());
        m.put("name", e.getEntryName());
        m.put("playbookCode", e.getPlaybookCode());
        return m;
    }

    private static JsonNode object(String json)
    {
        if (StringUtils.isBlank(json))
        {
            return MAPPER.createObjectNode();
        }
        try
        {
            return MAPPER.readTree(json);
        }
        catch (RuntimeException e)
        {
            return MAPPER.createObjectNode();
        }
    }

    /**
     * 剧本组合的短摘要（去重键用，16 位十六进制）
     */
    private static String digest(String text)
    {
        try
        {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }
}
