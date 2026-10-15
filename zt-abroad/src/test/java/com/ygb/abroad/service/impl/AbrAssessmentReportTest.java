package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrAssessmentResult;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.vo.AppReportVo;
import com.ygb.abroad.engine.BackgroundProfile;
import com.ygb.abroad.engine.NormalizationRules;
import com.ygb.abroad.engine.ProfileNormalizer;
import com.ygb.abroad.engine.ProgramFacts;
import com.ygb.abroad.engine.RecommendationPlanner;
import com.ygb.abroad.engine.SelectionEngine;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 测评报告结构化部分测试：用种子问卷与英国引擎配置实际计算后组装报告，检查区块内容、短板、跳过提示与内部字段不外泄
 *
 * @author ygb
 */
class AbrAssessmentReportTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    /** 211、均分 85、雅思 6.0（未填小分）、未答专业排名与预算，目标英国、美国（美国未开放） */
    private static final String ANSWERS = """
            {"age_band": "18plus", "target_countries": ["UK", "US"], "target_degree": "master", "target_field": "management",
             "undergrad_school": {"kind": "cn", "university_id": 7, "name": "某大学"},
             "undergrad_major": {"value": "management", "matched": true},
             "gpa": {"type": "percent", "total": 85}, "exp_research": {"has": "no"},
             "exp_work": {"has": "yes", "count": 1, "total_months": 3, "company_tier": "normal", "relevant": "yes"},
             "exp_awards": {"has": "no"},
             "english_status": "taken", "english_score": {"type": "ielts", "total": 6.0, "min_sub": null},
             "std_test": {"type": "none"}}
            """;

    private static AbrAssessmentAnswers.Questionnaire q;

    private static JsonNode uk;

    @BeforeAll
    static void load() throws IOException
    {
        q = AbrAssessmentAnswers.parse(Files.readString(Path.of("..", "seed", "questionnaire", "questionnaire_v1.json"), StandardCharsets.UTF_8));
        uk = MAPPER.readTree(Files.readString(Path.of("..", "seed", "engine-config", "uk_master.json"), StandardCharsets.UTF_8));
    }

    static ObjectNode answers()
    {
        return (ObjectNode) MAPPER.readTree(ANSWERS);
    }

    private static Date daysAgo(int days)
    {
        return Date.from(TODAY.minusDays(days).atStartOfDay(AbrLookupTools.BEIJING).toInstant());
    }

    /**
     * 英国 7 个项目：21–24 难度 D2–D5 按兜底要求；25 雅思 7.0、不接受语言班（补足雅思后可申）；
     * 26 均分 ≥ 90（补足后可申，不能靠短期动作补足）；27 同 25 但信息超过 90 天未核实
     */
    private static AbrAssessmentResult result(ObjectNode answers)
    {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        answers.properties().forEach(e -> map.put(e.getKey(), e.getValue()));
        BackgroundProfile p = ProfileNormalizer.normalize(map, NormalizationRules.parse(q.root()), id -> id == 7L ? "211" : null, TODAY);
        ProgramFacts.Requirement ielts7 = new ProgramFacts.Requirement(Map.of(), Set.of(), new BigDecimal("7.0"), null, null, null, null, false, null,
                null, "0");
        ProgramFacts.Requirement gpa90 = new ProgramFacts.Requirement(Map.of("211", new BigDecimal("90")), Set.of(), null, null, null, null, null,
                false, null, null, null);
        List<ProgramFacts> facts = List.of(new ProgramFacts(21L, "UK", "D2", "en", false, null), new ProgramFacts(22L, "UK", "D3", "en", false, null),
                new ProgramFacts(23L, "UK", "D4", "en", false, null), new ProgramFacts(24L, "UK", "D5", "en", false, null),
                new ProgramFacts(25L, "UK", "D3", "en", false, ielts7), new ProgramFacts(26L, "UK", "D3", "en", false, gpa90),
                new ProgramFacts(27L, "UK", "D4", "en", false, ielts7));
        Map<String, JsonNode> configs = Map.of("UK", uk);
        SelectionEngine.Evaluation e = SelectionEngine.evaluate(p, facts, configs, Map.of(), false);
        RecommendationPlanner.Plan plan = RecommendationPlanner.plan(p, e, configs);
        AbrAssessmentResult r = AbrSelectionServiceImpl.toResult(1L, e, plan, configs, List.of("US"), programs());
        r.setComputedTime(daysAgo(0));
        return r;
    }

    private static Map<Long, AbrProgram> programs()
    {
        Map<Long, AbrProgram> map = new HashMap<>();
        for (long id = 21; id <= 27; id++)
        {
            AbrProgram p = new AbrProgram();
            p.setProgramId(id);
            p.setSchoolName("学校" + id);
            p.setProgramName("项目" + id);
            p.setVerifiedTime(id == 27 ? daysAgo(120) : daysAgo(id == 21 ? 30 : 5));
            map.put(id, p);
        }
        return map;
    }

    static AppReportVo report(ObjectNode answers, JsonNode flags)
    {
        return AbrAssessmentReport.build(1L, q, answers, flags, result(answers), programs(), TODAY);
    }

    @Test
    void fullReportBlocks()
    {
        AbrAssessmentResult result = result(answers());
        AppReportVo vo = AbrAssessmentReport.build(1L, q, answers(), MAPPER.createObjectNode(), result, programs(), TODAY);
        assertEquals("full", vo.mode());
        assertEquals(List.of(), vo.notices());
        assertNull(vo.minorNotice());
        assertTrue(vo.headline().contains("主攻档有 " + result.getMatchCount() + " 个"), vo.headline());

        // 画像：首选国家英国的子分，取整到 5
        assertFalse(vo.profile().isEmpty());
        assertEquals("academic", vo.profile().get(0).key());
        assertEquals("学术背景", vo.profile().get(0).label());
        vo.profile().forEach(d -> assertEquals(0, d.level() % 5));

        // 选校结果：固定三档，数量与结果一致，代表校来自推荐组合、每档最多 3 所
        assertEquals(List.of("reach", "match", "safety"), vo.tiers().stream().map(AppReportVo.Tier::tier).toList());
        assertEquals(result.getReachCount(), vo.tiers().get(0).count());
        assertEquals(result.getMatchCount(), vo.tiers().get(1).count());
        assertEquals(result.getSafetyCount(), vo.tiers().get(2).count());
        JsonNode portfolio = MAPPER.readTree(result.getPortfolio());
        for (AppReportVo.Tier t : vo.tiers())
        {
            assertTrue(t.programs().size() <= AbrAssessmentReport.EXAMPLES);
            for (AppReportVo.Program p : t.programs())
            {
                assertTrue(portfolio.path(t.tier()).toString().contains(String.valueOf(p.programId())), t.tier() + " " + p.programId());
                assertEquals("英国", p.countryLabel());
                assertTrue(p.missing().isEmpty());
            }
        }

        // 补足后可申：25（雅思差 1 分，2 次 0.5）、26（均分，不能短期补足）、27（未核实，不展示要求）
        assertEquals(3, vo.needsImprovementCount());
        Map<Long, AppReportVo.Program> blocked = new HashMap<>();
        vo.needsImprovement().forEach(p -> blocked.put(p.programId(), p));
        AppReportVo.Program p25 = blocked.get(25L);
        assertEquals("language_en", p25.missing().get(0).item());
        assertNotNull(p25.missing().get(0).required());
        assertEquals(List.of(new AppReportVo.Fix("language_en", "ielts_plus_0_5", 2, p25.fixes().get(0).months())), p25.fixes());
        AppReportVo.Program p26 = blocked.get(26L);
        assertEquals(new AppReportVo.Gap("gpa", "≥ 90", "85"), p26.missing().get(0));
        assertNull(p26.fixes().get(0).action());
        AppReportVo.Program p27 = blocked.get(27L);
        assertTrue(p27.unverified());
        assertNull(p27.missing().get(0).required());
        assertEquals(TODAY.minusDays(120).toString(), p27.verifiedDate());

        // 短板：雅思提高 1 分可让 25、27 变为可申
        AppReportVo.Shortcoming unlock = vo.shortcomings().stream().filter(s -> "unlock".equals(s.kind())).findFirst().orElseThrow();
        assertEquals(List.of("ielts_plus_0_5"), unlock.actions());
        assertEquals(2, unlock.steps());
        assertEquals(2, unlock.programCount());
        assertTrue(vo.shortcomings().size() <= AbrAssessmentReport.SHORTCOMINGS);

        // 暂未开放的国家、跳过的题目、数据说明
        assertEquals("US", vo.skippedCountries().get(0).country());
        List<String> skipped = vo.skipHints().stream().map(AppReportVo.SkipHint::question).toList();
        assertTrue(skipped.containsAll(List.of("class_rank", "english_score", "budget_total")), skipped.toString());
        assertFalse(skipped.contains("std_test"));
        assertEquals(TODAY.minusDays(120).toString(), vo.dataNote().verifiedFrom());
        assertEquals(TODAY.minusDays(5).toString(), vo.dataNote().verifiedTo());
        assertEquals(1, vo.dataNote().unverifiedCount());
        assertNotNull(vo.dataNote().computedTime());
    }

    @Test
    void reportHidesInternals()
    {
        String text = MAPPER.writeValueAsString(report(answers(), MAPPER.createObjectNode()));
        for (String forbidden : List.of("\"c\"", "\"p\"", "\"t\"", "tEff", "requiredC", "gapC", "weights", "preferenceScore", "211", "non_key",
                "difficulty", "D3"))
        {
            assertFalse(text.contains(forbidden), forbidden + " 出现在学生可见报告中：" + text);
        }
    }

    @Test
    void minorNoticeAndReferralFlag()
    {
        ObjectNode a = answers();
        a.put("age_band", "14_17");
        AppReportVo vo = report(a, MAPPER.readTree("{\"is_minor\": true, \"exclude_from_referral_unlock\": true}"));
        assertEquals(List.of(AbrAssessmentReport.NOTICE_MINOR, AbrAssessmentReport.NOTICE_NO_REFERRAL_UNLOCK), vo.notices());
        assertTrue(vo.minorNotice().contains("未满 18 岁"), vo.minorNotice());
    }

    @Test
    void pendingAndConsultantModes()
    {
        AppReportVo pending = AbrAssessmentReport.build(1L, q, answers(), MAPPER.createObjectNode(), null, Map.of(), TODAY);
        assertEquals("pending", pending.mode());
        assertEquals(3, pending.tiers().size());
        assertEquals(0, pending.tiers().get(1).count());
        assertNull(pending.dataNote().computedTime());

        AbrAssessmentResult consultant = new AbrAssessmentResult();
        consultant.setMode("consultant");
        consultant.setSkippedCountries("US,UK");
        consultant.setNotices("[\"degree_consultant_review\"]");
        AppReportVo vo = AbrAssessmentReport.build(1L, q, answers(), MAPPER.createObjectNode(), consultant, Map.of(), TODAY);
        assertEquals("consultant", vo.mode());
        assertEquals(List.of("degree_consultant_review"), vo.notices());
        assertEquals(List.of("US", "UK"), vo.skippedCountries().stream().map(c -> c.country()).toList());
        assertTrue(vo.profile().isEmpty());
    }

    @Test
    void headlineAndLevel()
    {
        assertTrue(AbrAssessmentReport.headline(Map.of("reach", 0, "match", 0, "safety", 0), 4).contains("4 个项目可申"));
        assertTrue(AbrAssessmentReport.headline(Map.of("reach", 0, "match", 0, "safety", 0), 0).contains("顾问"));
        assertEquals(85, AbrAssessmentReport.level(new BigDecimal("83.75")));
        assertEquals(80, AbrAssessmentReport.level(new BigDecimal("82.49")));
        assertEquals(100, AbrAssessmentReport.level(new BigDecimal("120")));
        assertEquals(0, AbrAssessmentReport.level(new BigDecimal("-3")));
    }

    @Test
    void programIdsSkipExcluded()
    {
        AbrAssessmentResult r = new AbrAssessmentResult();
        r.setPrograms("[{\"programId\": 1, \"group\": \"match\"}, {\"programId\": 2, \"group\": \"excluded\"}]");
        assertEquals(List.of(1L), AbrAssessmentReport.programIds(r));
        assertEquals(List.of(), AbrAssessmentReport.programIds(null));
    }
}
