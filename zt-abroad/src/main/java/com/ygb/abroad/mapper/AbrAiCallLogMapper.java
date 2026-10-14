package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrAiCallLog;
import com.ygb.abroad.domain.vo.AbrAiUsageStat;

/**
 * AI 调用日志 数据层
 * 
 * @author ygb
 */
public interface AbrAiCallLogMapper
{
    /**
     * 查询调用日志列表（不含输入输出正文）
     * 
     * @param log 查询条件（agentCode、status、bizType、params.beginTime/endTime）
     * @return 调用日志集合
     */
    public List<AbrAiCallLog> selectAbrAiCallLogList(AbrAiCallLog log);

    /**
     * 按ID查询调用日志（含输入输出）
     * 
     * @param callId 调用ID
     * @return 调用日志
     */
    public AbrAiCallLog selectAbrAiCallLogById(Long callId);

    /**
     * 预取调用ID
     * 
     * @return 调用ID
     */
    public Long selectNextCallId();

    /**
     * 新增调用日志（call_id 已预取）
     * 
     * @param log 调用日志
     * @return 影响行数
     */
    public int insertAbrAiCallLog(AbrAiCallLog log);

    /**
     * 按北京时间的天 + Agent 汇总用量
     * 
     * @param beginTime 开始时刻（含）
     * @param endTime 结束时刻（不含）
     * @return 统计行
     */
    public List<AbrAiUsageStat> selectAbrAiUsageStats(@Param("beginTime") Date beginTime, @Param("endTime") Date endTime);
}
