package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrTouchLog;

/**
 * 触达记录与退订 数据层
 * 
 * @author ygb
 */
public interface AbrTouchLogMapper
{
    /**
     * 查询触达记录
     * 
     * @param log 查询条件（Agent、对象、渠道、类别、状态，时间区间 params.beginTime / endTime）
     * @return 触达记录集合
     */
    public List<AbrTouchLog> selectAbrTouchLogList(AbrTouchLog log);

    /**
     * 新增触达记录
     * 
     * @param log 触达记录
     * @return 结果
     */
    public int insertAbrTouchLog(AbrTouchLog log);

    /**
     * 对象某时刻以来已发出的营销触达数（频控）
     * 
     * @param targetType 对象类型
     * @param targetId 对象ID
     * @param since 起点
     * @return 条数
     */
    public int countMarketingSent(@Param("targetType") String targetType, @Param("targetId") Long targetId, @Param("since") Date since);

    /**
     * 对象某时刻以来最近一次已发出触达的 Agent（退订归因）
     * 
     * @param targetType 对象类型
     * @param targetId 对象ID
     * @param since 起点
     * @return Agent 编码，没有返回 null
     */
    public String selectLastTouchAgent(@Param("targetType") String targetType, @Param("targetId") Long targetId, @Param("since") Date since);

    /**
     * 某时刻以来各 Agent 已发出的营销触达数（退订率分母）
     * 
     * @param since 起点
     * @return agentCode、count
     */
    public List<Map<String, Object>> countMarketingSentByAgent(Date since);

    /**
     * 对象是否已退订某范围
     * 
     * @param targetType 对象类型
     * @param targetId 对象ID
     * @param scope 范围
     * @return 是否退订
     */
    public boolean existsOptout(@Param("targetType") String targetType, @Param("targetId") Long targetId, @Param("scope") String scope);

    /**
     * 退订（已退订时不变）
     * 
     * @param targetType 对象类型
     * @param targetId 对象ID
     * @param scope 范围
     * @param source 来源（user / admin）
     * @param reason 原因
     * @param createBy 操作人
     * @return 影响行数（已退订为 0）
     */
    public int insertOptout(@Param("targetType") String targetType, @Param("targetId") Long targetId, @Param("scope") String scope,
            @Param("source") String source, @Param("reason") String reason, @Param("createBy") String createBy);

    /**
     * 恢复订阅
     * 
     * @param targetType 对象类型
     * @param targetId 对象ID
     * @param scope 范围
     * @return 影响行数
     */
    public int deleteOptout(@Param("targetType") String targetType, @Param("targetId") Long targetId, @Param("scope") String scope);
}
