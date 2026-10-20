package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrNurtureEnrollment;
import com.ygb.abroad.domain.AbrNurtureSequence;
import com.ygb.abroad.domain.AbrNurtureStep;

/**
 * 培育序列 数据层
 *
 * @author ygb
 */
public interface AbrNurtureMapper
{
    /**
     * 查询序列列表（带进行中、待确认的入组数）
     *
     * @param sequence 条件
     * @return 列表
     */
    public List<AbrNurtureSequence> selectSequenceList(AbrNurtureSequence sequence);

    /**
     * 查询序列
     *
     * @param sequenceId 序列ID
     * @return 序列
     */
    public AbrNurtureSequence selectSequenceById(Long sequenceId);

    /**
     * 按编码查询序列
     *
     * @param sequenceCode 编码
     * @return 序列
     */
    public AbrNurtureSequence selectSequenceByCode(String sequenceCode);

    /**
     * 序列编码是否已被其他序列使用
     *
     * @param sequenceCode 编码
     * @param sequenceId 排除的序列ID（新增为 null）
     * @return 数量
     */
    public int countSequenceCode(@Param("sequenceCode") String sequenceCode, @Param("sequenceId") Long sequenceId);

    /**
     * 新增序列
     *
     * @param sequence 序列
     * @return 行数
     */
    public int insertSequence(AbrNurtureSequence sequence);

    /**
     * 修改序列
     *
     * @param sequence 序列
     * @return 行数
     */
    public int updateSequence(AbrNurtureSequence sequence);

    /**
     * 删除序列（连同步骤与入组）
     *
     * @param sequenceId 序列ID
     * @return 行数
     */
    public int deleteSequence(Long sequenceId);

    /**
     * 序列的步骤（按顺序）
     *
     * @param sequenceId 序列ID
     * @return 步骤
     */
    public List<AbrNurtureStep> selectSteps(Long sequenceId);

    /**
     * 删除序列的全部步骤
     *
     * @param sequenceId 序列ID
     * @return 行数
     */
    public int deleteSteps(Long sequenceId);

    /**
     * 新增步骤
     *
     * @param step 步骤
     * @return 行数
     */
    public int insertStep(AbrNurtureStep step);

    /**
     * 入组（同一对象在同一序列只入组一次）
     *
     * @param enrollment 入组
     * @return 行数（0 为已入组过）
     */
    public int insertEnrollment(AbrNurtureEnrollment enrollment);

    /**
     * 查询入组列表
     *
     * @param enrollment 条件
     * @return 列表
     */
    public List<AbrNurtureEnrollment> selectEnrollmentList(AbrNurtureEnrollment enrollment);

    /**
     * 查询入组
     *
     * @param enrollmentId 入组ID
     * @return 入组
     */
    public AbrNurtureEnrollment selectEnrollmentById(Long enrollmentId);

    /**
     * 到期的入组（进行中、序列启用）
     *
     * @param now 现在
     * @param limit 最多条数
     * @return 入组
     */
    public List<AbrNurtureEnrollment> selectDueEnrollments(@Param("now") Date now, @Param("limit") int limit);

    /**
     * 更新入组进度（状态、下一步、到期时间、结果；发出时记最近发出时间）
     *
     * @param enrollment 入组
     * @return 行数
     */
    public int updateEnrollment(AbrNurtureEnrollment enrollment);

    /**
     * 对象的退出判断依据：线索阶段、进行中的预约数、最近互动时间、关联的访客与学生账号
     *
     * @param targetType 对象类型
     * @param targetId 对象ID
     * @return stage、activeBookings、lastActivity、visitorId、appUserId（对象不存在为 null）
     */
    public Map<String, Object> selectTargetFacts(@Param("targetType") String targetType, @Param("targetId") Long targetId);

    /**
     * 选校单各档数量（引擎分档快照）
     *
     * @param visitorId 访客ID
     * @param appUserId 学生账号ID（可为 null）
     * @return tier → 数量
     */
    public List<Map<String, Object>> selectPlanTierCounts(@Param("visitorId") Long visitorId, @Param("appUserId") Long appUserId);
}
