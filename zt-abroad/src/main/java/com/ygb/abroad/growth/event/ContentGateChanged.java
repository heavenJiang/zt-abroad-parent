package com.ygb.abroad.growth.event;

/**
 * 应用事件：内容的合规闸门结果已写入，或内容被删除 / 归档（同步发布，在写入的同一线程中处理）
 *
 * 订阅方：例外队列与抽检（M6-04）——自动拦截进例外队列，改稿后通过或员工处理后关闭，被抽中的内容通过后进抽检队列。
 *
 * @param contentId 内容ID
 * @param gateStatus 闸门结果（passed / manual_passed / blocked；删除时为 null）
 * @param checker 检查人（compliance_checker 或员工账号）
 * @param note 员工审核意见（闸门检查为 null）
 * @param manual 是否为员工审核（false 为合规闸门检查的结果，无论由编排器还是员工触发）
 * @param removed 是否为删除或归档
 * @author ygb
 */
public record ContentGateChanged(Long contentId, String gateStatus, String checker, String note, boolean manual, boolean removed)
{
}
