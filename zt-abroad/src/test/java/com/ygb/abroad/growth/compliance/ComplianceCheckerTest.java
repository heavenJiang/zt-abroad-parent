package com.ygb.abroad.growth.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 合规审查测试：词表（空白与大小写、例外短语、平台）、需核实的说法、个人信息正则、事实核对（截止日、雅思、学费、学制、均分、院校）、
 * 过期项目、引用问题、案例类内容、AI 生成标识；M0-10 词表种子的正常句与违规句
 *
 * @author ygb
 */
class ComplianceCheckerTest
{
    private static final Set<String> OFF_SITE = Set.of("xiaohongshu", "douyin", "channels", "zhihu");

    private static ComplianceRuleSet.Rule rule(String category, String match, String pattern, String action, Set<String> platforms)
    {
        return new ComplianceRuleSet.Rule(null, category, match, pattern, action, platforms);
    }

    private static ComplianceRuleSet rules()
    {
        return new ComplianceRuleSet(List.of(
                rule("ad_banned", "word", "第一", "block", Set.of()),
                rule("ad_banned", "exception", "第一轮", "block", Set.of()),
                rule("admission_promise", "word", "保录", "block", Set.of()),
                rule("fact_claim", "word", "服务过", "cite", Set.of()),
                rule("diversion", "word", "加v", "block", OFF_SITE),
                rule("privacy", "regex", "(?<!\\d)1[3-9]\\d{9}(?!\\d)", "block", Set.of())));
    }

    private static ComplianceChecker.Input input(String platform, String body)
    {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("body", body);
        return new ComplianceChecker.Input(platform, f, f, false, null, "body", false);
    }

    private static ComplianceFacts facts(ComplianceFacts.ProgramFact... programs)
    {
        ComplianceFacts f = new ComplianceFacts();
        for (ComplianceFacts.ProgramFact p : programs)
        {
            f.addProgram(p);
        }
        f.addSchoolName("伦敦大学学院", 1L);
        f.addSchoolName("University College London", 1L);
        f.addSchoolName("伦敦大学", 2L);
        f.addSchoolName("爱丁堡大学", 3L);
        return f;
    }

    private static ComplianceFacts.ProgramFact ucl(boolean stale)
    {
        return new ComplianceFacts.ProgramFact(10L, 1L, "伦敦大学学院 计算机硕士", stale, Set.of(LocalDate.of(2027, 1, 15), LocalDate.of(2027, 3, 31)),
                Set.of(new BigDecimal("6.5")), Set.of(new BigDecimal("6")), new BigDecimal("35000"), "GBP", 12, Set.of(new BigDecimal("85")));
    }

    private static List<String> categories(ComplianceChecker.Result r)
    {
        List<String> out = new ArrayList<>();
        r.blocked().forEach(b -> out.add((String) b.get("category")));
        return out;
    }

    @Test
    void wordsIgnoreSpaceCaseAndRespectPlatform()
    {
        ComplianceChecker.Result r = ComplianceChecker.check(input("xiaohongshu", "有需要的 加 V 了解"), rules(), facts());
        assertFalse(r.passed());
        assertEquals(List.of("diversion"), categories(r));
        assertEquals("加 V", r.blocked().get(0).get("matched"));
        assertTrue(((String) r.blocked().get(0).get("snippet")).contains("加 V"));
        assertTrue(ComplianceChecker.check(input("wechat_oa", "有需要的 加 V 了解"), rules(), facts()).passed(), "服务号不算站外导流");
        assertTrue(ComplianceChecker.check(input(null, "有需要的加V了解"), rules(), facts()).passed(), "消息不指定平台时只用全平台规则");
    }

    @Test
    void exceptionPhrasesOnlyCoverTheirOwnSpan()
    {
        assertTrue(ComplianceChecker.check(input(null, "第一轮截止前递交材料"), rules(), facts()).passed());
        ComplianceChecker.Result r = ComplianceChecker.check(input(null, "第一轮截止前递交，我们是行业第一"), rules(), facts());
        assertEquals(List.of("ad_banned"), categories(r));
        assertTrue(((String) r.blocked().get(0).get("snippet")).contains("行业第一"));
    }

    @Test
    void citeRulesNeedASource()
    {
        ComplianceChecker.Result r = ComplianceChecker.check(input(null, "我们服务过很多学生"), rules(), facts());
        assertEquals(List.of("fact_claim"), categories(r));
        ComplianceFacts f = facts();
        f.addCitationType("url");
        r = ComplianceChecker.check(input(null, "我们服务过很多学生"), rules(), f);
        assertTrue(r.passed());
        assertEquals(1, r.cited().size());
    }

    @Test
    void privacyRegex()
    {
        assertEquals(List.of("privacy"), categories(ComplianceChecker.check(input(null, "电话 13812345678"), rules(), facts())));
        assertTrue(ComplianceChecker.check(input(null, "编号 213812345678901"), rules(), facts()).passed(), "更长数字串中的不算手机号");
        assertEquals(List.of("admission_promise"), categories(ComplianceChecker.check(input(null, "签约保 录"), rules(), facts())));
    }

    @Test
    void factsMatchCitedPrograms()
    {
        String ok = "伦敦大学学院计算机硕士第二轮截止日为 2027-03-31，雅思 6.5（小分 6）。学费约 3.5 万英镑，生活费约 1.5 万英镑。学制 1 年，均分 85 分。";
        ComplianceChecker.Result r = ComplianceChecker.check(input(null, ok), rules(), facts(ucl(false)));
        assertTrue(r.passed(), r.blocked().toString());
        assertTrue(r.facts().stream().allMatch(f -> "ok".equals(f.get("status"))));
        assertEquals(Set.of("deadline", "ielts", "ielts_sub", "tuition", "duration", "gpa", "school"),
                Set.copyOf(r.facts().stream().map(f -> f.get("kind")).toList()));
        assertTrue(ComplianceChecker.check(input(null, "伦敦大学学院 1 月 15 日截止，学费 £35,000"), rules(), facts(ucl(false))).passed());
    }

    @Test
    void caseScoresAllowStudentGrades()
    {
        String text = "伦敦大学学院计算机硕士录取：本科 985，均分 84–87，雅思 7.0。";
        ComplianceFacts f = facts(ucl(false));
        assertTrue(!ComplianceChecker.check(input(null, text), rules(), f).passed());
        f.addCaseScore(new BigDecimal("84"));
        f.addCaseScore(new BigDecimal("87"));
        f.addCaseScore(new BigDecimal("7.0"));
        ComplianceChecker.Result r = ComplianceChecker.check(input(null, text), rules(), f);
        assertTrue(r.passed(), r.blocked().toString());
        assertTrue(r.facts().stream().filter(x -> !"school".equals(x.get("kind"))).allMatch(x -> "与引用的案例中的学生成绩一致".equals(x.get("detail"))));
        // 项目要求以外、案例中也没有的数值仍不一致
        assertTrue(!ComplianceChecker.check(input(null, "伦敦大学学院 均分 90"), rules(), f).passed());
    }

    @Test
    void factsMismatchStaleAndUncited()
    {
        ComplianceChecker.Result r = ComplianceChecker.check(input(null, "伦敦大学学院 2027年1月20日截止，雅思 7，学费 3.5 万澳元，学制 18 个月"), rules(),
                facts(ucl(false)));
        assertEquals(4, r.blocked().size(), r.blocked().toString());
        assertTrue(r.facts().stream().filter(f -> !"school".equals(f.get("kind"))).allMatch(f -> "mismatch".equals(f.get("status"))));
        r = ComplianceChecker.check(input(null, "伦敦大学学院 2027-01-15 截止"), rules(), facts(ucl(true)));
        assertEquals("stale", r.facts().get(0).get("status"));
        assertEquals("2027-01-15", r.facts().get(0).get("text"));
        assertFalse(r.passed());
        r = ComplianceChecker.check(input(null, "雅思 6.5 就能申"), rules(), facts());
        assertEquals("uncited", r.facts().get(0).get("status"));
        assertTrue(ComplianceChecker.check(input(null, "QS 排名 6 月 10 日发布"), rules(), facts()).passed(), "不在截止语境中的日期不核对");
    }

    @Test
    void schoolsMustBeCitedLongestNameFirst()
    {
        ComplianceChecker.Result r = ComplianceChecker.check(input(null, "伦敦大学学院和爱丁堡大学都开放了"), rules(), facts(ucl(false)));
        assertEquals(List.of("fact"), categories(r));
        assertTrue(((String) r.blocked().get(0).get("detail")).contains("爱丁堡大学"));
        assertEquals(ComplianceChecker.SCHOOL_SUGGESTION, r.blocked().get(0).get("suggestion"));
        assertEquals(2, r.facts().size(), "伦敦大学学院不再算作伦敦大学");
        assertTrue(ComplianceChecker.check(input(null, "UNIVERSITY COLLEGE LONDON 已开放"), rules(), facts(ucl(false))).passed());
    }

    @Test
    void citationProblemsCaseContentAndAiLabel()
    {
        ComplianceFacts f = facts();
        f.addCitationProblem("引用的项目 99 不存在或已下线");
        assertEquals(List.of("citation"), categories(ComplianceChecker.check(input(null, "正文"), rules(), f)));
        Map<String, String> fields = Map.of("body", "这位同学拿到了 offer");
        ComplianceChecker.Input caseInput = new ComplianceChecker.Input(null, fields, fields, false, null, "body", true);
        assertEquals(List.of("citation"), categories(ComplianceChecker.check(caseInput, rules(), facts())));
        ComplianceChecker.Input ai = new ComplianceChecker.Input("xiaohongshu", fields, fields, true, "本文由 AI 辅助生成", "body", false);
        ComplianceChecker.Result r = ComplianceChecker.check(ai, rules(), facts());
        assertEquals(List.of("ai_label"), categories(r));
        assertEquals(1, r.notices().size());
        Map<String, String> labelled = Map.of("body", "这位同学拿到了 offer\n本文由 AI 辅助生成");
        assertTrue(ComplianceChecker.check(new ComplianceChecker.Input("wechat_oa", labelled, labelled, true, "本文由 AI 辅助生成", "body", false),
                rules(), facts()).passed());
    }

    @Test
    void resultMapHasVersionAndCounts()
    {
        Map<String, Object> m = ComplianceChecker.check(input(null, "正文"), rules(), facts()).toMap();
        assertEquals(ComplianceChecker.AGENT, m.get("checker"));
        assertEquals(ComplianceChecker.VERSION, m.get("version"));
        assertEquals(5, m.get("ruleCount"));
    }

    /**
     * M0-10 词表种子（seed/compliance）：正常句不误拦，违规句全部拦下
     */
    @Test
    void seedWordListsHaveNoFalsePositivesOnSamples() throws IOException
    {
        ComplianceRuleSet seed = new ComplianceRuleSet(seedRules());
        List<String> normal = List.of("爱丁堡大学计算机硕士第一轮截止日以官网为准。", "保研录取结果出来后，还来得及申请港新英的硕士。",
                "申请材料包括成绩单、语言成绩和个人陈述。", "用 30 秒快测看看你的背景能申哪些项目，私信口令 2318。", "选择首选专业时，先看课程设置。",
                "她获得了全国数学竞赛冠军。", "QS 排名前 100 的院校中，有 12 所在英国。", "我们的 AI 工具不保证结果，仅供参考。", "冲稳保三档录取情况见报告。");
        for (String s : normal)
        {
            assertTrue(seed.scan("body", s, "xiaohongshu").isEmpty(), s + " → " + seed.scan("body", s, "xiaohongshu"));
        }
        List<String> bad = List.of("我们是业内最专业的留学机构", "签约即录，不过退款", "前招生官一对一指导", "有需要的加V了解", "评论区扣1领取资料包",
                "录取率高达 98%", "保 G5 录取", "电话 13812345678");
        for (String s : bad)
        {
            assertFalse(seed.scan("body", s, "xiaohongshu").isEmpty(), s);
        }
        assertFalse(seed.scan("body", "转发朋友圈领取选校手册", "wechat_oa").isEmpty());
    }

    private static List<ComplianceRuleSet.Rule> seedRules() throws IOException
    {
        Path dir = Path.of("..", "seed", "compliance");
        Map<String, Object[]> files = Map.of("ad_banned.txt", new Object[] { "ad_banned", Set.of() }, "admission_promise.txt",
                new Object[] { "admission_promise", Set.of() }, "education_ad.txt", new Object[] { "education_ad", Set.of() }, "fact_claim.txt",
                new Object[] { "fact_claim", Set.of() }, "diversion_common.txt", new Object[] { "diversion", OFF_SITE }, "diversion_xiaohongshu.txt",
                new Object[] { "diversion", Set.of("xiaohongshu") }, "diversion_douyin.txt", new Object[] { "diversion", Set.of("douyin") },
                "inducement_wechat.txt", new Object[] { "inducement", Set.of("wechat_oa", "channels") });
        List<ComplianceRuleSet.Rule> out = new ArrayList<>();
        for (Map.Entry<String, Object[]> e : files.entrySet())
        {
            String category = (String) e.getValue()[0];
            @SuppressWarnings("unchecked")
            Set<String> platforms = (Set<String>) e.getValue()[1];
            for (String line : Files.readAllLines(dir.resolve(e.getKey()), StandardCharsets.UTF_8))
            {
                String l = line.trim();
                if (l.isEmpty() || l.startsWith("#"))
                {
                    continue;
                }
                boolean ex = l.startsWith("!");
                out.add(rule(category, ex ? "exception" : "word", ex ? l.substring(1) : l, "block", platforms));
            }
        }
        for (String line : Files.readAllLines(dir.resolve("patterns.txt"), StandardCharsets.UTF_8))
        {
            if (line.isBlank() || line.startsWith("#"))
            {
                continue;
            }
            String[] p = line.split("\t");
            out.add(rule(p[0], "regex", p[2], p[1], Set.of()));
        }
        return out;
    }
}
