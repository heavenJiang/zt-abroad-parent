package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrAiReview;

/**
 * AI 审核队列 数据层
 * 
 * @author ygb
 */
public interface AbrAiReviewMapper
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
     * 新增审核记录
     * 
     * @param review 审核记录
     * @return 影响行数
     */
    public int insertAbrAiReview(AbrAiReview review);

    /**
     * 写审核结果（只更新待审核、抽检状态的记录）
     * 
     * @param review 审核记录（reviewId、status、editedContent、reason、reviewer）
     * @param fromStatus 允许的原状态
     * @return 影响行数
     */
    public int updateAbrAiReviewResult(@Param("review") AbrAiReview review, @Param("fromStatus") List<String> fromStatus);
}
