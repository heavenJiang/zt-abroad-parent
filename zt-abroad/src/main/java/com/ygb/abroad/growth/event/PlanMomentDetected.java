package com.ygb.abroad.growth.event;

/**
 * 应用事件：“我的申请规划”中发生了转化时刻（事件已写入 abr_event，订阅方可据 eventId 读取与回写处理时间）
 *
 * 订阅方：线索评分 lead_scorer（M5-02）、邀约 booking_agent（M8-04）；在它们上线前事件只入库。
 *
 * @param eventId 事件ID（abr_event）
 * @param visitorId 访客ID
 * @param appUserId 学生账号ID（未登录为 null）
 * @param moment 时刻编码
 * @param recommendProduct 推荐的产品编码（未配置为 null）
 * @author ygb
 */
public record PlanMomentDetected(Long eventId, Long visitorId, Long appUserId, String moment, String recommendProduct)
{
}
