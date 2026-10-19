package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrGrowthAgent;

/**
 * 获客 Agent 运行配置 数据层
 * 
 * @author ygb
 */
public interface AbrGrowthAgentMapper
{
    /**
     * 查询全部 Agent 运行配置
     * 
     * @param agent 查询条件（执行模式、暂停）
     * @return 配置集合
     */
    public List<AbrGrowthAgent> selectAbrGrowthAgentList(AbrGrowthAgent agent);

    /**
     * 按编码查询
     * 
     * @param agentCode Agent 编码
     * @return 配置
     */
    public AbrGrowthAgent selectAbrGrowthAgentByCode(String agentCode);

    /**
     * 修改执行模式、抽检比例、预算
     * 
     * @param agent 配置
     * @return 结果
     */
    public int updateAbrGrowthAgent(AbrGrowthAgent agent);

    /**
     * 暂停（已暂停时不变）
     * 
     * @param agentCode Agent 编码
     * @param reason 原因
     * @param pausedBy 暂停人
     * @return 影响行数（已暂停为 0）
     */
    public int pauseAgent(@Param("agentCode") String agentCode, @Param("reason") String reason, @Param("pausedBy") String pausedBy);

    /**
     * 恢复（未暂停时不变）
     * 
     * @param agentCode Agent 编码
     * @param updateBy 恢复人
     * @return 影响行数（未暂停为 0）
     */
    public int resumeAgent(@Param("agentCode") String agentCode, @Param("updateBy") String updateBy);

    /**
     * 某时刻以来各 Agent 的 AI 用量（abr_ai_call_log，不含试用与评测）
     * 
     * @param since 起点
     * @param agentCode 只查该 Agent（为空查全部）
     * @return agentCode、cost、tokens
     */
    public List<Map<String, Object>> selectAiUsageSince(@Param("since") Date since, @Param("agentCode") String agentCode);
}
