package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.ai.AiConstants;
import com.ygb.abroad.ai.AiJson;
import com.ygb.abroad.ai.AiPipeline;
import com.ygb.abroad.ai.AiResult;
import com.ygb.abroad.ai.AiTestSupport;
import com.ygb.abroad.ai.FakeAiStore;
import com.ygb.abroad.ai.eval.AiEvalRunner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * AI 预填事后校验测试：引句必须在原文中、取值按人工保存规则校验；以及 catalog_extractor 的评测样例
 *
 * @author ygb
 */
class AbrAiPrefillCheckerTest
{
    private static final String DIR = "prompts/catalog_extractor/v1/";

    private final AbrAiPrefillChecker checker = new AbrAiPrefillChecker(null);

    private static JsonNode resource(String path)
    {
        try (InputStream in = AbrAiPrefillCheckerTest.class.getClassLoader().getResourceAsStream(path))
        {
            return AiJson.parseOrNull(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static String page1()
    {
        return resource(DIR + "eval/01_uk_msc_ielts_tuition.json").path("input").path("pageText").asString();
    }

    private static Map<String, Map<String, Object>> byField(List<Map<String, Object>> list)
    {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        list.forEach(r -> m.put((String) r.get("fieldName"), r));
        return m;
    }

    private static ObjectNode output(String json)
    {
        return (ObjectNode) AiJson.parseOrNull(json);
    }

    @Test
    void mockOutputAllFoundAgainstFixturePage()
    {
        Map<String, Map<String, Object>> r = byField(checker.check(resource(DIR + "mock.json"), page1()));
        assertEquals(19, r.size());
        for (String f : new String[] { "programNameEn", "degree", "durationMonths", "tuition", "currency", "intakes", "teachingLanguage", "minGpa",
            "ieltsOverall", "ieltsMinSub", "otherLanguage", "testRequirement", "prerequisites", "prerequisitesRequired", "workYears", "deadlines" })
        {
            assertEquals("found", r.get(f).get("status"), f + "：" + r.get(f));
        }
        for (String f : new String[] { "tiersNotAccepted", "testDetail", "acceptCrossMajor" })
        {
            assertEquals("not_found", r.get(f).get("status"));
            assertEquals("没有找到可靠来源", r.get(f).get("reason"));
            assertNull(r.get(f).get("value"));
        }
        assertEquals("program", r.get("tuition").get("target"));
        assertEquals("requirement", r.get("ieltsOverall").get("target"));
        assertEquals("38500", r.get("tuition").get("value").toString());
        assertEquals("GBP", r.get("currency").get("value"));
        assertEquals(List.of("fall"), r.get("intakes").get("value"));
        assertEquals("{\"c9\":80,\"985\":80,\"211\":80,\"double_first_class\":85,\"non_key\":85}", AiJson.toJson(r.get("minGpa").get("value")));
        assertEquals("{\"toefl\":{\"overall\":100,\"minSub\":22}}", AiJson.toJson(r.get("otherLanguage").get("value")), "null 键去掉，与前端形状一致");
        assertEquals("{\"rolling\":false,\"rounds\":[{\"name\":\"Round 1\",\"date\":\"2026-11-15\"},{\"name\":\"Round 2\",\"date\":\"2027-01-15\"},"
                + "{\"name\":\"Round 3\",\"date\":\"2027-03-01\"}]}", AiJson.toJson(r.get("deadlines").get("value")));
        assertEquals("IELTS 7.0 overall with no less than 6.5 in each component.", r.get("ieltsOverall").get("quote"));
    }

    @Test
    void quoteNotInPageIsDiscarded()
    {
        String page = page1().replace("Applicants are expected to have at least 2 years of relevant work experience.\n", "");
        Map<String, Map<String, Object>> r = byField(checker.check(resource(DIR + "mock.json"), page));
        assertEquals("discarded", r.get("workYears").get("status"));
        assertEquals("原文中找不到引句，已丢弃", r.get("workYears").get("reason"));
        assertNull(r.get("workYears").get("value"));
        assertNull(r.get("workYears").get("quote"));
        assertEquals("found", r.get("ieltsOverall").get("status"));
    }

    @Test
    void quoteMatchingNormalisesWhitespaceWidthAndQuotes()
    {
        String page = "入学要求\n雅思总分 ６．５，单项不低于６．０\nApplicants’ “first-class” degree —   required\nRound 1:\n15 November 2026";
        ObjectNode out = output("{\"ieltsOverall\":{\"value\":6.5,\"quote\":\"雅思总分6.5，单项不低于 6.0\"},"
                + "\"ieltsMinSub\":{\"value\":6,\"quote\":\"雅思总分 6.5，单项不低于 6.0\"},"
                + "\"programNameEn\":{\"value\":\"x\",\"quote\":\"applicants' \\\"FIRST-CLASS\\\" degree - required\"},"
                + "\"deadlines\":{\"value\":{\"rolling\":false,\"rounds\":[{\"name\":\"Round 1\",\"date\":\"2026-11-15\"}]},\"quote\":\"Round 1: 15 November 2026\"},"
                + "\"prerequisites\":{\"value\":\"微积分\",\"quote\":\"雅思总分 7.0\"}}");
        Map<String, Map<String, Object>> r = byField(checker.check(out, page));
        assertEquals("found", r.get("ieltsOverall").get("status"));
        assertEquals("found", r.get("ieltsMinSub").get("status"));
        assertEquals("6.0", r.get("ieltsMinSub").get("value").toString());
        assertEquals("found", r.get("programNameEn").get("status"));
        assertEquals("found", r.get("deadlines").get("status"));
        assertEquals("discarded", r.get("prerequisites").get("status"));
    }

    @Test
    void valueRulesSameAsManualSave()
    {
        String page = "Q1 quote text here. Q2 quote text here. Q3 quote text here.";
        String[][] bad = {
            { "minGpa", "{\"985\":80,\"top50\":90}" },
            { "minGpa", "{\"985\":120}" },
            { "tiersNotAccepted", "[\"non_key\",\"bogus\"]" },
            { "ieltsOverall", "9.5" },
            { "ieltsOverall", "6.3" },
            { "degree", "\"MSc\"" },
            { "currency", "\"POUND\"" },
            { "durationMonths", "12.5" },
            { "durationMonths", "0" },
            { "tuition", "-1" },
            { "intakes", "[\"autumn\"]" },
            { "teachingLanguage", "\"english\"" },
            { "otherLanguage", "{\"cambridge\":{\"overall\":180}}" },
            { "testRequirement", "\"maybe\"" },
            { "testDetail", "{\"gre\":350}" },
            { "workYears", "40" },
            { "acceptCrossMajor", "\"2\"" },
            { "prerequisitesRequired", "\"yes\"" },
            { "deadlines", "{\"rolling\":false,\"rounds\":[{\"name\":\"R1\",\"date\":\"15 Nov 2026\"}]}" },
            { "programNameEn", "123" } };
        for (String[] b : bad)
        {
            ObjectNode out = output("{\"" + b[0] + "\":{\"value\":" + b[1] + ",\"quote\":\"Q2 quote text here.\"}}");
            Map<String, Object> r = byField(checker.check(out, page)).get(b[0]);
            assertEquals("discarded", r.get("status"), b[0] + "=" + b[1]);
            assertTrue(((String) r.get("reason")).startsWith("取值不符合录入规则"), b[0] + "：" + r.get("reason"));
        }
        // 小分高于总分：丢弃小分
        ObjectNode out = output("{\"ieltsOverall\":{\"value\":6.0,\"quote\":\"Q1 quote text here.\"},\"ieltsMinSub\":{\"value\":6.5,\"quote\":\"Q1 quote text here.\"}}");
        Map<String, Map<String, Object>> r = byField(checker.check(out, page));
        assertEquals("found", r.get("ieltsOverall").get("status"));
        assertEquals("discarded", r.get("ieltsMinSub").get("status"));
        assertTrue(((String) r.get("ieltsMinSub").get("reason")).contains("雅思小分高于总分"));
    }

    @Test
    void emptyValuesAndMissingOrShortQuotes()
    {
        String page = "Applications are considered on a rolling basis. IELTS 6.5";
        ObjectNode out = output("{\"deadlines\":{\"value\":{\"rolling\":false,\"rounds\":[]},\"quote\":\"Applications are considered\"},"
                + "\"minGpa\":{\"value\":{},\"quote\":null},\"prerequisites\":{\"value\":\"  \",\"quote\":\"x\"},"
                + "\"ieltsOverall\":{\"value\":6.5,\"quote\":null},\"ieltsMinSub\":{\"value\":6.5,\"quote\":\"6.5\"},"
                + "\"workYears\":{\"value\":2,\"quote\":\"" + "IELTS 6.5 ".repeat(60) + "\"},"
                + "\"testRequirement\":{\"value\":\"none\"}}");
        Map<String, Map<String, Object>> r = byField(checker.check(out, page));
        assertEquals("not_found", r.get("deadlines").get("status"));
        assertEquals("not_found", r.get("minGpa").get("status"));
        assertEquals("not_found", r.get("prerequisites").get("status"));
        assertEquals("not_found", r.get("programNameEn").get("status"), "输出中缺少的字段视为未找到");
        assertEquals("没有附原文引句，已丢弃", r.get("ieltsOverall").get("reason"));
        assertEquals("没有附原文引句，已丢弃", r.get("testRequirement").get("reason"));
        assertEquals("引句过短，不能作为依据，已丢弃", r.get("ieltsMinSub").get("reason"));
        assertTrue(((String) r.get("workYears").get("reason")).startsWith("引句超过"));
        assertEquals(19, checker.check(null, page).size());
    }

    @Test
    void dictionaryFromCacheOverridesDefaults()
    {
        AbrAiPrefillChecker withDict = new AbrAiPrefillChecker(t -> "abr_degree".equals(t) ? Set.of("master", "mres") : null);
        assertEquals("mres", withDict.validate("degree", AiJson.parseOrNull("\"mres\"")).asString());
        assertThrows(IllegalArgumentException.class, () -> withDict.validate("degree", AiJson.parseOrNull("\"phd\"")));
        assertEquals("fall", withDict.validate("intakes", AiJson.parseOrNull("[\"fall\",\"fall\"]")).get(0).asString());
        assertEquals(1, withDict.validate("intakes", AiJson.parseOrNull("[\"fall\",\"fall\"]")).size());
        assertEquals("EUR", withDict.validate("currency", AiJson.parseOrNull("\"eur\"")).asString());
        assertEquals("{\"gmat_focus\":655}", withDict.validate("testDetail", AiJson.parseOrNull("{\"gre\":null,\"gmat_focus\":655}")).toString());
        assertEquals("{\"rolling\":true,\"rounds\":[]}", withDict.validate("deadlines", AiJson.parseOrNull("{\"rolling\":true}")).toString());
        assertThrows(IllegalArgumentException.class, () -> withDict.validate("nope", AiJson.parseOrNull("1")));
    }

    @Test
    void normaliseForMatch()
    {
        assertEquals("ielts7.0overall", AbrAiPrefillChecker.normalizeForMatch(" ＩＥＬＴＳ　７．０\n overall "));
        assertEquals("'a'\"b\"-c-d", AbrAiPrefillChecker.normalizeForMatch("‘a’“b”–c—d"));
        assertEquals("", AbrAiPrefillChecker.normalizeForMatch(null));
    }

    @Test
    void catalogExtractorEvalCasesPass()
    {
        FakeAiStore store = new FakeAiStore();
        store.agent("catalog_extractor", AiConstants.POLICY_USER_CONFIRM);
        AiPipeline pipeline = AiTestSupport.pipeline(store);
        AiEvalRunner.Report report = new AiEvalRunner(pipeline::invoke, AiTestSupport.LOADER, store).run("catalog_extractor", null, "eval");
        assertEquals(3, report.getCases().size());
        assertTrue(report.isPassed(), report.toMarkdown());
    }

    @Test
    void pipelineOutputForEvalCasesPassesPostCheck()
    {
        FakeAiStore store = new FakeAiStore();
        store.agent("catalog_extractor", AiConstants.POLICY_USER_CONFIRM);
        AiPipeline pipeline = AiTestSupport.pipeline(store);
        String[] cases = { "01_uk_msc_ielts_tuition.json", "02_rolling_deadline.json", "03_no_language_info.json" };
        for (String c : cases)
        {
            JsonNode spec = resource(DIR + "eval/" + c);
            @SuppressWarnings("unchecked")
            Map<String, Object> input = AiJson.MAPPER.convertValue(spec.get("input"), LinkedHashMap.class);
            AiResult result = pipeline.invoke("catalog_extractor", input, null);
            assertEquals(AiConstants.STATUS_SUCCESS, result.getStatus(), c + "：" + result.getMessage());
            Map<String, Map<String, Object>> r = byField(checker.check(result.getOutput(), spec.path("input").path("pageText").asString()));
            for (Map<String, Object> f : r.values())
            {
                assertTrue(!"discarded".equals(f.get("status")), c + " 的样例输出不应被丢弃：" + f);
            }
            if (c.startsWith("03"))
            {
                assertEquals("not_found", r.get("ieltsOverall").get("status"));
                assertEquals("found", r.get("acceptCrossMajor").get("status"));
            }
            if (c.startsWith("02"))
            {
                assertEquals("{\"rolling\":true,\"rounds\":[]}", AiJson.toJson(r.get("deadlines").get("value")));
            }
        }
    }
}
