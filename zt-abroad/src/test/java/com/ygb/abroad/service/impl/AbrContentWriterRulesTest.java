package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.ai.AiFilterHit;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.growth.compliance.ComplianceChecker;
import tools.jackson.databind.json.JsonMapper;

/**
 * 写稿规则测试：院校库事实（核对过 / 过期 / 未录入）、截止轮次、文末引导、正文拼装、拦截说明、话题
 *
 * @author ygb
 */
class AbrContentWriterRulesTest
{
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    private static AbrProgram program(LocalDate verified)
    {
        AbrProgram p = new AbrProgram();
        p.setProgramId(5L);
        p.setSchoolName("Eastvale University");
        p.setProgramName("MSc Management");
        p.setTuition(new BigDecimal("35000"));
        p.setCurrency("gbp");
        p.setDurationMonths(12);
        p.setVerifiedTime(verified == null ? null : Date.from(verified.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));
        return p;
    }

    @Test
    @SuppressWarnings("unchecked")
    void programFactsFreshStaleAndEmpty()
    {
        AbrRequirement r = new AbrRequirement();
        r.setIeltsOverall(new BigDecimal("7"));
        r.setIeltsMinSub(new BigDecimal("6.5"));
        r.setDeadlines("{\"rolling\":false,\"rounds\":[{\"name\":\"Round 1\",\"date\":\"2026-11-15\"},{\"name\":\"\",\"date\":\"2027-01-31\"},{\"date\":\"bad\"}]}");
        Map<String, Object> m = AbrContentWriterRules.programFacts(program(TODAY.minusDays(4)), r, "英国", TODAY);
        Map<String, String> f = (Map<String, String>) m.get("facts");
        assertEquals("雅思 7.0（小分 6.5）", f.get("雅思"));
        assertEquals("2026-11-15（Round 1）；2027-01-31", f.get("截止"));
        assertEquals("35,000 英镑 / 年", f.get("学费"));
        assertEquals("12 个月", f.get("学制"));
        assertEquals("2026-10-15", m.get("verified"));
        assertEquals(AbrContentWriterRules.UNVERIFIED, AbrContentWriterRules.programFacts(program(TODAY.minusDays(91)), r, "英国", TODAY).get("facts"));
        assertEquals(AbrContentWriterRules.UNVERIFIED, AbrContentWriterRules.programFacts(program(null), r, "英国", TODAY).get("facts"));
        AbrProgram bare = program(TODAY);
        bare.setTuition(null);
        bare.setDurationMonths(null);
        assertEquals(AbrContentWriterRules.UNVERIFIED, AbrContentWriterRules.programFacts(bare, null, "英国", TODAY).get("facts"), "核对过但没录入");
    }

    @Test
    void deadlinesRolling()
    {
        assertEquals("滚动录取", AbrContentWriterRules.deadlines("{\"rolling\":true,\"rounds\":[]}"));
        assertNull(AbrContentWriterRules.deadlines("{\"rounds\":[]}"));
        assertNull(AbrContentWriterRules.deadlines("not json"));
        assertNull(AbrContentWriterRules.deadlines(null));
    }

    @Test
    void closingAndAssemble()
    {
        String closing = AbrContentWriterRules.closing("私信发送口令「{codeword}」，用「{hook}」测一下。", "2318", "现在还能申");
        assertEquals("私信发送口令「2318」，用「现在还能申」测一下。", closing);
        assertEquals("", AbrContentWriterRules.closing(null, "1", "x"));
        assertTrue(AbrContentWriterRules.closing("用「{hook}」测一下", "1", null).contains("30 秒快测"));
        String body = AbrContentWriterRules.assemble("  正文第一段\n本文由 AI 辅助生成  ", closing, "本文由 AI 辅助生成");
        assertEquals("正文第一段\n\n私信发送口令「2318」，用「现在还能申」测一下。\n\n本文由 AI 辅助生成", body);
        assertEquals("正文", AbrContentWriterRules.assemble("正文", "", ""));
    }

    @Test
    void feedbackAndHashtags() throws Exception
    {
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("category", "admission_promise");
        hit.put("matched", "保录");
        hit.put("suggestion", "删去录取承诺");
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("category", "fact");
        fact.put("matched", null);
        fact.put("detail", "“2026-11-20”与引用的院校库记录中的截止日不一致");
        fact.put("suggestion", "数字只能取自院校库");
        ComplianceChecker.Result r = new ComplianceChecker.Result(false, List.of(hit, fact), List.of(), List.of(), List.of(), 1);
        List<String> fb = AbrContentWriterRules.feedback(r);
        assertEquals("admission_promise：命中「保录」（删去录取承诺）", fb.get(0));
        assertEquals("fact：“2026-11-20”与引用的院校库记录中的截止日不一致（数字只能取自院校库）", fb.get(1));
        assertEquals(List.of("英国硕士", "申请季"),
                AbrContentWriterRules.hashtags(JsonMapper.builder().build().readTree("[\"#英国硕士\",\" 申请季 \",\"英国硕士\",\"\",1]")));
    }

    @Test
    void filterHitsToFeedbackAndFlags()
    {
        List<AiFilterHit> hits = List.of(new AiFilterHit("admission_promise", "录取承诺", "保录", "/title"));
        assertEquals("录取承诺：title 中出现「保录」（删去或改写）", AbrContentWriterRules.filterFeedback(hits).get(0));
        assertEquals("[{\"category\":\"admission_promise\",\"field\":\"title\",\"matched\":\"保录\"}]", AbrContentWriterRules.filterFlags(hits));
    }
}
