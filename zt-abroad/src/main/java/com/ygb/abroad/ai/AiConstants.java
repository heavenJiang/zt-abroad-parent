package com.ygb.abroad.ai;

import java.util.Set;

/**
 * AI 网关常量：结果状态、审核策略、审核状态
 *
 * @author ygb
 */
public final class AiConstants
{
    /** 结果状态：成功 */
    public static final String STATUS_SUCCESS = "success";

    /** 结果状态：降级（输出两次未通过 Schema 校验，调用方走兜底） */
    public static final String STATUS_DEGRADED = "degraded";

    /** 结果状态：命中输出过滤，已拦截 */
    public static final String STATUS_FILTERED = "filtered";

    /** 结果状态：已进入审核队列，内容只在审核记录中 */
    public static final String STATUS_PENDING_REVIEW = "pending_review";

    /** 结果状态：错误（Agent 不可用、提示词缺失、主备模型均不可用等） */
    public static final String STATUS_ERROR = "error";

    /** 审核策略：直接发出 */
    public static final String POLICY_DIRECT = "direct";

    /** 审核策略：直接展示 + 抽检 */
    public static final String POLICY_DIRECT_SAMPLE = "direct_sample";

    /** 审核策略：顾问确认后发出 */
    public static final String POLICY_REVIEW_BEFORE_SEND = "review_before_send";

    /** 审核策略：使用者确认后写入 */
    public static final String POLICY_USER_CONFIRM = "user_confirm";

    /** 审核策略：仅内部可见 */
    public static final String POLICY_INTERNAL_ONLY = "internal_only";

    /** 审核策略：禁止 AI 生成 */
    public static final String POLICY_FORBIDDEN = "forbidden";

    /** 全部审核策略（字典 abr_ai_review_policy） */
    public static final Set<String> REVIEW_POLICIES = Set.of(POLICY_DIRECT, POLICY_DIRECT_SAMPLE, POLICY_REVIEW_BEFORE_SEND,
            POLICY_USER_CONFIRM, POLICY_INTERNAL_ONLY, POLICY_FORBIDDEN);

    /** 审核状态：待审核 */
    public static final String REVIEW_PENDING = "pending";

    /** 审核状态：抽检（内容已发出，事后检查） */
    public static final String REVIEW_SAMPLED = "sampled";

    /** 审核状态：已通过 */
    public static final String REVIEW_APPROVED = "approved";

    /** 审核状态：已驳回 */
    public static final String REVIEW_REJECTED = "rejected";

    /** 审核状态：修改后通过 */
    public static final String REVIEW_EDITED = "edited";

    /** 业务类型：管理端试用 */
    public static final String BIZ_TRIAL = "trial";

    /** 业务类型：评测命令 */
    public static final String BIZ_EVAL = "eval";

    /** mock 模型的故障模拟输入键 */
    public static final String MOCK_KEY = "_mock";

    /** 调用日志输入摘要最大长度 */
    public static final int INPUT_SUMMARY_MAX = 2000;

    /** 调用日志输出最大长度 */
    public static final int OUTPUT_MAX = 20000;

    /** 调用日志错误信息最大长度 */
    public static final int ERROR_MSG_MAX = 2000;

    private AiConstants()
    {
    }
}
