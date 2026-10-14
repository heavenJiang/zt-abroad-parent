package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrAiReview;
import tools.jackson.databind.JsonNode;

/**
 * AI 审核队列 服务层
 * 
 * @author ygb
 */
public interface IAbrAiReviewService
{
    /**
     * 查询审核记录列表
     * 
     * @param review 查询条件
     * @return 审核记录集合
     */
    public List<AbrAiReview> selectAbrAiReviewList(AbrAiReview review);

    /**
     * 按ID查询审核记录
     * 
     * @param reviewId 审核ID
     * @return 审核记录
     */
    public AbrAiReview selectAbrAiReviewById(Long reviewId);

    /**
     * 审核通过
     * 
     * @param reviewId 审核ID
     * @param reviewer 审核人
     * @return 最终内容
     */
    public JsonNode approveAbrAiReview(Long reviewId, String reviewer);

    /**
     * 审核驳回
     * 
     * @param reviewId 审核ID
     * @param reason 驳回原因（必填）
     * @param reviewer 审核人
     * @return 影响行数
     */
    public int rejectAbrAiReview(Long reviewId, String reason, String reviewer);

    /**
     * 修改后通过（修改后的内容按该 Agent 提示词版本的 Schema 校验）
     * 
     * @param reviewId 审核ID
     * @param editedContent 修改后的内容（JSON 文本）
     * @param reason 修改原因
     * @param reviewer 审核人
     * @return 最终内容
     */
    public JsonNode editAbrAiReview(Long reviewId, String editedContent, String reason, String reviewer);

    /**
     * 取审核通过后的最终内容（供后续业务发出）：已通过返回原内容，修改后通过返回修改后的内容，其他状态返回 null
     * 
     * @param reviewId 审核ID
     * @return 最终内容；尚未通过或已驳回返回 null
     */
    public JsonNode getApprovedContent(Long reviewId);
}
