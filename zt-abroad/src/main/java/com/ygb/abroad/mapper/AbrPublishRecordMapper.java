package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrPublishRecord;

/**
 * 发布记录 数据层
 * 
 * @author ygb
 */
public interface AbrPublishRecordMapper
{
    /**
     * 查询发布记录（带出内容编号、标题、账号）
     * 
     * @param record 查询条件（内容、账号、发布方式、状态，计划时间区间 params.beginTime / endTime）
     * @return 记录集合
     */
    public List<AbrPublishRecord> selectAbrPublishRecordList(AbrPublishRecord record);

    /**
     * 按ID查询
     * 
     * @param recordId 记录ID
     * @return 记录
     */
    public AbrPublishRecord selectAbrPublishRecordById(Long recordId);

    /**
     * 新增发布记录（待发布）
     * 
     * @param record 记录
     * @return 结果
     */
    public int insertAbrPublishRecord(AbrPublishRecord record);

    /**
     * 回填为已发布（待发布或失败的记录）
     * 
     * @param record 记录（recordId、postUrl、platformPostId、publishedTime、updateBy）
     * @return 影响行数
     */
    public int markPublished(AbrPublishRecord record);

    /**
     * 标为失败（待发布的记录）
     * 
     * @param recordId 记录ID
     * @param reason 原因
     * @param updateBy 操作人
     * @return 影响行数
     */
    public int markFailed(@Param("recordId") Long recordId, @Param("reason") String reason, @Param("updateBy") String updateBy);

    /**
     * 撤回（待发布或已发布的记录）
     * 
     * @param recordId 记录ID
     * @param reason 原因
     * @param updateBy 操作人
     * @return 影响行数
     */
    public int withdraw(@Param("recordId") Long recordId, @Param("reason") String reason, @Param("updateBy") String updateBy);

    /**
     * 内容的待发布与已发布记录数
     * 
     * @param contentId 内容ID
     * @param status 只统计该状态（为空统计待发布与已发布）
     * @return 条数
     */
    public int countActiveByContent(@Param("contentId") Long contentId, @Param("status") String status);

    /**
     * 内容的发布记录总数（含失败、撤回）
     * 
     * @param contentIds 内容ID
     * @return 条数
     */
    public int countByContentIds(Long[] contentIds);

    /**
     * 计划时间已到的待发布记录（publisher 取用；M7-05）
     * 
     * @param now 当前时刻
     * @param publishMode 发布方式
     * @return 记录
     */
    public List<AbrPublishRecord> selectDueRecords(@Param("now") Date now, @Param("publishMode") String publishMode);

    /**
     * 按发布链接找发布记录
     *
     * @param postUrl 发布链接
     * @return 记录（多条时取最新）
     */
    public AbrPublishRecord selectByPostUrl(String postUrl);

    /**
     * 按内容编号找最近一次已发布的记录
     *
     * @param contentCode 内容编号
     * @return 记录
     */
    public AbrPublishRecord selectLatestPublishedByContentCode(String contentCode);

    /**
     * 写效果数据
     *
     * @param recordId 记录ID
     * @param metrics 效果数据（JSON 对象）
     * @param updateBy 操作人
     * @return 行数
     */
    public int updateMetrics(@Param("recordId") Long recordId, @Param("metrics") String metrics, @Param("updateBy") String updateBy);
}
