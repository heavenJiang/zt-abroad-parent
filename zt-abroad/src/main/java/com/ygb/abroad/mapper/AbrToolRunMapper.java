package com.ygb.abroad.mapper;

import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrToolRun;

/**
 * 入口工具运行记录 数据层
 * 
 * @author ygb
 */
public interface AbrToolRunMapper
{
    /**
     * 新增运行记录
     * 
     * @param run 运行记录
     * @return 结果
     */
    public int insertAbrToolRun(AbrToolRun run);

    /**
     * 查询某访客的运行记录
     * 
     * @param toolRunId 运行ID
     * @param visitorId 访客ID
     * @return 运行记录（不属于该访客时为 null）
     */
    public AbrToolRun selectAbrToolRunOwned(@Param("toolRunId") Long toolRunId, @Param("visitorId") Long visitorId);
}
