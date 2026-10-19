package com.ygb.abroad.service.impl;

import java.time.LocalDateTime;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrConsultant;
import com.ygb.abroad.domain.AbrLeadAssignSetting;
import com.ygb.abroad.domain.AbrStaffNotice;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.abroad.integration.AbrWecomNotifier;
import com.ygb.abroad.mapper.AbrLeadAssignMapper;
import com.ygb.abroad.mapper.AbrLeadMapper;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.abroad.service.IAbrLeadAssignService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 线索分配与 SLA 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrLeadAssignServiceImpl implements IAbrLeadAssignService
{
    private static final Logger log = LoggerFactory.getLogger(AbrLeadAssignServiceImpl.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 每轮最多分配的线索数 */
    static final int BATCH = 50;

    static final Set<String> GRADES = Set.of("A", "B", "C");

    @Autowired
    private AbrLeadAssignMapper assignMapper;

    @Autowired
    private AbrLeadMapper leadMapper;

    @Autowired
    private AbrWecomNotifier wecom;

    @Autowired
    private IAbrEventService eventService;

    @Override
    public Map<String, Object> runRound()
    {
        AbrLeadAssignSetting s = assignMapper.selectSetting();
        Map<String, Object> r = new LinkedHashMap<>();
        int assigned = 0;
        if (s != null && "Y".equals(s.getAutoAssign()))
        {
            List<String> grades = strings(s.getAssignGrades());
            if (!grades.isEmpty())
            {
                for (Map<String, Object> lead : assignMapper.selectUnassigned(grades, BATCH))
                {
                    assigned += assignOne(lead, s) ? 1 : 0;
                }
            }
        }
        r.put("assigned", assigned);
        Date now = DateUtils.getNowDate();
        int overdue = 0;
        for (Map<String, Object> l : assignMapper.selectSlaOverdue(now))
        {
            Long leadId = ((Number) l.get("leadId")).longValue();
            if (assignMapper.updateSlaStatus(leadId, "pending", "overdue") > 0 && l.get("ownerUserId") instanceof Number owner)
            {
                notify(owner.longValue(), "sla_overdue", "线索超时未联系：" + name(l), name(l) + "（" + l.get("grade") + " 类）已超过首次联系时限，请尽快联系", leadId,
                        "sla_overdue:" + leadId + ":" + l.get("slaDueTime"));
                overdue++;
            }
        }
        r.put("overdue", overdue);
        int escalated = 0;
        int hours = s == null || s.getEscalateHours() == null ? 2 : Math.max(0, s.getEscalateHours());
        Date before = new Date(now.getTime() - hours * 3600_000L);
        for (Map<String, Object> l : assignMapper.selectSlaToEscalate(before))
        {
            Long leadId = ((Number) l.get("leadId")).longValue();
            if (assignMapper.updateSlaStatus(leadId, "overdue", "escalated") > 0)
            {
                Long to = s != null && s.getEscalateUserId() != null ? s.getEscalateUserId()
                        : l.get("ownerUserId") instanceof Number o ? o.longValue() : null;
                if (to != null)
                {
                    notify(to, "sla_escalated", "线索超时升级：" + name(l), name(l) + "（" + l.get("grade") + " 类）超时 " + hours + " 小时后仍未联系，请跟进或转给同事",
                            leadId, "sla_escalated:" + leadId + ":" + l.get("slaDueTime"));
                }
                escalated++;
            }
        }
        r.put("escalated", escalated);
        return r;
    }

    /**
     * 自动分配一条线索：挑选负责人、写负责人与首次联系时限、提醒顾问、写事件
     */
    @Transactional(rollbackFor = Exception.class)
    boolean assignOne(Map<String, Object> lead, AbrLeadAssignSetting s)
    {
        List<AbrLeadAssigner.Candidate> candidates = new ArrayList<>();
        for (AbrConsultant c : assignMapper.selectConsultants())
        {
            if ("Y".equals(c.getAccepting()))
            {
                candidates.add(new AbrLeadAssigner.Candidate(c.getUserId(), new LinkedHashSet<>(strings(c.getCountries())),
                        new LinkedHashSet<>(strings(c.getDegrees())), c.getMaxActive() == null ? 50 : c.getMaxActive(),
                        c.getActiveCount() == null ? 0 : c.getActiveCount(),
                        c.getLastAssignedTime() == null ? null : LocalDateTime.ofInstant(c.getLastAssignedTime().toInstant(), MarketingCalendar.ZONE)));
            }
        }
        Long leadId = ((Number) lead.get("leadId")).longValue();
        String grade = (String) lead.get("grade");
        Long owner = AbrLeadAssigner.pick(candidates, new LinkedHashSet<>(strings((String) lead.get("countries"))), (String) lead.get("degree"),
                "Y".equals(s.getMatchCountry()), s.getPickStrategy());
        if (owner == null)
        {
            return false;
        }
        Date due = slaDue(grade, DateUtils.getNowDate());
        leadMapper.updateOwner(leadId, owner, "auto", due, "system");
        assignMapper.touchAssigned(owner);
        String dueText = due == null ? "" : "，请在 " + DateUtils.parseDateToStr("MM-dd HH:mm", due) + " 前首次联系";
        notify(owner, "lead_assigned", "新线索（" + grade + " 类）", "分配给你一条 " + grade + " 类线索" + dueText, leadId, "lead_assigned:" + leadId + ":" + owner);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lead_id", leadId);
        payload.put("owner_user_id", owner);
        payload.put("grade", grade);
        payload.put("type", "auto");
        eventService.recordSystemEvent(EVENT_ASSIGNED, null, payload);
        return true;
    }

    @Override
    public Date slaDue(String grade, Date assigned)
    {
        AbrLeadAssignSetting s = assignMapper.selectSetting();
        Map<String, Integer> hours = new LinkedHashMap<>();
        if (s != null)
        {
            for (Map.Entry<String, JsonNode> e : json(s.getSlaHours(), "{}").properties())
            {
                if (e.getValue().isIntegralNumber())
                {
                    hours.put(e.getKey(), e.getValue().asInt());
                }
            }
        }
        LocalDateTime due = AbrLeadAssigner.slaDue(LocalDateTime.ofInstant(assigned.toInstant(), MarketingCalendar.ZONE), grade, hours);
        return due == null ? null : Date.from(due.atZone(MarketingCalendar.ZONE).toInstant());
    }

    @Override
    public void notify(Long userId, String kind, String title, String content, Long leadId, String dedupKey)
    {
        AbrStaffNotice n = new AbrStaffNotice();
        n.setUserId(userId);
        n.setNoticeKind(kind);
        n.setTitle(StringUtils.substring(title, 0, 200));
        n.setContent(StringUtils.substring(content, 0, 1000));
        n.setLeadId(leadId);
        n.setDedupKey(StringUtils.substring(dedupKey, 0, 100));
        try
        {
            n.setChannelResult(StringUtils.substring(wecom.send(userId, title, content), 0, 200));
        }
        catch (RuntimeException e)
        {
            log.warn("企业微信提醒发送失败 userId={}：{}", userId, e.getMessage());
            n.setChannelResult(StringUtils.substring("error：" + e.getMessage(), 0, 200));
        }
        assignMapper.insertNotice(n);
    }

    @Override
    public AbrLeadAssignSetting selectSetting()
    {
        return assignMapper.selectSetting();
    }

    @Override
    public int updateSetting(AbrLeadAssignSetting s)
    {
        s.setAutoAssign("N".equals(s.getAutoAssign()) ? "N" : "Y");
        s.setMatchCountry("N".equals(s.getMatchCountry()) ? "N" : "Y");
        if (!AbrLeadAssigner.ROUND_ROBIN.equals(s.getPickStrategy()) && !AbrLeadAssigner.LOAD.equals(s.getPickStrategy()))
        {
            throw new ServiceException("挑选方式只能是 round_robin 轮询 / load 按负载");
        }
        List<String> grades = strings(s.getAssignGrades());
        if (!GRADES.containsAll(grades))
        {
            throw new ServiceException("自动分配的意向度只能是 A / B / C");
        }
        s.setAssignGrades(MAPPER.writeValueAsString(new LinkedHashSet<>(grades)));
        JsonNode h = json(s.getSlaHours(), "{}");
        if (!h.isObject())
        {
            throw new ServiceException("首次联系时限须为 JSON 对象（如 {\"A\": 2, \"B\": 24}）");
        }
        for (Map.Entry<String, JsonNode> e : h.properties())
        {
            if (!GRADES.contains(e.getKey()) || !e.getValue().isIntegralNumber() || e.getValue().asInt() < 1 || e.getValue().asInt() > 720)
            {
                throw new ServiceException("首次联系时限的键须为 A / B / C、值为 1–720 小时");
            }
        }
        s.setSlaHours(h.toString());
        if (s.getEscalateHours() == null || s.getEscalateHours() < 0 || s.getEscalateHours() > 168)
        {
            throw new ServiceException("升级时间须为 0–168 小时");
        }
        if (s.getEscalateUserId() != null && leadMapper.countActiveUser(s.getEscalateUserId()) == 0)
        {
            throw new ServiceException("升级对象不存在或已停用");
        }
        return assignMapper.updateSetting(s);
    }

    @Override
    public List<AbrConsultant> selectConsultants()
    {
        return assignMapper.selectConsultants();
    }

    @Override
    public int saveConsultant(AbrConsultant c)
    {
        if (c.getUserId() == null || leadMapper.countActiveUser(c.getUserId()) == 0)
        {
            throw new ServiceException("员工不存在或已停用");
        }
        c.setCountries(codes(c.getCountries(), "abr_country", "擅长国家 / 地区"));
        c.setDegrees(codes(c.getDegrees(), "abr_degree", "擅长学段"));
        if (c.getMaxActive() == null || c.getMaxActive() < 1 || c.getMaxActive() > 10000)
        {
            throw new ServiceException("负责上限须为 1–10000");
        }
        c.setAccepting("N".equals(c.getAccepting()) ? "N" : "Y");
        AbrConsultant existing = assignMapper.selectConsultantByUser(c.getUserId());
        if (c.getConsultantId() == null)
        {
            if (existing != null)
            {
                throw new ServiceException("这位员工已在顾问列表中");
            }
            return assignMapper.insertConsultant(c);
        }
        if (existing != null && !existing.getConsultantId().equals(c.getConsultantId()))
        {
            throw new ServiceException("这位员工已在顾问列表中");
        }
        return assignMapper.updateConsultant(c);
    }

    @Override
    public int deleteConsultants(Long[] consultantIds)
    {
        return assignMapper.deleteConsultants(consultantIds);
    }

    @Override
    public List<AbrStaffNotice> myNotices(Long userId, boolean unreadOnly)
    {
        return assignMapper.selectNotices(userId, unreadOnly);
    }

    @Override
    public int unreadCount(Long userId)
    {
        return assignMapper.countUnread(userId);
    }

    @Override
    public int markRead(Long userId, Long[] ids)
    {
        return assignMapper.markRead(userId, ids);
    }

    private static String name(Map<String, Object> l)
    {
        String n = l.get("displayName") == null ? "" : String.valueOf(l.get("displayName"));
        return n.isBlank() ? "线索 #" + l.get("leadId") : n;
    }

    private static String codes(String json, String dict, String label)
    {
        JsonNode node = json(json, "[]");
        if (!node.isArray())
        {
            throw new ServiceException(label + "必须是 JSON 数组");
        }
        Set<String> allowed = AbrPlaybookServiceImpl.dictValues(dict);
        for (JsonNode n : node)
        {
            if (!allowed.contains(n.asString("")))
            {
                throw new ServiceException(label + "中的“" + n.asString("") + "”不是可选值");
            }
        }
        return node.toString();
    }

    private static List<String> strings(String json)
    {
        List<String> out = new ArrayList<>();
        for (JsonNode n : json(json, "[]"))
        {
            if (n.isString() && !n.asString().isBlank())
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
            return MAPPER.readTree(StringUtils.isBlank(text) ? empty : text);
        }
        catch (JacksonException e)
        {
            return MAPPER.readTree(empty);
        }
    }
}
