package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrBreakerTrip;

/**
 * 熔断与预算闸触发记录 数据层
 * 
 * @author ygb
 */
public interface AbrBreakerTripMapper
{
    /**
     * 查询触发记录
     * 
     * @param trip 查询条件（规则编码、Agent、范围，时间区间 params.beginTime / endTime）
     * @return 记录集合
     */
    public List<AbrBreakerTrip> selectAbrBreakerTripList(AbrBreakerTrip trip);

    /**
     * 新增触发记录；去重键已存在时不写入
     * 
     * @param trip 记录
     * @return 写入行数（已存在为 0）
     */
    public int insertAbrBreakerTrip(AbrBreakerTrip trip);

    /**
     * 恢复时记录处理人：某 Agent（或全局）未处理的熔断记录
     * 
     * @param scope 范围（agent / global）
     * @param agentCode Agent 编码（全局为空）
     * @param resolvedBy 恢复人
     * @return 影响行数
     */
    public int resolveTrips(@Param("scope") String scope, @Param("agentCode") String agentCode, @Param("resolvedBy") String resolvedBy);
}
