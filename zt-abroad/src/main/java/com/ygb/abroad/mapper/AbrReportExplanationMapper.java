package com.ygb.abroad.mapper;

import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrReportExplanation;

/**
 * 测评解读 数据层
 * 
 * @author ygb
 */
public interface AbrReportExplanationMapper
{
    /**
     * 按测评查询解读（current 表示是否对应当前的选校结果）
     * 
     * @param assessmentId 测评ID
     * @return 解读，没有返回 null
     */
    public AbrReportExplanation selectAbrReportExplanationByAssessmentId(Long assessmentId);

    /**
     * 认领生成：没有解读、解读对应的选校结果已重新计算、或生成中超过时限时，改为生成中并写入新令牌
     * 
     * @param assessmentId 测评ID
     * @param claimToken 新令牌
     * @param staleSeconds 生成中超过多少秒视为中断
     * @return 1 认领成功（调用方开始生成），0 无需生成
     */
    public int claimAbrReportExplanation(@Param("assessmentId") Long assessmentId, @Param("claimToken") String claimToken,
            @Param("staleSeconds") int staleSeconds);

    /**
     * 写回生成结果（令牌不一致说明已被新任务认领，不写）
     * 
     * @param explanation 结果（assessmentId、claimToken、explainStatus、aiOutput、aiStatus、callId、errorMsg）
     * @return 影响行数
     */
    public int finishAbrReportExplanation(AbrReportExplanation explanation);
}
