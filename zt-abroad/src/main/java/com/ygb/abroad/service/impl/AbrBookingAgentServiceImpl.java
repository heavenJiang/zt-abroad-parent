package com.ygb.abroad.service.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrBooking;
import com.ygb.abroad.domain.AbrMomentRule;
import com.ygb.abroad.domain.AbrProduct;
import com.ygb.abroad.growth.OutboundDecision;
import com.ygb.abroad.mapper.AbrBookingMapper;
import com.ygb.abroad.mapper.AbrMessageMapper;
import com.ygb.abroad.mapper.AbrMomentRuleMapper;
import com.ygb.abroad.mapper.AbrProductMapper;
import com.ygb.abroad.service.IAbrBookingAgentService;
import com.ygb.abroad.service.IAbrGrowthGuardService;
import com.ygb.abroad.service.IAbrLeadAssignService;
import com.ygb.abroad.service.IAbrMessageService;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.StringUtils;

/**
 * booking_agent 邀约 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrBookingAgentServiceImpl implements IAbrBookingAgentService
{
    static final String TPL_CONFIRMED = "booking_confirmed";

    static final String TPL_REMINDER = "booking_reminder";

    static final String TPL_CHANGED = "booking_changed";

    static final String TPL_NOSHOW = "booking_noshow";

    static final String TPL_INVITE = "booking_invite";

    /** 已过首访的阶段（不再邀约） */
    static final Set<String> LATE_STAGES = Set.of("booked", "proposal", "quoting", "won", "lost");

    /** 没有转化时刻推荐语时的邀约理由 */
    static final String DEFAULT_REASON = "选校单可以请顾问一起看看";

    /** 转人工邀约时提醒谁：没有负责顾问时提醒能看全部线索的员工 */
    static final String PERM_ALL_LEADS = "abroad:lead:all";

    @Autowired
    private AbrBookingMapper bookingMapper;

    @Autowired
    private AbrProductMapper productMapper;

    @Autowired
    private AbrMomentRuleMapper momentRuleMapper;

    @Autowired
    private AbrMessageMapper messageMapper;

    @Autowired
    private IAbrMessageService messageService;

    @Autowired
    private IAbrGrowthGuardService guardService;

    @Autowired
    private IAbrLeadAssignService assignService;

    /** C 端预约页（产品编码追加在后面） */
    @Value("${abroad.booking.page-url:${abroad.app.url:http://localhost:5173/}pages/booking/index?product=}")
    private String bookingPage;

    @Override
    public IAbrMessageService.Result confirm(Long bookingId, Long taskId)
    {
        AbrBooking b = bookingMapper.selectAbrBookingById(bookingId);
        if (b == null || !AbrBooking.STATUS_BOOKED.equals(b.getStatus()))
        {
            return null;
        }
        return send(TPL_CONFIRMED, b, vars(b, null), taskId);
    }

    @Override
    public IAbrMessageService.Result notifyChange(Long bookingId, String change, Long taskId)
    {
        AbrBooking b = bookingMapper.selectAbrBookingById(bookingId);
        if (b == null)
        {
            return null;
        }
        return switch (StringUtils.defaultString(change))
        {
            case "rescheduled" -> AbrBooking.STATUS_BOOKED.equals(b.getStatus()) ? send(TPL_CHANGED, b, vars(b, "已改约"), taskId) : null;
            case "cancelled" -> send(TPL_CHANGED, b, vars(b, "已取消"), taskId);
            case AbrBooking.STATUS_NO_SHOW -> send(TPL_NOSHOW, b, vars(b, null), taskId);
            default -> null;
        };
    }

    @Override
    public Map<String, Object> remindDue(int hoursBefore, Long taskId)
    {
        int hours = Math.max(1, Math.min(72, hoursBefore));
        Date now = DateUtils.getNowDate();
        List<AbrBooking> due = bookingMapper.selectDueReminders(now, new Date(now.getTime() + hours * 3600_000L), hours);
        int sent = 0;
        int deferred = 0;
        int notDelivered = 0;
        for (AbrBooking b : due)
        {
            IAbrMessageService.Result r = send(TPL_REMINDER, b, vars(b, null), taskId);
            if ("deferred".equals(r.status()))
            {
                deferred++;
                continue;
            }
            // 被拦下、渠道不可达也记为已处理，避免每 10 分钟重复尝试；顾问在发送记录里能看到原因
            bookingMapper.markReminded(b.getBookingId());
            if (r.delivered())
            {
                sent++;
            }
            else
            {
                notDelivered++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("due", due.size());
        out.put("sent", sent);
        out.put("deferred", deferred);
        out.put("notDelivered", notDelivered);
        return out;
    }

    @Override
    public Map<String, Object> invite(Long leadId, Long appUserId, Long visitorId, String moment, String productCode, Long taskId)
    {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> lead = bookingMapper.selectInviteTarget(leadId, appUserId, visitorId);
        if (lead != null)
        {
            if (LATE_STAGES.contains(String.valueOf(lead.get("stage"))))
            {
                return result(out, "skipped", "线索阶段已过首访（" + lead.get("stage") + "）");
            }
            if (((Number) lead.get("activeBookings")).intValue() > 0)
            {
                return result(out, "skipped", "已有进行中的预约");
            }
        }
        AbrMomentRule rule = rule(moment);
        String code = rule != null && StringUtils.isNotBlank(rule.getRecommendProduct()) ? rule.getRecommendProduct() : productCode;
        AbrProduct product = StringUtils.isBlank(code) ? null : productMapper.selectAbrProductByCode(code);
        if (product == null || !"0".equals(product.getStatus()) || !"Y".equals(product.getBookable()))
        {
            return result(out, "skipped", "推荐的产品不存在、已停用或不能预约：" + code);
        }
        String reason = rule != null && StringUtils.isNotBlank(rule.getRecommendText()) ? rule.getRecommendText() : DEFAULT_REASON;
        Map<String, Object> vars = new HashMap<>();
        vars.put("productName", product.getProductName());
        vars.put("reason", StringUtils.substring(reason, 0, 20));
        vars.put("bookingUrl", bookingPage + product.getProductCode());
        out.put("product", product.getProductCode());
        Long owner = lead == null || !(lead.get("ownerUserId") instanceof Number n) ? null : n.longValue();
        Long target = lead != null ? ((Number) lead.get("leadId")).longValue() : appUserId != null ? appUserId : visitorId;
        String targetType = lead != null ? "lead" : appUserId != null ? "app_user" : "visitor";
        out.put("targetType", targetType);
        out.put("targetId", target);
        OutboundDecision d = guardService.outbound(AGENT, targetType + ":" + target);
        if (d == OutboundDecision.BLOCKED)
        {
            return result(out, "blocked", "booking_agent 已暂停或停用");
        }
        String who = lead == null ? "访客 #" + target : StringUtils.defaultIfBlank((String) lead.get("displayName"), "线索 #" + target);
        if (d == OutboundDecision.CONFIRM)
        {
            manual(owner, lead == null ? null : target, "建议邀约：" + who, who + " 适合邀约「" + product.getProductName() + "」（" + reason
                    + "）。booking_agent 为人工确认模式，请顾问联系后代约", "invite_confirm:" + targetType + ":" + target + ":" + StringUtils.defaultString(moment));
            return result(out, "manual", "人工确认模式：已提醒顾问邀约");
        }
        IAbrMessageService.Result r = messageService.sendToStudent(TPL_INVITE, targetType, target, vars, AGENT, taskId, false);
        out.put("send", r);
        if (r.delivered())
        {
            return result(out, "sent", "");
        }
        if ("deferred".equals(r.status()))
        {
            out.put("deferUntil", r.deferUntil());
            return result(out, "deferred", r.reason());
        }
        if ("blocked".equals(r.status()))
        {
            // 退订、未成年人、频控、合规拦截：不转人工
            return result(out, "blocked", r.reason());
        }
        manual(owner, lead == null ? null : target, "请人工邀约：" + who, who + " 适合邀约「" + product.getProductName() + "」，自动消息未送达（" + r.reason()
                + "），请顾问联系", "invite_manual:" + targetType + ":" + target + ":" + StringUtils.defaultString(moment));
        return result(out, "manual", "自动消息未送达，已转顾问人工邀约：" + r.reason());
    }

    private void manual(Long owner, Long leadId, String title, String content, String dedupKey)
    {
        List<Long> to = owner != null ? List.of(owner) : messageMapper.selectUsersWithPerm(PERM_ALL_LEADS);
        for (Long u : to)
        {
            assignService.notify(u, "invite_manual", title, content, leadId, dedupKey);
        }
    }

    private AbrMomentRule rule(String moment)
    {
        if (StringUtils.isBlank(moment))
        {
            return null;
        }
        return momentRuleMapper.selectAbrMomentRuleList(new AbrMomentRule()).stream()
                .filter(r -> moment.equals(r.getMomentCode()) && "0".equals(r.getStatus())).findFirst().orElse(null);
    }

    /**
     * 预约消息发给预约人：有学生账号发账号，否则发线索
     */
    private IAbrMessageService.Result send(String template, AbrBooking b, Map<String, Object> vars, Long taskId)
    {
        boolean app = b.getAppUserId() != null;
        Long target = app ? b.getAppUserId() : b.getLeadId() != null ? b.getLeadId() : b.getVisitorId();
        String type = app ? "app_user" : b.getLeadId() != null ? "lead" : "visitor";
        return messageService.sendToStudent(template, type, target, vars, AGENT, taskId, false);
    }

    private Map<String, Object> vars(AbrBooking b, String changeText)
    {
        Map<String, Object> v = new HashMap<>();
        v.put("productName", b.getProductName());
        v.put("startTime", DateUtils.parseDateToStr("MM-dd HH:mm", b.getStartTime()));
        v.put("consultantName", StringUtils.defaultIfBlank(b.getConsultantName(), "顾问"));
        v.put("bookingUrl", bookingPage + StringUtils.defaultString(b.getProductCode()));
        if (changeText != null)
        {
            v.put("changeText", changeText);
        }
        return v;
    }

    private static Map<String, Object> result(Map<String, Object> out, String action, String reason)
    {
        out.put("action", action);
        out.put("reason", reason);
        return out;
    }
}
