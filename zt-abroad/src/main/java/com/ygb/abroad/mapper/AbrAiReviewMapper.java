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

    /**
     * 业务对象未处理的例外或抽检记录
     *
     * @param queueType 队列
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @return 记录，没有返回 null
     */
    public AbrAiReview selectOpenByBiz(@Param("queueType") String queueType, @Param("bizType") String bizType, @Param("bizId") String bizId);

    /**
     * 更新未处理记录的快照（同一对象再次进入队列时）
     *
     * @param review 记录（reviewId、content、flags、callId、agentCode、promptVersion、reasonCode、sourceInput、createBy）
     * @return 影响行数
     */
    public int updateOpenSnapshot(AbrAiReview review);

    /**
     * 关闭业务对象未处理的记录
     *
     * @param queueType 队列
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @param status 处理结果状态
     * @param reason 说明
     * @param reviewer 处理人
     * @return 影响行数
     */
    public int closeOpenByBiz(@Param("queueType") String queueType, @Param("bizType") String bizType, @Param("bizId") String bizId,
            @Param("status") String status, @Param("reason") String reason, @Param("reviewer") String reviewer);

    /**
     * 记录回流生成的评测样例
     *
     * @param reviewId 记录ID
     * @param evalCaseId 样例ID
     * @return 影响行数
     */
    public int updateEvalCase(@Param("reviewId") Long reviewId, @Param("evalCaseId") Long evalCaseId);
}
