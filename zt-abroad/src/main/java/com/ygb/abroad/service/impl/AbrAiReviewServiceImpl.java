package com.ygb.abroad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.ai.AiConstants;
import com.ygb.abroad.ai.AiException;
import com.ygb.abroad.ai.AiJson;
import com.ygb.abroad.ai.AiPrompt;
import com.ygb.abroad.ai.AiPromptLoader;
import com.ygb.abroad.ai.AiSchemaValidator;
import com.ygb.abroad.domain.AbrAiReview;
import com.ygb.abroad.mapper.AbrAiReviewMapper;
import com.ygb.abroad.service.IAbrAiReviewService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;

/**
 * AI 审核队列 服务层处理
 *
 * @author ygb
 */
@Service
public class AbrAiReviewServiceImpl implements IAbrAiReviewService
{
    /** 可以审核的状态：待审核、抽检 */
    private static final List<String> OPEN_STATUS = List.of(AiConstants.REVIEW_PENDING, AiConstants.REVIEW_SAMPLED);

    @Autowired
    private AbrAiReviewMapper reviewMapper;

    @Autowired
    private AiPromptLoader promptLoader;

    /**
     * 查询审核记录列表
     *
     * @param review 查询条件
     * @return 审核记录集合
     */
    @Override
    public List<AbrAiReview> selectAbrAiReviewList(AbrAiReview review)
    {
        return reviewMapper.selectAbrAiReviewList(review);
    }

    /**
     * 按ID查询审核记录
     *
     * @param reviewId 审核ID
     * @return 审核记录
     */
    @Override
    public AbrAiReview selectAbrAiReviewById(Long reviewId)
    {
        return reviewMapper.selectAbrAiReviewById(reviewId);
    }

    /**
     * 审核通过
     *
     * @param reviewId 审核ID
     * @param reviewer 审核人
     * @return 最终内容
     */
    @Override
    public JsonNode approveAbrAiReview(Long reviewId, String reviewer)
    {
        AbrAiReview review = requireOpen(reviewId);
        AbrAiReview change = new AbrAiReview();
        change.setReviewId(reviewId);
        change.setStatus(AiConstants.REVIEW_APPROVED);
        change.setReviewer(reviewer);
        saveResult(change);
        return AiJson.parseOrNull(review.getContent());
    }

    /**
     * 审核驳回
     *
     * @param reviewId 审核ID
     * @param reason 驳回原因
     * @param reviewer 审核人
     * @return 影响行数
     */
    @Override
    public int rejectAbrAiReview(Long reviewId, String reason, String reviewer)
    {
        if (StringUtils.isBlank(reason))
        {
            throw new ServiceException("请填写驳回原因");
        }
        if (reason.length() > 500)
        {
            throw new ServiceException("驳回原因不能超过500个字符");
        }
        requireOpen(reviewId);
        AbrAiReview change = new AbrAiReview();
        change.setReviewId(reviewId);
        change.setStatus(AiConstants.REVIEW_REJECTED);
        change.setReason(reason.trim());
        change.setReviewer(reviewer);
        return saveResult(change);
    }

    /**
     * 修改后通过
     *
     * @param reviewId 审核ID
     * @param editedContent 修改后的内容（JSON 文本）
     * @param reason 修改原因
     * @param reviewer 审核人
     * @return 最终内容
     */
    @Override
    public JsonNode editAbrAiReview(Long reviewId, String editedContent, String reason, String reviewer)
    {
        AbrAiReview review = requireOpen(reviewId);
        JsonNode content = AiJson.parseOrNull(editedContent);
        if (content == null)
        {
            throw new ServiceException("修改后的内容不是合法的 JSON");
        }
        AiPrompt prompt;
        try
        {
            prompt = promptLoader.load(review.getAgentCode(), review.getPromptVersion());
        }
        catch (AiException e)
        {
            throw new ServiceException("无法校验修改后的内容：" + e.getMessage());
        }
        List<String> errors = AiSchemaValidator.validate(prompt.getSchema(), content);
        if (!errors.isEmpty())
        {
            throw new ServiceException("修改后的内容不符合输出结构：" + String.join("；", errors));
        }
        if (reason != null && reason.length() > 500)
        {
            throw new ServiceException("修改原因不能超过500个字符");
        }
        AbrAiReview change = new AbrAiReview();
        change.setReviewId(reviewId);
        change.setStatus(AiConstants.REVIEW_EDITED);
        change.setEditedContent(content.toString());
        change.setReason(StringUtils.trim(reason));
        change.setReviewer(reviewer);
        saveResult(change);
        return content;
    }

    /**
     * 取审核通过后的最终内容
     *
     * @param reviewId 审核ID
     * @return 最终内容；尚未通过或已驳回返回 null
     */
    @Override
    public JsonNode getApprovedContent(Long reviewId)
    {
        AbrAiReview review = reviewMapper.selectAbrAiReviewById(reviewId);
        if (review == null)
        {
            throw new ServiceException("审核记录不存在");
        }
        if (AiConstants.REVIEW_APPROVED.equals(review.getStatus()))
        {
            return AiJson.parseOrNull(review.getContent());
        }
        if (AiConstants.REVIEW_EDITED.equals(review.getStatus()))
        {
            return AiJson.parseOrNull(review.getEditedContent());
        }
        return null;
    }

    private AbrAiReview requireOpen(Long reviewId)
    {
        AbrAiReview review = reviewMapper.selectAbrAiReviewById(reviewId);
        if (review == null)
        {
            throw new ServiceException("审核记录不存在");
        }
        if (!OPEN_STATUS.contains(review.getStatus()))
        {
            throw new ServiceException("审核记录已处理，不能重复审核");
        }
        return review;
    }

    private int saveResult(AbrAiReview change)
    {
        int rows = reviewMapper.updateAbrAiReviewResult(change, OPEN_STATUS);
        if (rows == 0)
        {
            throw new ServiceException("审核记录已被他人处理，请刷新");
        }
        return rows;
    }
}
