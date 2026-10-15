package com.ygb.abroad.service;

import com.ygb.abroad.domain.AbrAssessmentResult;

/**
 * 测评选校计算 服务层（归一化 → 选校引擎 → 推荐方案 → 结果留痕）
 * 
 * @author ygb
 */
public interface IAbrSelectionService
{
    /**
     * 查询测评的选校结果
     * 
     * @param assessmentId 测评ID
     * @return 结果，没有返回 null
     */
    public AbrAssessmentResult selectAbrAssessmentResult(Long assessmentId);

    /**
     * 计算（或重新计算）测评的选校结果：重新归一化，按启用的引擎配置、院校库与参考线计算并覆盖保存
     * 
     * @param assessmentId 测评ID
     * @param operator 操作人
     * @return 结果
     */
    public AbrAssessmentResult computeAssessment(Long assessmentId, String operator);
}
