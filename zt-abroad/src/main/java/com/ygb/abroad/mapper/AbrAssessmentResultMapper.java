package com.ygb.abroad.mapper;

import com.ygb.abroad.domain.AbrAssessmentResult;

/**
 * 测评选校结果 数据层
 * 
 * @author ygb
 */
public interface AbrAssessmentResultMapper
{
    /**
     * 按测评查询结果
     * 
     * @param assessmentId 测评ID
     * @return 结果，没有返回 null
     */
    public AbrAssessmentResult selectAbrAssessmentResultByAssessmentId(Long assessmentId);

    /**
     * 新增或覆盖测评结果（每份测评一条）
     * 
     * @param result 结果
     * @return 结果
     */
    public int upsertAbrAssessmentResult(AbrAssessmentResult result);
}
