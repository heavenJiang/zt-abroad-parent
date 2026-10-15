package com.ygb.abroad.engine;

import java.math.BigDecimal;

/**
 * 项目自己的录取参考线（abr_program_benchmark 的只读快照，由 Service 传入）
 *
 * @param t 参考线 T（录取者的典型综合分）
 * @param a 稳定度（为 null 时用国家默认值）
 * @param source 来源（own_cases 工作室案例 / official_stats 官方统计 / consultant_difficulty 顾问定档）
 * @author ygb
 */
public record ProgramBenchmark(BigDecimal t, BigDecimal a, String source)
{
}
