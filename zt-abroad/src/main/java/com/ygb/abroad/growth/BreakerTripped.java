package com.ygb.abroad.growth;

/**
 * 熔断或预算闸触发（Spring 事件，提交后通知有暂停权限的员工）
 *
 * @param tripId 触发记录ID
 * @param budget 是否为预算闸（否则为熔断）
 * @param title 标题
 * @param detail 说明（规则、指标、处理）
 * @author ygb
 */
public record BreakerTripped(Long tripId, boolean budget, String title, String detail)
{
}
