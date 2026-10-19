package com.ygb.abroad.growth;

/**
 * 对外内容或消息按 Agent 执行模式的发出方式（发出前仍须经合规闸门与触达判断）
 *
 * @author ygb
 */
public enum OutboundDecision
{
    /** 直接发出 */
    DIRECT,
    /** 直接发出，并进入抽检（M6-04 复用 AI 审核队列） */
    DIRECT_SAMPLED,
    /** 进入人工确认，确认后发出 */
    CONFIRM,
    /** 不能发出（全局或该 Agent 暂停、停用、超出 AI 预算） */
    BLOCKED
}
