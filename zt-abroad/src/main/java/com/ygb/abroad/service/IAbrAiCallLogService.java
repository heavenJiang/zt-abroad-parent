package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrAiCallLog;

/**
 * AI 调用日志 服务层
 * 
 * @author ygb
 */
public interface IAbrAiCallLogService
{
    /**
     * 查询调用日志列表
     * 
     * @param log 查询条件
     * @return 调用日志集合
     */
    public List<AbrAiCallLog> selectAbrAiCallLogList(AbrAiCallLog log);

    /**
     * 按ID查询调用日志
     * 
     * @param callId 调用ID
     * @return 调用日志
     */
    public AbrAiCallLog selectAbrAiCallLogById(Long callId);

    /**
     * 用量统计：按北京时间的天、按 Agent 汇总调用次数、token 与费用
     * 
     * @param beginTime 开始日期 yyyy-MM-dd（含，空为结束日期前 6 天）
     * @param endTime 结束日期 yyyy-MM-dd（含，空为今天）
     * @return {beginTime, endTime, total, daily, agents, rows}
     */
    public Map<String, Object> selectAbrAiUsageStats(String beginTime, String endTime);
}
