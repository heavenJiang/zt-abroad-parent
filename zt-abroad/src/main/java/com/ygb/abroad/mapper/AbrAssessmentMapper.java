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
}
