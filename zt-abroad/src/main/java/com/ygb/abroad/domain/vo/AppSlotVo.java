package com.ygb.abroad.domain.vo;

/**
 * C 端：可约时段
 *
 * @param slotId 时段ID
 * @param startTime 开始时间（yyyy-MM-dd HH:mm）
 * @param endTime 结束时间
 * @param mode 方式（online / offline）
 * @param consultant 顾问称呼
 * @author ygb
 */
public record AppSlotVo(Long slotId, String startTime, String endTime, String mode, String consultant)
{
}
