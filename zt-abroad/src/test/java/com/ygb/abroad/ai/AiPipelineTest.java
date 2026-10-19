package com.ygb.abroad.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrAiCallLog;
import com.ygb.abroad.domain.AbrAiReview;

/**
 * 网关管道测试（mock 模型 + 内存存储，不依赖 Spring 与数据库）
 *
 * @author ygb
 */
class AiPipelineTest
{
    private FakeAiStore store;

    private AiPipeline pipeline;

    @BeforeEach
    void setUp()
    {
        store = new FakeAiStore();
        store.agent("_selftest", AiConstants.POLICY_DIRECT);
        pipeline = AiTestSupport.pipeline(store);
    }

    private static Map<String, Object> input(String mock)
    {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("studentName", "韩梅梅");
        input.put("note", "手机 13812345678");
        if (mock != null)
        {
            input.put(AiConstants.MOCK_KEY, mock);
        }
        return input;
    }

    private static AiContext ctx()
    {
        AiContext c = AiContext.of("assessment", "42", "tester");
        c.setKnownNames(List.of("韩梅梅"));
        return c;
    }

    @Test
    void callGuardBlocksWithoutCallingModel()
    {
        pipeline.setCallGuard(code -> "_selftest".equals(code) ? "超出今天的 AI 预算" : null);
        AiResult r = pipeline.invoke("_selftest", input(null), ctx());
        assertEquals(AiConstants.STATUS_BLOCKED, r.getStatus());
        assertTrue(r.getMessage().contains("超出今天的 AI 预算"));
        assertNull(r.getOutput());
        assertEquals(1, store.logs.size());
        assertEquals(AiConstants.STATUS_BLOCKED, store.logs.get(0).getStatus());
        assertEquals(0, store.logs.get(0).getAttempts() == null ? 0 : store.logs.get(0).getAttempts());

        AiContext trial = ctx();
        trial.setTrial(true);
        assertEquals(AiConstants.STATUS_SUCCESS, pipeline.invoke("_selftest", input(null), trial).getStatus());
    }

    @Test
    void successDeidentifiesRestoresAndLogs()
    {
        AiResult r = pipeline.invoke("_selftest", input(null), ctx());
        assertEquals(AiConstants.STATUS_SUCCESS, r.getStatus(), r.getMessage());
        assertEquals("韩梅梅的自测摘要：网关链路正常。", r.getOutput().get("summary").asString());
        assertEquals("mock:default", r.getModelUsed());
        assertFalse(r.isFallbackUsed());
        assertEquals(1, r.getAttempts());
        assertTrue(r.getPromptTokens() > 0 && r.getCompletionTokens() > 0);
        assertTrue(r.getCost().signum() > 0);

        AbrAiCallLog log = store.lastLog();
        assertEquals(r.getCallId(), log.getCallId());
        assertEquals(AiConstants.STATUS_SUCCESS, log.getStatus());
        assertEquals("v1", log.getPromptVersion());
        assertEquals(64, log.getPromptHash().length());
        assertEquals("N", log.getFallbackUsed());
        assertEquals("assessment", log.getBizType());
        assertEquals("42", log.getBizId());
        assertEquals("tester", log.getCreateBy());
        assertEquals(r.getCost(), log.getCost());
        // 日志中只有占位符，没有姓名与手机号
        assertTrue(log.getInputSummary().contains("[姓名1]") && log.getInputSummary().contains("[手机1]"), log.getInputSummary());
        assertFalse(log.getInputSummary().contains("韩梅梅") || log.getInputSummary().contains("13812345678"));
        assertTrue(log.getOutput().contains("[姓名1]的自测摘要"));
        assertFalse(log.getOutput().contains("韩梅梅"));
        // 同一输入得到同一提示词哈希
        pipeline.invoke("_selftest", input(null), ctx());
        assertEquals(log.getPromptHash(), store.lastLog().getPromptHash());
    }

    @Test
    void primaryTimeoutFallsBackToBackup()
    {
        AiResult r = pipeline.invoke("_selftest", input("timeout"), ctx());
        assertEquals(AiConstants.STATUS_SUCCESS, r.getStatus(), r.getMessage());
        assertTrue(r.isFallbackUsed());
        assertEquals("mock:backup", r.getModelUsed());
        assertEquals(2, r.getAttempts());
        assertEquals("Y", store.lastLog().getFallbackUsed());

        assertEquals(AiConstants.STATUS_SUCCESS, pipeline.invoke("_selftest", input("error"), ctx()).getStatus());
    }

    @Test
    void bothModelsDownIsError()
    {
        AiResult r = pipeline.invoke("_selftest", input("timeout_all"), ctx());
        assertEquals(AiConstants.STATUS_ERROR, r.getStatus());
        assertTrue(r.getMessage().contains("主备模型均不可用"), r.getMessage());
        assertNull(r.getOutput());
        assertEquals(AiConstants.STATUS_ERROR, store.lastLog().getStatus());
        assertEquals(2, store.lastLog().getAttempts());

        store.agents.get("_selftest").setBackupModel("");
        AiResult r2 = pipeline.invoke("_selftest", input("error"), ctx());
        assertEquals(AiConstants.STATUS_ERROR, r2.getStatus());
        assertTrue(r2.getMessage().contains("未配置备用模型"), r2.getMessage());
    }

    @Test
    void misconfiguredPrimaryFallsBack()
    {
        store.agents.get("_selftest").setPrimaryModel("nope:x");
        AiResult r = pipeline.invoke("_selftest", input(null), ctx());
        assertEquals(AiConstants.STATUS_SUCCESS, r.getStatus());
        assertTrue(r.isFallbackUsed());
        assertEquals(1, r.getAttempts());
    }

    @Test
    void schemaFailOnceRetriesAndSucceeds()
    {
        AiResult r = pipeline.invoke("_selftest", input("schema_fail_once"), ctx());
        assertEquals(AiConstants.STATUS_SUCCESS, r.getStatus(), r.getMessage());
        assertEquals(2, r.getAttempts());
        assertFalse(r.isFallbackUsed());
        assertEquals(2, store.lastLog().getAttempts());
    }

    @Test
    void retryPromptCarriesSchemaErrors()
    {
        String p = AiPipeline.retryPrompt("原提示词", List.of("e1", "e2", "e3", "e4", "e5", "e6"));
        assertTrue(p.startsWith("原提示词"));
        assertTrue(p.contains("- e1") && p.contains("- e5"), p);
        assertFalse(p.contains("- e6"), p);
    }

    @Test
    void budgetCapsModelTimeout()
    {
        assertEquals(30000, AiPipeline.budgetedTimeout(30000, ctx(), "x"));
        int t = AiPipeline.budgetedTimeout(30000, ctx().withBudget(5000), "x");
        assertTrue(t <= 5000 && t > 4000, String.valueOf(t));
        assertEquals(3000, AiPipeline.budgetedTimeout(3000, ctx().withBudget(5000), "x"));
    }

    @Test
    void exhaustedBudgetSkipsModelCall()
    {
        AiResult r = pipeline.invoke("_selftest", input(null), ctx().withBudget(AiPipeline.MIN_CALL_MS - 1));
        assertEquals(AiConstants.STATUS_ERROR, r.getStatus());
        assertTrue(r.getMessage().contains("时间预算"), r.getMessage());
        assertEquals(0, r.getAttempts());
        assertEquals(AiConstants.STATUS_ERROR, store.lastLog().getStatus());
    }

    @Test
    void invalidTwiceIsDegraded()
    {
        AiResult r = pipeline.invoke("_selftest", input("invalid_json"), ctx());
        assertEquals(AiConstants.STATUS_DEGRADED, r.getStatus());
        assertNull(r.getOutput());
        assertEquals(2, r.getAttempts());
        assertTrue(r.getMessage().contains("兜底"), r.getMessage());
        AbrAiCallLog log = store.lastLog();
        assertEquals(AiConstants.STATUS_DEGRADED, log.getStatus());
        assertEquals("这不是 JSON {", log.getOutput());
        assertTrue(log.getErrorMsg().contains("不是合法的 JSON"));
    }

    @Test
    void bannedOutputIsFiltered()
    {
        AiResult r = pipeline.invoke("_selftest", input("banned"), ctx());
        assertEquals(AiConstants.STATUS_FILTERED, r.getStatus());
        assertNull(r.getOutput());
        assertEquals(1, r.getFlags().size());
        assertEquals("保证录取", r.getFlags().get(0).getWord());
        assertTrue(store.reviews.isEmpty());
        assertTrue(store.lastLog().getErrorMsg().contains("录取承诺"));
    }

    @Test
    void reviewBeforeSendQueuesContent()
    {
        store.agents.get("_selftest").setReviewPolicy(AiConstants.POLICY_REVIEW_BEFORE_SEND);
        AiResult r = pipeline.invoke("_selftest", input(null), ctx());
        assertEquals(AiConstants.STATUS_PENDING_REVIEW, r.getStatus());
        assertNull(r.getOutput());
        assertNotNull(r.getReviewId());
        AbrAiReview review = store.reviews.get(0);
        assertEquals(AiConstants.REVIEW_PENDING, review.getStatus());
        assertEquals(r.getCallId(), review.getCallId());
        assertEquals("v1", review.getPromptVersion());
        assertTrue(review.getContent().contains("韩梅梅的自测摘要"), "审核内容是还原后的");
        assertNull(review.getFlags());
        assertEquals(r.getReviewId(), store.lastLog().getReviewId());

        AiResult banned = pipeline.invoke("_selftest", input("banned"), ctx());
        assertEquals(AiConstants.STATUS_PENDING_REVIEW, banned.getStatus());
        assertTrue(store.reviews.get(1).getFlags().contains("保证录取"));
    }

    @Test
    void directSampleUsesRate()
    {
        store.agents.get("_selftest").setReviewPolicy(AiConstants.POLICY_DIRECT_SAMPLE);
        store.agents.get("_selftest").setSampleRate(BigDecimal.ONE);
        AiResult r = pipeline.invoke("_selftest", input(null), ctx());
        assertEquals(AiConstants.STATUS_SUCCESS, r.getStatus());
        assertNotNull(r.getOutput());
        assertEquals(AiConstants.REVIEW_SAMPLED, store.reviews.get(0).getStatus());

        store.agents.get("_selftest").setSampleRate(BigDecimal.ZERO);
        assertNull(pipeline.invoke("_selftest", input(null), ctx()).getReviewId());
        assertEquals(1, store.reviews.size());
    }

    @Test
    void samplingIsDeterministicAndProportional()
    {
        BigDecimal rate = new BigDecimal("0.1");
        int hits = 0;
        for (long id = 1; id <= 10000; id++)
        {
            boolean s = AiPipeline.sampled(id, rate);
            assertEquals(s, AiPipeline.sampled(id, rate));
            hits += s ? 1 : 0;
        }
        assertTrue(hits > 850 && hits < 1150, "抽中 " + hits);
        assertFalse(AiPipeline.sampled(1L, null));
    }

    @Test
    void userConfirmAndInternalOnlyAreFlagged()
    {
        store.agents.get("_selftest").setReviewPolicy(AiConstants.POLICY_USER_CONFIRM);
        AiResult r = pipeline.invoke("_selftest", input(null), ctx());
        assertTrue(r.isSuccess() && r.isUserConfirmRequired() && !r.isInternalOnly());
        store.agents.get("_selftest").setReviewPolicy(AiConstants.POLICY_INTERNAL_ONLY);
        r = pipeline.invoke("_selftest", input(null), ctx());
        assertTrue(r.isSuccess() && r.isInternalOnly());
    }

    @Test
    void rejectedAgents()
    {
        AiResult r = pipeline.invoke("nobody", input(null), ctx());
        assertEquals(AiConstants.STATUS_ERROR, r.getStatus());
        assertTrue(r.getMessage().contains("不存在"));
        assertEquals("nobody", store.lastLog().getAgentCode());

        store.agents.get("_selftest").setStatus("1");
        assertTrue(pipeline.invoke("_selftest", input(null), ctx()).getMessage().contains("已停用"));
        AiContext trial = ctx();
        trial.setTrial(true);
        assertEquals(AiConstants.STATUS_SUCCESS, pipeline.invoke("_selftest", input(null), trial).getStatus());

        store.agents.get("_selftest").setStatus("0");
        store.agents.get("_selftest").setReviewPolicy(AiConstants.POLICY_FORBIDDEN);
        assertTrue(pipeline.invoke("_selftest", input(null), ctx()).getMessage().contains("禁止 AI 生成"));
    }

    @Test
    void missingPromptDirFailsCleanly()
    {
        store.agent("no_prompt_agent", AiConstants.POLICY_DIRECT_SAMPLE);
        AiResult r = pipeline.invoke("no_prompt_agent", input(null), ctx());
        assertEquals(AiConstants.STATUS_ERROR, r.getStatus());
        assertTrue(r.getMessage().contains("提示词目录 prompts/no_prompt_agent/v1/ 不存在"), r.getMessage());
        AbrAiCallLog log = store.lastLog();
        assertEquals(AiConstants.STATUS_ERROR, log.getStatus());
        assertEquals("v1", log.getPromptVersion());
        assertEquals(0, log.getAttempts());

        store.agents.get("_selftest").setPromptVersion("../x");
        assertTrue(pipeline.invoke("_selftest", input(null), ctx()).getMessage().contains("不合法"));
    }

    @Test
    void evaluationModeSkipsQueueAndOverridesModel()
    {
        store.agents.get("_selftest").setReviewPolicy(AiConstants.POLICY_REVIEW_BEFORE_SEND);
        AiContext c = ctx();
        c.setEvaluation(true);
        c.setModelOverride("mock:backup");
        AiResult r = pipeline.invoke("_selftest", input("timeout"), c);
        // 指定模型时不切备用：mock 的 timeout 只让主模型失败，因此直接报错
        assertEquals(AiConstants.STATUS_ERROR, r.getStatus());
        r = pipeline.invoke("_selftest", input(null), c);
        assertEquals(AiConstants.STATUS_PENDING_REVIEW, r.getStatus());
        assertEquals("mock:backup", r.getModelUsed());
        assertNotNull(r.getOutput());
        assertTrue(store.reviews.isEmpty());
    }

    @Test
    void factsAreMaskedAndLogFailureDoesNotBreakResult()
    {
        AiContext c = ctx();
        c.setFacts(List.of(Map.of("text", "韩梅梅的学校要求雅思 6.5", "source", "https://example.edu")));
        store.failLogWrite = true;
        AiResult r = pipeline.invoke("_selftest", input(null), c);
        assertEquals(AiConstants.STATUS_SUCCESS, r.getStatus());
        assertTrue(store.logs.isEmpty());
        assertEquals(AiConstants.STATUS_SUCCESS, pipeline.invoke("_selftest", null, null).getStatus());
    }
}
