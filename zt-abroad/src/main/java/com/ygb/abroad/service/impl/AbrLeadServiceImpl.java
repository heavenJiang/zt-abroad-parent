package com.ygb.abroad.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.ygb.abroad.domain.AbrAppUser;
import com.ygb.abroad.domain.AbrEvent;
import com.ygb.abroad.domain.AbrLead;
import com.ygb.abroad.domain.AbrLeadFollow;
import com.ygb.abroad.domain.AbrLeadTouch;
import com.ygb.abroad.domain.vo.AbrLeadCapture;
import com.ygb.abroad.domain.vo.AbrLeadImportRow;
import com.ygb.abroad.mapper.AbrAppUserMapper;
import com.ygb.abroad.mapper.AbrEventMapper;
import com.ygb.abroad.mapper.AbrGrowthCursorMapper;
import com.ygb.abroad.mapper.AbrLeadMapper;
import com.ygb.abroad.service.IAbrAppAuthService;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.abroad.service.IAbrLeadAssignService;
import com.ygb.abroad.service.IAbrLeadService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 线索 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrLeadServiceImpl implements IAbrLeadService
{
    private static final Logger log = LoggerFactory.getLogger(AbrLeadServiceImpl.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 游标名 */
    static final String CURSOR = "lead_intake";

    /** 沿 merged_into 最多走几步 */
    static final int MAX_HOPS = 10;

    /** 一次导入最多行数 */
    static final int MAX_IMPORT = 2000;

    @Autowired
    private AbrLeadMapper leadMapper;

    @Autowired
    private AbrAppUserMapper appUserMapper;

    @Autowired
    private AbrEventMapper eventMapper;

    @Autowired
    private AbrGrowthCursorMapper cursorMapper;

    @Autowired
    private IAbrEventService eventService;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private IAbrLeadAssignService assignService;

    /** 事件写入后多少秒视为已静置（与编排器一致：游标可越过之前的 ID 空洞） */
    @Value("${abroad.growth.event-settle-seconds:120}")
    private int settleSeconds;

    /** 每批读取的事件数、每轮最多批数 */
    @Value("${abroad.growth.event-batch:500}")
    private int batchSize;

    @Value("${abroad.growth.event-batches-per-round:5}")
    private int batchesPerRound;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> capture(AbrLeadCapture c)
    {
        if (!AbrLeadRules.ALL_SOURCES.contains(c.source()))
        {
            throw new ServiceException("来源“" + c.source() + "”不是可选值");
        }
        String phone = AbrLeadRules.normalizePhone(c.phone());
        String phoneHash = phone == null ? null : AbrVisitorServiceImpl.hash(phone);
        String unionid = StringUtils.trimToNull(c.unionid());
        Date time = c.occurredTime() == null ? DateUtils.getNowDate() : c.occurredTime();
        String operator = StringUtils.defaultIfBlank(c.operator(), "system");
        List<AbrLead> roots = roots(c.appUserId(), unionid, phoneHash);
        boolean created = false;
        List<Long> merged = new ArrayList<>();
        AbrLead master;
        if (roots.isEmpty())
        {
            master = new AbrLead();
            master.setDisplayName(StringUtils.substring(StringUtils.trimToEmpty(c.displayName()), 0, 64));
            master.setPhone(phone);
            master.setPhoneHash(phoneHash);
            master.setUnionid(unionid);
            master.setAppUserId(c.appUserId());
            master.setVisitorId(c.visitorId());
            master.setMinor(c.minor() ? "Y" : "N");
            master.setFirstSource(c.source());
            master.setFirstChannel(StringUtils.substring(StringUtils.trimToEmpty(c.channelCode()), 0, 32));
            master.setFirstContent(StringUtils.substring(StringUtils.trimToEmpty(c.contentCode()), 0, 32));
            master.setFirstTouchTime(time);
            master.setCreateBy(operator);
            leadMapper.insertAbrLead(master);
            created = true;
        }
        else
        {
            master = AbrLeadRules.master(roots);
            for (AbrLead other : roots)
            {
                if (!other.getLeadId().equals(master.getLeadId()) && merge(other, master, time))
                {
                    merged.add(other.getLeadId());
                }
            }
            AbrLead fill = new AbrLead();
            fill.setLeadId(master.getLeadId());
            fill.setDisplayName(StringUtils.substring(StringUtils.trimToEmpty(c.displayName()), 0, 64));
            fill.setPhone(phone);
            fill.setPhoneHash(phoneHash);
            fill.setUnionid(unionid);
            fill.setAppUserId(c.appUserId());
            fill.setVisitorId(c.visitorId());
            fill.setMinor(c.minor() ? "Y" : "N");
            leadMapper.fillIdentity(fill);
        }
        boolean recorded = true;
        if (c.touchType() != null)
        {
            AbrLeadTouch t = new AbrLeadTouch();
            t.setLeadId(master.getLeadId());
            t.setSource(c.source());
            t.setTouchType(c.touchType());
            t.setChannelCode(StringUtils.substring(StringUtils.trimToEmpty(c.channelCode()), 0, 32));
            t.setContentCode(StringUtils.substring(StringUtils.trimToEmpty(c.contentCode()), 0, 32));
            t.setRefType(c.refType());
            t.setRefId(c.refId());
            t.setEventId(c.eventId());
            t.setSummary(StringUtils.substring(StringUtils.trimToEmpty(c.summary()), 0, 500));
            t.setOccurredTime(time);
            t.setCreateBy(operator);
            recorded = leadMapper.insertTouch(t) > 0;
        }
        if (recorded)
        {
            leadMapper.recordTouch(master.getLeadId(), c.source(), c.channelCode(), c.contentCode(), time, c.touchType() == null ? 0 : 1);
        }
        if (created)
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("lead_id", master.getLeadId());
            payload.put("source", c.source());
            payload.put("channel", master.getFirstChannel());
            eventService.recordSystemEvent(EVENT_CAPTURED, "lead:" + master.getLeadId(), payload);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("lead", masked(leadMapper.selectAbrLeadById(master.getLeadId())));
        r.put("created", created);
        r.put("merged", merged);
        r.put("recorded", recorded);
        return r;
    }

    /**
     * 按身份找到的线索，沿 merged_into 换成保留的一条后去重
     */
    private List<AbrLead> roots(Long appUserId, String unionid, String phoneHash)
    {
        if (appUserId == null && unionid == null && phoneHash == null)
        {
            return List.of();
        }
        Map<Long, AbrLead> out = new LinkedHashMap<>();
        for (AbrLead l : leadMapper.selectByIdentity(appUserId, unionid, phoneHash))
        {
            AbrLead root = root(l);
            if (root != null)
            {
                out.putIfAbsent(root.getLeadId(), root);
            }
        }
        return new ArrayList<>(out.values());
    }

    private AbrLead root(AbrLead l)
    {
        AbrLead cur = l;
        for (int i = 0; i < MAX_HOPS && cur != null && cur.getMergedInto() != null; i++)
        {
            cur = leadMapper.selectAbrLeadById(cur.getMergedInto());
        }
        return cur == null || cur.getMergedInto() != null ? null : cur;
    }

    /**
     * 把 other 合并进 master：标记合并、移动触达记录、首次 / 最近触达取两者的早晚、补身份、记一条合并记录
     */
    private boolean merge(AbrLead other, AbrLead master, Date time)
    {
        if (leadMapper.markMerged(other.getLeadId(), master.getLeadId()) == 0)
        {
            return false;
        }
        leadMapper.moveTouches(other.getLeadId(), master.getLeadId());
        leadMapper.recordTouch(master.getLeadId(), other.getFirstSource(), other.getFirstChannel(), other.getFirstContent(), other.getFirstTouchTime(),
                other.getTouchCount() == null ? 0 : other.getTouchCount());
        leadMapper.recordTouch(master.getLeadId(), other.getLastSource(), other.getLastChannel(), "", other.getLastTouchTime(), 0);
        AbrLead fill = new AbrLead();
        fill.setLeadId(master.getLeadId());
        fill.setDisplayName(other.getDisplayName());
        fill.setPhone(other.getPhone());
        fill.setPhoneHash(other.getPhoneHash());
        fill.setUnionid(other.getUnionid());
        fill.setAppUserId(other.getAppUserId());
        fill.setVisitorId(other.getVisitorId());
        fill.setMinor(other.getMinor());
        leadMapper.fillIdentity(fill);
        AbrLeadTouch t = new AbrLeadTouch();
        t.setLeadId(master.getLeadId());
        t.setSource(other.getFirstSource());
        t.setTouchType("merged");
        t.setRefType("lead");
        t.setRefId(other.getLeadId());
        t.setSummary("合并了线索 #" + other.getLeadId() + "（首次来源 " + other.getFirstSource() + "）");
        t.setOccurredTime(time);
        t.setCreateBy("system");
        leadMapper.insertTouch(t);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestEvents()
    {
        Map<String, Object> r = new LinkedHashMap<>();
        Map<String, Object> cursor = cursorMapper.lockCursor(CURSOR);
        if (cursor == null)
        {
            r.put("skipped", "另一轮正在收口");
            return r;
        }
        long cursorId = ((Number) cursor.get("lastEventId")).longValue();
        long scanFrom = cursorId;
        int read = 0;
        int created = 0;
        int recorded = 0;
        for (int b = 0; b < Math.max(1, batchesPerRound); b++)
        {
            List<AbrEvent> events = eventMapper.selectEventsAfter(scanFrom, settleSeconds, batchSize);
            boolean contiguous = cursorId == scanFrom;
            for (AbrEvent ev : events)
            {
                read++;
                if (ev.getAppUserId() != null)
                {
                    Map<String, Object> res = captureEvent(ev);
                    if (res != null)
                    {
                        created += Boolean.TRUE.equals(res.get("created")) ? 1 : 0;
                        recorded += Boolean.TRUE.equals(res.get("recorded")) ? 1 : 0;
                    }
                }
                // 只有连续的、或已静置的事件才推进游标（写入事务未提交时 ID 会有空洞，下轮再读到时按事件ID去重）
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
            if (events.size() < batchSize)
            {
                break;
            }
        }
        cursorMapper.updateCursor(CURSOR, cursorId, "{}");
        r.put("read", read);
        r.put("created", created);
        r.put("recorded", recorded);
        r.put("cursor", cursorId);
        return r;
    }

    /**
     * 一条有学生账号的业务事件 → 收口（注册、绑定手机号、转化时刻、分享记入时间线；其余只更新最近触达）
     */
    private Map<String, Object> captureEvent(AbrEvent ev)
    {
        AbrAppUser user = appUserMapper.selectAbrAppUserById(ev.getAppUserId());
        if (user == null)
        {
            return null;
        }
        String[] touch = AbrLeadRules.EVENT_TOUCHES.get(ev.getEventType());
        JsonNode payload = json(ev.getPayload());
        String summary = touch == null ? null : touch[1];
        if ("plan_moment".equals(ev.getEventType()))
        {
            String moment = payload.path("moment").asString("");
            summary = summary + "：" + AbrLeadRules.MOMENTS.getOrDefault(moment, moment);
        }
        Long visitorId = user.getPrimaryVisitorId() != null ? user.getPrimaryVisitorId() : ev.getVisitorId();
        boolean minor = visitorId != null && "14_17".equals(leadMapper.selectVisitorAgeBand(visitorId));
        String phone = IAbrAppAuthService.EVENT_PHONE_BOUND.equals(ev.getEventType()) ? user.getPhone() : null;
        try
        {
            // 渠道一律取账号注册时的渠道：登录前的匿名记录在登录后归到账号、时间更早，线索可能由它们新建
            return capture(new AbrLeadCapture(AbrLeadRules.SOURCE_H5, touch == null ? null : touch[0], user.getRegisterChannel(),
                    user.getRegisterContent(), user.getNickname(), phone, user.getUnionid(), user.getAppUserId(), visitorId, minor,
                    ev.getCreateTime(), summary, ev.getRefType(), ev.getRefId(), touch == null ? null : ev.getEventId(), "system"));
        }
        catch (ServiceException e)
        {
            log.warn("线索收口跳过事件 {}：{}", ev.getEventId(), e.getMessage());
            return null;
        }
    }

    @Override
    public List<AbrLead> selectAbrLeadList(AbrLead lead)
    {
        Object kw = lead.getParams().get("keyword");
        if (kw != null)
        {
            String text = String.valueOf(kw).replaceAll("[\\s\\-]", "");
            if (AbrLeadRules.PHONE.matcher(text).matches())
            {
                lead.getParams().remove("keyword");
                lead.getParams().put("phoneHash", AbrVisitorServiceImpl.hash(text));
            }
        }
        List<AbrLead> list = leadMapper.selectAbrLeadList(lead);
        list.forEach(AbrLeadServiceImpl::masked);
        return list;
    }

    @Override
    public AbrLead selectAbrLeadById(Long leadId)
    {
        AbrLead l = leadId == null ? null : leadMapper.selectAbrLeadById(leadId);
        return l == null ? null : masked(root(l));
    }

    @Override
    public List<Map<String, Object>> timeline(Long leadId, int limit)
    {
        AbrLead l = leadId == null ? null : leadMapper.selectAbrLeadById(leadId);
        l = l == null ? null : root(l);
        if (l == null)
        {
            throw new ServiceException("线索不存在");
        }
        List<Map<String, Object>> rows = leadMapper.selectTimeline(l.getLeadId(), l.getVisitorId(), l.getAppUserId(), Math.max(1, Math.min(500, limit)));
        for (Map<String, Object> row : rows)
        {
            row.put("label", AbrLeadRules.label(String.valueOf(row.get("kind")), String.valueOf(row.get("type")), (String) row.get("summary")));
            if (row.get("time") instanceof Date d)
            {
                row.put("time", DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, d));
            }
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> selectActiveStaff()
    {
        return leadMapper.selectActiveStaff();
    }

    @Override
    public String selectPhone(Long leadId)
    {
        AbrLead l = leadId == null ? null : leadMapper.selectAbrLeadById(leadId);
        if (l == null)
        {
            throw new ServiceException("线索不存在");
        }
        return StringUtils.defaultString(root(l).getPhone());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> insertManual(AbrLead lead, String operator)
    {
        String source = StringUtils.defaultIfBlank(lead.getFirstSource(), "manual");
        if (!AbrLeadRules.STAFF_SOURCES.contains(source))
        {
            throw new ServiceException("来源只能是讲座报名、合作方、转介绍、平台私信或员工录入");
        }
        if (StringUtils.isBlank(lead.getPhone()))
        {
            throw new ServiceException("请填写手机号（用于去重与联系）");
        }
        return capture(new AbrLeadCapture(source, "manual", lead.getFirstChannel(), lead.getFirstContent(), lead.getDisplayName(), lead.getPhone(), null,
                null, null, false, null, StringUtils.substring(lead.getRemark(), 0, 500), null, null, null, operator));
    }

    @Override
    public Map<String, Object> importLeads(List<AbrLeadImportRow> rows, String operator)
    {
        if (rows == null || rows.isEmpty())
        {
            throw new ServiceException("表格中没有数据");
        }
        if (rows.size() > MAX_IMPORT)
        {
            throw new ServiceException("一次最多导入 " + MAX_IMPORT + " 行");
        }
        int created = 0;
        int existing = 0;
        Set<Long> leads = new LinkedHashSet<>();
        List<String> failed = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++)
        {
            AbrLeadImportRow row = rows.get(i);
            String source = StringUtils.defaultIfBlank(StringUtils.trim(row.getSource()), "manual");
            try
            {
                if (!AbrLeadRules.STAFF_SOURCES.contains(source))
                {
                    throw new ServiceException("来源“" + source + "”不是可选值");
                }
                if (StringUtils.isBlank(row.getPhone()))
                {
                    throw new ServiceException("缺少手机号");
                }
                AbrLeadCapture c = new AbrLeadCapture(source, "import", row.getChannelCode(), row.getContentCode(), row.getDisplayName(), row.getPhone(), null,
                        null, null, false, row.getOccurredTime(), row.getSummary(), null, null, null, operator);
                // 逐行一个事务：某行失败不影响其他行
                Map<String, Object> r = tx.execute(status -> capture(c));
                if (Boolean.TRUE.equals(r.get("created")))
                {
                    created++;
                }
                else
                {
                    existing++;
                }
                leads.add(((AbrLead) r.get("lead")).getLeadId());
            }
            catch (ServiceException e)
            {
                failed.add("第 " + (i + 2) + " 行：" + e.getMessage());
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("created", created);
        r.put("existing", existing);
        r.put("leads", leads.size());
        r.put("failed", failed);
        return r;
    }

    @Override
    public int updateBasic(AbrLead lead, String operator)
    {
        AbrLead l = live(lead.getLeadId());
        String name = StringUtils.trimToEmpty(lead.getDisplayName());
        if (name.length() > 64)
        {
            throw new ServiceException("称呼最多 64 个字");
        }
        if (StringUtils.isNotBlank(lead.getRemark()) && lead.getRemark().length() > 500)
        {
            throw new ServiceException("备注最多 500 个字");
        }
        AbrLead u = new AbrLead();
        u.setLeadId(l.getLeadId());
        u.setDisplayName(name);
        u.setRemark(StringUtils.trimToNull(lead.getRemark()));
        u.setUpdateBy(operator);
        return leadMapper.updateBasic(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbrLead follow(Long leadId, AbrLeadFollow f, String operator)
    {
        AbrLead l = live(leadId);
        String method = StringUtils.trimToEmpty(f.getMethod());
        if (!AbrPlaybookServiceImpl.dictValues("abr_follow_method").contains(method))
        {
            throw new ServiceException("跟进方式“" + method + "”不是可选值");
        }
        String content = StringUtils.trimToEmpty(f.getContent());
        if (content.isEmpty() || content.length() > 2000)
        {
            throw new ServiceException("请填写跟进内容（最多 2000 字）");
        }
        AbrLeadRules.checkNextFollow(f.getNextFollowTime(), DateUtils.getNowDate());
        AbrLeadFollow row = new AbrLeadFollow();
        row.setLeadId(l.getLeadId());
        row.setFollowKind(AbrLeadFollow.KIND_FOLLOW);
        row.setMethod(method);
        row.setContent(content);
        row.setNextFollowTime(f.getNextFollowTime());
        row.setCreateBy(operator);
        leadMapper.insertFollow(row);
        leadMapper.recordFollow(l.getLeadId(), f.getNextFollowTime(), operator);
        if (AbrLead.STAGE_NEW.equals(l.getStage()))
        {
            stageRow(l.getLeadId(), AbrLead.STAGE_NEW, "contacted", "第一次跟进后进入已联系", operator);
        }
        String to = StringUtils.trimToNull(f.getStageTo());
        if (to != null)
        {
            changeStage(l.getLeadId(), to, f.getLostReason(), operator);
        }
        return selectAbrLeadById(l.getLeadId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbrLead changeStage(Long leadId, String stage, String lostReason, String operator)
    {
        AbrLead l = live(leadId);
        String to = StringUtils.trimToEmpty(stage);
        if (!AbrPlaybookServiceImpl.dictValues("abr_lead_stage").contains(to))
        {
            throw new ServiceException("阶段“" + to + "”不是可选值");
        }
        String reason = StringUtils.trimToEmpty(lostReason);
        if (AbrLeadRules.STAGE_LOST.equals(to) && reason.isEmpty())
        {
            throw new ServiceException("改为流失须填写原因");
        }
        if (reason.length() > 500)
        {
            throw new ServiceException("流失原因最多 500 字");
        }
        if (to.equals(l.getStage()) && !AbrLeadRules.STAGE_LOST.equals(to))
        {
            return selectAbrLeadById(l.getLeadId());
        }
        leadMapper.updateStage(l.getLeadId(), to, AbrLeadRules.STAGE_LOST.equals(to) ? reason : "", operator);
        stageRow(l.getLeadId(), l.getStage(), to, reason, operator);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lead_id", l.getLeadId());
        payload.put("from", l.getStage());
        payload.put("to", to);
        eventService.recordSystemEvent(EVENT_STAGE_CHANGED, null, payload);
        return selectAbrLeadById(l.getLeadId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbrLead assign(Long leadId, Long ownerUserId, String reason, String operator)
    {
        AbrLead l = live(leadId);
        if (ownerUserId == null || leadMapper.countActiveUser(ownerUserId) == 0)
        {
            throw new ServiceException("接手的同事不存在或已停用");
        }
        if (ownerUserId.equals(l.getOwnerUserId()))
        {
            throw new ServiceException("已经是这位同事负责");
        }
        String text = StringUtils.trimToEmpty(reason);
        if (text.length() > 500)
        {
            throw new ServiceException("原因最多 500 字");
        }
        leadMapper.updateOwner(l.getLeadId(), ownerUserId, "manual", assignService.slaDue(l.getGrade(), DateUtils.getNowDate()), operator);
        assignService.notify(ownerUserId, "lead_assigned", "同事转给你一条线索", (StringUtils.isBlank(l.getDisplayName()) ? "线索 #" + l.getLeadId() : l.getDisplayName())
                + (text.isEmpty() ? "" : "：" + text), l.getLeadId(), "lead_assigned:" + l.getLeadId() + ":" + ownerUserId + ":" + System.currentTimeMillis());
        AbrLeadFollow row = new AbrLeadFollow();
        row.setLeadId(l.getLeadId());
        row.setFollowKind(AbrLeadFollow.KIND_ASSIGN);
        row.setContent(text);
        row.setOwnerFrom(l.getOwnerUserId());
        row.setOwnerTo(ownerUserId);
        row.setCreateBy(operator);
        leadMapper.insertFollow(row);
        return selectAbrLeadById(l.getLeadId());
    }

    @Override
    public List<AbrLeadFollow> follows(Long leadId)
    {
        return leadMapper.selectFollows(live(leadId).getLeadId());
    }

    private void stageRow(Long leadId, String from, String to, String content, String operator)
    {
        AbrLeadFollow row = new AbrLeadFollow();
        row.setLeadId(leadId);
        row.setFollowKind(AbrLeadFollow.KIND_STAGE);
        row.setStageFrom(from);
        row.setStageTo(to);
        row.setContent(content);
        row.setCreateBy(operator);
        leadMapper.insertFollow(row);
    }

    /**
     * 未合并、未删除的线索（已合并的换成保留的一条）
     */
    private AbrLead live(Long leadId)
    {
        AbrLead l = leadId == null ? null : leadMapper.selectAbrLeadById(leadId);
        l = l == null ? null : root(l);
        if (l == null)
        {
            throw new ServiceException("线索不存在");
        }
        return l;
    }

    private static AbrLead masked(AbrLead l)
    {
        if (l != null)
        {
            l.setPhone(AbrAppAuthServiceImpl.maskPhone(l.getPhone()));
        }
        return l;
    }

    private static JsonNode json(String text)
    {
        try
        {
            return MAPPER.readTree(StringUtils.defaultIfBlank(text, "{}"));
        }
        catch (JacksonException e)
        {
            return MAPPER.createObjectNode();
        }
    }
}
