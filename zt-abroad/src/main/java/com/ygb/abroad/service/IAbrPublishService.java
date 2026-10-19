package com.ygb.abroad.service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrPublishRecord;

/**
 * 发布记录 服务层：安排发布（账号暂停与每天上限）、发布后回填链接、失败与撤回、一键发布包
 * 
 * @author ygb
 */
public interface IAbrPublishService
{
    /**
     * 查询发布记录
     * 
     * @param record 查询条件
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
     * 安排发布：内容须为可发布或已发布，账号须正常且未暂停，当天（北京时间）发布数未达上限
     * 
     * @param contentId 内容ID
     * @param plannedTime 计划时间（为空为现在）
     * @param operator 操作人
     * @return 新的发布记录
     */
    public AbrPublishRecord schedule(Long contentId, Date plannedTime, String operator);

    /**
     * 回填为已发布：内容改为已发布，写 content_published 事件
     * 
     * @param record 记录（recordId、postUrl、platformPostId、publishedTime）
     * @param operator 操作人
     * @return 是否有变化
     */
    public boolean markPublished(AbrPublishRecord record, String operator);

    /**
     * 标为失败
     * 
     * @param recordId 记录ID
     * @param reason 原因
     * @param operator 操作人
     * @return 是否有变化
     */
    public boolean markFailed(Long recordId, String reason, String operator);

    /**
     * 撤回（内容不再有已发布记录时回到可发布）
     * 
     * @param recordId 记录ID
     * @param reason 原因
     * @param operator 操作人
     * @return 是否有变化
     */
    public boolean withdraw(Long recordId, String reason, String operator);

    /**
     * 一键发布包：标题、正文、话题、图卡、工具链接、私信口令与计划时间，账号负责人复制即可发布
     * 
     * @param recordId 记录ID
     * @return 发布包
     */
    public Map<String, Object> publishPackage(Long recordId);
}
