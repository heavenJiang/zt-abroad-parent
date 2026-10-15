package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrAssessment;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppAnswersBody;
import com.ygb.abroad.domain.vo.AppAssessmentBody;
import com.ygb.abroad.domain.vo.AppAssessmentVo;
import com.ygb.abroad.domain.vo.AppDegreeVo;
import com.ygb.abroad.domain.vo.AppPosterBody;
import com.ygb.abroad.domain.vo.AppQuestionnaireVo;
import com.ygb.abroad.domain.vo.AppReportVo;

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

    /** 未选学段时的默认学段（1a 引擎只实现硕士） */
    public static final String DEFAULT_DEGREE = "master";

    /**
     * 取某问卷当前启用的版本
     * 
     * @param code 问卷编码
     * @return 问卷；没有启用版本时抛出业务异常
     */
    public AppQuestionnaireVo selectCurrentQuestionnaire(String code);

    /**
     * 取服务某学段的启用问卷（首屏选学段后用）
     * 
     * @param degree 学段
     * @return 问卷
     */
    public AppQuestionnaireVo selectCurrentQuestionnaireByDegree(String degree);

    /**
     * C 端首屏：各学段的开放情况（可测评并出结果 / 可测评、结果由顾问评估 / 由顾问评估）
     * 
     * @return 学段列表（按高中、本科、硕士、博士顺序）
     */
    public List<AppDegreeVo> selectDegrees();

    /**
     * 落地页：已提交的测评数（少于配置的下限时返回 null，不显示）
     * 
     * @return 数量或 null
     */
    public Long selectSubmittedCount();

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
     * 提交测评：检查必答题，剔除隐藏题的答案，改为已提交，并自动计算选校结果（计算失败不影响提交，可在管理端重新计算）
     * 
     * @param visitor 访客
     * @param assessmentId 测评ID
     * @return 测评
     */
    public AppAssessmentVo submitAssessment(AbrVisitor visitor, Long assessmentId);

    /**
     * 取本人测评的报告结构化部分（只含学生可见字段；须已提交）
     * 
     * @param visitor 访客
     * @param assessmentId 测评ID
     * @return 报告
     */
    public AppReportVo selectReport(AbrVisitor visitor, Long assessmentId);

    /**
     * 生成本人测评的定位海报（PNG；默认只含档位与校名，带分享码二维码）
     * 
     * @param visitor 访客
     * @param assessmentId 测评ID
     * @param body 显示项（未成年人勾选无效）
     * @return PNG 字节
     */
    public byte[] renderPoster(AbrVisitor visitor, Long assessmentId, AppPosterBody body);

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
