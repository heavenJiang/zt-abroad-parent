package com.ygb.abroad.ai;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ygb.abroad.ai.provider.AiModelRequest;
import com.ygb.abroad.ai.provider.AiModelResponse;
import com.ygb.abroad.ai.provider.AiProviderException;
import com.ygb.abroad.ai.provider.AiProviderRegistry;
import com.ygb.abroad.domain.AbrAiAgent;
import com.ygb.abroad.domain.AbrAiCallLog;
import com.ygb.abroad.domain.AbrAiReview;
import tools.jackson.databind.JsonNode;

/**
 * AI 网关管道（不依赖 Spring，便于单元测试）
 * <pre>
 * 1 读取 Agent 配置（须启用；审核策略为禁止 AI 生成则拒绝）
 * 2 去标识化：姓名、手机、证件、邮箱、微信号 → 占位符（映射只在内存）
 * 3 可选检索：AiRetriever 取事实，填入 {{facts}}
 * 4 渲染提示词 → 调用主模型；超时、5xx、连接失败、空内容 → 备用模型
 * 5 校验输出 JSON Schema；失败重试 1 次（同样的主备顺序，并把校验错误告诉模型）→ 仍失败返回降级结果
 * 6 输出过滤：录取承诺、广告违禁词、整篇文书
 * 7 还原占位符 → 按审核策略直接返回、抽检或进入审核队列
 * 8 finally 写调用日志（异常也记录）
 * 上下文设置了时间预算（{@link AiContext#withBudget(long)}）时，单次模型超时不超过剩余预算，预算用完不再重试或切备用模型。
 * </pre>
 *
 * @author ygb
 */
public class AiPipeline
{
    private static final Logger log = LoggerFactory.getLogger(AiPipeline.class);

    /** Schema 校验最多轮数（首次 + 重试 1 次） */
    private static final int MAX_ROUNDS = 2;

    /** 剩余预算低于该值（毫秒）时不再发起模型调用 */
    static final long MIN_CALL_MS = 1000L;

    /** 重试时附带的校验错误条数上限 */
    private static final int RETRY_ERRORS_MAX = 5;

    private final AiStore store;

    private final AiPromptLoader promptLoader;

    private final AiProviderRegistry registry;

    private final AiOutputFilter filter;

    private final AiRetriever retriever;

    /** 调用前放行检查（可为 null） */
    private AiCallGuard callGuard;

    public AiPipeline(AiStore store, AiPromptLoader promptLoader, AiProviderRegistry registry, AiOutputFilter filter, AiRetriever retriever)
    {
        this.store = store;
        this.promptLoader = promptLoader;
        this.registry = registry;
        this.filter = filter;
        this.retriever = retriever;
    }

    /**
     * 设置调用前放行检查（获客执行保障：暂停、停用、预算闸）
     *
     * @param callGuard 放行检查
     */
    public void setCallGuard(AiCallGuard callGuard)
    {
        this.callGuard = callGuard;
    }

    /**
     * 执行管道
     *
     * @param agentCode Agent 编码
     * @param input 输入
     * @param context 上下文（可为 null）
     * @return 结果（不抛异常）
     */
    public AiResult invoke(String agentCode, Map<String, Object> input, AiContext context)
    {
        AiContext ctx = context == null ? new AiContext() : context;
        long start = System.nanoTime();
        AiResult result = new AiResult();
        result.setAgentCode(agentCode);
        AbrAiCallLog callLog = new AbrAiCallLog();
        callLog.setAgentCode(agentCode == null ? "" : agentCode);
        callLog.setBizType(ctx.getBizType());
        callLog.setBizId(ctx.getBizId());
        callLog.setCreateBy(ctx.getOperator());
        Long callId = null;
        try
        {
            callId = store.nextCallId();
            callLog.setCallId(callId);
            result.setCallId(callId);
            run(agentCode, input, ctx, result, callLog);
        }
        catch (AiBlockedException e)
        {
            fail(result, e.getMessage());
            result.setStatus(AiConstants.STATUS_BLOCKED);
        }
        catch (AiException e)
        {
            fail(result, e.getMessage());
        }
        catch (RuntimeException e)
        {
            log.error("AI 网关调用异常 agent={} callId={}", agentCode, callId, e);
            fail(result, "AI 网关调用异常：" + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " " + e.getMessage()));
        }
        finally
        {
            result.setLatencyMs((System.nanoTime() - start) / 1_000_000L);
            writeLog(callLog, result);
        }
        return result;
    }

    private void run(String agentCode, Map<String, Object> input, AiContext ctx, AiResult result, AbrAiCallLog callLog)
    {
        // 1 读取 Agent 配置
        AbrAiAgent agent = store.selectAgentByCode(agentCode);
        if (agent == null)
        {
            throw new AiException("Agent「" + agentCode + "」不存在");
        }
        result.setReviewPolicy(agent.getReviewPolicy());
        result.setPromptVersion(agent.getPromptVersion());
        callLog.setPromptVersion(agent.getPromptVersion());
        if (!"0".equals(agent.getStatus()) && !ctx.isTrial() && !ctx.isEvaluation())
        {
            throw new AiException("Agent「" + agentCode + "」已停用");
        }
        String blocked = callGuard == null || ctx.isTrial() || ctx.isEvaluation() ? null : callGuard.blockReason(agentCode);
        if (blocked != null)
        {
            throw new AiBlockedException("Agent「" + agentCode + "」" + blocked);
        }
        if (AiConstants.POLICY_FORBIDDEN.equals(agent.getReviewPolicy()))
        {
            throw new AiException("Agent「" + agentCode + "」的审核策略为禁止 AI 生成");
        }
        AiPrompt prompt = promptLoader.load(agentCode, agent.getPromptVersion());

        // 2 去标识化
        AiDeidentifier deid = new AiDeidentifier(ctx.getKnownNames());
        Map<String, Object> masked = deid.maskMap(input);
        callLog.setInputSummary(AiJson.truncate(AiJson.toJson(masked), AiConstants.INPUT_SUMMARY_MAX));

        // 3 可选检索
        Map<String, Object> vars = new LinkedHashMap<>(masked);
        List<Map<String, Object>> facts = retriever == null ? null : retriever.retrieve(agent, masked, ctx);
        if (facts != null)
        {
            vars.put("facts", deid.maskValue(facts));
        }

        // 4 渲染提示词
        String system = AiTemplate.render(prompt.getSystem(), vars);
        String user = AiTemplate.render(prompt.getUserTemplate(), vars);
        callLog.setPromptHash(sha256(system + "\n\n" + user));

        // 4–5 调用模型 + Schema 校验，失败重试 1 次
        String primarySpec = ctx.getModelOverride() != null && !ctx.getModelOverride().isBlank() ? ctx.getModelOverride() : agent.getPrimaryModel();
        String backupSpec = ctx.getModelOverride() != null && !ctx.getModelOverride().isBlank() ? null : agent.getBackupModel();
        JsonNode output = null;
        String lastContent = null;
        List<String> errors = null;
        for (int round = 1; round <= MAX_ROUNDS; round++)
        {
            if (round > 1 && ctx.remainingMs() < MIN_CALL_MS)
            {
                log.warn("AI 时间预算已用完，不再做格式重试 agent={} callId={}", agentCode, result.getCallId());
                break;
            }
            AiModelRequest request = new AiModelRequest();
            request.setSystemPrompt(system);
            request.setUserPrompt(round == 1 ? user : retryPrompt(user, errors));
            request.setTimeoutMs(agent.getTimeoutMs() == null ? 30000 : agent.getTimeoutMs());
            request.setMaxTokens(agent.getMaxOutputTokens() == null ? 0 : agent.getMaxOutputTokens());
            request.setTemperature(agent.getTemperature() == null ? 0 : agent.getTemperature().doubleValue());
            request.setInput(masked);
            request.setPrompt(prompt);
            request.setAttempt(round);
            lastContent = callChain(primarySpec, backupSpec, request, result, ctx);
            JsonNode node = AiJson.parseOrNull(lastContent);
            errors = node == null ? List.of("输出不是合法的 JSON") : AiSchemaValidator.validate(prompt.getSchema(), node);
            if (errors.isEmpty())
            {
                output = node;
                break;
            }
            log.warn("AI 输出未通过 Schema 校验 agent={} callId={} round={} errors={}", agentCode, result.getCallId(), round, errors);
        }
        if (output == null)
        {
            callLog.setOutput(AiJson.truncate(lastContent, AiConstants.OUTPUT_MAX));
            result.setStatus(AiConstants.STATUS_DEGRADED);
            result.setMessage("AI 输出两次未通过格式校验，请使用兜底内容（" + String.join("；", errors.subList(0, Math.min(3, errors.size()))) + "）");
            return;
        }
        callLog.setOutput(AiJson.truncate(output.toString(), AiConstants.OUTPUT_MAX));

        // 6 输出过滤
        List<AiFilterHit> hits = filter.check(output);
        result.setFlags(hits);
        // 7 还原占位符
        JsonNode restored = deid.restore(output);
        String policy = agent.getReviewPolicy();
        if (!hits.isEmpty())
        {
            String reasons = describe(hits);
            if (AiConstants.POLICY_REVIEW_BEFORE_SEND.equals(policy))
            {
                queue(AiConstants.REVIEW_PENDING, agent, ctx, restored, hits, result);
                result.setStatus(AiConstants.STATUS_PENDING_REVIEW);
                result.setMessage("AI 输出命中过滤规则，已转人工审核：" + reasons);
                if (ctx.isEvaluation())
                {
                    result.setOutput(restored);
                }
            }
            else
            {
                result.setStatus(AiConstants.STATUS_FILTERED);
                result.setMessage("AI 输出命中过滤规则，已拦截，请使用兜底内容：" + reasons);
            }
            return;
        }
        switch (policy == null ? "" : policy)
        {
            case AiConstants.POLICY_REVIEW_BEFORE_SEND:
                queue(AiConstants.REVIEW_PENDING, agent, ctx, restored, hits, result);
                result.setStatus(AiConstants.STATUS_PENDING_REVIEW);
                result.setMessage("已进入审核队列，顾问确认后发出");
                if (ctx.isEvaluation())
                {
                    result.setOutput(restored);
                }
                return;
            case AiConstants.POLICY_DIRECT_SAMPLE:
                if (sampled(result.getCallId(), agent.getSampleRate()))
                {
                    queue(AiConstants.REVIEW_SAMPLED, agent, ctx, restored, hits, result);
                }
                break;
            default:
                break;
        }
        result.setStatus(AiConstants.STATUS_SUCCESS);
        result.setOutput(restored);
        if (AiConstants.POLICY_USER_CONFIRM.equals(policy))
        {
            result.setMessage("调用成功，内容须使用者确认后写入");
        }
        else if (AiConstants.POLICY_INTERNAL_ONLY.equals(policy))
        {
            result.setMessage("调用成功，内容仅内部可见");
        }
        else
        {
            result.setMessage("调用成功");
        }
    }

    /**
     * 按主 → 备顺序调用模型；累加 token、费用、调用次数
     *
     * @return 模型输出原文
     */
    private String callChain(String primarySpec, String backupSpec, AiModelRequest request, AiResult result, AiContext ctx)
    {
        int agentTimeoutMs = request.getTimeoutMs();
        String primaryError;
        try
        {
            request.setTimeoutMs(budgetedTimeout(agentTimeoutMs, ctx, "模型调用"));
            return callOne(primarySpec, true, request, result);
        }
        catch (AiProviderException e)
        {
            primaryError = e.getMessage();
            log.warn("AI 主模型不可用 callId={} model={}：{}", result.getCallId(), primarySpec, primaryError);
        }
        if (backupSpec == null || backupSpec.isBlank())
        {
            throw new AiException("模型不可用：" + primaryError + "（未配置备用模型）");
        }
        try
        {
            request.setTimeoutMs(budgetedTimeout(agentTimeoutMs, ctx, "主模型不可用（" + primaryError + "），切换备用模型"));
            String content = callOne(backupSpec, false, request, result);
            result.setFallbackUsed(true);
            return content;
        }
        catch (AiProviderException e)
        {
            throw new AiException("主备模型均不可用：主模型 " + primaryError + "；备用模型 " + e.getMessage());
        }
    }

    private String callOne(String spec, boolean primary, AiModelRequest request, AiResult result)
    {
        AiProviderRegistry.ModelRef ref = registry.resolve(spec);
        request.setProviderName(ref.getProviderName());
        request.setProvider(ref.getProvider());
        request.setModel(ref.getModel());
        request.setPrimary(primary);
        result.setAttempts(result.getAttempts() + 1);
        AiModelResponse response = ref.getImpl().chat(request);
        if (response == null || response.getContent() == null || response.getContent().isBlank())
        {
            throw new AiProviderException("模型 " + spec + " 返回内容为空");
        }
        result.setModelUsed(spec);
        result.setPromptTokens(result.getPromptTokens() + response.getPromptTokens());
        result.setCompletionTokens(result.getCompletionTokens() + response.getCompletionTokens());
        result.setCost(result.getCost().add(AiProviderRegistry.cost(ref, response.getPromptTokens(), response.getCompletionTokens())));
        return response.getContent();
    }

    /**
     * 单次模型超时：Agent 配置与剩余预算取小
     *
     * @param agentTimeoutMs Agent 配置的超时
     * @param ctx 上下文
     * @param action 预算不足时提示的动作
     * @return 本次调用的超时（毫秒）
     */
    static int budgetedTimeout(int agentTimeoutMs, AiContext ctx, String action)
    {
        long remaining = ctx.remainingMs();
        if (remaining < MIN_CALL_MS)
        {
            throw new AiException("AI 调用超出时间预算，未能" + action);
        }
        return (int) Math.min(agentTimeoutMs, remaining);
    }

    /**
     * 格式重试的用户提示词：原提示词 + 上一轮的校验错误，让模型有针对性地修正（温度为 0 时原样重发多半得到同样的输出）
     *
     * @param user 原用户提示词
     * @param errors 上一轮的校验错误
     * @return 重试提示词
     */
    static String retryPrompt(String user, List<String> errors)
    {
        StringBuilder sb = new StringBuilder(user);
        sb.append("\n\n上一次的输出未通过格式校验，请只输出符合约定 JSON Schema 的 JSON，不要附加任何说明。问题：");
        int n = errors == null ? 0 : Math.min(RETRY_ERRORS_MAX, errors.size());
        for (int i = 0; i < n; i++)
        {
            sb.append("\n- ").append(errors.get(i));
        }
        return sb.toString();
    }

    private void queue(String status, AbrAiAgent agent, AiContext ctx, JsonNode content, List<AiFilterHit> hits, AiResult result)
    {
        if (ctx.isEvaluation())
        {
            return;
        }
        AbrAiReview review = new AbrAiReview();
        review.setCallId(result.getCallId());
        review.setAgentCode(agent.getAgentCode());
        review.setPromptVersion(agent.getPromptVersion());
        review.setBizType(ctx.getBizType());
        review.setBizId(ctx.getBizId());
        review.setContent(content.toString());
        review.setStatus(status);
        review.setFlags(hits.isEmpty() ? null : AiJson.toJson(hits));
        review.setCreateBy(ctx.getOperator());
        result.setReviewId(store.insertReview(review));
    }

    /**
     * 抽检：以调用ID为种子的确定性随机数，同一调用结果固定
     *
     * @param callId 调用ID
     * @param rate 抽检比例
     * @return true 抽中
     */
    static boolean sampled(Long callId, BigDecimal rate)
    {
        if (rate == null || rate.signum() <= 0)
        {
            return false;
        }
        if (rate.compareTo(BigDecimal.ONE) >= 0)
        {
            return true;
        }
        long seed = callId == null ? 0L : callId;
        return new SplittableRandom(seed).nextDouble() < rate.doubleValue();
    }

    private static void fail(AiResult result, String message)
    {
        result.setStatus(AiConstants.STATUS_ERROR);
        result.setOutput(null);
        result.setMessage(message);
    }

    private static String describe(List<AiFilterHit> hits)
    {
        StringBuilder sb = new StringBuilder();
        for (AiFilterHit hit : hits)
        {
            if (sb.length() > 0)
            {
                sb.append("；");
            }
            sb.append(hit.toString());
        }
        return sb.toString();
    }

    private void writeLog(AbrAiCallLog callLog, AiResult result)
    {
        if (callLog.getCallId() == null)
        {
            log.error("AI 调用未取得调用ID，无法写调用日志 agent={} status={} message={}", callLog.getAgentCode(), result.getStatus(), result.getMessage());
            return;
        }
        callLog.setStatus(result.getStatus());
        callLog.setModelUsed(result.getModelUsed());
        callLog.setFallbackUsed(result.isFallbackUsed() ? "Y" : "N");
        callLog.setAttempts(result.getAttempts());
        callLog.setPromptTokens(result.getPromptTokens());
        callLog.setCompletionTokens(result.getCompletionTokens());
        callLog.setCost(result.getCost());
        callLog.setLatencyMs((int) Math.min(Integer.MAX_VALUE, result.getLatencyMs()));
        callLog.setReviewId(result.getReviewId());
        if (!AiConstants.STATUS_SUCCESS.equals(result.getStatus()) || !result.getFlags().isEmpty())
        {
            callLog.setErrorMsg(AiJson.truncate(result.getMessage(), AiConstants.ERROR_MSG_MAX));
        }
        try
        {
            store.insertCallLog(callLog);
        }
        catch (RuntimeException e)
        {
            log.error("写 AI 调用日志失败 callId={} agent={}", callLog.getCallId(), callLog.getAgentCode(), e);
        }
    }

    private static String sha256(String text)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }
}
