package com.ygb.abroad.domain.vo;

/**
 * C 端：我的预约
 *
 * @param bookingId 预约ID
 * @param productName 产品名称
 * @param startTime 开始时间（yyyy-MM-dd HH:mm）
 * @param endTime 结束时间
 * @param mode 方式
 * @param status 状态（booked / cancelled / done / no_show）
 * @param consultant 顾问称呼
 * @param changeable 是否还能改约或取消
 * @author ygb
 */
public record AppBookingVo(Long bookingId, String productName, String startTime, String endTime, String mode, String status, String consultant,
        boolean changeable)
{
}
