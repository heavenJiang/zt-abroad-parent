package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrAssessment;

/**
 * 测评 数据层
 * 
 * @author ygb
 */
public interface AbrAssessmentMapper
{
    /**
     * 查询测评列表（管理端；不含答案，带背景档案摘要）
     * 
     * @param assessment 查询条件
     * @return 测评集合
     */
    public List<AbrAssessment> selectAbrAssessmentList(AbrAssessment assessment);

    /**
     * 按ID查询测评（含答案）
     * 
     * @param assessmentId 测评ID
     * @return 测评
     */
    public AbrAssessment selectAbrAssessmentById(Long assessmentId);

    /**
     * 按访客与请求ID查询测评（幂等创建）
     * 
     * @param visitorId 访客ID
     * @param requestId 请求ID
     * @return 测评，不存在返回 null
     */
    public AbrAssessment selectAbrAssessmentByRequest(@Param("visitorId") Long visitorId, @Param("requestId") String requestId);

    /**
     * 查询访客最近一次答题中的测评（断点续答）
     * 
     * @param visitorId 访客ID
     * @param questionnaireCode 问卷编码
     * @return 测评，没有返回 null
     */
    public AbrAssessment selectLatestInProgressAssessment(@Param("visitorId") Long visitorId, @Param("questionnaireCode") String questionnaireCode);

    /**
     * 新增测评
     * 
     * @param assessment 测评
     * @return 结果
     */
    public int insertAbrAssessment(AbrAssessment assessment);

    /**
     * 分步保存答案（只更新答题中的测评）
     * 
     * @param assessmentId 测评ID
     * @param set 要写入的答案（JSON 对象）
     * @param removed 要清除的题目 id（JSON 数组）
     * @param flags 要合并的流程标志（JSON 对象）
     * @param currentScreen 断点屏 id（null 表示不改）
     * @return 结果
     */
    public int saveAbrAssessmentAnswers(@Param("assessmentId") Long assessmentId, @Param("set") String set, @Param("removed") String removed,
            @Param("flags") String flags, @Param("currentScreen") String currentScreen);

    /**
     * 终止测评（答案按参数决定是否清空）
     * 
     * @param assessmentId 测评ID
     * @param flags 要合并的流程标志（JSON 对象）
     * @param clearAnswers 是否清空答案
     * @return 结果
     */
    public int terminateAbrAssessment(@Param("assessmentId") Long assessmentId, @Param("flags") String flags,
            @Param("clearAnswers") boolean clearAnswers);

    /**
     * 提交测评：写入只含可见题目的答案并改为已提交（只对答题中的测评生效）
     * 
     * @param assessmentId 测评ID
     * @param answers 答案 JSON
     * @return 结果
     */
    public int submitAbrAssessment(@Param("assessmentId") Long assessmentId, @Param("answers") String answers);

    /**
     * 已提交的测评数（落地页“已有 N 人完成测评”）
     * 
     * @return 数量
     */
    public long countSubmittedAbrAssessment();

    /**
     * 访客是否有标记为未成年的测评（营销类推荐不展示给未成年人）
     * 
     * @param visitorId 访客ID
     * @return 数量
     */
    public int countMinorAssessments(Long visitorId);

    /**
     * 线索或学生账号是否未成年：线索标记为未成年，或关联访客（线索的访客、学生账号的访客）有未成年测评
     *
     * @param targetType 对象类型（lead / app_user）
     * @param targetId 对象ID
     * @return 大于 0 为未成年
     */
    public int countMinorForTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId);
}
