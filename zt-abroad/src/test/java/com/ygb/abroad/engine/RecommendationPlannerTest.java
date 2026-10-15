package com.ygb.abroad.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.ygb.abroad.engine.GapAnalyzer.Gap;
import com.ygb.abroad.engine.GapAnalyzer.ThresholdFix;
import com.ygb.abroad.engine.PreferenceRanker.Preference;
import com.ygb.abroad.engine.RecommendationPlanner.Hint;
import com.ygb.abroad.engine.RecommendationPlanner.Plan;
import com.ygb.abroad.engine.RecommendationPlanner.Ranked;

/**
 * 偏好排序、推荐组合与约束、差距反算测试（M0-06 说明文档 §3.5 的差距算例为基准）
 *
 * @author ygb
 */
class RecommendationPlannerTest
{
    private static final JsonNode UK = EngineSeeds.config("uk_master.json");

    private static final JsonNode AU = EngineSeeds.config("au_master.json");

    private static final JsonNode SG = EngineSeeds.config("sg_master.json");

    private static final JsonNode US = EngineSeeds.config("us_master.json");

    private static final JsonNode DE = EngineSeeds.config("de_master.json");

    private static final Map<String, JsonNode> CONFIGS = Map.of("UK", UK, "AU", AU, "SG", SG, "US", US, "DE", DE);

    private static final NormalizationRules RULES = NormalizationRules.parse(EngineSeeds.questionnaire());

    /** M0-06 算例学生（C = 81.00），目标英国 */
    private static BackgroundProfile student()
    {
        return ProfileNormalizer.normalize(EngineSeeds.answers("{\"undergrad_school\": {\"kind\": \"cn\", \"university_id\": 7},"
                + " \"target_countries\": [\"UK\"], \"target_field\": \"management\", \"undergrad_major\": {\"value\": \"management\", \"matched\": true},"
                + " \"gpa\": {\"type\": \"percent\", \"total\": 85}, \"class_rank\": \"top30\", \"exp_research\": {\"has\": \"no\"},"
                + " \"exp_work\": {\"has\": \"yes\", \"count\": 1, \"total_months\": 3, \"company_tier\": \"normal\", \"relevant\": \"yes\"},"
                + " \"exp_awards\": {\"has\": \"yes\", \"count\": 1, \"level\": \"provincial\", \"relevant\": \"yes\"},"
                + " \"english_status\": \"taken\", \"english_score\": {\"type\": \"ielts\", \"total\": 6.5, \"min_sub\": 6.0},"
                + " \"std_test\": {\"type\": \"none\"}}"), RULES, id -> id == 7L ? "211" : null, LocalDate.of(2026, 10, 14));
    }

    private static BigDecimal d(String v)
    {
        return new BigDecimal(v);
    }

    private static ProgramFacts.Listing listing(String tuition, String currency, Integer months, String city, Integer qs)
    {
        return new ProgramFacts.Listing(tuition == null ? null : d(tuition), currency, months, city, qs == null ? Map.of() : Map.of("QS", qs));
    }

    /** 难度 D3（兜底门槛学生能过），档位由参考线 T 控制 */
    private static ProgramFacts program(long id, String country, ProgramFacts.Listing listing)
    {
        return new ProgramFacts(id, country, "D3", "en", false, null, listing);
    }

    // ---------------------------------------------------------------- 偏好匹配分

    @Test
    void preferenceParts()
    {
        BackgroundProfile p = student();
        p.setBudgetTotal("40_60");
        p.setRankVsEmployment("ranking");
        p.setCityPreference("metropolis");
        ProgramFacts london = program(1, "UK", listing("30000", "GBP", 12, "london", 40));
        Preference pr = PreferenceRanker.score(p, london, UK);
        // 费用 = 30000 × 9.5 + 13000 × 12 = 44.1 万 ≤ 60 万
        assertEquals(d("44.1"), pr.costWan());
        assertEquals(Boolean.FALSE, pr.overBudget());
        assertEquals(Map.of("budget", d("100"), "rank_or_employment", d("87.50"), "city", d("100"), "duration", d("100.00")), pr.parts());
        assertEquals(d("96.88"), pr.score());

        // 选了“不超预算”为最看重：预算权重 2
        p.setBudgetTotal("20_40");
        p.setPriorityTop2(List.of("budget", "short_duration"));
        Preference over = PreferenceRanker.score(p, london, UK);
        assertEquals(Boolean.TRUE, over.overBudget());
        // (2 × 0 + 87.5 + 100 + 2 × 100) / 6 = 64.58
        assertEquals(d("64.58"), over.score());

        p.setCityPreference("quiet_city");
        assertEquals(d("0"), PreferenceRanker.score(p, london, UK).parts().get("city"));
        assertEquals(d("100"), PreferenceRanker.score(p, program(2, "UK", listing("30000", "GBP", 18, "Bath", null)), UK).parts().get("city"));
        assertEquals(d("70.00"), PreferenceRanker.score(p, program(2, "UK", listing("30000", "GBP", 18, "Bath", null)), UK).parts().get("duration"));
        assertEquals(d("85.00"), PreferenceRanker.score(p, program(2, "UK", listing("30000", "GBP", 15, null, null)), UK).parts().get("duration"));
    }

    @Test
    void preferenceMissingData()
    {
        BackgroundProfile p = student();
        p.setBudgetTotal("40_60");
        p.setRankVsEmployment("employment");
        p.setCityPreference("metropolis");
        Preference unknown = PreferenceRanker.score(p, program(1, "UK", listing("30000", "XYZ", null, null, 10)), UK);
        assertEquals(List.of("cost_unknown", "employment_data_missing", "city_missing"), unknown.flags());
        assertNull(unknown.score());
        assertNull(unknown.overBudget());

        p.setRankVsEmployment(null);
        p.setCityPreference("no_pref");
        p.setBudgetTotal("unlimited");
        Preference noRank = PreferenceRanker.score(p, program(1, "UK", listing(null, "GBP", 12, "London", null)), UK);
        assertEquals(List.of("rank_missing"), noRank.flags());
        assertEquals(Map.of("duration", d("100.00")), noRank.parts());

        Preference none = PreferenceRanker.score(p, new ProgramFacts(1L, "UK", "D3", "en", false, null), UK);
        assertEquals(List.of("listing_missing"), none.flags());
        assertNull(PreferenceRanker.budgetLimit(null, UK.path("preference").path("budget_buckets_wan")));
        assertNull(PreferenceRanker.costWan(listing("1", "GBP", 12, null, null), EngineSeeds.json("{\"fx_to_cny\":{\"GBP\":9}}")));
    }

    @Test
    void preferenceConfigErrors()
    {
        BackgroundProfile p = student();
        ProgramFacts prog = program(1, "UK", listing("30000", "GBP", 12, "London", 40));
        ObjectNode noPref = (ObjectNode) UK.deepCopy();
        noPref.remove("preference");
        assertThrows(IllegalArgumentException.class, () -> PreferenceRanker.score(p, prog, noPref));
        ObjectNode noImportance = (ObjectNode) UK.deepCopy();
        ((ObjectNode) noImportance.path("preference")).remove("importance");
        assertThrows(IllegalArgumentException.class, () -> PreferenceRanker.score(p, prog, noImportance));
        ObjectNode noPoints = (ObjectNode) UK.deepCopy();
        ((ObjectNode) noPoints.path("preference")).set("duration_points", EngineSeeds.json("[]"));
        assertThrows(IllegalArgumentException.class, () -> PreferenceRanker.score(p, prog, noPoints));
    }

    // ---------------------------------------------------------------- 推荐组合

    /** 按 T 控制档位：T 90 → 冲，T 81 → 稳，T 70 → 保（学生 C = 81，a = 0.18） */
    private static Plan plan(BackgroundProfile p, List<ProgramFacts> programs, Map<Long, Long> tById)
    {
        Map<Long, ProgramBenchmark> benchmarks = new HashMap<>();
        tById.forEach((id, t) -> benchmarks.put(id, new ProgramBenchmark(BigDecimal.valueOf(t), null, "own_cases")));
        SelectionEngine.Evaluation e = SelectionEngine.evaluate(p, programs, CONFIGS, benchmarks, false);
        return RecommendationPlanner.plan(p, e, CONFIGS);
    }

    private static List<Long> ids(List<Ranked> list)
    {
        return list.stream().map(r -> r.evaluation().program().programId()).toList();
    }

    @Test
    void defaultMixOrderedByPreference()
    {
        BackgroundProfile p = student();
        p.setRankVsEmployment("ranking");
        List<ProgramFacts> programs = new ArrayList<>();
        Map<Long, Long> t = new HashMap<>();
        // 3 冲、5 稳、3 保；QS 名次越小偏好分越高
        long[][] spec = { { 1, 90, 300 }, { 2, 90, 20 }, { 3, 90, 100 }, { 4, 81, 500 }, { 5, 81, 10 }, { 6, 81, 60 }, { 7, 81, 200 },
                { 8, 81, 150 }, { 9, 70, 400 }, { 10, 70, 30 }, { 11, 70, 90 } };
        for (long[] s : spec)
        {
            programs.add(program(s[0], "UK", listing("30000", "GBP", 12, "London", (int) s[2])));
            t.put(s[0], s[1]);
        }
        Plan plan = plan(p, programs, t);
        assertEquals(List.of(2L, 3L, 1L), ids(plan.tiers().get("reach")));
        assertEquals(List.of(5L, 6L, 8L, 7L, 4L), ids(plan.tiers().get("match")));
        assertEquals(List.of(2L, 3L), ids(plan.portfolio().get("reach")));
        assertEquals(List.of(5L, 6L, 8L, 7L), ids(plan.portfolio().get("match")));
        assertEquals(List.of(10L, 11L), ids(plan.portfolio().get("safety")));
        assertTrue(plan.hints().isEmpty());
        assertTrue(plan.notices().isEmpty());
        // 同一输入同一输出
        assertEquals(plan, plan(p, programs, t));
    }

    @Test
    void budgetShortageAndHints()
    {
        BackgroundProfile p = student();
        p.setBudgetTotal("20_40");
        p.setRankVsEmployment("employment");
        List<ProgramFacts> programs = List.of(program(1, "UK", listing("30000", "GBP", 12, "London", null)),
                program(2, "UK", listing("10000", "GBP", 12, "London", null)), program(3, "UK", listing("10000", "GBP", 12, "London", null)),
                program(4, "UK", null), new ProgramFacts(5L, "UK", null, "en", false, null));
        Plan plan = plan(p, programs, Map.of(1L, 70L, 2L, 70L, 3L, 81L, 4L, 90L));
        // 1 号超预算（44.1 万 > 40 万）：不进组合
        assertEquals(List.of(2L), ids(plan.portfolio().get("safety")));
        assertEquals(List.of(5L), ids(plan.unrated()));
        assertTrue(plan.hints().contains(new Hint("over_budget", null, List.of("1"))));
        assertTrue(plan.hints().contains(new Hint("tier_shortage", null, List.of("reach", "1"))));
        assertTrue(plan.hints().contains(new Hint("tier_shortage", null, List.of("match", "3"))));
        assertTrue(plan.hints().contains(new Hint("tier_shortage", null, List.of("safety", "1"))));
        assertTrue(plan.hints().contains(new Hint("employment_data_missing", null, List.of())));
    }

    @Test
    void safetyInFirstChoiceCountryAndSafetyCountry()
    {
        BackgroundProfile p = student();
        p.setTargetCountries(List.of("UK", "AU"));
        p.setRankVsEmployment("ranking");
        // 澳洲两个保底偏好分更高，英国保底偏好分低：组合先取澳洲（保底国家），再换入一个英国保底满足首选国家约束
        List<ProgramFacts> programs = List.of(program(1, "AU", listing("40000", "AUD", 12, "Sydney", 20)),
                program(2, "AU", listing("40000", "AUD", 12, "Sydney", 30)), program(3, "UK", listing("30000", "GBP", 12, "London", 800)),
                program(4, "UK", listing("30000", "GBP", 12, "London", 900)));
        Plan plan = plan(p, programs, Map.of(1L, 60L, 2L, 60L, 3L, 70L, 4L, 70L));
        assertEquals(List.of(1L, 3L), ids(plan.portfolio().get("safety")));

        // 首选澳洲但没有澳洲保底：提示
        p.setTargetCountries(List.of("AU", "UK"));
        Plan noHome = plan(p, List.of(program(3, "UK", listing("30000", "GBP", 12, "London", 800)), program(1, "AU", null)),
                Map.of(3L, 70L, 1L, 95L));
        assertTrue(noHome.hints().contains(new Hint("no_safety_in_first_choice", "AU", List.of())));

        // 首选国家不在配置中：用第一个项目所在国家的组合参数
        p.setTargetCountries(List.of("JP"));
        assertEquals(List.of(3L), ids(plan(p, List.of(program(3, "UK", null)), Map.of(3L, 70L)).portfolio().get("safety")));
        p.setTargetCountries(List.of());
        assertTrue(plan(p, List.of(), Map.of()).portfolio().isEmpty());
    }

    @Test
    void singaporeWithoutSafetySuggestsMixedApplication()
    {
        BackgroundProfile p = student();
        p.setTargetCountries(List.of("SG"));
        Plan plan = plan(p, List.of(program(1, "SG", null)), Map.of(1L, 90L));
        assertTrue(plan.hints().contains(new Hint("mix_application", "SG", List.of("UK", "AU"))));
        // 新加坡 D3 要求 211 均分 87，学生 85 → 补足后可申，同样没有保底；换成 D5 项目才有保底
        ProgramFacts d5 = new ProgramFacts(1L, "SG", "D5", "en", false, null);
        Plan withSafety = plan(p, List.of(d5), Map.of(1L, 30L));
        assertEquals(List.of(1L), ids(withSafety.tiers().get("safety")));
        assertFalse(withSafety.hints().stream().anyMatch(h -> "mix_application".equals(h.code())));
    }

    @Test
    void excludedProgramsAndQuickNotice()
    {
        BackgroundProfile p = student();
        p.setJlptLevel("english_only");
        Map<String, JsonNode> configs = Map.of("JP", EngineSeeds.config("jp_master.json"));
        SelectionEngine.Evaluation e = SelectionEngine.evaluate(p, List.of(new ProgramFacts(1L, "JP", "D3", "ja", false, null)), configs,
                Map.of(), false);
        Plan plan = RecommendationPlanner.plan(p, e, configs);
        assertTrue(plan.tiers().values().stream().allMatch(List::isEmpty));
        assertTrue(plan.needsImprovement().isEmpty());

        SelectionEngine.Evaluation quick = SelectionEngine.evaluate(p, List.of(program(1, "UK", null)), CONFIGS, Map.of(), true);
        assertEquals(List.of("quick_academic_only"), RecommendationPlanner.plan(p, quick, CONFIGS).notices());
        assertThrows(IllegalArgumentException.class, () -> RecommendationPlanner.plan(p, quick, Map.of()));
    }

    // ---------------------------------------------------------------- 差距反算

    private static Gap gap(BackgroundProfile p, ProgramFacts program, ProgramBenchmark b, JsonNode config)
    {
        Map<Long, ProgramBenchmark> bm = b == null ? Map.of() : Map.of(program.programId(), b);
        SelectionEngine.Evaluation e = SelectionEngine.evaluate(p, List.of(program), Map.of(program.country(), config), bm, false);
        return GapAnalyzer.analyze(p, e.results().get(0), config);
    }

    @Test
    void m006GapExample()
    {
        // §3.5：D1 项目（T = 88）冲 → 稳需要 C ≥ 88 + ln(0.35 / 0.65) / 0.18 = 84.56；差 3.56（向上取整到 84.57，差 3.57）
        BackgroundProfile p = student();
        ProgramFacts d1 = program(1, "UK", null);
        ProgramBenchmark t88 = new ProgramBenchmark(d("88"), null, "own_cases");
        Gap g = gap(p, d1, t88, UK);
        assertEquals(d("84.57"), g.requiredC());
        assertEquals(d("3.57"), g.gapC());
        // 单独：课题组经历 +1.75，3 个月相关实习 +2.80；都不够，组合 +4.55 够
        assertEquals(List.of(new GapAnalyzer.ActionGain("add_lab_research", "research", d("1.75"), 4),
                new GapAnalyzer.ActionGain("add_related_internship_3m", "work_experience", d("2.80"), 3)), g.options());
        assertEquals(List.of("add_lab_research", "add_related_internship_3m"), g.plan());
        assertEquals(d("4.55"), g.planGain());
        assertEquals(7, g.planMonths());
        assertTrue(g.achievable());
        assertNull(g.inTime());
        assertTrue(g.thresholdFixes().isEmpty());

        // 距入学 11 个月、递交提前 6 个月：7 个月来不及；20 个月来得及
        p.setMonthsToIntake(11);
        assertEquals(Boolean.FALSE, gap(p, d1, t88, UK).inTime());
        p.setMonthsToIntake(20);
        assertEquals(Boolean.TRUE, gap(p, d1, t88, UK).inTime());

        // 差距小时选单个、月数少的动作：T = 86 → 需要 82.57，差 1.57，实习（3 个月）即可
        Gap small = gap(p, d1, new ProgramBenchmark(d("86"), null, "own_cases"), UK);
        assertEquals(List.of("add_related_internship_3m"), small.plan());
    }

    @Test
    void gapNotAchievableAndNotNeeded()
    {
        BackgroundProfile p = student();
        Gap far = gap(p, program(1, "UK", null), new ProgramBenchmark(d("120"), null, "own_cases"), UK);
        assertFalse(far.achievable());
        assertEquals(List.of("add_lab_research", "add_related_internship_3m"), far.plan());
        // 稳档不做反算
        assertNull(gap(p, program(1, "UK", null), new ProgramBenchmark(d("81"), null, "own_cases"), UK));

        // 已有科研：再加一段；实习已相关
        p.setResearchLevel("conference");
        p.setResearchCount(1);
        BackgroundProfile applied = GapAnalyzer.apply(p, List.of(new GapAnalyzer.Action("add_lab_research", "research", 4),
                new GapAnalyzer.Action("add_related_internship_3m", "work_experience", 3)));
        assertEquals(2, applied.getResearchCount());
        assertEquals(6, applied.getWorkMonths());
        assertEquals(1, p.getResearchCount());
        BackgroundProfile empty = new BackgroundProfile();
        BackgroundProfile w = GapAnalyzer.apply(empty, List.of(new GapAnalyzer.Action("add_related_internship_3m", "work_experience", 3),
                new GapAnalyzer.Action("add_lab_research", "research", 4)));
        assertEquals("yes", w.getWorkRelevant());
        assertEquals("normal", w.getWorkCompanyTier());
        assertEquals(1, w.getWorkCount());
        assertEquals("lab_project", w.getResearchLevel());
        // 没有可做的动作：计划为空，月数 0
        BackgroundProfile maxed = student();
        maxed.setResearchLevel("core_journal");
        maxed.setResearchCount(9);
        maxed.setWorkMonths(60);
        maxed.setWorkCompanyTier("top");
        maxed.setMonthsToIntake(30);
        Gap none = gap(maxed, program(1, "UK", null), new ProgramBenchmark(d("120"), null, "own_cases"), UK);
        assertTrue(none.plan().isEmpty());
        assertNull(none.inTime());
    }

    @Test
    void greActionForUsStudents()
    {
        BackgroundProfile p = student();
        p.setStdTestType("gre");
        p.setStdTestTotal(315);
        Gap g = gap(p, new ProgramFacts(1L, "US", "D3", "en", false, null), new ProgramBenchmark(d("95"), null, "own_cases"), US);
        assertTrue(g.options().stream().anyMatch(o -> "gre_plus_10".equals(o.code()) && o.gainC().signum() > 0));
        p.setStdTestTotal(338);
        assertEquals(340, GapAnalyzer.apply(p, List.of(new GapAnalyzer.Action("gre_plus_10", "test_scores", 2))).getStdTestTotal());
        // 没考 GRE：不试算标化
        p.setStdTestType("none");
        p.setStdTestTotal(null);
        assertTrue(gap(p, new ProgramFacts(1L, "US", "D3", "en", false, null), new ProgramBenchmark(d("95"), null, "own_cases"), US)
                .options().stream().noneMatch(o -> "test_scores".equals(o.dimension())));
    }

    @Test
    void thresholdFixes()
    {
        // 新加坡 D1：雅思 7.0 / 小分 6.5、均分不够 → 雅思提高 0.5 一次（2 个月）可补；均分不能靠短期动作补足
        Gap sg = gap(student(), new ProgramFacts(1L, "SG", "D1", "en", false, null), null, SG);
        assertTrue(sg.thresholdFixes().contains(new ThresholdFix("language_en", "ielts_plus_0_5", 1, 2)));
        assertTrue(sg.thresholdFixes().contains(new ThresholdFix("gpa", null, 0, null)));

        // 德国 APS 未办理：提前 6 个月办理
        BackgroundProfile de = student();
        de.setDeApsStatus("not_started");
        de.setCourseMatchRatio(d("0.9"));
        Gap aps = gap(de, new ProgramFacts(1L, "DE", "D5", "en", false, null), null, DE);
        assertEquals(List.of(new ThresholdFix("aps", "do_aps", 1, 6)), aps.thresholdFixes());
        assertNull(aps.requiredC());

        // 托福按原始分比较：无法用雅思试算
        BackgroundProfile toefl = student();
        toefl.setEnglishTest("toefl_ibt");
        toefl.setEnglishTotal(d("90"));
        ProgramFacts.Requirement req = new ProgramFacts.Requirement(Map.of("211", d("70")), Set.of(), null, null, null,
                EngineSeeds.json("{\"toefl\":{\"overall\":100}}"), null, false, null, "2", "2");
        Gap raw = gap(toefl, new ProgramFacts(1L, "SG", "D5", "en", false, req), null, SG);
        assertEquals(List.of(new ThresholdFix("language_en", null, 0, null)), raw.thresholdFixes());

        // 雅思差太多（6 次仍不够）或没有雅思等效分
        BackgroundProfile low = student();
        low.setIeltsEquivTotal(d("3.0"));
        low.setIeltsEquivMinSub(null);
        assertEquals(new ThresholdFix("language_en", null, 0, null), gap(low, new ProgramFacts(1L, "SG", "D1", "en", false, null), null, SG)
                .thresholdFixes().get(0));
    }

    @Test
    void copyIsIndependent()
    {
        BackgroundProfile p = student();
        BackgroundProfile c = p.copy();
        c.getTargetCountries().add("AU");
        c.setGpaPercent(d("60"));
        assertEquals(List.of("UK"), p.getTargetCountries());
        assertEquals(d("85.0"), p.getGpaPercent());
        assertEquals(p.getWarnings(), c.getWarnings());
    }
}
