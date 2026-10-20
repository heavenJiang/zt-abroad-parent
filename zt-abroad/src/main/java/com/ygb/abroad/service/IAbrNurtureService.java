package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ygb.abroad.domain.AbrNurtureEnrollment;
import com.ygb.abroad.domain.AbrNurtureSequence;

/**
 * nurture_agent 培育序列（M8-03）：入组、按天推进的多步触达、退出条件；人工确认模式下的确认与跳过
 *
 * @author ygb
 */
public interface IAbrNurtureService
{
    /** Agent 编码 */
    public static final String AGENT = "nurture_agent";

    /**
     * 入组（序列停用或已入组过时不入组）
     *
     * @param sequenceCode 序列编码
     * @param targetType 对象类型（lead / app_user / visitor）
     * @param targetId 对象ID
     * @return 入组ID（未入组为 null）
     */
    public Long enroll(String sequenceCode, String targetType, Long targetId);

    /**
     * 推进到期的入组
     *
     * @param activePlaybooks 正在生效的剧本
     * @param taskId 编排任务ID
     * @return 统计（due、sent、skipped、waiting、deferred、exited、completed）
     */
    public Map<String, Object> runDue(Set<String> activePlaybooks, Long taskId);

    /**
     * 员工确认发送待确认的一步（经触达判断与合规闸门，不再看执行模式）
     *
     * @param enrollmentId 入组ID
     * @return 处理后的入组
     */
    public AbrNurtureEnrollment confirm(Long enrollmentId);

    /**
     * 员工跳过待确认或进行中的下一步
     *
     * @param enrollmentId 入组ID
     * @return 处理后的入组
     */
    public AbrNurtureEnrollment skip(Long enrollmentId);

    /**
     * 员工让对象退出序列
     *
     * @param enrollmentId 入组ID
     * @param reason 原因
     * @return 处理后的入组
     */
    public AbrNurtureEnrollment exit(Long enrollmentId, String reason);

    /**
     * 查询序列
     *
     * @param sequence 条件
     * @return 列表
     */
    public List<AbrNurtureSequence> selectSequenceList(AbrNurtureSequence sequence);

    /**
     * 序列详情（含步骤）
     *
     * @param sequenceId 序列ID
     * @return 序列
     */
    public AbrNurtureSequence selectSequenceById(Long sequenceId);

    /**
     * 新增或修改序列（步骤整体替换；已入组对象按新步骤的顺序继续）
     *
     * @param sequence 序列（含步骤）
     * @return 序列ID
     */
    public Long saveSequence(AbrNurtureSequence sequence);

    /**
     * 删除序列（连同步骤与入组）
     *
     * @param sequenceId 序列ID
     * @return 行数
     */
    public int deleteSequence(Long sequenceId);

    /**
     * 查询入组
     *
     * @param enrollment 条件
     * @return 列表
     */
    public List<AbrNurtureEnrollment> selectEnrollmentList(AbrNurtureEnrollment enrollment);
}
