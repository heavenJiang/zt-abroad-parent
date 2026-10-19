package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrAiReview;

/**
 * 例外队列与抽检 服务层（复用 AI 审核队列 abr_ai_review，queue_type 为 exception / sample）
 * <ul>
 * <li>例外：合规闸门拦截、消息被拦截、Agent 转人工等需要人处理的；员工放行或确认拦截；内容改稿后通过闸门时自动关闭；</li>
 * <li>抽检：执行模式为“全自动 + 抽检”的 Agent 按比例抽中的内容，过闸门后进队列、照常发布；员工判定合格 / 不合格；</li>
 * <li>不合格的 AI 样本（抽检不合格、确认拦截）回流评测集；抽检结果上报信号，供熔断统计不合格率。</li>
 * </ul>
 *
 * @author ygb
 */
public interface IAbrExceptionService
{
    /** 业务类型：内容 */
    public static final String BIZ_CONTENT = "content";

    /** 原因：合规闸门拦截 */
    public static final String REASON_GATE_BLOCKED = "gate_blocked";

    /** 原因：消息被拦截 */
    public static final String REASON_MESSAGE_BLOCKED = "message_blocked";

    /** 原因：Agent 转人工 */
    public static final String REASON_AGENT_HANDOFF = "agent_handoff";

    /** 原因：按比例抽检 */
    public static final String REASON_AUTO_SAMPLE = "auto_sample";

    /**
     * 查询例外与抽检记录（未指定队列时两类都列）
     *
     * @param review 查询条件（queueType、status、reasonCode、agentCode、bizType、bizId、时间区间）
     * @return 记录集合
     */
    public List<AbrAiReview> selectExceptionList(AbrAiReview review);

    /**
     * 按ID查询例外或抽检记录（网关审核记录不在此返回）
     *
     * @param reviewId 记录ID
     * @return 记录
     */
    public AbrAiReview selectExceptionById(Long reviewId);

    /**
     * 写入例外或抽检（同一业务对象已有未处理记录时更新快照，不重复写入）
     *
     * @param queueType 队列（exception / sample）
     * @param reasonCode 原因（字典 abr_exception_reason）
     * @param agentCode 产生内容的 Agent（员工撰写为 null）
     * @param bizType 业务类型（如 content、message）
     * @param bizId 业务ID
     * @param callId AI 调用ID（可为 null）
     * @param snapshotJson 业务对象快照（JSON 对象）
     * @param flagsJson 命中项（JSON 数组，可为 null）
     * @param sourceInputJson 模型输入（已去标识，JSON 对象，可为 null；生成评测样例用）
     * @return 记录ID
     */
    public Long enqueue(String queueType, String reasonCode, String agentCode, String bizType, String bizId, Long callId, String snapshotJson,
            String flagsJson, String sourceInputJson);

    /**
     * 例外：放行（内容记为人工审核通过，进入可发布或待人工确认）
     *
     * @param reviewId 记录ID
     * @param note 审核意见（可空）
     * @param operator 处理人
     * @return 处理后的记录
     */
    public AbrAiReview release(Long reviewId, String note, String operator);

    /**
     * 例外：确认拦截（内容保持草稿；AI 生成的回流评测集）
     *
     * @param reviewId 记录ID
     * @param reason 原因（必填）
     * @param operator 处理人
     * @return 处理后的记录
     */
    public AbrAiReview confirmBlock(Long reviewId, String reason, String operator);

    /**
     * 抽检：合格
     *
     * @param reviewId 记录ID
     * @param operator 处理人
     * @return 处理后的记录
     */
    public AbrAiReview samplePass(Long reviewId, String operator);

    /**
     * 抽检：不合格（未发布的内容同时拦下；回流评测集；上报不合格信号）
     *
     * @param reviewId 记录ID
     * @param reason 原因（必填）
     * @param operator 处理人
     * @return 处理后的记录
     */
    public AbrAiReview sampleFail(Long reviewId, String reason, String operator);
}
