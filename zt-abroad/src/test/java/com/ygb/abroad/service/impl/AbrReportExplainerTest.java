package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.vo.AppReportVo;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 测评解读纯函数测试：模板文字、模型输入不含内部字段、AI 输出逐条校验与合并
 *
 * @author ygb
 */
class AbrReportExplainerTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static AppReportVo report;

    @BeforeAll
    static void load() throws IOException
    {
        AbrAssessmentReportTest.load();
        report = AbrAssessmentReportTest.report(AbrAssessmentReportTest.answers(), MAPPER.createObjectNode());
    }

    @Test
    void templatesCoverEveryListedProgramAndShortcoming()
    {
        AbrReportExplainer.Texts t = AbrReportExplainer.templates(report);
        assertEquals(report.headline(), t.headline());
        int listed = report.tiers().stream().mapToInt(x -> x.programs().size()).sum() + report.needsImprovement().size();
        assertEquals(listed, t.reasons().size());
        assertEquals(report.shortcomings().size(), t.shortcomings().size());
        // 25：雅思 7.0 未达标，补 1 分后可申；27：信息未核实，不复述要求
        assertEquals("英语成绩要求 雅思 7.0，你目前 雅思 6.0；雅思提高 1 分后可申", t.reasons().get(25L));
        assertTrue(t.reasons().get(27L).endsWith("（项目信息待核实，以官网为准）"), t.reasons().get(27L));
        assertFalse(t.reasons().get(27L).contains("7.0"));
        assertEquals("均分要求 ≥ 90，你目前 85；补足方式需顾问评估", t.reasons().get(26L));
        assertTrue(t.shortcomings().stream().anyMatch(s -> s.startsWith("雅思提高 1 分，可让 2 个项目达到申请要求")), t.shortcomings().toString());
    }

    @Test
    void reasonByTier()
    {
        AppReportVo.Program p = new AppReportVo.Program(1L, "A", "B", "UK", "英国", List.of("gpa", "language_en"), List.of(), List.of(),
                List.of("add_related_internship_3m"), true, List.of(), "2026-10-01", false);
        assertEquals("已满足均分、英语成绩要求；增加 3 个月相关实习后有望进入主攻档", AbrReportExplainer.reason("reach", p));
        assertTrue(AbrReportExplainer.reason("match", p).contains("适合作为主攻"));
        assertTrue(AbrReportExplainer.reason("safety", p).contains("适合作为保底"));
        assertEquals("雅思提高 1.5 分、完成 APS 审核", AbrReportExplainer.actions(List.of("ielts_plus_0_5", "do_aps"), 3));
        AppReportVo.Shortcoming late = new AppReportVo.Shortcoming("upgrade", List.of("add_lab_research"), 1, 3, 6, false);
        assertEquals("增加一段科研经历，可让 3 个冲刺项目有望进入主攻档（约需 6 个月），按你的入学时间可能来不及", AbrReportExplainer.shortcoming(late));
    }

    @Test
    void inputHasOnlyStudentVisibleFacts()
    {
        Map<String, Object> input = AbrReportExplainer.input(report, "硕士", "管理", AbrReportExplainer.templates(report));
        String text = MAPPER.writeValueAsString(input);
        assertTrue(text.contains("\"degree\":\"硕士\"") && text.contains("学校25"), text);
        for (String forbidden : List.of("211", "\"c\"", "\"p\"", "tEff", "refLine", "difficulty", "D3", "probability"))
        {
            assertFalse(text.contains(forbidden), forbidden + " 出现在模型输入中：" + text);
        }
    }

    @Test
    void validateKeepsOnlyGoodItems()
    {
        AbrReportExplainer.Texts t = AbrReportExplainer.templates(report);
        Map<String, Object> input = AbrReportExplainer.input(report, "硕士", "管理", t);
        JsonNode ai = MAPPER.readTree("""
                {"headline": "你在英国管理类硕士中有不少可选项目",
                 "programs": [
                   {"programId": 25, "reason": "差在雅思：要求 7.0，你目前 6.0，提高 1 分后可申"},
                   {"programId": 25, "reason": "重复的条目不要"},
                   {"programId": 26, "reason": "录取概率约 30%"},
                   {"programId": 27, "reason": "雅思需要 7.5"},
                   {"programId": 999, "reason": "不在报告里的项目"}],
                 "shortcomings": [{"index": 0, "text": "<b>加粗</b>"}, {"index": 5, "text": "越界"}]}
                """);
        ObjectNode kept = AbrReportExplainer.validate(t, ai, input);
        assertEquals("你在英国管理类硕士中有不少可选项目", kept.path("headline").asString());
        assertEquals(Set.of("25"), Set.copyOf(kept.path("programs").propertyNames()));
        assertEquals("差在雅思：要求 7.0，你目前 6.0，提高 1 分后可申", kept.path("programs").path("25").asString());
        assertTrue(kept.path("shortcomings").isEmpty());

        AppReportVo.Explanation e = AbrReportExplainer.explanation(t, kept, AppReportVo.EXPLAIN_DONE);
        assertTrue(e.headlineAi() && e.aiGenerated());
        assertEquals(t.reasons().size(), e.programs().size());
        AppReportVo.Reason r25 = e.programs().stream().filter(r -> r.programId() == 25L).findFirst().orElseThrow();
        assertTrue(r25.ai());
        AppReportVo.Reason r26 = e.programs().stream().filter(r -> r.programId() == 26L).findFirst().orElseThrow();
        assertFalse(r26.ai());
        assertEquals(t.reasons().get(26L), r26.text());
        assertFalse(e.shortcomings().get(0).ai());
    }

    @Test
    void pendingAndFallbackUseTemplates()
    {
        AbrReportExplainer.Texts t = AbrReportExplainer.templates(report);
        ObjectNode saved = MAPPER.createObjectNode().put("headline", "AI 定位");
        AppReportVo.Explanation pending = AbrReportExplainer.explanation(t, saved, AppReportVo.EXPLAIN_PENDING);
        assertEquals(t.headline(), pending.headline());
        assertFalse(pending.aiGenerated());
        AppReportVo.Explanation fallback = AbrReportExplainer.explanation(t, null, AppReportVo.EXPLAIN_FALLBACK);
        assertEquals(AppReportVo.EXPLAIN_FALLBACK, fallback.status());
        assertFalse(fallback.headlineAi());
        assertTrue(AbrReportExplainer.validate(t, null, Map.of()).isEmpty());
    }

    @Test
    void okRules()
    {
        Set<String> numbers = Set.of("6", "7", "1");
        assertTrue(AbrReportExplainer.ok("雅思 7.0，目前 6", 20, numbers));
        assertFalse(AbrReportExplainer.ok("雅思 7.5", 20, numbers));
        assertFalse(AbrReportExplainer.ok("把握很大（70％）", 20, numbers));
        assertFalse(AbrReportExplainer.ok("录取几率较高", 20, numbers));
        assertFalse(AbrReportExplainer.ok("  ", 20, numbers));
        assertFalse(AbrReportExplainer.ok("很长很长很长很长很长很长很长很长很长很长很长", 20, numbers));
    }
}
