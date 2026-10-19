package com.ygb.abroad.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrSiteBuild;

/**
 * 公开项目页生成记录 数据层（含生成所需的院校库指纹与来源网址查询）
 * 
 * @author ygb
 */
public interface AbrSiteBuildMapper
{
    /**
     * 查询生成记录（新的在前）
     * 
     * @param build 查询条件（触发方式、状态，开始时间区间 params.beginTime / endTime）
     * @return 记录集合
     */
    public List<AbrSiteBuild> selectAbrSiteBuildList(AbrSiteBuild build);

    /**
     * 按ID查询
     * 
     * @param buildId 记录ID
     * @return 记录
     */
    public AbrSiteBuild selectAbrSiteBuildById(Long buildId);

    /**
     * 最近一次成功的记录
     * 
     * @return 记录；没有为 null
     */
    public AbrSiteBuild selectLastSuccess();

    /**
     * 最近一次记录（任何状态）
     * 
     * @return 记录；没有为 null
     */
    public AbrSiteBuild selectLatest();

    /**
     * 新增记录（生成中）
     * 
     * @param build 记录
     * @return 结果
     */
    public int insertAbrSiteBuild(AbrSiteBuild build);

    /**
     * 写入结果（状态、计数、变化、提交结果、失败原因、结束时间）
     * 
     * @param build 记录
     * @return 结果
     */
    public int finishAbrSiteBuild(AbrSiteBuild build);

    /**
     * 把遗留的生成中记录（进程中断）标记为失败
     * 
     * @return 结果
     */
    public int failRunning();

    /**
     * 院校库指纹原文：项目、申请要求、院校、字段来源与相关字典的行数和最近变更时间
     * 
     * @return 指纹原文
     */
    public String selectCatalogStamp();

    /**
     * 项目与申请要求的字段来源网址
     * 
     * @param programIds 项目ID
     * @param requirementIds 申请要求ID（各项目最新申请季）
     * @return program_id、source_url
     */
    public List<Map<String, Object>> selectSourceUrls(@Param("programIds") List<Long> programIds, @Param("requirementIds") List<Long> requirementIds);
}
