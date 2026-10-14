package com.ygb.abroad.service;

import com.ygb.abroad.domain.AbrProfile;

/**
 * 背景档案 服务层（归一化：测评答案 → 背景档案）
 * 
 * @author ygb
 */
public interface IAbrProfileService
{
    /**
     * 按测评查询背景档案
     * 
     * @param assessmentId 测评ID
     * @return 背景档案，没有返回 null
     */
    public AbrProfile selectAbrProfileByAssessmentId(Long assessmentId);

    /**
     * 归一化：剔除隐藏题答案后按测评所用问卷版本的换算表生成背景档案，写入或覆盖 abr_profile
     * 
     * @param assessmentId 测评ID
     * @param operator 操作人（学生提交时为空）
     * @return 背景档案
     */
    public AbrProfile normalizeAssessment(Long assessmentId, String operator);
}
