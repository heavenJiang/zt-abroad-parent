package com.ygb.abroad.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * 归一化测试：均分（百分制 / 4 分制 / 5 分制 / 其他）、英语（雅思 / 托福 / PTE / 多邻国 / 新托福）换算、院校层次、专业相关度与经历
 *
 * @author ygb
 */
class ProfileNormalizerTest
{
    private static final NormalizationRules RULES = NormalizationRules.parse(EngineSeeds.questionnaire());

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    private static final Function<Long, String> TIERS = id -> Map.of(1L, "c9", 7L, "211").get(id);

    private static BackgroundProfile normalize(String answers)
    {
        return ProfileNormalizer.normalize(EngineSeeds.answers(answers), RULES, TIERS, TODAY);
    }

    private static BigDecimal gpa(String type, String total, String max)
    {
        String m = max == null ? "" : ", \"max\": " + max;
        return normalize("{\"gpa\": {\"type\": \"" + type + "\", \"total\": " + total + m + "}}").getGpaPercent();
    }

    private static BigDecimal ielts(String type, String total)
    {
        return normalize("{\"english_status\": \"taken\", \"english_score\": {\"type\": \"" + type + "\", \"total\": " + total + "}}")
                .getIeltsEquivTotal();
    }

    private static void assertDecimal(String expected, BigDecimal actual)
    {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void gpaConversions()
    {
        assertDecimal("85.0", gpa("percent", "85", null));
        assertDecimal("86.3", gpa("percent", "86.25", null));
        // 4 分制折线：3.0 → 80、3.3 → 84、3.5 在 3.3–3.7 之间插值 = 84 + 5 × 0.5 = 86.5
        assertDecimal("80.0", gpa("gpa4", "3.0", null));
        assertDecimal("86.5", gpa("gpa4", "3.5", null));
        assertDecimal("95.0", gpa("gpa4", "4.0", null));
        assertDecimal("95.0", gpa("gpa4", "4.3", null));
        assertDecimal("0.0", gpa("gpa4", "-1", null));
        // 5 分制：gpa ≥ 1 时 50 + 10 × gpa；gpa < 1 时 60 × gpa
        assertDecimal("90.0", gpa("gpa5", "4.0", null));
        assertDecimal("30.0", gpa("gpa5", "0.5", null));
        // 其他满分：按比例，低置信
        BackgroundProfile other = normalize("{\"gpa\": {\"type\": \"other\", \"total\": 80, \"max\": 90}}");
        assertDecimal("88.9", other.getGpaPercent());
        assertTrue(other.getWarnings().contains("gpa_low_confidence"));
        BackgroundProfile noMax = normalize("{\"gpa\": {\"type\": \"other\", \"total\": 80}}");
        assertNull(noMax.getGpaPercent());
        assertTrue(noMax.getWarnings().contains("gpa_unconverted"));
        assertNull(normalize("{\"gpa\": {\"type\": \"percent\"}}").getGpaPercent());
        assertNull(normalize("{}").getGpaPercent());
    }

    @Test
    void englishConversions()
    {
        assertDecimal("7.0", ielts("ielts", "7"));
        // 托福 iBT：取阈值 ≤ 原始分的最高段（94 → 7.0，102 → 7.5）
        assertDecimal("7.0", ielts("toefl_ibt", "100"));
        assertDecimal("7.5", ielts("toefl_ibt", "102"));
        assertDecimal("6.5", ielts("toefl_ibt", "79"));
        // 低于最低阈值：最低段 − 0.5
        assertDecimal("4.5", ielts("toefl_ibt", "30"));
        assertDecimal("7.0", ielts("pte", "65"));
        assertDecimal("6.5", ielts("duolingo", "120"));
        BackgroundProfile band = normalize("{\"english_status\": \"taken\", \"english_score\": {\"type\": \"toefl_band\", \"total\": 4.5}}");
        assertNull(band.getIeltsEquivTotal());
        assertTrue(band.getWarnings().contains("english_unmapped"));
        BackgroundProfile ieltsSub = normalize("{\"english_status\": \"estimated\", \"english_score\": {\"type\": \"ielts\", \"total\": 6.5, \"min_sub\": 6.0}}");
        assertDecimal("6.0", ieltsSub.getIeltsEquivMinSub());
        assertTrue(ieltsSub.getEnglishEstimated());
        BackgroundProfile toeflSub = normalize("{\"english_status\": \"taken\", \"english_score\": {\"type\": \"toefl_ibt\", \"total\": 100, \"min_sub\": 22}}");
        assertNull(toeflSub.getIeltsEquivMinSub());
        assertDecimal("22", toeflSub.getEnglishMinSub());
        assertFalse(toeflSub.getEnglishEstimated());
        BackgroundProfile none = normalize("{\"english_status\": \"none\"}");
        assertNull(none.getIeltsEquivTotal());
        assertTrue(none.getWarnings().contains("english_none"));
        assertNull(normalize("{\"english_status\": \"taken\", \"english_score\": {\"type\": \"ielts\"}}").getIeltsEquivTotal());
    }

    @Test
    void undergradTier()
    {
        BackgroundProfile c9 = normalize("{\"undergrad_school\": {\"kind\": \"cn\", \"university_id\": 1, \"name\": \"北京大学\"}}");
        assertEquals("c9", c9.getUndergradTier());
        assertEquals(1L, c9.getUndergradUniversityId());
        assertEquals("overseas", normalize("{\"undergrad_school\": {\"kind\": \"overseas\"}}").getUndergradTier());
        BackgroundProfile notFound = normalize("{\"undergrad_school\": {\"kind\": \"not_found\"}}");
        assertEquals("unknown", notFound.getUndergradTier());
        assertTrue(notFound.getWarnings().contains("tier_unknown"));
        BackgroundProfile badId = normalize("{\"undergrad_school\": {\"kind\": \"cn\", \"university_id\": 999}}");
        assertEquals("unknown", badId.getUndergradTier());
        assertNull(badId.getUndergradUniversityId());
        assertNull(normalize("{}").getUndergradTier());
    }

    @Test
    void majorRelevance()
    {
        assertEquals("same", normalize("{\"target_field\": \"finance\", \"undergrad_major\": {\"value\": \"finance\", \"matched\": true}}").getMajorRelevance());
        assertEquals("related", normalize("{\"target_field\": \"finance\", \"undergrad_major\": {\"value\": \"accounting\", \"matched\": true}}").getMajorRelevance());
        BackgroundProfile cross = normalize("{\"target_field\": \"finance\", \"undergrad_major\": {\"value\": \"cs\", \"matched\": true}}");
        assertEquals("cross", cross.getMajorRelevance());
        assertTrue(cross.getWarnings().contains("prereq_unverified"));
        BackgroundProfile self = normalize("{\"target_field\": \"finance\", \"undergrad_major\": {\"value\": \"other\", \"text\": \"金融数学\", \"matched\": false},"
                + " \"major_relevance_self\": \"related\"}");
        assertEquals("related", self.getMajorRelevance());
        assertEquals("self_reported", self.getMajorRelevanceSource());
        assertTrue(self.getWarnings().contains("major_self_reported"));
        // 目标方向“还没想好”：按本科专业推荐
        BackgroundProfile undecided = normalize("{\"target_field\": \"undecided\", \"undergrad_major\": {\"value\": \"cs\", \"matched\": true}}");
        assertEquals("cs", undecided.getTargetField());
        assertEquals("stem", undecided.getTargetFieldGroup());
        assertEquals("same", undecided.getMajorRelevance());
        assertTrue(undecided.getWarnings().contains("target_field_from_major"));
        BackgroundProfile stillUndecided = normalize("{\"target_field\": \"undecided\", \"undergrad_major\": {\"value\": \"other\", \"matched\": false}}");
        assertEquals("undecided", stillUndecided.getTargetFieldGroup());
        assertNull(stillUndecided.getMajorRelevance());
        assertNull(stillUndecided.getMajorRelevanceSource());
    }

    @Test
    void goalExperienceAndPreferences()
    {
        BackgroundProfile p = normalize("{\"target_countries\": [\"UK\", \"HK\"], \"target_degree\": \"master\", \"target_field\": \"management\","
                + " \"intake_year\": \"2027\", \"class_rank\": \"top30\", \"std_test\": {\"type\": \"gmat_focus\", \"total\": 655, \"estimated\": true},"
                + " \"exp_research\": {\"has\": \"no\"}, \"exp_work\": {\"has\": \"yes\", \"count\": 2, \"total_months\": 5, \"fulltime_months\": 0,"
                + " \"company_tier\": \"top\", \"relevant\": \"yes\"}, \"exp_awards\": {\"has\": \"yes\", \"count\": 2, \"level\": \"national\", \"relevant\": \"no\"},"
                + " \"budget_total\": \"b40_60\", \"rank_vs_employment\": \"rank\", \"city_preference\": \"big_city\", \"priority_top2\": [\"rank\", \"cost\"],"
                + " \"jlpt_level\": \"n2\", \"topik_level\": \"none\", \"de_aps_status\": \"passed\"}");
        assertEquals(List.of("UK", "HK"), p.getTargetCountries());
        assertEquals("business", p.getTargetFieldGroup());
        assertEquals(11, p.getMonthsToIntake());
        assertEquals("top30", p.getClassRank());
        assertEquals("gmat_focus", p.getStdTestType());
        assertEquals(655, p.getStdTestTotal());
        assertTrue(p.getStdTestEstimated());
        assertEquals("none", p.getResearchLevel());
        assertEquals(0, p.getResearchCount());
        assertEquals(5, p.getWorkMonths());
        assertEquals("top", p.getWorkCompanyTier());
        assertEquals("national", p.getAwardsLevel());
        assertEquals(2, p.getAwardsCount());
        assertEquals(List.of("rank", "cost"), p.getPriorityTop2());
        assertEquals("n2", p.getJlptLevel());
        assertEquals("passed", p.getDeApsStatus());
        assertEquals(0, p.getMissingPrereqCount());

        BackgroundProfile later = normalize("{\"intake_year\": \"later\", \"class_rank\": \"unknown\", \"exp_work\": {\"has\": \"no\"},"
                + " \"exp_awards\": {\"has\": \"no\"}, \"exp_research\": {\"has\": \"yes\", \"level\": \"conference\", \"relevant\": \"yes\"}}");
        assertNull(later.getMonthsToIntake());
        assertNull(later.getClassRank());
        assertEquals("none", later.getStdTestType());
        assertEquals(0, later.getWorkMonths());
        assertEquals("none", later.getAwardsLevel());
        assertEquals(1, later.getResearchCount());
        assertEquals(0, normalize("{\"intake_year\": \"2026\"}").getMonthsToIntake());
    }
}
