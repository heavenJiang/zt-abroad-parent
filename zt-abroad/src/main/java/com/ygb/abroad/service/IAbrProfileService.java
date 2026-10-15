package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrProfile;
import com.ygb.abroad.engine.BackgroundProfile;
import tools.jackson.databind.JsonNode;

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

    /**
     * 入口工具：只取工具采集的题目答案，按问卷版本的换算表归一化（不落库）
     * 
     * @param questionnaireId 问卷版本ID（换算表来自该版本）
     * @param answers 答案（题目 id → 答案）
     * @param questions 该工具采集的题目 id
     * @return 只含这些题目信息的背景档案
     */
    public BackgroundProfile normalizeToolAnswers(Long questionnaireId, Map<String, JsonNode> answers, List<String> questions);
}
