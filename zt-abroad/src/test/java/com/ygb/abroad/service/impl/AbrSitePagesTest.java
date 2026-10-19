package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.AbrSchool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 公开项目页测试：项目页取值与过期隐藏数值、转义、入口链接、结构化数据、聚合页、站点地图、确定性
 *
 * @author ygb
 */
class AbrSitePagesTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    private static final AbrSitePages.Settings SETTINGS = new AbrSitePages.Settings("https://www.example.com/", "https://m.example.com/", "测试工作室", "site",
            "pages/follow/index");

    private static final Map<String, Map<String, String>> DICTS = new HashMap<>();

    static
    {
        Map<String, String> countries = new LinkedHashMap<>();
        countries.put("US", "美国");
        countries.put("UK", "英国");
        countries.put("HK", "中国香港");
        DICTS.put("abr_country", countries);
        DICTS.put("abr_degree", Map.of("master", "硕士"));
        Map<String, String> majors = new LinkedHashMap<>();
        majors.put("finance", "金融 / 金融工程");
        majors.put("cs", "计算机 / 软件");
        DICTS.put("abr_major_direction", majors);
        DICTS.put("abr_intake", Map.of("fall", "秋季", "spring", "春季"));
        DICTS.put("abr_teaching_language", Map.of("en", "英语"));
        DICTS.put("abr_test_requirement", Map.of("none", "不要求", "optional", "可选", "required", "必需"));
        DICTS.put("abr_cross_major", Map.of("0", "不接受", "1", "接受", "2", "未知"));
        DICTS.put("abr_pre_sessional", Map.of("0", "不接受", "1", "接受", "2", "未知"));
    }

    private static Date daysAgo(int days)
    {
        return Date.from(TODAY.minusDays(days).atStartOfDay(ZoneId.of("Asia/Shanghai")).plusHours(10).toInstant());
    }

    private static AbrSchool school(long id, String name, String en, String country)
    {
        AbrSchool s = new AbrSchool();
        s.setSchoolId(id);
        s.setSchoolName(name);
        s.setSchoolNameEn(en);
        s.setCountry(country);
        s.setCity("伦敦");
        s.setWebsite("https://www.uni" + id + ".example.ac.uk/");
        return s;
    }

    private static AbrProgram program(long id, AbrSchool s, String name, String en, String major, int verifiedDaysAgo)
    {
        AbrProgram p = new AbrProgram();
        p.setProgramId(id);
        p.setSchoolId(s.getSchoolId());
        p.setSchoolName(s.getSchoolName());
        p.setSchoolNameEn(s.getSchoolNameEn());
        p.setCountry(s.getCountry());
        p.setProgramName(name);
        p.setProgramNameEn(en);
        p.setDegree("master");
        p.setMajorDirection(major);
        p.setDurationMonths(12);
        p.setIntakes("fall");
        p.setTuition(new BigDecimal("32500"));
        p.setCurrency("GBP");
        p.setTeachingLanguage("en");
        p.setWebsite("https://www.uni" + s.getSchoolId() + ".example.ac.uk/p" + id);
        p.setApsRequired("N");
        p.setVerifiedTime(verifiedDaysAgo < 0 ? null : daysAgo(verifiedDaysAgo));
        p.setStatus("0");
        return p;
    }

    private static AbrRequirement req(long programId, String deadlines)
    {
        AbrRequirement r = new AbrRequirement();
        r.setRequirementId(100 + programId);
        r.setProgramId(programId);
        r.setSeason("2027-fall");
        r.setMinGpa("{\"985\":82,\"211\":85}");
        r.setIeltsOverall(new BigDecimal("7.0"));
        r.setIeltsMinSub(new BigDecimal("6.5"));
        r.setOtherLanguage("{\"toefl\":{\"overall\":100,\"minSub\":22,\"level\":null,\"note\":null}}");
        r.setTestRequirement("optional");
        r.setTestDetail("{\"gre\":320}");
        r.setWorkYears(2);
        r.setAcceptCrossMajor("1");
        r.setAcceptPreSessional("2");
        r.setGpaNoCutoff("N");
        r.setDeadlines(deadlines);
        return r;
    }

    private static List<AbrSitePages.Entry> entries()
    {
        AbrSchool a = school(1, "甲大学", "Alpha University", "UK");
        AbrSchool b = school(2, "乙大学 <B>", "Beta & Co University", "HK");
        List<AbrSitePages.Entry> list = new ArrayList<>();
        list.add(new AbrSitePages.Entry(program(1, a, "金融硕士", "MSc Finance", "finance", 10), a,
                req(1, "{\"rolling\":false,\"rounds\":[{\"name\":\"R1\",\"date\":\"2026-10-01\"},{\"name\":\"R2\",\"date\":\"2026-11-30\"},{\"name\":\"R3\",\"date\":\"2027-01-31\"}]}"),
                List.of("https://www.uni1.example.ac.uk/p1/entry", "javascript:alert(1)")));
        list.add(new AbrSitePages.Entry(program(2, a, "计算机硕士", "MSc Computer Science", "cs", 200), a,
                req(2, "{\"rounds\":[{\"name\":\"R1\",\"date\":\"2026-12-01\"}]}"), List.of()));
        list.add(new AbrSitePages.Entry(program(3, b, "金融科技硕士 \"FT\"", "MSc FinTech", "finance", 30), b,
                req(3, "{\"rounds\":[],\"rolling\":true}"), List.of()));
        list.add(new AbrSitePages.Entry(program(4, b, "会计硕士", "MSc Accounting", null, 5), b, null, List.of()));
        return list;
    }

    private static Map<String, AbrSitePages.Page> build()
    {
        Map<String, AbrSitePages.Page> m = new LinkedHashMap<>();
        AbrSitePages.build(entries(), DICTS, SETTINGS, TODAY).forEach(p -> m.put(p.path(), p));
        return m;
    }

    private static JsonNode jsonLd(String html, String type)
    {
        Matcher m = Pattern.compile("<script type=\"application/ld\\+json\">(.*?)</script>").matcher(html);
        while (m.find())
        {
            JsonNode n = MAPPER.readTree(m.group(1));
            if (type.equals(n.path("@type").asString("")))
            {
                return n;
            }
        }
        return null;
    }

    @Test
    void generatesExpectedPages()
    {
        Map<String, AbrSitePages.Page> pages = build();
        assertEquals(List.of("404.html", "country/hk.html", "country/uk.html", "deadline/2026-11.html", "deadline/2027-01.html", "index.html",
                "major/cs.html", "major/finance.html", "program/1.html", "program/2.html", "program/3.html", "program/4.html"), new ArrayList<>(pages.keySet()));
        assertEquals("program/1", pages.get("program/1.html").url());
        assertEquals("", pages.get("index.html").url());
    }

    @Test
    void programPageShowsCatalogNumbers()
    {
        String html = build().get("program/1.html").content();
        assertTrue(html.contains("<h1>甲大学 · 金融硕士</h1>"));
        assertTrue(html.contains("数据核对于 2026-10-09"));
        assertTrue(html.contains("<dt>雅思</dt><dd>7.0（单项 6.5）</dd>"));
        assertTrue(html.contains("<dt>托福</dt><dd>100（单项 22）</dd>"));
        assertTrue(html.contains("<dt>GRE / GMAT</dt><dd>可选（GRE 320）</dd>"));
        assertTrue(html.contains("<dt>工作经验</dt><dd>至少 2 年</dd>"));
        assertTrue(html.contains("<dt>跨专业申请</dt><dd>接受</dd>"));
        assertFalse(html.contains("语言班"), "未知不展示");
        assertTrue(html.contains("<dt>学费</dt><dd>GBP 32,500 / 年</dd>"));
        assertTrue(html.contains("<dt>学制</dt><dd>12 个月</dd>"));
        // 均分按院校背景，不展示各层次的数值
        assertTrue(html.contains("按本科院校背景有不同要求"));
        assertFalse(html.contains("82") || html.contains("985"));
        // 截止：已过的轮次标已截止
        assertTrue(html.contains("<tr class=\"passed\"><td>R1</td><td class=\"nw\">2026-10-01</td><td class=\"nw\">已截止</td></tr>"));
        assertTrue(html.contains("<tr><td>R2</td><td class=\"nw\">2026-11-30</td><td class=\"nw\">可申请</td></tr>"));
        assertTrue(html.contains("最近截止 2026-11-30（R2）"), "描述带最近截止");
        // 来源：项目官网与字段来源，非 http(s) 的丢弃
        assertTrue(html.contains("href=\"https://www.uni1.example.ac.uk/p1/entry\" rel=\"nofollow noopener\""));
        assertFalse(html.contains("javascript:"));
        assertTrue(html.contains("<link rel=\"canonical\" href=\"https://www.example.com/program/1\">"));
    }

    @Test
    void entryLinksCarryChannelAndProgram()
    {
        String html = build().get("program/1.html").content();
        assertTrue(html.contains("href=\"https://m.example.com/pages/quick/index?ch=site&amp;ct=p1\">测一下我能不能申</a>"));
        assertTrue(html.contains("href=\"https://m.example.com/pages/follow/index?programId=1&amp;ch=site&amp;ct=p1\">关注这个项目</a>"));
        assertTrue(html.contains("https://m.example.com/pages/tool/recognition?ch=site&amp;ct=p1"));
        assertTrue(html.contains("<a href=\"/program/2\">甲大学 计算机硕士</a>"), "同校其他项目");
    }

    @Test
    void staleProgramHidesNumbers()
    {
        String html = build().get("program/2.html").content();
        assertTrue(html.contains("超过 90 天未重新核对官网"));
        assertTrue(html.contains("<dt>雅思</dt><dd>以官网为准</dd>"));
        assertTrue(html.contains("<dt>托福</dt><dd>以官网为准</dd>"));
        assertTrue(html.contains("<dt>GRE / GMAT</dt><dd>可选（以官网为准）</dd>"));
        assertTrue(html.contains("<dt>学费</dt><dd>以官网为准</dd>"));
        assertTrue(html.contains("<dt>学制</dt><dd>以官网为准</dd>"));
        assertFalse(html.contains("2026-12-01"), "过期不展示截止日");
        assertFalse(html.contains("32,500") || html.contains("7.0") || html.contains("320"));
        JsonNode ld = jsonLd(html, "EducationalOccupationalProgram");
        assertNotNull(ld);
        assertTrue(ld.path("offers").isMissingNode());
        assertTrue(ld.path("applicationDeadline").isMissingNode());
        assertTrue(ld.path("timeToComplete").isMissingNode());
    }

    @Test
    void escapesCatalogText()
    {
        Map<String, AbrSitePages.Page> pages = build();
        String html = pages.get("program/3.html").content();
        assertTrue(html.contains("乙大学 &lt;B&gt; · 金融科技硕士 &quot;FT&quot;"));
        assertFalse(html.contains("<B>"));
        assertTrue(html.contains("Beta &amp; Co University"));
        assertTrue(html.contains("滚动录取"));
        JsonNode ld = jsonLd(html, "EducationalOccupationalProgram");
        assertEquals("乙大学 <B>", ld.path("provider").path("name").asString(""));
    }

    @Test
    void programWithoutRequirement()
    {
        String html = build().get("program/4.html").content();
        assertTrue(html.contains("院校库暂未录入申请要求，请以官网为准"));
        assertTrue(html.contains("院校库暂未录入截止日期"));
    }

    @Test
    void structuredData()
    {
        String html = build().get("program/1.html").content();
        JsonNode ld = jsonLd(html, "EducationalOccupationalProgram");
        assertEquals("https://www.example.com/program/1", ld.path("url").asString(""));
        assertEquals("GB", ld.path("provider").path("address").path("addressCountry").asString(""));
        assertEquals("P12M", ld.path("timeToComplete").asString(""));
        assertEquals("2026-11-30", ld.path("applicationDeadline").asString(""));
        assertEquals("GBP", ld.path("offers").path("priceSpecification").path("priceCurrency").asString(""));
        assertEquals(32500, ld.path("offers").path("priceSpecification").path("price").asInt());
        assertEquals("2026-10-09", ld.path("dateModified").asString(""));
        JsonNode crumb = jsonLd(html, "BreadcrumbList");
        assertEquals(3, crumb.path("itemListElement").size());
        assertEquals("https://www.example.com/country/uk", crumb.path("itemListElement").get(1).path("item").asString(""));
    }

    @Test
    void aggregatePages()
    {
        Map<String, AbrSitePages.Page> pages = build();
        String uk = pages.get("country/uk.html").content();
        assertTrue(uk.contains("共 2 个项目"));
        assertTrue(uk.contains("<td><a href=\"/program/1\">金融硕士</a></td><td class=\"nw\">硕士</td><td>7.0（单项 6.5）</td><td>2026-11-30（R2）</td>"));
        assertTrue(uk.contains("<td><a href=\"/program/2\">计算机硕士</a></td><td class=\"nw\">硕士</td><td>以官网为准</td><td>以官网为准</td>"));
        String finance = pages.get("major/finance.html").content();
        assertTrue(finance.indexOf("英国") < finance.indexOf("中国香港"), "按国家字典顺序");
        assertTrue(finance.contains("乙大学 &lt;B&gt; 金融科技硕士 &quot;FT&quot;"));
        String nov = pages.get("deadline/2026-11.html").content();
        assertTrue(nov.contains("<tr><td class=\"nw\">2026-11-30</td><td><a href=\"/program/1\">甲大学 金融硕士</a></td><td>R2</td><td>英国</td></tr>"));
        assertFalse(pages.containsKey("deadline/2026-12.html"), "过期项目不进截止月份");
        assertFalse(pages.containsKey("deadline/2026-10.html"), "已过的轮次不进");
        String home = pages.get("index.html").content();
        assertTrue(home.indexOf("/country/uk") < home.indexOf("/country/hk"));
        assertTrue(home.contains("/deadline/2027-01"));
        assertTrue(home.indexOf("/major/finance") < home.indexOf("/major/cs"), "按字典顺序");
        assertTrue(pages.get("404.html").content().contains("<meta name=\"robots\" content=\"noindex\">"));
    }

    @Test
    void sitemapAndRobots()
    {
        List<AbrSitePages.Page> pages = AbrSitePages.build(entries(), DICTS, SETTINGS, TODAY);
        Map<String, LocalDate> lastmod = Map.of("program/1.html", LocalDate.of(2026, 10, 1));
        String xml = AbrSitePages.sitemap(pages, lastmod, SETTINGS.siteUrl());
        assertTrue(xml.contains("<url><loc>https://www.example.com/</loc></url>"));
        assertTrue(xml.contains("<url><loc>https://www.example.com/program/1</loc><lastmod>2026-10-01</lastmod></url>"));
        assertFalse(xml.contains("404"));
        assertEquals(11, xml.split("<url>").length - 1);
        assertTrue(AbrSitePages.robots(SETTINGS.siteUrl()).contains("Sitemap: https://www.example.com/sitemap.xml"));
    }

    @Test
    void deterministic()
    {
        List<AbrSitePages.Entry> shuffled = new ArrayList<>(entries());
        Collections.reverse(shuffled);
        assertEquals(AbrSitePages.build(entries(), DICTS, SETTINGS, TODAY), AbrSitePages.build(shuffled, DICTS, SETTINGS, TODAY));
        // 一天后（轮次状态不变）页面不变；R2 截止后项目页变化
        assertEquals(build().get("program/1.html"), AbrSitePages.build(entries(), DICTS, SETTINGS, TODAY.plusDays(1)).stream()
                .filter(p -> p.path().equals("program/1.html")).findFirst().orElseThrow());
        assertFalse(build().get("program/1.html").equals(AbrSitePages.build(entries(), DICTS, SETTINGS, LocalDate.of(2026, 12, 1)).stream()
                .filter(p -> p.path().equals("program/1.html")).findFirst().orElseThrow()));
    }
}
