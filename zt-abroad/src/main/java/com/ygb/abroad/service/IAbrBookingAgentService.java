package com.ygb.abroad.service;

import java.util.Map;

/**
 * booking_agent 邀约（M8-04）：预约确认、开始前提醒、变更与爽约通知、A 类线索与转化时刻的邀约
 *
 * @author ygb
 */
public interface IAbrBookingAgentService
{
    /** Agent 编码 */
    public static final String AGENT = "booking_agent";

    /**
     * 预约成功后发确认
     *
     * @param bookingId 预约ID
     * @param taskId 编排任务ID
     * @return 发送结果
     */
    public IAbrMessageService.Result confirm(Long bookingId, Long taskId);

    /**
     * 预约变更后通知（rescheduled 改约 / cancelled 取消 发变更通知，no_show 未到 发改约邀请，done 不发）
     *
     * @param bookingId 预约ID
     * @param change 变更
     * @param taskId 编排任务ID
     * @return 发送结果（不需要发时为 null）
     */
    public IAbrMessageService.Result notifyChange(Long bookingId, String change, Long taskId);

    /**
     * 发开始前提醒（到时间、未提醒的预约逐个发；免打扰中推迟的下一轮再发）
     *
     * @param hoursBefore 提前小时数
     * @param taskId 编排任务ID
     * @return 统计（due、sent、deferred、notDelivered）
     */
    public Map<String, Object> remindDue(int hoursBefore, Long taskId);

    /**
     * 邀约：对象为线索、学生账号或访客，推荐产品（转化时刻配置优先，否则 productCode）；
     * 已有进行中的预约或阶段已过首访的跳过；执行模式为人工确认、或渠道不可达 / 发送失败时提醒负责顾问人工邀约
     *
     * @param leadId 线索ID（可为 null）
     * @param appUserId 学生账号ID（可为 null）
     * @param visitorId 访客ID（可为 null）
     * @param moment 转化时刻编码（可为 null）
     * @param productCode 默认推荐的产品
     * @param taskId 编排任务ID
     * @return 结果（action：sent / manual / skipped / deferred / blocked，reason，send）
     */
    public Map<String, Object> invite(Long leadId, Long appUserId, Long visitorId, String moment, String productCode, Long taskId);
}
