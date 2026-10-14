package com.ygb.abroad.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 子分测试：以 M0-06 说明文档的算例为基准（英国草案），覆盖各维度公式、截断、按专业大类覆盖与配置缺失
 *
 * @author ygb
 */
class SubScoreCalculatorTest
{
    private static final JsonNode UK = EngineSeeds.config("uk_master.json").path("scoring");

    /** M0-06 算例学生：211、本专业、均分 85、前 30%、无科研、3 个月相关实习（一般公司）、一项省级竞赛、雅思 6.5、商科、没考 GMAT */
    private static BackgroundProfile exampleStudent()
    {
        NormalizationRules rules = NormalizationRules.parse(EngineSeeds.questionnaire());
        return ProfileNormalizer.normalize(EngineSeeds.answers("{\"undergrad_school\": {\"kind\": \"cn\", \"university_id\": 7},"
                + " \"target_field\": \"management\", \"undergrad_major\": {\"value\": \"management\", \"matched\": true},"
                + " \"gpa\": {\"type\": \"percent\", \"total\": 85}, \"class_rank\": \"top30\", \"exp_research\": {\"has\": \"no\"},"
                + " \"exp_work\": {\"has\": \"yes\", \"count\": 1, \"total_months\": 3, \"company_tier\": \"normal\", \"relevant\": \"yes\"},"
                + " \"exp_awards\": {\"has\": \"yes\", \"count\": 1, \"level\": \"provincial\", \"relevant\": \"yes\"},"
                + " \"english_status\": \"taken\", \"english_score\": {\"type\": \"ielts\", \"total\": 6.5, \"min_sub\": 6.0},"
                + " \"std_test\": {\"type\": \"none\"}}"), rules, id -> id == 7L ? "211" : null, LocalDate.of(2026, 10, 14));
    }

    private static Map<String, BigDecimal> scores(BackgroundProfile p, JsonNode scoring)
    {
        return SubScoreCalculator.compute(p, scoring).scores();
    }

    private static void assertScore(String expected, BigDecimal actual)
    {
        assertEquals(new BigDecimal(expected).setScale(2), actual);
    }

    @Test
    void m006WorkedExample()
    {
        Map<String, BigDecimal> s = scores(exampleStudent(), UK);
        assertEquals(List.of("academic", "major_relevance", "test_scores", "research", "work_experience", "other_soft"), List.copyOf(s.keySet()));
        assertScore("88", s.get("academic"));
        assertScore("100", s.get("major_relevance"));
        assertScore("50", s.get("test_scores"));
        assertScore("20", s.get("research"));
        assertScore("48", s.get("work_experience"));
        assertScore("60", s.get("other_soft"));
    }

    @Test
    void sameInputSameOutput()
    {
        assertEquals(scores(exampleStudent(), UK), scores(exampleStudent(), UK));
    }

    @Test
    void academicClampsAndHandlesUnknownTier()
    {
        BackgroundProfile p = new BackgroundProfile();
        p.setUndergradTier("c9");
        p.setGpaPercent(new BigDecimal("95"));
        p.setClassRank("top10");
        assertScore("100", scores(p, UK).get("academic"));
        p.setUndergradTier("unknown");
        p.setGpaPercent(new BigDecimal("60"));
        p.setClassRank(null);
        // 双非：55 + 3 × (60 − 85) = −20 → 截断为 0
        assertScore("0", scores(p, UK).get("academic"));
        p.setUndergradTier(null);
        p.setGpaPercent(null);
        SubScoreCalculator.Result r = SubScoreCalculator.compute(p, UK);
        assertScore("55", r.scores().get("academic"));
        assertTrue(r.warnings().contains("academic_gpa_missing"));
        assertTrue(r.warnings().contains("major_relevance_missing"));
        assertScore("35", r.scores().get("major_relevance"));
    }

    @Test
    void majorRelevancePrereqPenalty()
    {
        BackgroundProfile p = new BackgroundProfile();
        p.setMajorRelevance("related");
        p.setMissingPrereqCount(5);
        // 70 − 10 × min(5, 3) = 40
        assertScore("40", scores(p, UK).get("major_relevance"));
        p.setMajorRelevance("cross");
        p.setMissingPrereqCount(null);
        assertScore("35", scores(p, UK).get("major_relevance"));
    }

    @Test
    void testScores()
    {
        BackgroundProfile p = new BackgroundProfile();
        p.setStdTestType("gre");
        p.setStdTestTotal(325);
        // 60 + 4 × (325 − 320) = 80
        assertScore("80", scores(p, UK).get("test_scores"));
        p.setStdTestTotal(340);
        assertScore("100", scores(p, UK).get("test_scores"));
        p.setStdTestType("gmat_focus");
        p.setStdTestTotal(655);
        // 60 + 0.4 × (655 − 645) = 64
        assertScore("64", scores(p, UK).get("test_scores"));
        p.setStdTestType("gmat_classic");
        p.setStdTestTotal(700);
        assertScore("68", scores(p, UK).get("test_scores"));
        p.setStdTestTotal(null);
        assertScore("50", scores(p, UK).get("test_scores"));
        p.setStdTestType(null);
        assertScore("50", scores(p, UK).get("test_scores"));
    }

    @Test
    void researchAndAwards()
    {
        BackgroundProfile p = new BackgroundProfile();
        p.setResearchLevel("conference");
        p.setResearchCount(3);
        p.setResearchRelevant("no");
        // max(20, 75 × 0.5) + 5 × 2 = 47.5
        assertScore("47.5", scores(p, UK).get("research"));
        p.setResearchLevel("core_journal");
        p.setResearchRelevant("yes");
        p.setResearchCount(5);
        // 90 + 5 × 4 = 110 → 100
        assertScore("100", scores(p, UK).get("research"));
        p.setResearchRelevant(null);
        p.setResearchCount(null);
        assertScore("90", scores(p, UK).get("research"));
        p.setAwardsLevel("international");
        p.setAwardsCount(2);
        assertScore("95", scores(p, UK).get("other_soft"));
        p.setAwardsLevel("none");
        assertScore("20", scores(p, UK).get("other_soft"));
    }

    @Test
    void workExperienceSaturationAndFieldOverride()
    {
        BackgroundProfile p = new BackgroundProfile();
        p.setWorkMonths(12);
        p.setWorkCompanyTier("top");
        p.setWorkRelevant("yes");
        assertScore("100", scores(p, UK).get("work_experience"));
        p.setWorkMonths(0);
        assertScore("20", scores(p, UK).get("work_experience"));
        p.setWorkMonths(null);
        assertScore("20", scores(p, UK).get("work_experience"));
        // 按专业大类覆盖满分月数：商科 12 个月 → 6 个月只得一半
        ObjectNode scoring = (ObjectNode) UK.deepCopy();
        ((ObjectNode) scoring.path("work_experience").path("saturation_months_by_field")).put("business", 12);
        p.setWorkMonths(6);
        p.setWorkCompanyTier("unknown_tier");
        p.setWorkRelevant(null);
        p.setTargetFieldGroup("business");
        assertScore("60", scores(p, scoring).get("work_experience"));
    }

    @Test
    void courseMatchOnlyWhereApplies()
    {
        JsonNode de = EngineSeeds.config("de_master.json").path("scoring");
        BackgroundProfile p = new BackgroundProfile();
        SubScoreCalculator.Result missing = SubScoreCalculator.compute(p, de);
        assertFalse(missing.scores().containsKey("course_match"));
        assertTrue(missing.warnings().contains("course_match_missing"));
        p.setCourseMatchRatio(new BigDecimal("0.8"));
        assertScore("80", scores(p, de).get("course_match"));
        p.setCourseMatchRatio(new BigDecimal("1.5"));
        assertScore("100", scores(p, de).get("course_match"));
        assertFalse(scores(p, UK).containsKey("course_match"));
    }

    @Test
    void everySeedConfigScoresTheExample()
    {
        for (String file : List.of("uk_master.json", "au_master.json", "sg_master.json", "hk_master.json", "us_master.json",
                "jp_master.json", "kr_master.json", "de_master.json"))
        {
            Map<String, BigDecimal> s = scores(exampleStudent(), EngineSeeds.config(file).path("scoring"));
            s.values().forEach(v -> assertTrue(v.compareTo(BigDecimal.ZERO) >= 0 && v.compareTo(new BigDecimal("100")) <= 0, file));
        }
    }

    @Test
    void missingParameterFailsLoudly()
    {
        ObjectNode scoring = (ObjectNode) UK.deepCopy();
        ((ObjectNode) scoring.path("academic")).remove("gpa_slope");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> scores(exampleStudent(), scoring));
        assertTrue(e.getMessage().contains("scoring.academic.gpa_slope"));
        ObjectNode badTier = (ObjectNode) UK.deepCopy();
        ((ObjectNode) badTier.path("academic").path("tier_base")).remove("211");
        assertThrows(IllegalArgumentException.class, () -> scores(exampleStudent(), badTier));
    }
}
