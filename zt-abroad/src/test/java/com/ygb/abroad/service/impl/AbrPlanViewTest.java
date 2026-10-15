package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrAssessmentResult;
import com.ygb.abroad.domain.AbrPlanItem;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.vo.AppPlanVo;
import com.ygb.abroad.engine.BackgroundProfile;
import com.ygb.abroad.engine.NormalizationRules;
import com.ygb.abroad.engine.ProfileNormalizer;
import com.ygb.abroad.engine.ProgramFacts;
import com.ygb.abroad.engine.SelectionEngine;
import com.ygb.abroad.service.IAbrSelectionService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 选校单纯函数测试：当前分组与逐项达标、加入时档位变化、已下线 / 由顾问评估 / 待补充背景、档位分布提示、档案口径判断
 *
 * @author ygb
 */
class AbrPlanViewTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    private static AbrAssessmentAnswers.Questionnaire q;

    private static JsonNode uk;

    @BeforeAll
    static void load() throws IOException
    {
        q = AbrAssessmentAnswers.parse(Files.readString(Path.of("..", "seed", "questionnaire", "questionnaire_v1.json"), StandardCharsets.UTF_8));
        uk = MAPPER.readTree(Files.readString(Path.of("..", "seed", "engine-config", "uk_master.json"), StandardCharsets.UTF_8));
    }

    private static ObjectNode answers()
    {
        return AbrAssessmentReportTest.answers();
    }

    private static AbrPlanItem item(long id, String tierAtAdd, boolean active)
    {
        AbrPlanItem i = new AbrPlanItem();
        i.setProgramId(id);
        i.setSchoolName("学校" + id);
        i.setProgramName("项目" + id);
        i.setCountry(id == 40 ? "US" : "UK");
        i.setTierSnapshot(tierAtAdd);
        i.setSource("quick");
        i.setActive(active);
        return i;
    }

    private static Map<Long, AbrProgram> programs()
    {
        Map<Long, AbrProgram> map = new HashMap<>();
        for (long id : new long[] {31, 32, 33, 40})
        {
            AbrProgram p = new AbrProgram();
            p.setProgramId(id);
            p.setVerifiedTime(Date.from(TODAY.minusDays(id == 33 ? 100 : 3).atStartOfDay(AbrLookupTools.BEIJING).toInstant()));
            map.put(id, p);
        }
        return map;
    }

    /** 31 D5 无要求、32 D3 雅思 7.0 不接受语言班（补足后可申）、33 同 32 但信息超过 90 天未核实；40 美国未开放、41 已下线 */
    private static IAbrSelectionService.PlanRun run()
    {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        answers().properties().forEach(e -> map.put(e.getKey(), e.getValue()));
        BackgroundProfile p = ProfileNormalizer.normalize(map, NormalizationRules.parse(q.root()), id -> id == 7L ? "211" : null, TODAY);
        ProgramFacts.Requirement ielts7 = new ProgramFacts.Requirement(Map.of(), Set.of(), new BigDecimal("7.0"), null, null, null, null, false, null,
                null, "0");
        List<ProgramFacts> facts = List.of(new ProgramFacts(31L, "UK", "D5", "en", false, null), new ProgramFacts(32L, "UK", "D3", "en", false, ielts7),
                new ProgramFacts(33L, "UK", "D3", "en", false, ielts7));
        SelectionEngine.Evaluation e = SelectionEngine.evaluate(p, facts, Map.of("UK", uk), Map.of(), false);
        Map<Long, String> skipped = new LinkedHashMap<>();
        skipped.put(40L, AbrSelectionServiceImpl.PLAN_CONSULTANT);
        return new IAbrSelectionService.PlanRun(e, Map.of("UK", uk), skipped, programs());
    }

    @Test
    void buildEvaluatesEachItem()
    {
        List<AbrPlanItem> items = List.of(item(31, "match", true), item(32, null, true), item(33, null, true), item(40, null, true),
                item(41, "safety", false));
        AppPlanVo vo = AbrPlanView.build(AbrPlanView.BASIS_FULL, items, programs(), run(), c -> "UK".equals(c) ? "英国" : c, List.of(), TODAY);
        assertEquals("full", vo.basis());
        assertEquals(5, vo.items().size());
        AppPlanVo.Item i31 = vo.items().get(0);
        assertEquals("ok", i31.status());
        assertEquals("英国", i31.countryLabel());
        assertTrue(List.of("reach", "match", "safety").contains(i31.tier()), i31.tier());
        assertEquals(!"match".equals(i31.tier()), i31.tierChanged());
        assertFalse(i31.checks().isEmpty());

        AppPlanVo.Item i32 = vo.items().get(1);
        assertEquals(SelectionEngine.NEEDS_IMPROVEMENT, i32.tier());
        assertFalse(i32.tierChanged());
        AppPlanVo.Check lang = i32.checks().stream().filter(c -> "language_en".equals(c.item())).findFirst().orElseThrow();
        assertEquals("not_met", lang.outcome());
        assertEquals("雅思 7.0", lang.required());
        assertEquals("雅思 6.0", lang.actual());
        // 33：超过 90 天未核实，不展示要求数值
        AppPlanVo.Item i33 = vo.items().get(2);
        assertTrue(i33.unverified());
        assertTrue(i33.checks().stream().allMatch(c -> c.required() == null));

        assertEquals("consultant", vo.items().get(3).status());
        assertNull(vo.items().get(3).tier());
        assertEquals("offline", vo.items().get(4).status());
        assertFalse(vo.items().get(4).tierChanged());

        assertEquals(2, vo.tierCounts().get(SelectionEngine.NEEDS_IMPROVEMENT));
        assertEquals(List.of("reach", "match", "safety", "unrated", "needs_improvement", "excluded"), List.copyOf(vo.tierCounts().keySet()));
        assertTrue(vo.hints().stream().anyMatch(h -> "mostly_blocked".equals(h.code())), vo.hints().toString());
        assertTrue(vo.hints().stream().anyMatch(h -> "too_few".equals(h.code()) && h.suggested() == 8), vo.hints().toString());
        String text = MAPPER.writeValueAsString(vo);
        for (String forbidden : List.of("211", "\"c\"", "\"p\"", "tEff", "non_key"))
        {
            assertFalse(text.contains(forbidden), forbidden + " 出现在选校单中：" + text);
        }
    }

    @Test
    void noProfileMarksItemsNeedProfile()
    {
        AppPlanVo vo = AbrPlanView.build(AbrPlanView.BASIS_NONE, List.of(item(31, "match", true), item(41, null, false)), programs(), null, c -> c,
                List.of("gpa"), TODAY);
        assertEquals("need_profile", vo.items().get(0).status());
        assertEquals("offline", vo.items().get(1).status());
        assertEquals(List.of("gpa"), vo.missingInfo());
        assertTrue(vo.hints().isEmpty());
    }

    @Test
    void hintsFollowPortfolioMix()
    {
        JsonNode mix = uk.path("portfolio").path("default_mix");
        assertEquals(List.of(), codes(counts(2, 4, 2, 0), mix));
        assertEquals(List.of("no_safety"), codes(counts(2, 6, 0, 0), mix));
        assertEquals(List.of("no_safety", "no_match", "reach_heavy", "too_few"), codes(counts(3, 0, 0, 0), mix));
        assertEquals(List.of("mostly_blocked", "too_few"), codes(counts(1, 1, 1, 4), mix));
        assertEquals(List.of("no_match"), codes(counts(0, 0, 1, 0), null));
        assertEquals(List.of(), codes(counts(0, 0, 0, 0), mix));
        assertEquals(2, AbrPlanView.hints(counts(2, 6, 0, 0), mix).get(0).suggested());
    }

    private static Map<String, Integer> counts(int reach, int match, int safety, int blocked)
    {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("reach", reach);
        m.put("match", match);
        m.put("safety", safety);
        m.put("needs_improvement", blocked);
        return m;
    }

    private static List<String> codes(Map<String, Integer> counts, JsonNode mix)
    {
        List<String> list = new ArrayList<>();
        AbrPlanView.hints(counts, mix).forEach(h -> list.add(h.code()));
        return list;
    }

    @Test
    void profileBasisAndVisibility()
    {
        ObjectNode a = answers();
        a.put("english_status", "none");
        a.put("intake_year", "2027");
        Map<String, JsonNode> visible = AbrPlanServiceImpl.visibleAnswers(q, a);
        // 未考时成绩题隐藏，答案不参与计算
        assertFalse(visible.containsKey("english_score"));
        assertTrue(AbrPlanServiceImpl.fullProfile(q, visible));
        ObjectNode quickOnly = (ObjectNode) MAPPER.readTree(
                "{\"undergrad_school\": {\"kind\": \"cn\", \"university_id\": 7, \"name\": \"某大学\"}, \"gpa\": {\"type\": \"percent\", \"total\": 85},"
                        + " \"target_countries\": [\"UK\"], \"target_field\": \"finance\", \"unknown_q\": 1}");
        Map<String, JsonNode> v2 = AbrPlanServiceImpl.visibleAnswers(q, quickOnly);
        assertFalse(v2.containsKey("unknown_q"));
        assertFalse(AbrPlanServiceImpl.fullProfile(q, v2));
    }

    @Test
    void groupInResult()
    {
        AbrAssessmentResult r = new AbrAssessmentResult();
        r.setPrograms("[{\"programId\": 1, \"group\": \"match\"}, {\"programId\": 2, \"group\": \"excluded\"}]");
        assertEquals("match", AbrPlanServiceImpl.groupInResult(r, 1L));
        assertNull(AbrPlanServiceImpl.groupInResult(r, 2L));
        assertNull(AbrPlanServiceImpl.groupInResult(r, 3L));
        assertNull(AbrPlanServiceImpl.groupInResult(null, 1L));
        r.setPrograms("not json");
        assertNull(AbrPlanServiceImpl.groupInResult(r, 1L));
    }
}
