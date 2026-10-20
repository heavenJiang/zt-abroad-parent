package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 项目页规则抽取（样例取自真实项目页的原句）
 *
 * @author ygb
 */
class AbrProgramRuleExtractorTest
{
    private static final String UCL = String.join("\n", "Title: Computer Science MSc | Study at UCL", "Computer Science MSc", "Course starts:", "September 2027",
            "Full-time", "Duration", "1 calendar year", "Fees (2027/28)", "UK students: £21,500 per year", "International: £44,400 per year",
            "Application deadlines", "All applicants: 31 March 2027", "Entry requirements",
            "A minimum of an upper second-class UK Bachelor’s degree (or international qualification of an equivalent standard).",
            "For full-time and part-time offer holders with a fee status classification of Overseas, a fee deposit will be charged at 10% of the first year fee.");

    private static final String EDINBURGH = String.join("\n", "Title: Data Science MSc - Postgraduate taught programmes | The University of Edinburgh",
            "Year of entry: 2027", "Full-time", "MSc | 1 year | Start date: September 2027", "Entry requirements",
            "IELTS Academic : total 7.0 with at least 6.5 in three components and 6.0 in the remaining component. We do not accept IELTS One Skill Retake to meet our English language requirements.",
            "TOEFL-iBT (including Home Edition) before 21 January 2026 : total 100 with at least 23 in three components and 20 in the remaining component.",
            "TOEFL-iBT (including Home Edition) from 21 January 2026 : total 5.0 with at least 4.5 in three components and 4.0 in the remaining component.");

    @Test
    void ucl()
    {
        assertTrue(AbrProgramRuleExtractor.isProgramPage("Computer Science MSc | Study at UCL", UCL));
        JsonNode o = AbrProgramRuleExtractor.extract("Computer Science MSc | Study at UCL", UCL, "UK");
        assertEquals("Computer Science MSc", o.path("programNameEn").path("value").asString());
        assertEquals("master", o.path("degree").path("value").asString());
        assertEquals(12, o.path("durationMonths").path("value").asInt());
        assertEquals("1 calendar year", o.path("durationMonths").path("quote").asString());
        assertEquals(44400, o.path("tuition").path("value").asInt());
        assertEquals("GBP", o.path("currency").path("value").asString());
        assertEquals("International: £44,400 per year", o.path("tuition").path("quote").asString());
        assertEquals("fall", o.path("intakes").path("value").get(0).asString());
        assertEquals("2027-03-31", o.path("deadlines").path("value").path("rounds").get(0).path("date").asString());
        assertTrue(o.path("ieltsOverall").isMissingNode());
        // 均分等不做规则抽取
        assertTrue(o.path("minGpa").isMissingNode());
    }

    @Test
    void edinburgh()
    {
        JsonNode o = AbrProgramRuleExtractor.extract("Data Science MSc - Postgraduate taught programmes | The University of Edinburgh", EDINBURGH, "UK");
        assertEquals("Data Science MSc", o.path("programNameEn").path("value").asString());
        assertEquals(12, o.path("durationMonths").path("value").asInt());
        assertEquals("7.0", o.path("ieltsOverall").path("value").decimalValue().toPlainString());
        assertEquals("6.0", o.path("ieltsMinSub").path("value").decimalValue().toPlainString());
        assertEquals(100, o.path("otherLanguage").path("value").path("toefl").path("overall").asInt());
        assertEquals(20, o.path("otherLanguage").path("value").path("toefl").path("minSub").asInt());
        assertEquals("fall", o.path("intakes").path("value").get(0).asString());
        // 截止日只认截止语境中的日期（TOEFL 的“21 January 2026”不是截止日）
        assertTrue(o.path("deadlines").isMissingNode());
    }

    @Test
    void checkerAcceptsRuleOutput()
    {
        JsonNode o = AbrProgramRuleExtractor.extract("Computer Science MSc | Study at UCL", UCL, "UK");
        List<java.util.Map<String, Object>> fields = new AbrAiPrefillChecker(null).check(o, UCL);
        long found = fields.stream().filter(f -> "found".equals(f.get("status"))).count();
        assertEquals(7, found, fields.toString());
    }

    @Test
    void pagesAndNames()
    {
        assertFalse(AbrProgramRuleExtractor.isProgramPage("PhD in Computer Science | UCL", "Entry requirements"));
        assertFalse(AbrProgramRuleExtractor.isProgramPage("Masterclass: Policy | UCL", "fees"));
        assertTrue(AbrProgramRuleExtractor.isProgramPage("Security Studies MA, PGDip and PGCert | King's College London", "Entry requirements"));
        assertFalse(AbrProgramRuleExtractor.isProgramPage("MA Public Policy Masterclasses | King's College London", "Entry requirements"));
        assertFalse(AbrProgramRuleExtractor.isProgramPage("News | UCL", "IELTS"));
        assertTrue(AbrProgramRuleExtractor.isProgramPage("Master of Data Science", "Entry requirements"));
        assertFalse(AbrProgramRuleExtractor.isProgramPage("Funding your Master's | Study at UCL", "fees"));
        assertFalse(AbrProgramRuleExtractor.isProgramPage("Master in Science FAQs | KCL", "fees"));
        assertFalse(AbrProgramRuleExtractor.isProgramPage("MA Scholarships | Department of War Studies", "fees"));
        assertFalse(AbrProgramRuleExtractor.isProgramPage("Chemistry Research MPhil/PhD | KCL", "Entry requirements"));
        assertTrue(AbrProgramRuleExtractor.isProgramPage("Social Research Methods MRes | UCL", "Entry requirements"));
        assertNull(AbrProgramRuleExtractor.programName("Massachusetts Institute of Technology"));
        assertEquals("MSc Finance", AbrProgramRuleExtractor.programName("MSc Finance – Imperial Business School"));
        assertEquals("HKD", AbrProgramRuleExtractor.currency("$", "HK"));
        assertEquals(List.of(java.time.LocalDate.of(2027, 1, 15), java.time.LocalDate.of(2027, 3, 1)),
                AbrProgramRuleExtractor.dates("Round 1: 15 January 2027; Round 2: March 1, 2027"));
    }

    @Test
    void ieltsVariants()
    {
        String t = "IELTS: overall score of 6.5 with no less than 6.0 in each subtest";
        JsonNode o = AbrProgramRuleExtractor.extract("MSc Finance | X", "Title: MSc Finance | X\n" + t, "UK");
        assertEquals("6.5", o.path("ieltsOverall").path("value").decimalValue().toPlainString());
        assertEquals("6.0", o.path("ieltsMinSub").path("value").decimalValue().toPlainString());
        JsonNode o2 = AbrProgramRuleExtractor.extract("MSc Finance | X", "Title: MSc Finance | X\nIELTS 7.0 overall", "UK");
        assertTrue(o2.path("ieltsMinSub").isMissingNode());
        String hk = "Title: MSc in Finance | HKU\nTuition fee for non-local students: HK$420,000\nDuration: 1.5 years full-time";
        JsonNode o3 = AbrProgramRuleExtractor.extract("MSc in Finance | HKU", hk, "HK");
        assertEquals("HKD", o3.path("currency").path("value").asString());
        assertEquals(18, o3.path("durationMonths").path("value").asInt());
    }

    @Test
    void uclTwoLineFees()
    {
        String t = String.join("\n", "Title: Advanced Materials Science MSc | UCL", "UK tuition fees (2026/27)", "£16,800", "Overseas tuition fees (2026/27)", "£42,700",
                "Programme starts", "September 2026", "For full-time and part-time offer holders with a fee status classification of Overseas, a fee deposit will be charged at 10% of the first year fee.");
        JsonNode o = AbrProgramRuleExtractor.extract("Advanced Materials Science MSc | UCL", t, "UK");
        assertEquals(42700, o.path("tuition").path("value").asInt());
        assertEquals("£42,700", o.path("tuition").path("quote").asString());
        assertEquals("fall", o.path("intakes").path("value").get(0).asString());
    }

    @Test
    void skip()
    {
        assertTrue(AbrProgramRuleExtractor.skipReason("Applied Data Science (Online Learning) MSc | Edinburgh", 2026).contains("线上"));
        assertTrue(AbrProgramRuleExtractor.skipReason("Architecture (MArch) 2021-2022 | Edinburgh", 2026).contains("往年"));
        assertNull(AbrProgramRuleExtractor.skipReason("Architecture (MArch) 2026-27 | Edinburgh", 2026));
        assertNull(AbrProgramRuleExtractor.skipReason("Data Science MSc | Edinburgh", 2026));
    }

    @Test
    void deadlineTable()
    {
        String t = String.join("\n", "Title: Advanced Chemical Engineering MSc | Edinburgh", "When to apply", "Programme start date", "Application deadline",
                "13 September 2027", "30 August 2027", "We encourage you to apply at least one month prior to entry.",
                "You must submit all supporting documents by the application deadline, or we will be unable to consider your application.");
        JsonNode o = AbrProgramRuleExtractor.extract("Advanced Chemical Engineering MSc | Edinburgh", t, "UK");
        JsonNode rounds = o.path("deadlines").path("value").path("rounds");
        assertEquals(1, rounds.size(), o.toString());
        assertEquals("2027-08-30", rounds.get(0).path("date").asString());
        assertEquals("Application deadline", rounds.get(0).path("name").asString());
        assertEquals("30 August 2027", o.path("deadlines").path("quote").asString());
        String t2 = "Title: MSc X | Y\nRound 1 deadline: 15 January 2027\nRound 2 deadline: 1 March 2027";
        JsonNode r2 = AbrProgramRuleExtractor.extract("MSc X | Y", t2, "UK").path("deadlines").path("value").path("rounds");
        assertEquals(2, r2.size());
        assertEquals("Round 2", r2.get(1).path("name").asString());
    }
}
