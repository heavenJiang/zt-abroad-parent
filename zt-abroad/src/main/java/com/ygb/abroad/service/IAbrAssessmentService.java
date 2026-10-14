package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrAssessment;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppAnswersBody;
import com.ygb.abroad.domain.vo.AppAssessmentBody;
import com.ygb.abroad.domain.vo.AppAssessmentVo;
import com.ygb.abroad.domain.vo.AppQuestionnaireVo;

/**
 * 测评 服务层（C 端：匿名可用、分步保存、断点续答）
 * 
 * 所有方法都按“本人”取数：测评必须属于传入的访客，否则视为不存在。
 * 
 * @author ygb
 */
public interface IAbrAssessmentService
{
    /** 选校测评问卷编码（学段框架 M3-09 之前的默认问卷） */
    public static final String DEFAULT_QUESTIONNAIRE_CODE = "abr_school_assessment";

    /**
     * 取某问卷当前启用的版本
     * 
     * @param code 问卷编码
     * @return 问卷；没有启用版本时抛出业务异常
     */
    public AppQuestionnaireVo selectCurrentQuestionnaire(String code);

    /**
     * 按版本ID取问卷（续答旧测评时使用；草稿不可取）
     * 
     * @param questionnaireId 问卷版本ID
     * @return 问卷
     */
    public AppQuestionnaireVo selectQuestionnaire(Long questionnaireId);

    /**
     * 同意隐私说明后创建测评（按请求ID幂等），并用访客档案预填
     * 
     * @param visitor 访客
     * @param body 请求体
     * @return 测评
     */
    public AppAssessmentVo createAssessment(AbrVisitor visitor, AppAssessmentBody body);

    /**
     * 取本人的测评
     * 
     * @param visitor 访客
     * @param assessmentId 测评ID
     * @return 测评
     */
    public AppAssessmentVo selectAssessment(AbrVisitor visitor, Long assessmentId);

    /**
     * 取本人最近一次答题中的测评（断点续答）
     * 
     * @param visitor 访客
     * @param code 问卷编码
     * @return 测评，没有返回 null
     */
    public AppAssessmentVo selectCurrentAssessment(AbrVisitor visitor, String code);

    /**
     * 分步保存答案
     * 
     * @param visitor 访客
     * @param assessmentId 测评ID
     * @param body 本次改动
     * @return 保存后的测评
     */
    public AppAssessmentVo saveAnswers(AbrVisitor visitor, Long assessmentId, AppAnswersBody body);

    /**
     * 管理端：查询测评列表（不含答案，带背景档案摘要）
     * 
     * @param assessment 查询条件
     * @return 测评集合
     */
    public List<AbrAssessment> selectAbrAssessmentList(AbrAssessment assessment);

    /**
     * 管理端：按ID查询测评（含答案）
     * 
     * @param assessmentId 测评ID
     * @return 测评
     */
    public AbrAssessment selectAbrAssessmentById(Long assessmentId);
}
