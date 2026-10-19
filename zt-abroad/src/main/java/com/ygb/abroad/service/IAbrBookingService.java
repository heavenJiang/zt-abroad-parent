package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrBooking;
import com.ygb.abroad.domain.AbrConsultSlot;
import com.ygb.abroad.domain.vo.AbrSlotBatchBody;
import com.ygb.abroad.domain.vo.AppBookingBody;
import com.ygb.abroad.domain.vo.AppBookingVo;
import com.ygb.abroad.domain.vo.AppSlotVo;

/**
 * 顾问时段与预约 服务层（学生 / 家长自助预约，或员工代约；同一时段只能被约一次）
 *
 * @author ygb
 */
public interface IAbrBookingService
{
    /** 事件：预约成功（线索卡片、邀约提醒 M8-04 订阅） */
    public static final String EVENT_CREATED = "booking_created";

    /** 事件：改约、取消 */
    public static final String EVENT_CHANGED = "booking_changed";

    /**
     * 查询时段
     *
     * @param slot 查询条件
     * @return 时段
     */
    public List<AbrConsultSlot> selectSlots(AbrConsultSlot slot);

    /**
     * 批量开放时段（同一顾问同一开始时间已有的跳过；早于现在的跳过）
     *
     * @param body 顾问、日期区间、星期几、时间段、时长、方式
     * @param operator 操作人
     * @return created 新开放数、skipped 跳过数
     */
    public Map<String, Object> batchSlots(AbrSlotBatchBody body, String operator);

    /**
     * 关闭可约时段
     *
     * @param slotIds 时段ID
     * @param operator 操作人
     * @return 关闭数
     */
    public int closeSlots(Long[] slotIds, String operator);

    /**
     * 删除没有被预约的时段
     *
     * @param slotIds 时段ID
     * @return 删除数
     */
    public int deleteSlots(Long[] slotIds);

    /**
     * 查询预约
     *
     * @param booking 查询条件
     * @return 预约
     */
    public List<AbrBooking> selectBookings(AbrBooking booking);

    /**
     * 按ID查询预约
     *
     * @param bookingId 预约ID
     * @return 预约
     */
    public AbrBooking selectBooking(Long bookingId);

    /**
     * 员工代约
     *
     * @param leadId 线索ID
     * @param productCode 产品编码
     * @param slotId 时段ID
     * @param note 备注
     * @param operator 操作人
     * @return 预约
     */
    public AbrBooking bookForLead(Long leadId, String productCode, Long slotId, String note, String operator);

    /**
     * 员工改约
     *
     * @param bookingId 预约ID
     * @param slotId 新时段
     * @param operator 操作人
     * @return 预约
     */
    public AbrBooking reschedule(Long bookingId, Long slotId, String operator);

    /**
     * 员工取消（释放时段）
     *
     * @param bookingId 预约ID
     * @param reason 原因（必填）
     * @param operator 操作人
     * @return 预约
     */
    public AbrBooking cancel(Long bookingId, String reason, String operator);

    /**
     * 标记已完成或未到（开始时间之后）
     *
     * @param bookingId 预约ID
     * @param status done / no_show
     * @param operator 操作人
     * @return 预约
     */
    public AbrBooking finish(Long bookingId, String status, String operator);

    /**
     * C 端：可约时段（从至少 abroad.booking.min-hours-ahead 小时后起，时长够该产品）
     *
     * @param productCode 可在线预约的产品
     * @param from 开始日期（yyyy-MM-dd，空为今天）
     * @param days 天数（1–31）
     * @return 时段
     */
    public List<AppSlotVo> appSlots(String productCode, String from, int days);

    /**
     * C 端：预约（须已登录；本人取学生令牌）
     *
     * @param body 产品、时段、留言
     * @return 预约
     */
    public AppBookingVo appBook(AppBookingBody body);

    /**
     * C 端：我的预约
     *
     * @return 预约
     */
    public List<AppBookingVo> appMine();

    /**
     * C 端：改约（只能改本人未开始的预约，最多 abroad.booking.max-reschedule 次）
     *
     * @param bookingId 预约ID
     * @param slotId 新时段
     * @return 预约
     */
    public AppBookingVo appReschedule(Long bookingId, Long slotId);

    /**
     * C 端：取消（只能取消本人未开始的预约）
     *
     * @param bookingId 预约ID
     * @param reason 原因（可空）
     * @return 预约
     */
    public AppBookingVo appCancel(Long bookingId, String reason);
}
