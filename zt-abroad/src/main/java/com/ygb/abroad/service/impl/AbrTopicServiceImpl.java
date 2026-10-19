package com.ygb.abroad.service.impl;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.ygb.abroad.ai.AiContext;
import com.ygb.abroad.ai.AiGateway;
import com.ygb.abroad.ai.AiResult;
import com.ygb.abroad.domain.AbrCalendarEntry;
import com.ygb.abroad.domain.AbrChannelAccount;
import com.ygb.abroad.domain.AbrEvent;
import com.ygb.abroad.domain.AbrPlaybook;
import com.ygb.abroad.domain.AbrTopic;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.abroad.growth.topic.TopicPlanner;
import com.ygb.abroad.mapper.AbrCalendarEntryMapper;
import com.ygb.abroad.mapper.AbrChannelAccountMapper;
import com.ygb.abroad.mapper.AbrPlaybookMapper;
import com.ygb.abroad.mapper.AbrTopicMapper;
import com.ygb.abroad.service.IAbrComplianceService;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.abroad.service.IAbrGrowthGuardService;
import com.ygb.abroad.service.IAbrTopicService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DictUtils;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 选题排期 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrTopicServiceImpl implements IAbrTopicService
{
    private static final Logger log = LoggerFactory.getLogger(AbrTopicServiceImpl.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** Agent 编码 */
    static final String AGENT = "topic_planner";

    /** 不排选题的平台（项目页由院校库自动生成） */
    static final Set<String> SKIP_PLATFORMS = Set.of("website");

    /** 输入给大模型的截止项目、变化项目上限 */
    static final int MAX_DEADLINES = 30;

    static final int MAX_CHANGES = 20;

    static final int MAX_RECENT_TITLES = 60;

    /** 员工可设的状态（已写稿由写稿环节设置） */
    static final Set<String> EDITABLE_STATUS = Set.of(AbrTopic.STATUS_PLANNED, AbrTopic.STATUS_SKIPPED, AbrTopic.STATUS_CANCELLED);

    private final ReentrantLock lock = new ReentrantLock();

    @Autowired
    private AbrTopicMapper topicMapper;

    @Autowired
    private AbrChannelAccountMapper accountMapper;

    @Autowired
    private AbrCalendarEntryMapper entryMapper;

    @Autowired
    private AbrPlaybookMapper playbookMapper;

    @Autowired
    private IAbrComplianceService complianceService;

    @Autowired
    private IAbrGrowthGuardService guardService;

    @Autowired
    private IAbrEventService eventService;

    @Autowired
    private AiGateway gateway;

    /** 排未来几天（从明天起） */
    @Value("${abroad.topic.days:7}")
    private int days;

    /** 每个账号每天几条（不超过账号的每天发布上限） */
    @Value("${abroad.topic.per-day:1}")
    private int perDay;

    /** 没有生效剧本或剧本未配比时的默认配比 */
    @Value("${abroad.topic.default-mix:case:35,flash:30,longtail:20,invite:15}")
    private String defaultMix;

    /** 暂不排的内容类型（逗号分隔） */
    @Value("${abroad.topic.disabled-kinds:case}")
    private String disabledKinds;

    /** 近期标题回看天数 */
    @Value("${abroad.topic.recent-days:60}")
    private int recentDays;

    /** 输入中的“近期截止”天数 */
    @Value("${abroad.topic.deadline-days:45}")
    private int deadlineDays;

    /** 输入中的“近期有更新”天数 */
    @Value("${abroad.topic.change-days:14}")
    private int changeDays;

    @Override
    public List<AbrTopic> selectAbrTopicList(AbrTopic topic)
    {
        return topicMapper.selectAbrTopicList(topic);
    }

    @Override
    public AbrTopic selectAbrTopicById(Long topicId)
    {
        return topicMapper.selectAbrTopicById(topicId);
    }

    @Override
    public int insertAbrTopic(AbrTopic t)
    {
        normalize(t);
        t.setSource(AbrTopic.SOURCE_MANUAL);
        t.setStatus(AbrTopic.STATUS_PLANNED);
        t.setAiCallId(null);
        for (int i = 0; i < 5; i++)
        {
            t.setSlotNo(topicMapper.maxSlotNo(t.getAccountId(), t.getPlanDate()) + 1);
            try
            {
                return topicMapper.insertAbrTopic(t);
            }
            catch (DuplicateKeyException e)
            {
                // 同时有选题写入同一天：换下一个条次
            }
        }
        throw new ServiceException("当天的条次冲突，请重试");
    }

    @Override
    public int updateAbrTopic(AbrTopic t)
    {
        AbrTopic before = t.getTopicId() == null ? null : topicMapper.selectAbrTopicById(t.getTopicId());
        if (before == null)
        {
            throw new ServiceException("选题不存在");
        }
        if (before.getContentId() != null || AbrTopic.STATUS_DRAFTED.equals(before.getStatus()))
        {
            throw new ServiceException("选题已写成稿件，不能再改（改稿件即可）");
        }
        normalize(t);
        String status = StringUtils.defaultIfBlank(t.getStatus(), before.getStatus());
        if (!EDITABLE_STATUS.contains(status))
        {
            throw new ServiceException("状态只能是待写稿、已跳过或已取消");
        }
        t.setStatus(status);
        t.setAccountId(before.getAccountId());
        boolean sameDay = MarketingCalendar.toLocalDate(before.getPlanDate()).equals(MarketingCalendar.toLocalDate(t.getPlanDate()));
        boolean reviving = AbrTopic.STATUS_CANCELLED.equals(before.getStatus()) && !AbrTopic.STATUS_CANCELLED.equals(status);
        t.setSlotNo(sameDay && !reviving ? before.getSlotNo() : topicMapper.maxSlotNo(before.getAccountId(), t.getPlanDate()) + 1);
        try
        {
            return topicMapper.updateAbrTopic(t);
        }
        catch (DuplicateKeyException e)
        {
            throw new ServiceException("当天的条次冲突，请重试");
        }
    }

    @Override
    public int deleteAbrTopicByIds(Long[] topicIds)
    {
        return topicMapper.deleteUnwrittenTopicByIds(topicIds);
    }

    @Override
    public Map<String, Object> plan(Long accountId, boolean replan, String operator)
    {
        String blocked = guardService.agentBlockReason(AGENT);
        if (blocked != null)
        {
            throw new ServiceException("topic_planner 现在不能运行：" + blocked);
        }
        if (!lock.tryLock())
        {
            throw new ServiceException("正在排期，请稍后刷新查看");
        }
        try
        {
            return doPlan(accountId, replan, operator);
        }
        finally
        {
            lock.unlock();
        }
    }

    private Map<String, Object> doPlan(Long accountId, boolean replan, String operator)
    {
        LocalDate today = MarketingCalendar.today();
        LocalDate from = today.plusDays(1);
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < Math.max(1, days); i++)
        {
            dates.add(from.plusDays(i));
        }
        LocalDate to = dates.get(dates.size() - 1);
        // 直接读剧本与日历（编排器的处理器依赖本服务，不能反向依赖编排器服务）
        List<AbrCalendarEntry> entries = entryMapper.selectAbrCalendarEntryList(new AbrCalendarEntry());
        List<AbrPlaybook> active = MarketingCalendar.activePlaybooks(playbookMapper.selectAbrPlaybookList(new AbrPlaybook()), entries, from);
        List<TopicPlanner.Playbook> playbooks = new ArrayList<>();
        Map<String, TopicPlanner.Playbook> byCode = new LinkedHashMap<>();
        Map<String, List<String>> hooksByPlaybook = new HashMap<>();
        Map<String, AbrPlaybook> rows = new HashMap<>();
        for (AbrPlaybook p : active)
        {
            rows.put(p.getPlaybookCode(), p);
            TopicPlanner.Playbook pb = new TopicPlanner.Playbook(p.getPlaybookCode(), mix(p.getContentMix()), liveHooks(p.getHookTools()),
                    strings(p.getBackupTopics()));
            playbooks.add(pb);
            byCode.put(pb.code(), pb);
            hooksByPlaybook.put(pb.code(), pb.hooks());
        }
        hooksByPlaybook.put("", List.of("quick"));
        Catalog catalog = catalog(today);
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (MarketingCalendar.NodeHit n : MarketingCalendar.nodes(entries, from, dates.size()))
        {
            nodes.add(Map.of("date", n.date().toString(), "name", n.entry().getEntryName()));
        }
        List<String> activeCodes = MarketingCalendar.codes(active);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("playbooks", activeCodes);
        List<Map<String, Object>> accounts = new ArrayList<>();
        List<Long> created = new ArrayList<>();
        for (AbrChannelAccount a : accounts(accountId))
        {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("account", a.getAccountCode());
            if (replan)
            {
                r.put("cancelled", topicMapper.cancelStalePlanned(a.getAccountId(), Date.valueOf(from), activeCodes, operator));
            }
            planAccount(a, dates, playbooks, byCode, rows, hooksByPlaybook, catalog, nodes, today, operator, r, created);
            accounts.add(r);
        }
        result.put("accounts", accounts);
        result.put("created", created.size());
        if (!created.isEmpty())
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("topic_ids", created);
            payload.put("from", from.toString());
            payload.put("to", to.toString());
            eventService.recordSystemEvent(AbrEvent.TYPE_TOPICS_PLANNED, null, payload);
        }
        return result;
    }

    private void planAccount(AbrChannelAccount a, List<LocalDate> dates, List<TopicPlanner.Playbook> playbooks, Map<String, TopicPlanner.Playbook> byCode,
            Map<String, AbrPlaybook> rows, Map<String, List<String>> hooksByPlaybook, Catalog catalog, List<Map<String, Object>> nodes, LocalDate today, String operator,
            Map<String, Object> r, List<Long> created)
    {
        Date from = Date.valueOf(dates.get(0));
        Date to = Date.valueOf(dates.get(dates.size() - 1));
        Map<LocalDate, Integer> perDayCount = new HashMap<>();
        for (Map<String, Object> row : topicMapper.countPerDay(a.getAccountId(), from, to))
        {
            perDayCount.put(MarketingCalendar.toLocalDate((java.util.Date) row.get("planDate")), ((Number) row.get("count")).intValue());
        }
        Map<String, Integer> kinds = new HashMap<>();
        for (Map<String, Object> row : topicMapper.countPerKind(a.getAccountId(), from, to))
        {
            kinds.put(String.valueOf(row.get("contentKind")), ((Number) row.get("count")).intValue());
        }
        int cap = a.getDailyPublishCap() == null || a.getDailyPublishCap() <= 0 ? perDay : Math.min(perDay, a.getDailyPublishCap());
        List<TopicPlanner.Slot> slots = TopicPlanner.slots(dates, Math.max(1, cap), perDayCount, kinds, playbooks, mix(defaultMixJson()),
                disabled());
        r.put("slots", slots.size());
        if (slots.isEmpty())
        {
            return;
        }
        Set<String> recent = new LinkedHashSet<>(topicMapper.selectRecentTitles(a.getAccountId(), Date.valueOf(today.minusDays(recentDays))));
        BiPredicate<String, List<Long>> compliant = (text, ids) -> complianceService.checkMessage(a.getPlatform(), text, citations(ids), false).passed();
        TopicPlanner.Checked checked = new TopicPlanner.Checked(List.of(), List.of());
        Long callId = null;
        try
        {
            AiResult ai = gateway.invoke(AGENT, input(a, slots, playbooks, byCode, rows, catalog, nodes, recent), AiContext.of("topic", a.getAccountCode(), operator));
            callId = ai.getCallId();
            r.put("aiStatus", ai.getStatus());
            if (ai.isSuccess())
            {
                checked = TopicPlanner.accept(slots, ai.getOutput(), hooksByPlaybook, catalog.programIds(), recent, compliant);
            }
            else
            {
                r.put("aiMessage", StringUtils.substring(ai.getMessage(), 0, 200));
            }
        }
        catch (RuntimeException e)
        {
            log.warn("选题排期调用失败 account={}：{}", a.getAccountCode(), e.getMessage());
            r.put("aiStatus", "error");
            r.put("aiMessage", StringUtils.substring(e.getMessage(), 0, 200));
        }
        Set<String> filled = new LinkedHashSet<>();
        checked.accepted().forEach(t -> filled.add(t.slot().id()));
        Set<String> used = new LinkedHashSet<>(recent);
        checked.accepted().forEach(t -> used.add(t.title()));
        List<TopicPlanner.Slot> rest = slots.stream().filter(s -> !filled.contains(s.id())).toList();
        List<TopicPlanner.Topic> backup = TopicPlanner.backup(rest, byCode, used, compliant);
        int ai = 0;
        int bk = 0;
        for (TopicPlanner.Topic t : concat(checked.accepted(), backup))
        {
            Long id = save(a, t, TopicPlanner.SOURCE_AI.equals(t.source()) ? callId : null);
            if (id != null)
            {
                created.add(id);
                if (TopicPlanner.SOURCE_AI.equals(t.source()))
                {
                    ai++;
                }
                else
                {
                    bk++;
                }
            }
        }
        r.put("ai", ai);
        r.put("backup", bk);
        r.put("empty", slots.size() - ai - bk);
        r.put("rejected", checked.rejected());
        r.put("callId", callId);
    }

    private Long save(AbrChannelAccount a, TopicPlanner.Topic t, Long callId)
    {
        AbrTopic row = new AbrTopic();
        row.setAccountId(a.getAccountId());
        row.setPlanDate(Date.valueOf(t.slot().date()));
        row.setSlotNo(t.slot().slotNo());
        row.setContentKind(t.kind());
        row.setTitle(t.title());
        row.setAngle(t.angle());
        row.setHookTool(t.hookTool());
        row.setPlaybookCode(t.slot().playbookCode());
        ArrayNode ids = MAPPER.createArrayNode();
        t.programIds().forEach(ids::add);
        row.setProgramIds(ids.toString());
        row.setSource(t.source());
        row.setStatus(AbrTopic.STATUS_PLANNED);
        row.setAiCallId(callId);
        row.setCreateBy(AGENT);
        try
        {
            topicMapper.insertAbrTopic(row);
            return row.getTopicId();
        }
        catch (DuplicateKeyException e)
        {
            // 员工刚好在同一天同一条次插入了选题：保留员工的
            return null;
        }
    }

    /**
     * 大模型输入：账号、空位、剧本、日历节点、近期截止与更新的项目、近期标题（不含个人信息）
     */
    private Map<String, Object> input(AbrChannelAccount a, List<TopicPlanner.Slot> slots, List<TopicPlanner.Playbook> playbooks,
            Map<String, TopicPlanner.Playbook> byCode, Map<String, AbrPlaybook> rows, Catalog catalog, List<Map<String, Object>> nodes, Set<String> recent)
    {
        Map<String, Object> in = new LinkedHashMap<>();
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("platform", label("abr_platform", a.getPlatform()));
        account.put("persona", label("abr_persona", a.getPersona()));
        account.put("personaDesc", StringUtils.defaultString(a.getPersonaDesc()));
        in.put("account", account);
        List<Map<String, Object>> slotList = new ArrayList<>();
        for (TopicPlanner.Slot s : slots)
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slot", s.id());
            m.put("date", s.date().toString());
            m.put("weekday", s.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.CHINA));
            m.put("kind", s.kind());
            m.put("kindLabel", label("abr_content_kind", s.kind()));
            m.put("playbook", s.playbookCode());
            slotList.add(m);
        }
        in.put("slots", slotList);
        TopicPlanner.Slot first = slots.stream().filter(s -> !TopicPlanner.KIND_FLASH.equals(s.kind())).findFirst().orElse(slots.get(0));
        AbrPlaybook firstPb = first.playbookCode() == null ? null : rows.get(first.playbookCode());
        Map<String, Object> firstSlot = new LinkedHashMap<>();
        firstSlot.put("slot", first.id());
        firstSlot.put("playbookName", firstPb == null ? "申请季" : firstPb.getPlaybookName());
        List<String> firstHooks = first.playbookCode() == null ? List.of("quick") : byCode.get(first.playbookCode()).hooks();
        firstSlot.put("hook", firstHooks.isEmpty() ? "quick" : firstHooks.get(0));
        in.put("firstSlot", firstSlot);
        List<Map<String, Object>> pbs = new ArrayList<>();
        for (TopicPlanner.Playbook pb : playbooks)
        {
            AbrPlaybook p = rows.get(pb.code());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", pb.code());
            m.put("name", p == null ? pb.code() : p.getPlaybookName());
            m.put("audience", p == null ? "" : StringUtils.defaultString(p.getAudience()));
            m.put("focus", p == null ? "" : StringUtils.defaultString(p.getContentFocus()));
            List<Map<String, Object>> hooks = new ArrayList<>();
            pb.hooks().forEach(h -> hooks.add(Map.of("code", h, "label", label("abr_hook_tool", h))));
            m.put("hooks", hooks);
            pbs.add(m);
        }
        in.put("playbooks", pbs);
        in.put("nodes", nodes);
        in.put("deadlines", catalog.deadlines());
        in.put("changes", catalog.changes());
        in.put("recentTitles", recent.stream().limit(MAX_RECENT_TITLES).toList());
        return in;
    }

    /**
     * 院校库切片：近期截止（从明天起若干天内、按日期）与近期有更新的项目；只取 90 天内核对过的
     */
    record Catalog(List<Map<String, Object>> deadlines, List<Map<String, Object>> changes, Set<Long> programIds)
    {
    }

    private Catalog catalog(LocalDate today)
    {
        List<Map<String, Object>> deadlines = new ArrayList<>();
        List<Map<String, Object>> changes = new ArrayList<>();
        Set<Long> ids = new LinkedHashSet<>();
        LocalDate until = today.plusDays(deadlineDays);
        LocalDate changedSince = today.minusDays(changeDays);
        for (Map<String, Object> row : topicMapper.selectCatalogForTopics(Date.valueOf(today.minusDays(AbrLookupTools.STALE_DAYS))))
        {
            Long id = ((Number) row.get("programId")).longValue();
            String country = label("abr_country", String.valueOf(row.get("country")));
            for (JsonNode round : json(String.valueOf(row.get("deadlines")), "{}").path("rounds"))
            {
                try
                {
                    LocalDate d = LocalDate.parse(round.path("date").asString(""));
                    if (d.isAfter(today) && !d.isAfter(until))
                    {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("programId", id);
                        m.put("school", row.get("schoolName"));
                        m.put("program", row.get("programName"));
                        m.put("country", country);
                        m.put("deadline", d.toString());
                        m.put("round", round.path("name").asString(""));
                        deadlines.add(m);
                    }
                }
                catch (DateTimeParseException e)
                {
                    // 日期格式不对的轮次跳过
                }
            }
            Object changed = row.get("changedTime");
            if (changed instanceof java.util.Date c && !MarketingCalendar.toLocalDate(c).isBefore(changedSince))
            {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("programId", id);
                m.put("school", row.get("schoolName"));
                m.put("program", row.get("programName"));
                m.put("country", country);
                m.put("updated", MarketingCalendar.toLocalDate(c).toString());
                changes.add(m);
            }
        }
        deadlines.sort(Comparator.comparing(m -> String.valueOf(m.get("deadline"))));
        List<Map<String, Object>> d = deadlines.stream().limit(MAX_DEADLINES).toList();
        changes.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("updated"))).reversed());
        List<Map<String, Object>> c = changes.stream().limit(MAX_CHANGES).toList();
        d.forEach(m -> ids.add((Long) m.get("programId")));
        c.forEach(m -> ids.add((Long) m.get("programId")));
        return new Catalog(d, c, ids);
    }

    private List<AbrChannelAccount> accounts(Long accountId)
    {
        List<AbrChannelAccount> list = new ArrayList<>();
        if (accountId != null)
        {
            AbrChannelAccount a = accountMapper.selectAbrChannelAccountById(accountId);
            if (a == null)
            {
                throw new ServiceException("发布账号不存在");
            }
            if (!"0".equals(a.getStatus()) || "Y".equals(a.getPaused()) || SKIP_PLATFORMS.contains(a.getPlatform()))
            {
                throw new ServiceException("账号已停用、已暂停或不需要排选题");
            }
            list.add(a);
            return list;
        }
        AbrChannelAccount q = new AbrChannelAccount();
        q.setStatus("0");
        for (AbrChannelAccount a : accountMapper.selectAbrChannelAccountList(q))
        {
            if (!"Y".equals(a.getPaused()) && !SKIP_PLATFORMS.contains(a.getPlatform()))
            {
                list.add(a);
            }
        }
        return list;
    }

    /**
     * 校验员工填写的选题：账号、类型、工具、项目、日期（不早于今天）、标题写法
     */
    private void normalize(AbrTopic t)
    {
        AbrChannelAccount a = t.getAccountId() == null ? null : accountMapper.selectAbrChannelAccountById(t.getAccountId());
        if (a == null)
        {
            throw new ServiceException("发布账号不存在");
        }
        if (!AbrPlaybookServiceImpl.dictValues("abr_content_kind").contains(t.getContentKind()))
        {
            throw new ServiceException("内容类型“" + t.getContentKind() + "”不是可选值");
        }
        t.setHookTool(StringUtils.trimToNull(t.getHookTool()));
        if (t.getHookTool() != null && !AbrPlaybookServiceImpl.dictValues("abr_hook_tool").contains(t.getHookTool()))
        {
            throw new ServiceException("文末工具“" + t.getHookTool() + "”不是可选值");
        }
        t.setPlaybookCode(StringUtils.trimToNull(t.getPlaybookCode()));
        LocalDate d = MarketingCalendar.toLocalDate(t.getPlanDate());
        if (d == null || d.isBefore(MarketingCalendar.today()))
        {
            throw new ServiceException("计划日期不能早于今天");
        }
        t.setPlanDate(Date.valueOf(d));
        t.setTitle(StringUtils.trim(t.getTitle()));
        JsonNode ids = json(t.getProgramIds(), "[]");
        if (!ids.isArray())
        {
            throw new ServiceException("引用的项目必须是 JSON 数组");
        }
        ArrayNode out = MAPPER.createArrayNode();
        Set<Long> seen = new LinkedHashSet<>();
        for (JsonNode n : ids)
        {
            if (!n.isIntegralNumber() || n.asLong() <= 0)
            {
                throw new ServiceException("引用的项目须为项目ID数组");
            }
            if (seen.add(n.asLong()))
            {
                out.add(n.asLong());
            }
        }
        if (TopicPlanner.KIND_FLASH.equals(t.getContentKind()) && seen.isEmpty())
        {
            throw new ServiceException("快讯须引用院校库项目");
        }
        t.setProgramIds(out.toString());
        for (String text : new String[] { t.getTitle(), t.getAngle(), t.getRemark() })
        {
            if (StringUtils.isNotBlank(text) && AbrAssessmentAnswers.containsTag(JsonNodeFactory.instance.stringNode(text)))
            {
                throw new ServiceException("选题中不能包含 < 加字母的写法（类似 HTML 标签）");
            }
        }
    }

    private static String citations(List<Long> ids)
    {
        ArrayNode arr = MAPPER.createArrayNode();
        ids.forEach(id -> arr.addObject().put("type", "program").put("id", id));
        return arr.toString();
    }

    /**
     * 剧本的主推钩子中已上线的（有 C 端页面）；都没上线时用 30 秒快测
     */
    static List<String> liveHooks(String json)
    {
        List<String> out = new ArrayList<>();
        for (String h : strings(json))
        {
            if (AbrContentRules.HOOK_PAGES.containsKey(h) && !out.contains(h))
            {
                out.add(h);
            }
        }
        if (out.isEmpty())
        {
            out.add("quick");
        }
        return out;
    }

    static Map<String, Integer> mix(String json)
    {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : json(json, "{}").properties())
        {
            if (e.getValue().isNumber() && e.getValue().asInt() > 0)
            {
                m.put(e.getKey(), e.getValue().asInt());
            }
        }
        return m;
    }

    /**
     * 默认配比配置（case:35,flash:30,…）转为 JSON 对象
     */
    private String defaultMixJson()
    {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String part : StringUtils.defaultString(defaultMix).split(","))
        {
            String[] kv = part.trim().split(":");
            if (kv.length == 2 && kv[1].trim().matches("\\d+"))
            {
                m.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
            }
        }
        return MAPPER.writeValueAsString(m);
    }

    private Set<String> disabled()
    {
        Set<String> s = new LinkedHashSet<>();
        for (String k : StringUtils.defaultString(disabledKinds).split(","))
        {
            if (StringUtils.isNotBlank(k))
            {
                s.add(k.trim());
            }
        }
        return s;
    }

    static List<String> strings(String json)
    {
        List<String> out = new ArrayList<>();
        for (JsonNode n : json(json, "[]"))
        {
            if (n.isString() && StringUtils.isNotBlank(n.asString()))
            {
                out.add(n.asString().trim());
            }
        }
        return out;
    }

    private static JsonNode json(String text, String empty)
    {
        try
        {
            return MAPPER.readTree(StringUtils.isBlank(text) || "null".equals(text) ? empty : text);
        }
        catch (JacksonException e)
        {
            return MAPPER.readTree(empty);
        }
    }

    private static String label(String dictType, String value)
    {
        String l = value == null ? null : DictUtils.getDictLabel(dictType, value);
        return StringUtils.isBlank(l) ? StringUtils.defaultString(value) : l;
    }

    private static <T> List<T> concat(List<T> a, List<T> b)
    {
        List<T> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}
