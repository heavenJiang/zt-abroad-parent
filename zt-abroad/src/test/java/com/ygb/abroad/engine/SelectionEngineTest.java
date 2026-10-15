package com.ygb.abroad.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.ygb.abroad.engine.SelectionEngine.Evaluation;
import com.ygb.abroad.engine.SelectionEngine.ProgramEvaluation;

/**
 * 综合分、录取可能性、分档与快测测试：以 M0-06 说明文档 §3.2、§3.4、§3.5 的算例为基准
 *
 * @author ygb
 */
class SelectionEngineTest
{
    private static final JsonNode UK = EngineSeeds.config("uk_master.json");

    private static final JsonNode US = EngineSeeds.config("us_master.json");

    private static final JsonNode DE = EngineSeeds.config("de_master.json");

    private static final NormalizationRules RULES = NormalizationRules.parse(EngineSeeds.questionnaire());

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    private static final String ANSWERS = "{\"undergrad_school\": {\"kind\": \"cn\", \"university_id\": 7},"
            + " \"target_countries\": [\"UK\"], \"target_field\": \"management\", \"undergrad_major\": {\"value\": \"management\", \"matched\": true},"
            + " \"gpa\": {\"type\": \"percent\", \"total\": 85}, \"class_rank\": \"top30\", \"exp_research\": {\"has\": \"no\"},"
            + " \"exp_work\": {\"has\": \"yes\", \"count\": 1, \"total_months\": 3, \"company_tier\": \"normal\", \"relevant\": \"yes\"},"
            + " \"exp_awards\": {\"has\": \"yes\", \"count\": 1, \"level\": \"provincial\", \"relevant\": \"yes\"},"
            + " \"english_status\": \"taken\", \"english_score\": {\"type\": \"ielts\", \"total\": 6.5, \"min_sub\": 6.0},"
            + " \"std_test\": {\"type\": \"none\"}}";

    /** M0-06 算例学生 */
    private static BackgroundProfile student()
    {
        return ProfileNormalizer.normalize(EngineSeeds.answers(ANSWERS), RULES, id -> id == 7L ? "211" : null, TODAY);
    }

    private static ProgramFacts program(long id, String country, String difficulty)
    {
        return new ProgramFacts(id, country, difficulty, "en", false, null);
    }

    private static ProgramFacts.Requirement req(String testRequirement)
    {
        return new ProgramFacts.Requirement(Map.of("211", new BigDecimal("80")), Set.of(), new BigDecimal("6.5"), new BigDecimal("6.0"),
                testRequirement, null, null, false, null, "2", "2");
    }

    private static ProgramEvaluation one(BackgroundProfile p, ProgramFacts program, JsonNode config, ProgramBenchmark b, boolean quick)
    {
        Map<Long, ProgramBenchmark> benchmarks = b == null ? Map.of() : Map.of(program.programId(), b);
        return SelectionEngine.evaluate(p, List.of(program), Map.of(program.country(), config), benchmarks, quick).results().get(0);
    }

    private static BigDecimal d(String v)
    {
        return new BigDecimal(v);
    }

    // ---------------------------------------------------------------- M0-06 算例

    @Test
    void m006WorkedExample()
    {
        Evaluation e = SelectionEngine.evaluate(student(), List.of(program(1, "UK", "D2"), program(2, "UK", "D4")), Map.of("UK", UK),
                Map.of(), false);
        ProgramEvaluation a = e.results().get(0);
        ProgramEvaluation b = e.results().get(1);
        // §3.2：C = 0.65 × 88 + 0.15 × 100 + 0.05 × 20 + 0.10 × 48 + 0.05 × 60 = 81.00
        assertEquals(d("81.00"), a.composite().c());
        assertTrue(a.composite().removed().isEmpty());
        // §3.4：A（D2，T = 82）P = 0.4551 → 稳；B（D4，T = 70）P = 0.8787 → 保
        assertEquals(d("82"), a.estimate().t());
        assertEquals("consultant_difficulty", a.estimate().tSource());
        assertEquals(d("0.18"), a.estimate().a());
        assertEquals(d("0.4551"), a.estimate().p());
        assertEquals("match", a.group());
        assertEquals(d("0.8787"), b.estimate().p());
        assertEquals("safety", b.group());
        assertEquals(UK.path("version").asString(), a.configVersion());
        assertEquals(List.of(a), e.group("match"));
        assertEquals(SelectionEngine.MODE_FULL, e.mode());
        assertEquals(d("88.00"), e.subScores().get("UK").scores().get("academic"));
    }

    @Test
    void m006D1Program()
    {
        // §3.5：兜底要求下 D1 项目未过硬门槛 → 补足后可申；C、P 仍计算，供差距反算
        ProgramEvaluation fallback = one(student(), program(3, "UK", "D1"), UK, null, false);
        assertEquals("needs_improvement", fallback.group());
        assertEquals(d("0.2210"), fallback.estimate().p());
        assertEquals("reach", fallback.estimate().tier());

        // 项目库要求更低、硬门槛通过：P = 0.2210 → 冲
        ProgramEvaluation real = one(student(), new ProgramFacts(3L, "UK", "D1", "en", false, req(null)), UK, null, false);
        assertEquals("reach", real.group());
        assertEquals(d("0.2210"), real.estimate().p());

        // 差距反算的结果：增加课题组经历（科研 55）与 3 个月相关实习（实习 76）后 C = 85.55，P = 0.3915 → 稳
        BackgroundProfile better = student();
        better.setResearchLevel("lab_project");
        better.setResearchCount(1);
        better.setResearchRelevant("yes");
        better.setWorkMonths(6);
        ProgramEvaluation after = one(better, new ProgramFacts(3L, "UK", "D1", "en", false, req(null)), UK, null, false);
        assertEquals(d("85.55"), after.composite().c());
        assertEquals(d("0.3915"), after.estimate().p());
        assertEquals("match", after.group());
    }

    @Test
    void sameInputSameOutput()
    {
        List<ProgramFacts> programs = List.of(program(1, "UK", "D1"), program(2, "UK", "D3"));
        assertEquals(SelectionEngine.evaluate(student(), programs, Map.of("UK", UK), Map.of(), false),
                SelectionEngine.evaluate(student(), programs, Map.of("UK", UK), Map.of(), false));
    }

    // ---------------------------------------------------------------- 参考线与分档

    @Test
    void programBenchmarkOverridesDifficulty()
    {
        // 项目自己的 T = 76、a = 0.3：P = 1 / (1 + e^{-1.5}) = 0.8176 → 保
        ProgramEvaluation own = one(student(), program(1, "UK", "D1"), UK, new ProgramBenchmark(d("76"), d("0.3"), "own_cases"), false);
        assertEquals(d("76"), own.estimate().t());
        assertEquals("own_cases", own.estimate().tSource());
        assertEquals(d("0.8176"), own.estimate().p());
        // 参考线没有 a：用国家默认 a
        ProgramEvaluation noA = one(student(), program(1, "UK", "D1"), UK, new ProgramBenchmark(d("82"), null, "official_stats"), false);
        assertEquals(d("0.18"), noA.estimate().a());
        assertEquals(d("0.4551"), noA.estimate().p());
        // 参考线只有 a：T 仍按难度档
        ProgramEvaluation onlyA = one(student(), program(1, "UK", "D2"), UK, new ProgramBenchmark(null, d("0.5"), "own_cases"), false);
        assertEquals("consultant_difficulty", onlyA.estimate().tSource());
        assertEquals(d("0.5"), onlyA.estimate().a());
    }

    @Test
    void unratedWithoutBenchmarkOrDifficulty()
    {
        ProgramEvaluation e = one(student(), program(1, "UK", null), UK, null, false);
        assertEquals("unrated", e.group());
        assertNull(e.estimate().p());
        assertEquals("unrated", one(student(), program(1, "UK", "D9"), UK, null, false).group());
    }

    @Test
    void tierBoundaries()
    {
        JsonNode cut = UK.path("tier_cutoffs");
        assertEquals("reach", AdmissionEstimator.tier(d("0.3499"), cut));
        assertEquals("match", AdmissionEstimator.tier(d("0.3500"), cut));
        assertEquals("match", AdmissionEstimator.tier(d("0.7000"), cut));
        assertEquals("safety", AdmissionEstimator.tier(d("0.7001"), cut));
        // C 恰好等于 T：P = 0.5000 → 稳
        AdmissionEstimator.Estimate eq = AdmissionEstimator.estimate(d("82.00"), "D2", null, UK);
        assertEquals(d("0.5000"), eq.p());
        assertEquals("match", eq.tier());
    }

    @Test
    void usTOffsetRaisesReferenceLine()
    {
        AdmissionEstimator.Estimate e = AdmissionEstimator.estimate(d("80"), "D3", null, US);
        BigDecimal t = US.path("benchmark_defaults").path("difficulty_levels").path("D3").path("t").decimalValue();
        assertEquals(t.add(d("2.0")), e.tEff());
        AdmissionEstimator.Estimate noOffset = AdmissionEstimator.estimate(d("80"), "D3", null, without(US, "probability", "t_offset"));
        assertEquals(t, noOffset.tEff());
    }

    @Test
    void missingProbabilityParametersAreRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> AdmissionEstimator.estimate(d("80"), "D3", null, without(UK, "probability", "a_default")));
        assertThrows(IllegalArgumentException.class, () -> AdmissionEstimator.estimate(d("80"), "D3", null, without(UK, "tier_cutoffs", "reach_below")));
    }

    private static JsonNode without(JsonNode config, String section, String key)
    {
        ObjectNode copy = (ObjectNode) config.deepCopy();
        ((ObjectNode) copy.path(section)).remove(key);
        return copy;
    }

    // ---------------------------------------------------------------- 权重

    @Test
    void usBusinessWeightsAndTestRenormalize()
    {
        BackgroundProfile p = student();
        assertEquals("business", p.getTargetFieldGroup());
        // 美国商科：0.35 学术 + 0.10 专业 + 0.20 标化 + 0.05 科研 + 0.25 实习 + 0.05 其他
        ProgramEvaluation required = one(p, new ProgramFacts(1L, "US", "D3", "en", false, req("required")), US, null, false);
        Map<String, BigDecimal> s = SelectionEngine.evaluate(p, List.of(program(1, "US", "D3")), Map.of("US", US), Map.of(), false)
                .subScores().get("US").scores();
        BigDecimal expected = d("0.35").multiply(s.get("academic")).add(d("0.10").multiply(s.get("major_relevance")))
                .add(d("0.20").multiply(s.get("test_scores"))).add(d("0.05").multiply(s.get("research")))
                .add(d("0.25").multiply(s.get("work_experience"))).add(d("0.05").multiply(s.get("other_soft")))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertEquals(expected, required.composite().c());
        assertEquals(d("0.2"), required.composite().weights().get("test_scores"));

        // 项目不要求标化：去掉标化，其余权重放大到和为 1
        ProgramEvaluation none = one(p, new ProgramFacts(1L, "US", "D3", "en", false, req("none")), US, null, false);
        assertEquals(List.of("test_scores"), none.composite().removed());
        assertEquals(d("0.4375"), none.composite().weights().get("academic"));
        BigDecimal renorm = expected.subtract(d("0.20").multiply(s.get("test_scores"))).divide(d("0.80"), 2, java.math.RoundingMode.HALF_UP);
        assertTrue(renorm.subtract(none.composite().c()).abs().compareTo(d("0.01")) <= 0);
    }

    @Test
    void germanyCourseMatchMissingIsRenormalized()
    {
        BackgroundProfile p = student();
        p.setDeApsStatus("passed");
        ProgramEvaluation e = one(p, program(1, "DE", "D3"), DE, null, false);
        assertEquals(List.of("course_match"), e.composite().removed());
        p.setCourseMatchRatio(d("0.8"));
        assertTrue(one(p, program(1, "DE", "D3"), DE, null, false).composite().removed().isEmpty());
    }

    @Test
    void weightConfigErrors()
    {
        Map<String, BigDecimal> scores = Map.of("academic", d("80"));
        ObjectNode noKey = (ObjectNode) UK.deepCopy();
        ((ObjectNode) noKey.path("weights").path("default")).remove("research");
        assertThrows(IllegalArgumentException.class, () -> CompositeScorer.compute(scores, noKey, null, Set.of()));
        ObjectNode noDefault = (ObjectNode) UK.deepCopy();
        ((ObjectNode) noDefault.path("weights")).remove("default");
        assertThrows(IllegalArgumentException.class, () -> CompositeScorer.compute(scores, noDefault, null, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> CompositeScorer.compute(scores, UK, null, Set.of("academic")));
        // 只有学术子分：C = 学术子分
        assertEquals(d("80.00"), CompositeScorer.compute(scores, UK, "business", Set.of()).c());
    }

    // ---------------------------------------------------------------- 快测

    @Test
    void quickModeUsesOnlyQuickInputs()
    {
        Evaluation e = SelectionEngine.quick(EngineSeeds.answers(ANSWERS), RULES, id -> id == 7L ? "211" : null, TODAY,
                List.of(program(1, "UK", "D2"), program(2, "UK", "D1")), Map.of("UK", UK), Map.of());
        assertEquals(SelectionEngine.MODE_QUICK, e.mode());
        ProgramEvaluation a = e.results().get(0);
        // 快测不问排名：学术 = 72 + 3 × (85 − 80) = 87，只用学术维度 → C = 87
        assertEquals(d("87.00"), a.composite().c());
        assertEquals(Map.of("academic", d("1")), a.composite().weights());
        // P = 1 / (1 + e^{-0.18 × 5}) = 0.7109 → 保
        assertEquals(d("0.7109"), a.estimate().p());
        assertEquals("safety", a.group());
        // 语言未采集：不筛掉，标语言待考
        assertTrue(a.threshold().flags().contains("language_pending"));
        assertTrue(a.threshold().flags().contains("quick_mode"));
        assertNull(a.threshold().items().stream().filter(i -> "prerequisites".equals(i.key())).findFirst().orElse(null));
        // D1 兜底均分 87 > 85 → 补足后可申
        assertEquals("needs_improvement", e.results().get(1).group());
        assertEquals(List.of("gpa"), e.results().get(1).threshold().missing());
    }

    @Test
    void quickModeSkipsUncollectedChecks()
    {
        BackgroundProfile p = student();
        // 快测下德国 APS、课程匹配未采集，不判不达标
        ProgramEvaluation de = one(p, program(1, "DE", "D3"), DE, null, true);
        assertEquals(List.of(), de.threshold().missing());
        assertEquals(Map.of("academic", d("1")), de.composite().weights());
    }

    // ---------------------------------------------------------------- 分组、配置、全部国家、耗时

    @Test
    void groupsAndMissingConfig()
    {
        BackgroundProfile p = student();
        p.setJlptLevel("english_only");
        Evaluation e = SelectionEngine.evaluate(p, List.of(new ProgramFacts(1L, "JP", "D3", "ja", false, null), program(2, "UK", "D1")),
                Map.of("JP", EngineSeeds.config("jp_master.json"), "UK", UK), Map.of(), false);
        assertEquals("excluded", e.results().get(0).group());
        assertEquals(1, e.group("needs_improvement").size());
        assertThrows(IllegalArgumentException.class,
                () -> SelectionEngine.evaluate(p, List.of(program(1, "US", "D3")), Map.of("UK", UK), Map.of(), false));
    }

    @Test
    void everyCountryProducesTiers()
    {
        for (String c : List.of("uk", "au", "sg", "hk", "us", "jp", "kr", "de"))
        {
            JsonNode cfg = EngineSeeds.config(c + "_master.json");
            String country = cfg.path("country").asString();
            List<ProgramFacts> programs = new ArrayList<>();
            for (int i = 1; i <= 5; i++)
            {
                programs.add(program(i, country, "D" + i));
            }
            Evaluation e = SelectionEngine.evaluate(student(), programs, Map.of(country, cfg), Map.of(), false);
            BigDecimal last = BigDecimal.ZERO;
            for (ProgramEvaluation r : e.results())
            {
                BigDecimal p = r.estimate().p();
                assertTrue(p.compareTo(BigDecimal.ZERO) >= 0 && p.compareTo(BigDecimal.ONE) <= 0, c);
                // 难度越低，P 越高
                assertTrue(p.compareTo(last) >= 0, c + " P 应随难度降低而升高");
                last = p;
            }
        }
    }

    @Test
    void oneHundredsOfProgramsWithinOneSecond()
    {
        List<ProgramFacts> programs = new ArrayList<>();
        for (int i = 0; i < 300; i++)
        {
            programs.add(program(i, "UK", "D" + (i % 5 + 1)));
        }
        BackgroundProfile p = student();
        long start = System.nanoTime();
        Evaluation e = SelectionEngine.evaluate(p, programs, Map.of("UK", UK), Map.of(), false);
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertEquals(300, e.results().size());
        assertTrue(ms < 1000, "300 个项目耗时 " + ms + " ms");
    }
}
