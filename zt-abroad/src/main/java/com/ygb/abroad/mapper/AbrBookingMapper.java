package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrBooking;

/**
 * 预约 数据层
 *
 * @author ygb
 */
public interface AbrBookingMapper
{
    /**
     * 查询预约（线索、顾问、学生账号、状态、产品精确；params.beginTime / endTime 开始日期区间）
     *
     * @param booking 查询条件
     * @return 预约集合
     */
    public List<AbrBooking> selectAbrBookingList(AbrBooking booking);

    /**
     * 按ID查询预约
     *
     * @param bookingId 预约ID
     * @return 预约
     */
    public AbrBooking selectAbrBookingById(Long bookingId);

    /**
     * 学生账号未来的有效预约数
     *
     * @param appUserId 学生账号ID
     * @return 数量
     */
    public int countActiveFuture(Long appUserId);

    /**
     * 新增预约
     *
     * @param booking 预约
     * @return 结果
     */
    public int insertAbrBooking(AbrBooking booking);

    /**
     * 改到新时段（只在已预约时）
     *
     * @param booking 预约（bookingId、slotId、consultantId、startTime、endTime、mode、updateBy）
     * @return 影响行数
     */
    public int reschedule(AbrBooking booking);

    /**
     * 改状态（只从已预约改）
     *
     * @param bookingId 预约ID
     * @param status 新状态
     * @param reason 取消原因
     * @param operator 操作人
     * @return 影响行数
     */
    public int updateStatus(@Param("bookingId") Long bookingId, @Param("status") String status, @Param("reason") String reason,
            @Param("operator") String operator);

    /**
     * 到了开始前提醒时间的预约（未开始、未提醒，且预约时间早于提醒时间点：临时约的只发确认不发提醒）
     *
     * @param now 现在
     * @param until 现在 + 提前小时数
     * @param hours 提前小时数
     * @return 预约
     */
    public List<AbrBooking> selectDueReminders(@Param("now") Date now, @Param("until") Date until, @Param("hours") int hours);

    /**
     * 记已提醒（只记一次）
     *
     * @param bookingId 预约ID
     * @return 行数（0 为已提醒过）
     */
    public int markReminded(@Param("bookingId") Long bookingId);

    /**
     * 邀约对象对应的线索（按线索、学生账号或访客找，取合并后的主线索），带进行中的预约数
     *
     * @param leadId 线索ID
     * @param appUserId 学生账号ID
     * @param visitorId 访客ID
     * @return leadId、stage、ownerUserId、minor、displayName、appUserId、activeBookings（没有线索为 null）
     */
    public Map<String, Object> selectInviteTarget(@Param("leadId") Long leadId, @Param("appUserId") Long appUserId, @Param("visitorId") Long visitorId);
}
