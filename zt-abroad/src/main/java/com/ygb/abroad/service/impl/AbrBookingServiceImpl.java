package com.ygb.abroad.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrAppUser;
import com.ygb.abroad.domain.AbrBooking;
import com.ygb.abroad.domain.AbrConsultSlot;
import com.ygb.abroad.domain.AbrLead;
import com.ygb.abroad.domain.AbrProduct;
import com.ygb.abroad.domain.vo.AbrLeadCapture;
import com.ygb.abroad.domain.vo.AbrSlotBatchBody;
import com.ygb.abroad.domain.vo.AppBookingBody;
import com.ygb.abroad.domain.vo.AppBookingVo;
import com.ygb.abroad.domain.vo.AppSlotVo;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.abroad.mapper.AbrAppUserMapper;
import com.ygb.abroad.mapper.AbrBookingMapper;
import com.ygb.abroad.mapper.AbrConsultSlotMapper;
import com.ygb.abroad.mapper.AbrLeadMapper;
import com.ygb.abroad.service.IAbrAppAuthService;
import com.ygb.abroad.service.IAbrBookingService;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.abroad.service.IAbrLeadService;
import com.ygb.abroad.service.IAbrProductService;
import com.ygb.common.constant.HttpStatus;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 顾问时段与预约 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrBookingServiceImpl implements IAbrBookingService
{
    /** 预约后线索自动进入“首访已约”的阶段 */
    static final List<String> EARLY_STAGES = List.of("new", "contacted");

    @Autowired
    private AbrConsultSlotMapper slotMapper;

    @Autowired
    private AbrBookingMapper bookingMapper;

    @Autowired
    private AbrLeadMapper leadMapper;

    @Autowired
    private AbrAppUserMapper appUserMapper;

    @Autowired
    private IAbrProductService productService;

    @Autowired
    private IAbrLeadService leadService;

    @Autowired
    private IAbrAppAuthService appAuthService;

    @Autowired
    private IAbrEventService eventService;

    /** C 端最早可约到几小时之后 */
    @Value("${abroad.booking.min-hours-ahead:2}")
    private int minHoursAhead;

    /** 每个学生账号同时最多几个未开始的预约 */
    @Value("${abroad.booking.max-active:2}")
    private int maxActive;

    /** C 端每个预约最多改约几次 */
    @Value("${abroad.booking.max-reschedule:3}")
    private int maxReschedule;

    @Override
    public List<AbrConsultSlot> selectSlots(AbrConsultSlot slot)
    {
        return slotMapper.selectAbrConsultSlotList(slot);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchSlots(AbrSlotBatchBody body, String operator)
    {
        if (body.getUserId() == null || leadMapper.countActiveUser(body.getUserId()) == 0)
        {
            throw new ServiceException("顾问不存在或已停用");
        }
        String mode = StringUtils.defaultIfBlank(body.getMode(), "online");
        if (!AbrBookingRules.MODES.contains(mode))
        {
            throw new ServiceException("方式只能是 online / offline");
        }
        LocalDate from;
        LocalDate to;
        try
        {
            from = LocalDate.parse(StringUtils.trimToEmpty(body.getFrom()));
            to = LocalDate.parse(StringUtils.trimToEmpty(body.getTo()));
        }
        catch (DateTimeParseException e)
        {
            throw new ServiceException("日期格式须为 yyyy-MM-dd");
        }
        List<LocalDateTime[]> slots = AbrBookingRules.slots(from, to, body.getWeekdays() == null ? null : new LinkedHashSet<>(body.getWeekdays()),
                body.getRanges(), body.getMinutes() == null ? 30 : body.getMinutes(), LocalDateTime.now(MarketingCalendar.ZONE));
        int created = 0;
        for (LocalDateTime[] s : slots)
        {
            AbrConsultSlot slot = new AbrConsultSlot();
            slot.setUserId(body.getUserId());
            slot.setStartTime(date(s[0]));
            slot.setEndTime(date(s[1]));
            slot.setMode(mode);
            slot.setCreateBy(operator);
            created += slotMapper.insertSlotIgnore(slot);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("created", created);
        r.put("skipped", slots.size() - created);
        return r;
    }

    @Override
    public int closeSlots(Long[] slotIds, String operator)
    {
        return slotMapper.closeSlots(slotIds, operator);
    }

    @Override
    public int deleteSlots(Long[] slotIds)
    {
        return slotMapper.deleteFreeSlots(slotIds);
    }

    @Override
    public List<AbrBooking> selectBookings(AbrBooking booking)
    {
        return bookingMapper.selectAbrBookingList(booking);
    }

    @Override
    public AbrBooking selectBooking(Long bookingId)
    {
        return bookingMapper.selectAbrBookingById(bookingId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbrBooking bookForLead(Long leadId, String productCode, Long slotId, String note, String operator)
    {
        AbrLead lead = leadService.selectAbrLeadById(leadId);
        if (lead == null)
        {
            throw new ServiceException("线索不存在");
        }
        return book(lead.getLeadId(), lead.getVisitorId(), lead.getAppUserId(), productCode, slotId, note, "staff", false, operator);
    }

    /**
     * 预约：产品可在线预约、时段可约且时长够、C 端须在最早可约时间之后；占用时段失败（刚被别人约走）时整体回滚
     */
    private AbrBooking book(Long leadId, Long visitorId, Long appUserId, String productCode, Long slotId, String note, String source, boolean app,
            String operator)
    {
        AbrProduct product = productService.requireActive(productCode);
        if (!"Y".equals(product.getBookable()))
        {
            throw new ServiceException("这个产品不能在线预约");
        }
        AbrConsultSlot slot = requireFreeSlot(slotId, product, app);
        String text = note(note);
        AbrBooking b = new AbrBooking();
        b.setLeadId(leadId);
        b.setVisitorId(visitorId);
        b.setAppUserId(appUserId);
        b.setProductCode(product.getProductCode());
        b.setProductName(product.getProductName());
        b.setSlotId(slot.getSlotId());
        b.setConsultantId(slot.getUserId());
        b.setStartTime(slot.getStartTime());
        b.setEndTime(slot.getEndTime());
        b.setMode(slot.getMode());
        b.setSource(source);
        b.setNote(text);
        b.setCreateBy(operator);
        bookingMapper.insertAbrBooking(b);
        if (slotMapper.occupy(slot.getSlotId(), b.getBookingId()) == 0)
        {
            throw new ServiceException("这个时段刚被约走了，请换一个时段");
        }
        AbrLead lead = leadId == null ? null : leadMapper.selectAbrLeadById(leadId);
        if (lead != null && EARLY_STAGES.contains(lead.getStage()))
        {
            leadService.changeStage(leadId, "booked", null, operator);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("booking_id", b.getBookingId());
        payload.put("lead_id", leadId);
        payload.put("product_code", product.getProductCode());
        payload.put("start_time", DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, slot.getStartTime()));
        payload.put("source", source);
        eventService.recordSystemEvent(EVENT_CREATED, "booking:" + b.getBookingId(), payload);
        return bookingMapper.selectAbrBookingById(b.getBookingId());
    }

    private AbrConsultSlot requireFreeSlot(Long slotId, AbrProduct product, boolean app)
    {
        AbrConsultSlot slot = slotId == null ? null : slotMapper.selectAbrConsultSlotById(slotId);
        if (slot == null || !AbrConsultSlot.STATUS_OPEN.equals(slot.getStatus()))
        {
            throw new ServiceException("这个时段已不可约，请换一个时段");
        }
        Date earliest = app ? date(LocalDateTime.now(MarketingCalendar.ZONE).plusHours(minHoursAhead)) : DateUtils.getNowDate();
        if (slot.getStartTime().before(earliest))
        {
            throw new ServiceException(app ? "请选择 " + minHoursAhead + " 小时之后的时段" : "不能约已经开始的时段");
        }
        long minutes = (slot.getEndTime().getTime() - slot.getStartTime().getTime()) / 60000;
        if (product.getDurationMinutes() != null && minutes < product.getDurationMinutes())
        {
            throw new ServiceException("这个时段不够 " + product.getDurationMinutes() + " 分钟，请换一个时段");
        }
        return slot;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbrBooking reschedule(Long bookingId, Long slotId, String operator)
    {
        return doReschedule(requireBooked(bookingId), slotId, false, operator);
    }

    private AbrBooking doReschedule(AbrBooking b, Long slotId, boolean app, String operator)
    {
        if (!b.getStartTime().after(DateUtils.getNowDate()))
        {
            throw new ServiceException("预约已经开始，不能改约");
        }
        if (slotId != null && slotId.equals(b.getSlotId()))
        {
            throw new ServiceException("新时段与原时段相同");
        }
        AbrProduct product = productService.requireActive(b.getProductCode());
        AbrConsultSlot slot = requireFreeSlot(slotId, product, app);
        if (slotMapper.occupy(slot.getSlotId(), b.getBookingId()) == 0)
        {
            throw new ServiceException("这个时段刚被约走了，请换一个时段");
        }
        slotMapper.release(b.getSlotId(), b.getBookingId());
        AbrBooking u = new AbrBooking();
        u.setBookingId(b.getBookingId());
        u.setSlotId(slot.getSlotId());
        u.setConsultantId(slot.getUserId());
        u.setStartTime(slot.getStartTime());
        u.setEndTime(slot.getEndTime());
        u.setMode(slot.getMode());
        u.setUpdateBy(operator);
        if (bookingMapper.reschedule(u) == 0)
        {
            throw new ServiceException("预约状态已变化，请刷新");
        }
        changed(b, "rescheduled");
        return bookingMapper.selectAbrBookingById(b.getBookingId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbrBooking cancel(Long bookingId, String reason, String operator)
    {
        String text = StringUtils.trimToEmpty(reason);
        if (text.isEmpty())
        {
            throw new ServiceException("请填写取消原因");
        }
        return doCancel(requireBooked(bookingId), text, operator);
    }

    private AbrBooking doCancel(AbrBooking b, String reason, String operator)
    {
        if (reason.length() > 500)
        {
            throw new ServiceException("原因最多 500 字");
        }
        if (bookingMapper.updateStatus(b.getBookingId(), AbrBooking.STATUS_CANCELLED, reason, operator) == 0)
        {
            throw new ServiceException("预约状态已变化，请刷新");
        }
        slotMapper.release(b.getSlotId(), b.getBookingId());
        changed(b, "cancelled");
        return bookingMapper.selectAbrBookingById(b.getBookingId());
    }

    @Override
    public AbrBooking finish(Long bookingId, String status, String operator)
    {
        AbrBooking b = requireBooked(bookingId);
        if (!AbrBooking.STATUS_DONE.equals(status) && !AbrBooking.STATUS_NO_SHOW.equals(status))
        {
            throw new ServiceException("只能标记为 done 已完成 / no_show 未到");
        }
        if (b.getStartTime().after(DateUtils.getNowDate()))
        {
            throw new ServiceException("预约还没开始");
        }
        bookingMapper.updateStatus(b.getBookingId(), status, "", operator);
        changed(b, status);
        return bookingMapper.selectAbrBookingById(b.getBookingId());
    }

    @Override
    public List<AppSlotVo> appSlots(String productCode, String from, int days)
    {
        AbrProduct product = productService.requireActive(productCode);
        if (!"Y".equals(product.getBookable()))
        {
            throw new ServiceException("这个产品不能在线预约");
        }
        LocalDate d;
        try
        {
            d = StringUtils.isBlank(from) ? MarketingCalendar.today() : LocalDate.parse(from.trim());
        }
        catch (DateTimeParseException e)
        {
            throw new ServiceException("日期格式须为 yyyy-MM-dd");
        }
        LocalDateTime earliest = LocalDateTime.now(MarketingCalendar.ZONE).plusHours(minHoursAhead);
        LocalDateTime start = d.atStartOfDay().isBefore(earliest) ? earliest : d.atStartOfDay();
        LocalDateTime end = d.plusDays(Math.max(1, Math.min(31, days))).atStartOfDay();
        int minutes = product.getDurationMinutes() == null ? 0 : product.getDurationMinutes();
        return slotMapper.selectOpenSlots(date(start), date(end), minutes).stream()
                .map(s -> new AppSlotVo(s.getSlotId(), minute(s.getStartTime()), minute(s.getEndTime()), s.getMode(), StringUtils.defaultString(s.getConsultantName())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppBookingVo appBook(AppBookingBody body)
    {
        AbrAppUser user = requireAppUser();
        if (bookingMapper.countActiveFuture(user.getAppUserId()) >= maxActive)
        {
            throw new ServiceException("你已有 " + maxActive + " 个未开始的预约，请先完成或取消后再约");
        }
        AbrProduct product = productService.requireActive(body == null ? null : body.getProductCode());
        Map<String, Object> captured = leadService.capture(new AbrLeadCapture(AbrLeadRules.SOURCE_H5, "booking", user.getRegisterChannel(),
                user.getRegisterContent(), user.getNickname(), user.getPhone(), user.getUnionid(), user.getAppUserId(), user.getPrimaryVisitorId(), false, null,
                "预约了" + product.getProductName(), "product", product.getProductId(), null, "app"));
        Long leadId = ((AbrLead) captured.get("lead")).getLeadId();
        AbrBooking b = book(leadId, user.getPrimaryVisitorId(), user.getAppUserId(), product.getProductCode(), body.getSlotId(), body.getNote(), "app", true,
                "app");
        return vo(b);
    }

    @Override
    public List<AppBookingVo> appMine()
    {
        AbrAppUser user = requireAppUser();
        AbrBooking q = new AbrBooking();
        q.setAppUserId(user.getAppUserId());
        return bookingMapper.selectAbrBookingList(q).stream().map(this::vo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppBookingVo appReschedule(Long bookingId, Long slotId)
    {
        AbrBooking b = requireOwn(bookingId);
        if (b.getRescheduleCount() != null && b.getRescheduleCount() >= maxReschedule)
        {
            throw new ServiceException("每个预约最多改约 " + maxReschedule + " 次，请取消后重新预约");
        }
        return vo(doReschedule(b, slotId, true, "app"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppBookingVo appCancel(Long bookingId, String reason)
    {
        AbrBooking b = requireOwn(bookingId);
        if (!b.getStartTime().after(DateUtils.getNowDate()))
        {
            throw new ServiceException("预约已经开始，不能取消");
        }
        return vo(doCancel(b, StringUtils.defaultIfBlank(StringUtils.trim(reason), "学生取消"), "app"));
    }

    private AppBookingVo vo(AbrBooking b)
    {
        boolean changeable = AbrBooking.STATUS_BOOKED.equals(b.getStatus()) && b.getStartTime().after(DateUtils.getNowDate());
        return new AppBookingVo(b.getBookingId(), b.getProductName(), minute(b.getStartTime()), minute(b.getEndTime()), b.getMode(), b.getStatus(),
                StringUtils.defaultString(b.getConsultantName()), changeable);
    }

    private void changed(AbrBooking b, String change)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("booking_id", b.getBookingId());
        payload.put("lead_id", b.getLeadId());
        payload.put("change", change);
        eventService.recordSystemEvent(EVENT_CHANGED, null, payload);
    }

    private AbrBooking requireBooked(Long bookingId)
    {
        AbrBooking b = bookingId == null ? null : bookingMapper.selectAbrBookingById(bookingId);
        if (b == null)
        {
            throw new ServiceException("预约不存在");
        }
        if (!AbrBooking.STATUS_BOOKED.equals(b.getStatus()))
        {
            throw new ServiceException("预约已取消或已结束");
        }
        return b;
    }

    private AbrBooking requireOwn(Long bookingId)
    {
        AbrAppUser user = requireAppUser();
        AbrBooking b = requireBooked(bookingId);
        if (!user.getAppUserId().equals(b.getAppUserId()))
        {
            throw new ServiceException("预约不存在");
        }
        return b;
    }

    private AbrAppUser requireAppUser()
    {
        Long id = appAuthService.currentAppUserId();
        if (id == null)
        {
            throw new ServiceException("请先登录后再预约", HttpStatus.UNAUTHORIZED);
        }
        return appUserMapper.selectAbrAppUserById(id);
    }

    private static String note(String note)
    {
        String text = StringUtils.trimToEmpty(note);
        if (text.length() > 500)
        {
            throw new ServiceException("留言最多 500 字");
        }
        if (!text.isEmpty() && AbrAssessmentAnswers.containsTag(JsonNodeFactory.instance.stringNode(text)))
        {
            throw new ServiceException("留言不能包含 < 加字母的写法");
        }
        return text;
    }

    private static Date date(LocalDateTime t)
    {
        return Date.from(t.atZone(MarketingCalendar.ZONE).toInstant());
    }

    private static String minute(Date d)
    {
        return d == null ? "" : DateUtils.parseDateToStr("yyyy-MM-dd HH:mm", d);
    }
}
