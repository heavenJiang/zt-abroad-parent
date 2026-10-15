package com.ygb.abroad.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.ygb.abroad.engine.HardThresholdChecker.Item;
import com.ygb.abroad.engine.HardThresholdChecker.Result;

/**
 * 硬门槛测试：M0-06 算例（§3.3、§3.5）、项目库优先与兜底、各检查项的通过 / 不达标 / 边界，以及 8 个国家草案的正、反、边界样例
 *
 * @author ygb
 */
class HardThresholdCheckerTest
{
    private static final JsonNode UK = EngineSeeds.config("uk_master.json");

    private static final JsonNode SG = EngineSeeds.config("sg_master.json");

    private static final JsonNode US = EngineSeeds.config("us_master.json");

    private static final JsonNode JP = EngineSeeds.config("jp_master.json");

    private static final JsonNode KR = EngineSeeds.config("kr_master.json");

    private static final JsonNode DE = EngineSeeds.config("de_master.json");

    /** M0-06 算例学生：211、均分 85、本专业、雅思 6.5（小分 6.0） */
    private static BackgroundProfile student()
    {
        BackgroundProfile p = new BackgroundProfile();
        p.setUndergradTier("211");
        p.setGpaPercent(new BigDecimal("85"));
        p.setMajorRelevance("same");
        p.setEnglishStatus("taken");
        p.setEnglishTest("ielts");
        p.setEnglishTotal(new BigDecimal("6.5"));
        p.setEnglishMinSub(new BigDecimal("6.0"));
        p.setEnglishEstimated(false);
        p.setIeltsEquivTotal(new BigDecimal("6.5"));
        p.setIeltsEquivMinSub(new BigDecimal("6.0"));
        p.setMissingPrereqCount(0);
        p.setWorkFulltimeMonths(0);
        return p;
    }

    private static ProgramFacts program(String country, String difficulty)
    {
        return program(country, difficulty, "en", false, null);
    }

    private static ProgramFacts program(String country, String difficulty, String lang, boolean aps, ProgramFacts.Requirement req)
    {
        return new ProgramFacts(1L, country, difficulty, lang, aps, req);
    }

    private static ProgramFacts.Requirement req(Map<String, BigDecimal> minGpa, Set<String> notAccepted, String ielts, String sub,
            String otherLanguage, String prereq, boolean prereqRequired, Integer workYears, String cross)
    {
        return new ProgramFacts.Requirement(minGpa, notAccepted, ielts == null ? null : new BigDecimal(ielts),
                sub == null ? null : new BigDecimal(sub), otherLanguage == null ? null : EngineSeeds.json(otherLanguage), prereq, prereqRequired,
                workYears, cross);
    }

    /** 只有语言要求、其余为空的项目库记录 */
    private static ProgramFacts.Requirement emptyReq()
    {
        return req(Map.of(), Set.of(), null, null, null, null, false, null, null);
    }

    private static Item item(Result r, String key)
    {
        return r.items().stream().filter(i -> key.equals(i.key())).findFirst().orElse(null);
    }

    private static String outcome(Result r, String key)
    {
        Item i = item(r, key);
        return i == null ? null : i.outcome();
    }

    // ---------------------------------------------------------------- M0-06 算例

    @Test
    void m006ProgramsAAndBPassWithDefaults()
    {
        Result a = HardThresholdChecker.check(student(), program("UK", "D2"), UK);
        assertEquals(HardThresholdChecker.PASS, a.status());
        assertEquals(new Item("language_en", "met", "雅思 6.5（小分 6.0）", "雅思 6.5（小分 6.0）", "default"), item(a, "language_en"));
        assertEquals(new Item("gpa", "met", "211 ≥ 84", "85", "default"), item(a, "gpa"));
        assertEquals(List.of("requirement_unverified"), a.flags());
        assertTrue(a.missing().isEmpty());

        Result b = HardThresholdChecker.check(student(), program("UK", "D4"), UK);
        assertEquals(HardThresholdChecker.PASS, b.status());
        assertEquals("211 ≥ 78", item(b, "gpa").required());
    }

    @Test
    void m006D1ProgramNeedsImprovement()
    {
        Result r = HardThresholdChecker.check(student(), program("UK", "D1"), UK);
        assertEquals("needs_improvement", r.status());
        assertEquals(List.of("gpa"), r.missing());
        assertEquals("211 ≥ 87", item(r, "gpa").required());
        // 雅思差 0.5，英国允许课前语言：标「可读语言班」而不判不达标
        assertEquals("pre_sessional", outcome(r, "language_en"));
        assertTrue(r.flags().contains("pre_sessional"));

        // 新加坡没有课前语言规则：同样差 0.5 判不达标
        Result sg = HardThresholdChecker.check(student(), program("SG", "D1"), SG);
        assertEquals("not_met", outcome(sg, "language_en"));
        assertTrue(sg.missing().contains("language_en"));
    }

    @Test
    void preSessionalOnlyWithinGap()
    {
        BackgroundProfile p = student();
        p.setIeltsEquivTotal(new BigDecimal("6.0"));
        // 差 1.0 > 0.5：不达标
        assertEquals("not_met", outcome(HardThresholdChecker.check(p, program("UK", "D1"), UK), "language_en"));
        // 小分差同样计入
        p = student();
        p.setIeltsEquivMinSub(new BigDecimal("5.5"));
        assertEquals("not_met", outcome(HardThresholdChecker.check(p, program("UK", "D1"), UK), "language_en"));
    }

    @Test
    void sameInputSameOutput()
    {
        assertEquals(HardThresholdChecker.check(student(), program("UK", "D1"), UK),
                HardThresholdChecker.check(student(), program("UK", "D1"), UK));
    }

    // ---------------------------------------------------------------- 均分与层次

    @Test
    void gpaBoundary()
    {
        BackgroundProfile p = student();
        p.setGpaPercent(new BigDecimal("84"));
        assertEquals("met", outcome(HardThresholdChecker.check(p, program("UK", "D2"), UK), "gpa"));
        p.setGpaPercent(new BigDecimal("83.9"));
        Result r = HardThresholdChecker.check(p, program("UK", "D2"), UK);
        assertEquals("not_met", outcome(r, "gpa"));
        assertEquals("needs_improvement", r.status());
    }

    @Test
    void programRequirementOverridesDefaults()
    {
        ProgramFacts.Requirement req = req(Map.of("211", new BigDecimal("80")), Set.of(), "6.0", "5.5", null, null, false, null, "2");
        Result r = HardThresholdChecker.check(student(), program("UK", "D1", "en", false, req), UK);
        assertEquals(HardThresholdChecker.PASS, r.status());
        assertEquals("program", item(r, "gpa").source());
        assertEquals("program", item(r, "language_en").source());
        assertFalse(r.flags().contains("requirement_unverified"));

        // 项目库没写学生所在层次：该项回到兜底值并标待核实
        req = req(Map.of("985", new BigDecimal("80")), Set.of(), "6.0", null, null, null, false, null, null);
        r = HardThresholdChecker.check(student(), program("UK", "D1", "en", false, req), UK);
        assertEquals(new Item("gpa", "not_met", "211 ≥ 87", "85", "default"), item(r, "gpa"));
        assertEquals("雅思 6.0", item(r, "language_en").required());
        assertTrue(r.flags().contains("requirement_unverified"));
    }

    @Test
    void tierNotAcceptedAndUnknownTier()
    {
        BackgroundProfile p = student();
        p.setUndergradTier("unknown");
        p.setGpaPercent(new BigDecimal("95"));
        ProgramFacts.Requirement req = req(Map.of(), Set.of("non_key", "double_first_class"), "6.0", null, null, null, false, null, null);
        Result r = HardThresholdChecker.check(p, program("UK", "D3", "en", false, req), UK);
        assertEquals(new Item("tier_accepted", "not_met", "不接收：double_first_class、non_key", "non_key", "program"), item(r, "tier_accepted"));
        assertTrue(r.flags().contains("tier_unknown"));
        assertEquals("needs_improvement", r.status());

        p.setUndergradTier("985");
        assertEquals("met", outcome(HardThresholdChecker.check(p, program("UK", "D3", "en", false, req), UK), "tier_accepted"));
        p.setUndergradTier(null);
        assertTrue(HardThresholdChecker.check(p, program("UK", "D3"), UK).flags().contains("tier_unknown"));
    }

    @Test
    void tierNotAcceptedFromDefaults()
    {
        ObjectNode cfg = (ObjectNode) UK.deepCopy();
        ((ObjectNode) cfg.path("hard_thresholds").path("min_gpa_by_undergrad_tier").path("undergrad_tiers_not_accepted_by_difficulty"))
                .set("D1", EngineSeeds.json("[\"non_key\"]"));
        BackgroundProfile p = student();
        p.setUndergradTier("non_key");
        p.setGpaPercent(new BigDecimal("99"));
        Result r = HardThresholdChecker.check(p, program("UK", "D1"), cfg);
        assertEquals("default", item(r, "tier_accepted").source());
        assertEquals("not_met", outcome(r, "tier_accepted"));
        assertTrue(r.flags().contains("requirement_unverified"));
    }

    @Test
    void gpaMissingIsUncheckedNotFiltered()
    {
        BackgroundProfile p = student();
        p.setGpaPercent(null);
        Result r = HardThresholdChecker.check(p, program("UK", "D1"), UK);
        assertEquals(new Item("gpa", "unchecked", "211 ≥ 87", "未填写", "default"), item(r, "gpa"));
        assertTrue(r.flags().contains("gpa_missing"));
        assertFalse(r.missing().contains("gpa"));
    }

    @Test
    void noDifficultyNoRequirementIsUnchecked()
    {
        Result r = HardThresholdChecker.check(student(), program("UK", null), UK);
        assertEquals(HardThresholdChecker.PASS, r.status());
        assertEquals("unchecked", outcome(r, "language_en"));
        assertEquals("unchecked", outcome(r, "gpa"));
        assertNull(item(r, "gpa").required());
        assertTrue(r.flags().contains("requirement_missing"));
        assertFalse(r.flags().contains("requirement_unverified"));
        assertEquals("unchecked", outcome(HardThresholdChecker.check(student(), program("UK", " "), UK), "gpa"));
        // 难度档在配置中不存在
        assertEquals("unchecked", outcome(HardThresholdChecker.check(student(), program("UK", "D9"), UK), "gpa"));
    }

    // ---------------------------------------------------------------- 英语

    @Test
    void englishPendingAndEstimated()
    {
        BackgroundProfile p = student();
        p.setEnglishStatus("none");
        p.setIeltsEquivTotal(null);
        p.setIeltsEquivMinSub(null);
        Result r = HardThresholdChecker.check(p, program("UK", "D1"), UK);
        assertEquals(new Item("language_en", "unchecked", "雅思 7.0（小分 6.5）", "未考", "default"), item(r, "language_en"));
        assertTrue(r.flags().contains("language_pending"));
        p.setEnglishStatus(null);
        assertTrue(HardThresholdChecker.check(p, program("UK", "D1"), UK).flags().contains("language_pending"));

        p = student();
        p.setEnglishStatus("estimated");
        p.setEnglishEstimated(true);
        Result e = HardThresholdChecker.check(p, program("UK", "D2"), UK);
        assertEquals("met", outcome(e, "language_en"));
        assertTrue(e.flags().contains("language_estimated"));
    }

    @Test
    void englishSameTestComparedOnRawScale()
    {
        BackgroundProfile p = student();
        p.setEnglishTest("toefl_ibt");
        p.setEnglishTotal(new BigDecimal("98"));
        p.setEnglishMinSub(new BigDecimal("20"));
        p.setIeltsEquivTotal(new BigDecimal("7.0"));
        p.setIeltsEquivMinSub(null);
        ProgramFacts.Requirement req = req(Map.of(), Set.of(), "6.5", "6.0", "{\"toefl\":{\"overall\":100,\"minSub\":22}}", null, false, null, null);
        Result r = HardThresholdChecker.check(p, program("UK", "D2", "en", false, req), UK);
        // 原始分比较，且不适用课前语言
        assertEquals(new Item("language_en", "not_met", "托福 100（小分 22）", "托福 98（小分 20）", "program"), item(r, "language_en"));

        p.setEnglishTotal(new BigDecimal("100"));
        p.setEnglishMinSub(new BigDecimal("22"));
        assertEquals("met", outcome(HardThresholdChecker.check(p, program("UK", "D2", "en", false, req), UK), "language_en"));

        // 项目只写了雅思：用雅思等效分，托福没有小分换算 → 只比总分并提示
        req = req(Map.of(), Set.of(), "6.5", "6.0", "{\"pte\":{\"overall\":58}}", null, false, null, null);
        Result r2 = HardThresholdChecker.check(p, program("UK", "D2", "en", false, req), UK);
        assertEquals("met", outcome(r2, "language_en"));
        assertTrue(r2.flags().contains("sub_score_unchecked"));
        assertEquals("雅思 7.0", item(r2, "language_en").actual());
    }

    @Test
    void englishUnconvertedScore()
    {
        BackgroundProfile p = student();
        p.setEnglishTest("toefl_band");
        p.setIeltsEquivTotal(null);
        p.setIeltsEquivMinSub(null);
        Result r = HardThresholdChecker.check(p, program("UK", "D2"), UK);
        assertEquals(new Item("language_en", "unchecked", "雅思 6.5（小分 6.0）", "无雅思等效分", "default"), item(r, "language_en"));
        assertTrue(r.flags().contains("language_unconverted"));
    }

    @Test
    void englishOverallOnlyOrSubOnlyRequirement()
    {
        ProgramFacts.Requirement subOnly = req(Map.of(), Set.of(), null, "6.5", null, null, false, null, null);
        Result r = HardThresholdChecker.check(student(), program("SG", "D5", "en", false, subOnly), SG);
        assertEquals(new Item("language_en", "not_met", "雅思（小分 6.5）", "雅思 6.5（小分 6.0）", "program"), item(r, "language_en"));
        // 项目只给了总分以外的考试且非学生所考：按兜底值
        ProgramFacts.Requirement other = req(Map.of(), Set.of(), null, null, "{\"toefl\":{\"overall\":90}}", null, false, null, null);
        assertEquals("default", item(HardThresholdChecker.check(student(), program("SG", "D5", "en", false, other), SG), "language_en").source());
        // 项目写了学生所考考试但没有总分：不按原始分比较
        BackgroundProfile p = student();
        p.setEnglishTest("pte");
        ProgramFacts.Requirement noOverall = req(Map.of(), Set.of(), "6.0", null, "{\"pte\":{\"minSub\":50}}", null, false, null, null);
        assertEquals("雅思 6.0", item(HardThresholdChecker.check(p, program("SG", "D5", "en", false, noOverall), SG), "language_en").required());
    }

    // ---------------------------------------------------------------- 先修课、工作年限、跨专业

    @Test
    void prerequisites()
    {
        ProgramFacts.Requirement hard = req(Map.of(), Set.of(), "6.0", null, null, "微积分、线性代数", true, null, null);
        ProgramFacts.Requirement soft = req(Map.of(), Set.of(), "6.0", null, null, "统计学", false, null, null);
        BackgroundProfile p = student();
        assertEquals(new Item("prerequisites", "met", "微积分、线性代数（硬性）", "本专业", "program"),
                item(HardThresholdChecker.check(p, program("UK", "D3", "en", false, hard), UK), "prerequisites"));

        p.setMissingPrereqCount(1);
        Result r = HardThresholdChecker.check(p, program("UK", "D3", "en", false, hard), UK);
        assertEquals(new Item("prerequisites", "not_met", "微积分、线性代数（硬性）", "缺 1 门", "program"), item(r, "prerequisites"));
        assertEquals(List.of("prerequisites"), r.missing());
        Result s = HardThresholdChecker.check(p, program("UK", "D3", "en", false, soft), UK);
        assertEquals("risk", outcome(s, "prerequisites"));
        assertTrue(s.flags().contains("prereq_risk"));
        assertEquals(HardThresholdChecker.PASS, s.status());

        // 非本专业、问卷未采集课程：风险项
        p = student();
        p.setMajorRelevance("related");
        p.setMissingPrereqCount(null);
        Result v = HardThresholdChecker.check(p, program("UK", "D3", "en", false, hard), UK);
        assertEquals("risk", outcome(v, "prerequisites"));
        assertTrue(v.flags().contains("prereq_to_verify"));

        // 项目没写先修课：不检查
        assertNull(item(HardThresholdChecker.check(p, program("UK", "D3", "en", false, emptyReq()), UK), "prerequisites"));
        ProgramFacts.Requirement blank = req(Map.of(), Set.of(), "6.0", null, null, " ", true, null, null);
        assertNull(item(HardThresholdChecker.check(p, program("UK", "D3", "en", false, blank), UK), "prerequisites"));
    }

    @Test
    void workYearsBoundary()
    {
        ProgramFacts.Requirement req = req(Map.of(), Set.of(), "6.0", null, null, null, false, 2, null);
        BackgroundProfile p = student();
        p.setWorkFulltimeMonths(24);
        assertEquals(new Item("work_years", "met", "2 年", "全职 24 个月", "program"),
                item(HardThresholdChecker.check(p, program("US", "D3", "en", false, req), US), "work_years"));
        p.setWorkFulltimeMonths(23);
        assertEquals("not_met", outcome(HardThresholdChecker.check(p, program("US", "D3", "en", false, req), US), "work_years"));
        p.setWorkFulltimeMonths(null);
        assertEquals("全职 0 个月", item(HardThresholdChecker.check(p, program("US", "D3", "en", false, req), US), "work_years").actual());
        ProgramFacts.Requirement zero = req(Map.of(), Set.of(), "6.0", null, null, null, false, 0, null);
        assertNull(item(HardThresholdChecker.check(p, program("US", "D3", "en", false, zero), US), "work_years"));
    }

    @Test
    void crossMajor()
    {
        BackgroundProfile p = student();
        p.setMajorRelevance("cross");
        assertEquals("not_met", outcome(HardThresholdChecker.check(p, program("UK", "D3", "en", false,
                req(Map.of(), Set.of(), "6.0", null, null, null, false, null, "0")), UK), "cross_major"));
        assertEquals("met", outcome(HardThresholdChecker.check(p, program("UK", "D3", "en", false,
                req(Map.of(), Set.of(), "6.0", null, null, null, false, null, "1")), UK), "cross_major"));
        Result unknown = HardThresholdChecker.check(p, program("UK", "D3", "en", false,
                req(Map.of(), Set.of(), "6.0", null, null, null, false, null, "2")), UK);
        assertEquals("risk", outcome(unknown, "cross_major"));
        assertTrue(unknown.flags().contains("cross_major_unknown"));
        Item noRecord = item(HardThresholdChecker.check(p, program("UK", "D3"), UK), "cross_major");
        assertEquals(new Item("cross_major", "risk", null, "跨专业", "none"), noRecord);
        // 本专业不检查
        assertNull(item(HardThresholdChecker.check(student(), program("UK", "D3", "en", false,
                req(Map.of(), Set.of(), "6.0", null, null, null, false, null, "0")), UK), "cross_major"));
    }

    // ---------------------------------------------------------------- 日本、韩国：按授课语言区分

    @Test
    void japaneseTaughtProgram()
    {
        BackgroundProfile p = student();
        p.setJlptLevel("N2");
        Result r = HardThresholdChecker.check(p, program("JP", "D3", "ja", false, null), JP);
        assertEquals(new Item("language_ja", "met", "N2", "N2", "default"), item(r, "language_ja"));
        assertNull(item(r, "language_en"));
        p.setJlptLevel("N3");
        assertEquals("not_met", outcome(HardThresholdChecker.check(p, program("JP", "D3", "ja", false, null), JP), "language_ja"));
        p.setJlptLevel("N4_N5");
        assertEquals("N4", item(HardThresholdChecker.check(p, program("JP", "D3", "ja", false, null), JP), "language_ja").actual());
        p.setJlptLevel("none_jp_course");
        assertEquals(new Item("language_ja", "not_met", "N2", "未考", "default"),
                item(HardThresholdChecker.check(p, program("JP", "D3", "ja", false, null), JP), "language_ja"));
        p.setJlptLevel("english_only");
        Result ex = HardThresholdChecker.check(p, program("JP", "D3", "ja", false, null), JP);
        assertEquals(HardThresholdChecker.EXCLUDED, ex.status());
        p.setJlptLevel(null);
        Result pending = HardThresholdChecker.check(p, program("JP", "D3", "ja", false, null), JP);
        assertEquals("unchecked", outcome(pending, "language_ja"));
        assertTrue(pending.flags().contains("language_pending"));

        // 项目库写了 N1：以项目库为准；写法无法识别时回到兜底
        p.setJlptLevel("N2");
        ProgramFacts.Requirement n1 = req(Map.of(), Set.of(), null, null, "{\"jlpt\":{\"level\":\"N1\"}}", null, false, null, null);
        assertEquals(new Item("language_ja", "not_met", "N1", "N2", "program"),
                item(HardThresholdChecker.check(p, program("JP", "D3", "ja", false, n1), JP), "language_ja"));
        ProgramFacts.Requirement odd = req(Map.of(), Set.of(), null, null, "{\"jlpt\":{\"level\":\"高级\"}}", null, false, null, null);
        assertEquals("default", item(HardThresholdChecker.check(p, program("JP", "D3", "ja", false, odd), JP), "language_ja").source());

        // 英语授课的日本项目只看英语，学生只考虑英语授课不受影响
        p.setJlptLevel("english_only");
        Result en = HardThresholdChecker.check(p, program("JP", "D3"), JP);
        assertEquals(HardThresholdChecker.PASS, en.status());
        assertEquals("met", outcome(en, "language_en"));
    }

    @Test
    void koreanTaughtProgram()
    {
        BackgroundProfile p = student();
        p.setTopikLevel("4");
        assertEquals(new Item("language_ko", "met", "TOPIK 4 级", "TOPIK 4 级", "default"),
                item(HardThresholdChecker.check(p, program("KR", "D3", "ko", false, null), KR), "language_ko"));
        p.setTopikLevel("3");
        assertEquals("not_met", outcome(HardThresholdChecker.check(p, program("KR", "D3", "ko", false, null), KR), "language_ko"));
        p.setTopikLevel("1_2");
        assertEquals("TOPIK 2 级", item(HardThresholdChecker.check(p, program("KR", "D5", "ko", false, null), KR), "language_ko").actual());
        p.setTopikLevel("none_kr_course");
        assertEquals("未考", item(HardThresholdChecker.check(p, program("KR", "D5", "ko", false, null), KR), "language_ko").actual());
        p.setTopikLevel("english_only");
        assertEquals(HardThresholdChecker.EXCLUDED, HardThresholdChecker.check(p, program("KR", "D5", "ko", false, null), KR).status());

        p.setTopikLevel("5");
        ProgramFacts.Requirement six = req(Map.of(), Set.of(), null, null, "{\"topik\":{\"level\":\"6级\"}}", null, false, null, null);
        assertEquals(new Item("language_ko", "not_met", "TOPIK 6 级", "TOPIK 5 级", "program"),
                item(HardThresholdChecker.check(p, program("KR", "D5", "ko", false, six), KR), "language_ko"));

        // 未定难度档且项目库没写：未判定
        Result none = HardThresholdChecker.check(p, program("KR", null, "ko", false, null), KR);
        assertEquals(new Item("language_ko", "unchecked", null, "TOPIK 5 级", "none"), item(none, "language_ko"));
        assertTrue(none.flags().contains("requirement_missing"));
    }

    @Test
    void otherTeachingLanguages()
    {
        assertNull(item(HardThresholdChecker.check(student(), program("UK", "D3", "zh", false, null), UK), "language_en"));
        // 缺省按英语
        assertEquals("met", outcome(HardThresholdChecker.check(student(), program("UK", "D3", null, false, null), UK), "language_en"));
        assertEquals("met", outcome(HardThresholdChecker.check(student(), program("UK", "D3", "", false, null), UK), "language_en"));
    }

    // ---------------------------------------------------------------- 德国：德语、APS、课程匹配

    private static BackgroundProfile germanyReady()
    {
        BackgroundProfile p = student();
        p.setDeApsStatus("passed");
        p.setCourseMatchRatio(new BigDecimal("0.5"));
        return p;
    }

    @Test
    void germanyAps()
    {
        BackgroundProfile p = germanyReady();
        assertEquals(new Item("aps", "met", "需通过 APS", "已通过", "default"), item(HardThresholdChecker.check(p, program("DE", "D3"), DE), "aps"));
        p.setDeApsStatus("in_progress");
        Result r = HardThresholdChecker.check(p, program("DE", "D3", "en", true, null), DE);
        assertEquals(new Item("aps", "met", "需通过 APS", "审核中", "program"), item(r, "aps"));
        assertTrue(r.flags().contains("aps_in_progress"));
        p.setDeApsStatus("not_started");
        Result ns = HardThresholdChecker.check(p, program("DE", "D3"), DE);
        assertEquals(List.of("aps"), ns.missing());
        assertEquals("needs_improvement", ns.status());
        p.setDeApsStatus("unknown");
        Result u = HardThresholdChecker.check(p, program("DE", "D3"), DE);
        assertEquals("not_met", outcome(u, "aps"));
        assertTrue(u.flags().contains("aps_unknown"));
        p.setDeApsStatus(null);
        assertEquals("not_met", outcome(HardThresholdChecker.check(p, program("DE", "D3"), DE), "aps"));

        // 海本学生、项目未标 APS：不检查；项目标了仍检查
        p.setUndergradTier("overseas");
        assertNull(item(HardThresholdChecker.check(p, program("DE", "D3"), DE), "aps"));
        assertEquals("program", item(HardThresholdChecker.check(p, program("DE", "D3", "en", true, null), DE), "aps").source());
        // 其他国家没有 APS 规则
        assertNull(item(HardThresholdChecker.check(p, program("UK", "D3", "en", true, null), UK), "aps"));
    }

    @Test
    void germanyCourseMatch()
    {
        BackgroundProfile p = germanyReady();
        Result r = HardThresholdChecker.check(p, program("DE", "D3"), DE);
        assertEquals(new Item("course_match", "met", "课程匹配 ≥ 0.5", "0.5", "default"), item(r, "course_match"));
        p.setCourseMatchRatio(new BigDecimal("0.49"));
        assertEquals("not_met", outcome(HardThresholdChecker.check(p, program("DE", "D3"), DE), "course_match"));
        p.setCourseMatchRatio(null);
        Result m = HardThresholdChecker.check(p, program("DE", "D3"), DE);
        assertEquals("unchecked", outcome(m, "course_match"));
        assertTrue(m.flags().contains("course_match_missing"));
    }

    @Test
    void germanTaughtProgram()
    {
        ProgramFacts.Requirement req = req(Map.of(), Set.of(), null, null, "{\"testdaf\":{\"level\":\"TDN4\"},\"dsh\":{\"note\":\"DSH-2\"}}",
                null, false, null, null);
        Result r = HardThresholdChecker.check(germanyReady(), program("DE", "D3", "de", false, req), DE);
        assertEquals(new Item("language_de", "unchecked", "TESTDAF TDN4、DSH", "问卷未采集", "program"), item(r, "language_de"));
        assertTrue(r.flags().contains("german_to_verify"));
        Item none = item(HardThresholdChecker.check(germanyReady(), program("DE", "D3", "de", false, null), DE), "language_de");
        assertEquals(new Item("language_de", "unchecked", null, "问卷未采集", "none"), none);
        assertNull(item(HardThresholdChecker.check(germanyReady(), program("DE", "D3", "de", false, emptyReq()), DE), "language_de").required());
    }

    // ---------------------------------------------------------------- 8 个国家：正、反、边界

    /**
     * 每个国家：D3 兜底值上刚好达标（边界）→ 通过；均分低 0.1 → 补足后可申；雅思低于兜底（超出课前语言范围）→ 补足后可申
     */
    @Test
    void everyCountryPositiveNegativeBoundary()
    {
        for (String c : List.of("uk", "au", "sg", "hk", "us", "jp", "kr", "de"))
        {
            JsonNode cfg = EngineSeeds.config(c + "_master.json");
            String country = cfg.path("country").asString();
            JsonNode lang = cfg.path("hard_thresholds").path("language").path("defaults_by_difficulty").path("D3");
            BigDecimal minGpa = cfg.path("hard_thresholds").path("min_gpa_by_undergrad_tier").path("defaults_by_difficulty").path("D3")
                    .path("211").decimalValue();

            BackgroundProfile edge = germanyReady();
            edge.setGpaPercent(minGpa);
            edge.setIeltsEquivTotal(lang.path("overall").decimalValue());
            edge.setIeltsEquivMinSub(lang.path("min_sub").decimalValue());
            Result pass = HardThresholdChecker.check(edge, program(country, "D3"), cfg);
            assertEquals(HardThresholdChecker.PASS, pass.status(), c + " 边界应通过");
            assertEquals(List.of("requirement_unverified"), pass.flags(), c);

            BackgroundProfile lowGpa = germanyReady();
            lowGpa.setGpaPercent(minGpa.subtract(new BigDecimal("0.1")));
            lowGpa.setIeltsEquivTotal(new BigDecimal("9"));
            lowGpa.setIeltsEquivMinSub(new BigDecimal("9"));
            Result fail = HardThresholdChecker.check(lowGpa, program(country, "D3"), cfg);
            assertEquals("needs_improvement", fail.status(), c + " 均分不够应补足后可申");
            assertEquals(List.of("gpa"), fail.missing(), c);

            BackgroundProfile lowLang = germanyReady();
            lowLang.setGpaPercent(new BigDecimal("99"));
            lowLang.setIeltsEquivTotal(lang.path("overall").decimalValue().subtract(BigDecimal.ONE));
            Result lf = HardThresholdChecker.check(lowLang, program(country, "D3"), cfg);
            assertEquals(List.of("language_en"), lf.missing(), c + " 雅思差 1 分应不达标");
        }
    }

    // ---------------------------------------------------------------- 批量筛选与配置

    @Test
    void screenGroupsPrograms()
    {
        BackgroundProfile p = student();
        p.setJlptLevel("english_only");
        Map<String, JsonNode> configs = new LinkedHashMap<>();
        configs.put("UK", UK);
        configs.put("JP", JP);
        ProgramFacts a = new ProgramFacts(1L, "UK", "D2", "en", false, null);
        ProgramFacts d1 = new ProgramFacts(2L, "UK", "D1", "en", false, null);
        ProgramFacts jp = new ProgramFacts(3L, "JP", "D3", "ja", false, null);
        ProgramFacts b = new ProgramFacts(4L, "UK", "D4", "en", false, null);
        HardThresholdChecker.Screening s = HardThresholdChecker.screen(p, List.of(a, d1, jp, b), configs);
        assertEquals(List.of(1L, 4L), s.passed().stream().map(x -> x.program().programId()).toList());
        assertEquals(List.of(2L), s.needsImprovement().stream().map(x -> x.program().programId()).toList());
        assertEquals(List.of(3L), s.excluded().stream().map(x -> x.program().programId()).toList());

        ProgramFacts us = new ProgramFacts(5L, "US", "D3", "en", false, null);
        assertThrows(IllegalArgumentException.class, () -> HardThresholdChecker.screen(p, List.of(us), configs));
    }

    @Test
    void configWithoutOnFailIsRejected()
    {
        ObjectNode cfg = (ObjectNode) UK.deepCopy();
        ((ObjectNode) cfg.path("hard_thresholds")).remove("on_fail");
        assertThrows(IllegalArgumentException.class, () -> HardThresholdChecker.check(student(), program("UK", "D2"), cfg));
        ((ObjectNode) cfg.path("hard_thresholds")).put("on_fail", " ");
        assertThrows(IllegalArgumentException.class, () -> HardThresholdChecker.check(student(), program("UK", "D2"), cfg));
        // 关闭“用兜底值打待核实”
        ObjectNode quiet = (ObjectNode) UK.deepCopy();
        ((ObjectNode) quiet.path("hard_thresholds")).put("unverified_flag_when_default_used", false);
        assertTrue(HardThresholdChecker.check(student(), program("UK", "D2"), quiet).flags().isEmpty());
    }

    @Test
    void levelParsing()
    {
        assertNull(HardThresholdChecker.jlptOf(null));
        assertNull(HardThresholdChecker.jlptOf("none_jp_course"));
        assertEquals(5, HardThresholdChecker.jlptOf("n1"));
        assertNull(HardThresholdChecker.topikOf(null));
        assertNull(HardThresholdChecker.topikOf("none_kr_course"));
        assertEquals(3, HardThresholdChecker.topikOf("TOPIK 3级"));
    }
}
