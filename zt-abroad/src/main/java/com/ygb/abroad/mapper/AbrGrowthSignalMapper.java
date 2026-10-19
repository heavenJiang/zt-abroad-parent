package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrGrowthSignal;

/**
 * 熔断信号 数据层
 * 
 * @author ygb
 */
public interface AbrGrowthSignalMapper
{
    /**
     * 查询信号
     * 
     * @param signal 查询条件（类型、Agent、来源，时间区间 params.beginTime / endTime）
     * @return 信号集合
     */
    public List<AbrGrowthSignal> selectAbrGrowthSignalList(AbrGrowthSignal signal);

    /**
     * 新增信号
     * 
     * @param signal 信号
     * @return 结果
     */
    public int insertAbrGrowthSignal(AbrGrowthSignal signal);

    /**
     * 某时刻以来各 Agent 某类信号数（Agent 为空的信号记在空键下）
     * 
     * @param signalType 信号类型
     * @param since 起点
     * @return agentCode、count
     */
    public List<Map<String, Object>> countSignalsByAgent(@Param("signalType") String signalType, @Param("since") Date since);
}
