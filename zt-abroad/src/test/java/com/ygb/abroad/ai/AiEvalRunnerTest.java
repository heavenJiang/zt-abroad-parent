package com.ygb.abroad.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.ai.eval.AiEvalRunner;
import tools.jackson.databind.JsonNode;

/**
 * 评测执行与断言测试
 *
 * @author ygb
 */
class AiEvalRunnerTest
{
    @Test
    void selftestCasesPass()
    {
        FakeAiStore store = new FakeAiStore();
        store.agent("_selftest", AiConstants.POLICY_DIRECT).setStatus("1");
        AiPipeline pipeline = AiTestSupport.pipeline(store);
        AiEvalRunner.Report report = new AiEvalRunner(pipeline::invoke, AiTestSupport.LOADER, store).run("_selftest", null, "eval");
        assertEquals(2, report.getCases().size());
        assertTrue(report.isPassed(), report.toMarkdown());
        String md = report.toMarkdown();
        assertTrue(md.contains("| PASS |") && md.contains("基本输出符合 Schema"), md);
        assertTrue(report.summary().contains("样例 2/2 通过"), report.summary());
        assertEquals("eval", store.lastLog().getBizType());
        assertFalse(store.lastLog().getInputSummary().contains("13812345678"));
        assertTrue(store.reviews.isEmpty());
    }

    @Test
    void reportExplainerEvalCasesPass()
    {
        FakeAiStore store = new FakeAiStore();
        store.agent("report_explainer", AiConstants.POLICY_DIRECT_SAMPLE);
        AiPipeline pipeline = AiTestSupport.pipeline(store);
        AiEvalRunner.Report report = new AiEvalRunner(pipeline::invoke, AiTestSupport.LOADER, store).run("report_explainer", null, "eval");
        assertEquals(2, report.getCases().size());
        assertTrue(report.isPassed(), report.toMarkdown());
    }

    @Test
    void resumeParserEvalCasesPass()
    {
        FakeAiStore store = new FakeAiStore();
        store.agent("resume_parser", AiConstants.POLICY_USER_CONFIRM);
        AiPipeline pipeline = AiTestSupport.pipeline(store);
        AiEvalRunner.Report report = new AiEvalRunner(pipeline::invoke, AiTestSupport.LOADER, store).run("resume_parser", null, "eval");
        assertEquals(3, report.getCases().size());
        assertTrue(report.isPassed(), report.toMarkdown());
    }

    @Test
    void failingModelMakesReportFail()
    {
        FakeAiStore store = new FakeAiStore();
        store.agent("_selftest", AiConstants.POLICY_DIRECT);
        AiPipeline pipeline = AiTestSupport.pipeline(store);
        AiEvalRunner.Report report = new AiEvalRunner(pipeline::invoke, AiTestSupport.LOADER, store).run("_selftest", "mock:none", "eval");
        assertFalse(report.isPassed());
        assertTrue(report.toMarkdown().contains("FAIL"));

        assertThrows(AiException.class, () -> new AiEvalRunner(pipeline::invoke, AiTestSupport.LOADER, store).run("nobody", null, "eval"));
        store.agent("no_prompt_agent", AiConstants.POLICY_DIRECT);
        assertThrows(AiException.class, () -> new AiEvalRunner(pipeline::invoke, AiTestSupport.LOADER, store).run("no_prompt_agent", null, "eval"));
    }

    @Test
    void assertions()
    {
        JsonNode out = AiJson.parseOrNull("{\"a\":\"hello\",\"n\":2,\"list\":[1,\"x\"],\"obj\":{\"k\":null}}");
        assertTrue(check(out, "{\"path\":\"/a\",\"equals\":\"hello\"}"));
        assertTrue(check(out, "{\"path\":\"/n\",\"equals\":2.0}"));
        assertFalse(check(out, "{\"path\":\"/n\",\"equals\":3}"));
        assertTrue(check(out, "{\"path\":\"/a\",\"contains\":\"ell\"}"));
        assertTrue(check(out, "{\"path\":\"/list\",\"contains\":\"x\"}"));
        assertTrue(check(out, "{\"path\":\"/list\",\"contains\":1}"));
        assertTrue(check(out, "{\"path\":\"/obj\",\"contains\":\"k\"}"));
        assertTrue(check(out, "{\"path\":\"/obj/k\",\"exists\":true}"));
        assertTrue(check(out, "{\"path\":\"/zzz\",\"exists\":false}"));
        assertTrue(check(out, "{\"path\":\"/zzz\",\"notExists\":true}"));
        assertFalse(check(out, "{\"path\":\"/a\",\"notExists\":true}"));
        assertFalse(check(out, "{\"path\":\"bad\",\"exists\":true}"));
        assertFalse(check(out, "{\"path\":\"/a\",\"unknown\":1}"));
        assertFalse(check(null, "{\"path\":\"/a\",\"exists\":true}"));
    }

    private static boolean check(JsonNode out, String assertion)
    {
        return AiEvalRunner.check(out, AiJson.parseOrNull(assertion)).isPassed();
    }
}
