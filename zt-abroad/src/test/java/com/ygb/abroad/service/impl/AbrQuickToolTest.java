package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.vo.AppQuickResultVo;
import com.ygb.abroad.engine.BackgroundProfile;
import com.ygb.abroad.engine.HardThresholdChecker;
import com.ygb.abroad.engine.NormalizationRules;
import com.ygb.abroad.engine.ProfileNormalizer;
import com.ygb.abroad.engine.ProgramFacts;
import com.ygb.abroad.engine.RecommendationPlanner;
import com.ygb.abroad.engine.SelectionEngine;
import com.ygb.abroad.service.IAbrSelectionService;
import com.ygb.common.exception.ServiceException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 30 秒快测纯函数测试：答案校验、学生可见结果（不含内部字段、不暴露院校层次）、关键差距、留痕取档位
 *
 * @author ygb
 */
class AbrQuickToolTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final String VALID = """
            {"undergrad_school": {"kind": "cn", "university_id": 1, "name": "某211大学"},
             "gpa": {"type": "percent", "total": 85},
             "target_countries": ["UK", "US"],
             "target_field": "finance",
             "age_band": "18_plus"}
            """;

    private static AbrAssessmentAnswers.Questionnaire q;

    private static JsonNode uk;

    @BeforeAll
    static void load() throws IOException
    {
        q = AbrAssessmentAnswers.parse(Files.readString(Path.of("..", "seed", "questionnaire", "questionnaire_v1.json"), StandardCharsets.UTF_8));
        uk = MAPPER.readTree(Files.readString(Path.of("..", "seed", "engine-config", "uk_master.json"), StandardCharsets.UTF_8));
    }

    private static ObjectNode json(String text)
    {
        return (ObjectNode) MAPPER.readTree(text);
    }

    private static ObjectNode valid()
    {
        return json(VALID);
    }

    @Test
    void validateKeepsOnlyQuickQuestions()
    {
        ObjectNode out = AbrQuickTool.validate(q, valid());
        assertEquals(List.of("undergrad_school", "gpa", "target_countries", "target_field"), List.copyOf(out.propertyNames()));
        assertEquals(1L, out.path("undergrad_school").path("university_id").asLong());
        assertEquals(new BigDecimal("85"), out.path("gpa").path("total").decimalValue());
    }

    @Test
    void validateDeduplicatesCountriesAndAcceptsOverseas()
    {
        ObjectNode a = valid();
        a.putArray("target_countries").add("UK").add("UK").add("HK");
        a.set("undergrad_school", json("{\"kind\": \"overseas\", \"name\": \"\"}"));
        ObjectNode out = AbrQuickTool.validate(q, a);
        assertEquals(2, out.path("target_countries").size());
        assertTrue(out.path("undergrad_school").path("university_id").isNull());
    }

    @Test
    void validateRejectsBadInput()
    {
        assertRejects("{}", "请完成");
        assertRejects(VALID.replace("\"kind\": \"cn\", \"university_id\": 1", "\"kind\": \"cn\""), "请从搜索结果中选择本科学校");
        assertRejects(VALID.replace("\"total\": 85", "\"total\": 101"), "不在合理范围内");
        assertRejects(VALID.replace("\"type\": \"percent\"", "\"type\": \"gpa4\""), "不在合理范围内");
        assertRejects(VALID.replace("\"percent\", \"total\": 85", "\"other\", \"total\": 85"), "请填写满分");
        assertRejects(VALID.replace("[\"UK\", \"US\"]", "[\"FR\"]"), "目标国家 / 地区不正确");
        assertRejects(VALID.replace("[\"UK\", \"US\"]", "[]"), "请选择目标国家 / 地区");
        assertRejects(VALID.replace("\"finance\"", "\"astrology\""), "请选择专业方向");
        assertRejects(VALID.replace("某211大学", "<script>"), "不能包含");
        assertRejects("[]", "请填写快测信息");
    }

    @Test
    void validateOtherScaleNeedsMaxInRange()
    {
        ObjectNode a = valid();
        a.set("gpa", json("{\"type\": \"other\", \"total\": 8.5, \"max\": 10}"));
        assertEquals(new BigDecimal("10"), AbrQuickTool.validate(q, a).path("gpa").path("max").decimalValue());
        a.set("gpa", json("{\"type\": \"other\", \"total\": 8.5, \"max\": 1000}"));
        assertThrows(ServiceException.class, () -> AbrQuickTool.validate(q, a));
    }

    private static void assertRejects(String answers, String message)
    {
        ServiceException e = assertThrows(ServiceException.class, () -> AbrQuickTool.validate(q, MAPPER.readTree(answers)));
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    /**
     * 211、均分 85 的快测档案，英国 5 个项目：D2 稳、D4 / D5 保、D1 补足后可申（项目要求 211 ≥ 87）、D1 兜底要求
     */
    private static IAbrSelectionService.QuickRun run(String school)
    {
        BackgroundProfile p = profile(school);
        ProgramFacts.Requirement strict = new ProgramFacts.Requirement(Map.of("211", new BigDecimal("87"), "non_key", new BigDecimal("90")),
                Set.of(), null, null, null, null, null, false, null, null, null);
        ProgramFacts.Requirement noNonKey = new ProgramFacts.Requirement(Map.of("211", new BigDecimal("80")), Set.of("non_key"), null, null,
                null, null, null, false, null, null, null);
        List<ProgramFacts> facts = List.of(new ProgramFacts(11L, "UK", "D2", "en", false, null), new ProgramFacts(12L, "UK", "D4", "en", false, null),
                new ProgramFacts(13L, "UK", "D5", "en", false, null), new ProgramFacts(14L, "UK", "D1", "en", false, strict),
                new ProgramFacts(15L, "UK", "D3", "en", false, noNonKey));
        Map<String, JsonNode> configs = Map.of("UK", uk);
        SelectionEngine.Evaluation e = SelectionEngine.evaluate(p, facts, configs, Map.of(), true);
        RecommendationPlanner.Plan plan = RecommendationPlanner.plan(p, e, configs);
        Map<Long, AbrProgram> programs = new HashMap<>();
        for (ProgramFacts f : facts)
        {
            AbrProgram program = new AbrProgram();
            program.setProgramId(f.programId());
            program.setSchoolName("学校" + f.programId());
            program.setProgramName("项目" + f.programId());
            programs.put(f.programId(), program);
        }
        return new IAbrSelectionService.QuickRun(e, plan, Map.of("UK", "draft"), List.of("UK"), List.of("US"), programs);
    }

    private static BackgroundProfile profile(String school)
    {
        ObjectNode a = valid();
        a.set("undergrad_school", json(school));
        Map<String, JsonNode> answers = new LinkedHashMap<>();
        AbrQuickTool.validate(q, a).properties().forEach(e -> answers.put(e.getKey(), e.getValue()));
        return ProfileNormalizer.normalize(answers, NormalizationRules.parse(q.root()), id -> id == 1L ? "211" : null, LocalDate.of(2026, 10, 16));
    }

    @Test
    void buildShowsTiersExamplesAndGapsWithoutInternals()
    {
        String school = "{\"kind\": \"cn\", \"university_id\": 1, \"name\": \"某211大学\"}";
        AppQuickResultVo vo = AbrQuickTool.build(run(school), profile(school), Map.of("UK", "英国", "US", "美国"), 99L);
        assertEquals(99L, vo.getToolRunId());
        assertEquals(List.of(SelectionEngine.NOTICE_QUICK_ACADEMIC_ONLY), vo.getNotices());
        assertEquals(new BigDecimal("85.0"), vo.getGpaPercent());
        assertEquals(List.of(new AppQuickResultVo.CountryRef("US", "美国")), vo.getClosedCountries());
        AppQuickResultVo.CountryResult uk = vo.getCountries().get(0);
        assertEquals("英国", uk.label());
        assertEquals(5, uk.programCount());
        assertEquals(List.of("reach", "match", "safety"), uk.tiers().stream().map(AppQuickResultVo.TierGroup::tier).toList());
        int tiered = uk.tiers().stream().mapToInt(AppQuickResultVo.TierGroup::count).sum();
        assertEquals(5, tiered + uk.unratedCount() + uk.needsImprovementCount());
        // D1 均分要求 87 未达：补足后可申，要求不带院校层次
        assertEquals(1, uk.needsImprovementCount());
        AppQuickResultVo.ProgramRef blocked = uk.needsImprovement().get(0);
        assertEquals(14L, blocked.programId());
        assertEquals("学校14", blocked.schoolName());
        assertEquals(List.of(new AppQuickResultVo.GapItem("gpa", "≥ 87", "85", false)), blocked.gaps());
        assertEquals(List.of(new AppQuickResultVo.KeyGap("gpa", 1, "≥ 87", "85")), uk.keyGaps());
        // 学生可见结果中不得出现综合分、概率、参考线、院校层次
        String text = MAPPER.writeValueAsString(vo);
        for (String forbidden : List.of("\"c\"", "\"p\"", "\"t\"", "tEff", "probability", "211", "undergradTier"))
        {
            assertFalse(text.contains(forbidden), forbidden + " 出现在学生可见结果中：" + text);
        }
    }

    @Test
    void schoolNotFoundAddsNoticeAndHidesTierGap()
    {
        String school = "{\"kind\": \"not_found\", \"name\": \"某学院\"}";
        AppQuickResultVo vo = AbrQuickTool.build(run(school), profile(school), Map.of(), 1L);
        assertTrue(vo.getNotices().contains(AbrQuickTool.NOTICE_SCHOOL_NOT_FOUND));
        AppQuickResultVo.CountryResult uk = vo.getCountries().get(0);
        assertEquals("UK", uk.label());
        // 未收录按双非：D3 项目不接收双非 → 院校背景不达标，要求与学生情况都不展示
        AppQuickResultVo.GapItem tier = uk.needsImprovement().stream().flatMap(r -> r.gaps().stream())
                .filter(g -> AbrQuickTool.ITEM_TIER.equals(g.item())).findFirst().orElseThrow();
        assertNull(tier.required());
        assertNull(tier.actual());
        assertFalse(MAPPER.writeValueAsString(vo).contains("non_key"));
        // 关键差距按影响项目数排序
        List<Integer> counts = new ArrayList<>(uk.keyGaps().stream().map(AppQuickResultVo.KeyGap::programCount).toList());
        List<Integer> sorted = new ArrayList<>(counts);
        sorted.sort((x, y) -> y - x);
        assertEquals(sorted, counts);
    }

    @Test
    void traceKeepsGroupsForPlanSnapshot()
    {
        String school = "{\"kind\": \"cn\", \"university_id\": 1, \"name\": \"某211大学\"}";
        IAbrSelectionService.QuickRun run = run(school);
        String trace = AbrQuickTool.trace(run).toString();
        assertEquals(SelectionEngine.NEEDS_IMPROVEMENT, AbrQuickTool.groupOf(trace, 14L));
        assertEquals(run.evaluation().results().get(0).group(), AbrQuickTool.groupOf(trace, 11L));
        assertNull(AbrQuickTool.groupOf(trace, 999L));
        assertNull(AbrQuickTool.groupOf("{}", 11L));
        assertTrue(trace.contains("\"skippedCountries\":[\"US\"]"));
    }

    @Test
    void requiredTextStripsTierOnlyForGpa()
    {
        assertEquals("≥ 87", AbrQuickTool.requiredText(new HardThresholdChecker.Item("gpa", "not_met", "211 ≥ 87", "85", "program")));
        assertEquals("雅思 6.5", AbrQuickTool.requiredText(new HardThresholdChecker.Item("language_en", "not_met", "雅思 6.5", "", "program")));
        assertNull(AbrQuickTool.requiredText(new HardThresholdChecker.Item("gpa", "unchecked", null, "85", "none")));
    }
}
