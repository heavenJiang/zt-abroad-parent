package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.AbrSchool;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 公开项目页（纯计算，不访问数据库、不调用大模型）：把院校库确定性地渲染成静态 HTML
 *
 * 项目页、国家 / 地区聚合页、专业方向聚合页、截止月份聚合页、首页与 404 页；数字只取院校库，项目超过 90 天未核对时
 * 要求、截止日、学费、学制都不展示数值，改为“以官网为准”。页面内容只随院校库和“今天”（轮次是否已过、是否过期）变化，
 * 不含生成时间，同样的数据生成同样的文件，便于只重写变化的页面。
 *
 * @author ygb
 */
final class AbrSitePages
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 模板版本：改模板或样式（site/site.css）时加一，促使下一轮全部重新生成、浏览器重新取样式 */
    static final int VERSION = 1;

    static final String UNVERIFIED = AbrContentCards.UNVERIFIED;

    /** 页面类型 */
    static final String KIND_HOME = "home";

    static final String KIND_PROGRAM = "program";

    static final String KIND_COUNTRY = "country";

    static final String KIND_MAJOR = "major";

    static final String KIND_DEADLINE = "deadline";

    static final String KIND_OTHER = "other";

    /** 截止月份聚合页：只生成从本月起多少个月内的 */
    static final int DEADLINE_MONTHS = 12;

    /** 项目页上同校其他项目最多列出的个数 */
    static final int MAX_RELATED = 12;

    /** 其他语言考试的显示顺序与名称（键与 catalog_extractor 的 otherLanguage 一致） */
    private static final Map<String, String> LANGUAGE_TESTS = new LinkedHashMap<>();

    /** 国家 / 地区编码 → ISO 3166 代码（结构化数据用） */
    private static final Map<String, String> ISO_COUNTRY = Map.of("UK", "GB");

    static
    {
        LANGUAGE_TESTS.put("toefl", "托福");
        LANGUAGE_TESTS.put("pte", "PTE");
        LANGUAGE_TESTS.put("duolingo", "多邻国");
        LANGUAGE_TESTS.put("jlpt", "JLPT");
        LANGUAGE_TESTS.put("topik", "TOPIK");
        LANGUAGE_TESTS.put("testdaf", "TestDaF");
        LANGUAGE_TESTS.put("dsh", "DSH");
        LANGUAGE_TESTS.put("goethe", "歌德证书");
    }

    private AbrSitePages()
    {
    }

    /**
     * 站点设置
     *
     * @param siteUrl 项目页站点地址（以 / 结尾，规范链接与站点地图用）
     * @param appUrl C 端地址（以 / 结尾，“测一下”“关注”入口）
     * @param brand 品牌名
     * @param channel 渠道码（入口链接的 ch）
     * @param followPage C 端“关注这个项目”页面路径
     */
    record Settings(String siteUrl, String appUrl, String brand, String channel, String followPage)
    {
    }

    /**
     * 一个上架项目及其院校、最新申请季的申请要求、来源网址
     *
     * @param program 项目
     * @param school 院校
     * @param requirement 最新申请季的申请要求（可为 null）
     * @param sources 字段来源网址（http(s)，去重）
     */
    record Entry(AbrProgram program, AbrSchool school, AbrRequirement requirement, List<String> sources)
    {
    }

    /**
     * 生成的文件
     *
     * @param path 相对站点根目录的文件路径（如 program/12.html）
     * @param kind 页面类型
     * @param content 文件内容
     */
    record Page(String path, String kind, String content)
    {
        /**
         * 页面地址（相对站点根目录，不带 .html；首页为空串）
         *
         * @return 地址
         */
        String url()
        {
            return urlOf(path);
        }
    }

    /**
     * 生成全部 HTML 页面
     *
     * @param entries 上架项目
     * @param dicts 字典（类型 → 值 → 标签，按字典顺序）
     * @param s 站点设置
     * @param today 今天（北京时间）
     * @return 页面（按路径排序）
     */
    static List<Page> build(List<Entry> entries, Map<String, Map<String, String>> dicts, Settings s, LocalDate today)
    {
        Ctx ctx = new Ctx(dicts, s, today);
        List<Entry> list = new ArrayList<>(entries);
        list.sort(ctx.order());
        Map<String, Page> pages = new TreeMap<>();
        Map<String, List<Entry>> byCountry = new LinkedHashMap<>();
        Map<String, List<Entry>> byMajor = new LinkedHashMap<>();
        Map<Long, List<Entry>> bySchool = new LinkedHashMap<>();
        for (Entry e : list)
        {
            byCountry.computeIfAbsent(e.program().getCountry(), k -> new ArrayList<>()).add(e);
            if (StringUtils.isNotBlank(e.program().getMajorDirection()))
            {
                byMajor.computeIfAbsent(e.program().getMajorDirection(), k -> new ArrayList<>()).add(e);
            }
            bySchool.computeIfAbsent(e.program().getSchoolId(), k -> new ArrayList<>()).add(e);
        }
        for (Entry e : list)
        {
            put(pages, programPage(ctx, e, bySchool.get(e.program().getSchoolId())));
        }
        byCountry.forEach((code, members) -> put(pages, countryPage(ctx, code, members)));
        List<String> majors = new ArrayList<>(byMajor.keySet());
        majors.sort(Comparator.comparingInt(ctx::majorRank).thenComparing(x -> x));
        for (String code : majors)
        {
            put(pages, majorPage(ctx, code, byMajor.get(code)));
        }
        Map<String, List<Round>> months = deadlineMonths(ctx, list);
        months.forEach((month, rounds) -> put(pages, deadlinePage(ctx, month, rounds)));
        put(pages, homePage(ctx, list, byCountry, majors, byMajor, months));
        put(pages, notFoundPage(ctx));
        return new ArrayList<>(pages.values());
    }

    private static void put(Map<String, Page> pages, Page p)
    {
        pages.put(p.path(), p);
    }

    // ---------------------------------------------------------------- 项目页

    private static Page programPage(Ctx ctx, Entry e, List<Entry> sameSchool)
    {
        AbrProgram p = e.program();
        AbrRequirement r = e.requirement();
        boolean stale = AbrLookupTools.stale(p, ctx.today);
        String name = programName(e);
        String path = programPath(p);
        StringBuilder b = new StringBuilder();
        b.append(breadcrumb(List.of(link("", "首页"), link(countryPath(p.getCountry()), ctx.country(p.getCountry())), text(name))));
        b.append("<main>\n<h1>").append(esc(e.school().getSchoolName())).append(" · ").append(esc(p.getProgramName())).append("</h1>\n");
        String en = joinNonBlank(" · ", e.school().getSchoolNameEn(), p.getProgramNameEn());
        if (!en.isEmpty())
        {
            b.append("<p class=\"en\" lang=\"en\">").append(esc(en)).append("</p>\n");
        }
        b.append("<p class=\"meta\">").append(esc(joinNonBlank(" · ", ctx.label("abr_degree", p.getDegree()), ctx.country(p.getCountry()),
                e.school().getCity()))).append(" · ").append(esc(verifiedText(p))).append(officialLink(p.getWebsite(), " · ")).append("</p>\n");
        if (stale)
        {
            b.append("<p class=\"notice\">该项目超过 90 天未重新核对官网，要求、截止日与学费暂不展示数值，请以官网为准。</p>\n");
        }
        b.append("<section>\n<h2>申请要求").append(r == null || StringUtils.isBlank(r.getSeason()) ? "" : "（" + esc(r.getSeason()) + " 申请季）")
                .append("</h2>\n");
        if (r == null)
        {
            b.append("<p>院校库暂未录入申请要求，请以官网为准。</p>\n");
        }
        else
        {
            b.append(rows(requirementRows(ctx, e, stale)));
        }
        b.append("</section>\n");
        b.append("<section>\n<h2>截止日期</h2>\n").append(deadlineTable(r, stale, ctx.today)).append("</section>\n");
        b.append("<section>\n<h2>项目信息</h2>\n").append(rows(programRows(ctx, p, stale))).append("</section>\n");
        b.append(cta(ctx, p));
        b.append(sources(p, e.sources()));
        b.append(related(ctx, e, sameSchool));
        b.append("</main>\n");
        String title = name + " 申请要求与截止日期";
        return new Page(path + ".html", KIND_PROGRAM,
                html(ctx, title, programDescription(ctx, e, stale), path, List.of(programJsonLd(ctx, e, stale), breadcrumbJsonLd(ctx, List.of(
                        new String[] { "首页", "" }, new String[] { ctx.country(p.getCountry()), countryPath(p.getCountry()) }, new String[] { name, path }))),
                        b.toString()));
    }

    private static List<String[]> requirementRows(Ctx ctx, Entry e, boolean stale)
    {
        AbrProgram p = e.program();
        AbrRequirement r = e.requirement();
        List<String[]> rows = new ArrayList<>();
        String quick = ctx.toolLink("quick", p);
        if ("Y".equals(r.getGpaNoCutoff()))
        {
            rows.add(new String[] { "均分要求", esc("官网不设均分线") });
        }
        else if (!AbrAssessmentAnswers.object(r.getMinGpa()).isEmpty())
        {
            rows.add(new String[] { "均分要求", esc("按本科院校背景有不同要求") + " · <a href=\"" + esc(quick) + "\">测一下我够不够</a>" });
        }
        rows.add(new String[] { "院校背景", "<a href=\"" + esc(ctx.toolLink("recognition", p)) + "\">查一查我的本科学校是否被认可</a>" });
        boolean english = "en".equals(p.getTeachingLanguage());
        if (r.getIeltsOverall() != null || english)
        {
            rows.add(new String[] { "雅思", esc(stale ? UNVERIFIED : AbrContentCards.value("ielts", p, r, ctx.today)) });
        }
        JsonNode other = AbrAssessmentAnswers.object(r.getOtherLanguage());
        for (Map.Entry<String, String> t : LANGUAGE_TESTS.entrySet())
        {
            JsonNode v = other.get(t.getKey());
            String text = v == null ? "" : otherLanguage(v);
            if (!text.isEmpty())
            {
                rows.add(new String[] { t.getValue(), esc(stale ? UNVERIFIED : text) });
            }
        }
        if (StringUtils.isNotBlank(r.getTestRequirement()))
        {
            String detail = testDetail(AbrAssessmentAnswers.object(r.getTestDetail()));
            String v = ctx.label("abr_test_requirement", r.getTestRequirement());
            rows.add(new String[] { "GRE / GMAT", esc(detail.isEmpty() ? v : v + "（" + (stale ? UNVERIFIED : detail) + "）") });
        }
        if (StringUtils.isNotBlank(r.getPrerequisites()))
        {
            rows.add(new String[] { "先修课", esc(stale ? UNVERIFIED : r.getPrerequisites().trim() + ("Y".equals(r.getPrerequisitesRequired()) ? "（硬性要求）" : "")) });
        }
        if (r.getWorkYears() != null && r.getWorkYears() > 0)
        {
            rows.add(new String[] { "工作经验", esc(stale ? UNVERIFIED : "至少 " + r.getWorkYears() + " 年") });
        }
        if ("0".equals(r.getAcceptCrossMajor()) || "1".equals(r.getAcceptCrossMajor()))
        {
            rows.add(new String[] { "跨专业申请", esc(ctx.label("abr_cross_major", r.getAcceptCrossMajor())) });
        }
        if ("0".equals(r.getAcceptPreSessional()) || "1".equals(r.getAcceptPreSessional()))
        {
            rows.add(new String[] { "语言班", esc(ctx.label("abr_pre_sessional", r.getAcceptPreSessional())) });
        }
        return rows;
    }

    private static List<String[]> programRows(Ctx ctx, AbrProgram p, boolean stale)
    {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "学位", esc(ctx.label("abr_degree", p.getDegree())) });
        if (StringUtils.isNotBlank(p.getMajorDirection()))
        {
            rows.add(new String[] { "专业方向", "<a href=\"/" + majorPath(p.getMajorDirection()) + "\">" + esc(ctx.label("abr_major_direction", p.getMajorDirection()))
                    + "</a>" });
        }
        rows.add(new String[] { "学制", esc(stale ? UNVERIFIED : AbrContentCards.value("duration", p, null, ctx.today)) });
        rows.add(new String[] { "学费", esc(stale ? UNVERIFIED : AbrContentCards.value("tuition", p, null, ctx.today)) });
        if (StringUtils.isNotBlank(p.getIntakes()))
        {
            List<String> labels = new ArrayList<>();
            for (String i : p.getIntakes().split(","))
            {
                if (!i.isBlank())
                {
                    labels.add(ctx.label("abr_intake", i.trim()));
                }
            }
            rows.add(new String[] { "入学季", esc(String.join("、", labels)) });
        }
        if (StringUtils.isNotBlank(p.getTeachingLanguage()))
        {
            rows.add(new String[] { "授课语言", esc(ctx.label("abr_teaching_language", p.getTeachingLanguage())) });
        }
        if ("Y".equals(p.getApsRequired()))
        {
            rows.add(new String[] { "APS 审核", esc("需要") });
        }
        return rows;
    }

    private static String deadlineTable(AbrRequirement r, boolean stale, LocalDate today)
    {
        if (r == null || AbrLookupTools.deadline(r.getDeadlines(), today) == null)
        {
            return "<p>院校库暂未录入截止日期，请以官网为准。</p>\n";
        }
        if (stale)
        {
            return "<p>" + UNVERIFIED + "。</p>\n";
        }
        JsonNode d = AbrAssessmentAnswers.object(r.getDeadlines());
        List<String[]> rounds = new ArrayList<>();
        for (JsonNode n : d.path("rounds"))
        {
            LocalDate date = date(n.path("date").asString(""));
            if (date != null)
            {
                rounds.add(new String[] { date.toString(), n.path("name").asString("").trim(), date.isBefore(today) ? "已截止" : "可申请" });
            }
        }
        rounds.sort(Comparator.comparing((String[] x) -> x[0]).thenComparing(x -> x[1]));
        StringBuilder b = new StringBuilder();
        if (!rounds.isEmpty())
        {
            b.append("<table>\n<thead><tr><th>轮次</th><th>截止日</th><th>状态</th></tr></thead>\n<tbody>\n");
            for (String[] x : rounds)
            {
                b.append("<tr").append("已截止".equals(x[2]) ? " class=\"passed\"" : "").append("><td>").append(esc(x[1].isEmpty() ? "—" : x[1]))
                        .append("</td><td class=\"nw\">").append(x[0]).append("</td><td class=\"nw\">").append(x[2]).append("</td></tr>\n");
            }
            b.append("</tbody>\n</table>\n");
        }
        if (d.path("rolling").asBoolean(false))
        {
            b.append("<p>滚动录取：先到先审，名额满即止，建议尽早申请。</p>\n");
        }
        return b.toString();
    }

    private static String cta(Ctx ctx, AbrProgram p)
    {
        return "<section class=\"cta\">\n<a class=\"btn primary\" href=\"" + esc(ctx.toolLink("quick", p)) + "\">测一下我能不能申</a>\n"
                + "<a class=\"btn\" href=\"" + esc(ctx.followLink(p)) + "\">关注这个项目</a>\n"
                + "<p class=\"hint\">关注后，开放申请、截止前、要求变化时提醒你。</p>\n</section>\n";
    }

    private static String sources(AbrProgram p, List<String> urls)
    {
        Set<String> all = new LinkedHashSet<>();
        if (isHttp(p.getWebsite()))
        {
            all.add(p.getWebsite().trim());
        }
        if (urls != null)
        {
            urls.stream().filter(AbrSitePages::isHttp).map(String::trim).forEach(all::add);
        }
        StringBuilder b = new StringBuilder("<section class=\"sources\">\n<h2>数据来源</h2>\n");
        if (!all.isEmpty())
        {
            b.append("<ul>\n");
            for (String u : all)
            {
                b.append("<li><a href=\"").append(esc(u)).append("\" rel=\"nofollow noopener\" target=\"_blank\">").append(esc(u)).append("</a></li>\n");
            }
            b.append("</ul>\n");
        }
        b.append("<p>页面中的要求、截止日与学费取自院校库，由工作室对照学校官网核对后录入；学校随时可能调整，请以官网最新公布为准。</p>\n</section>\n");
        return b.toString();
    }

    private static String related(Ctx ctx, Entry e, List<Entry> sameSchool)
    {
        StringBuilder b = new StringBuilder("<section>\n<h2>相关</h2>\n<ul class=\"links\">\n");
        int n = 0;
        for (Entry x : sameSchool)
        {
            if (x == e || n >= MAX_RELATED)
            {
                continue;
            }
            b.append("<li><a href=\"/").append(programPath(x.program())).append("\">").append(esc(programName(x))).append("</a></li>\n");
            n++;
        }
        AbrProgram p = e.program();
        b.append("<li><a href=\"/").append(countryPath(p.getCountry())).append("\">").append(esc(ctx.country(p.getCountry()))).append("全部项目</a></li>\n");
        if (StringUtils.isNotBlank(p.getMajorDirection()))
        {
            b.append("<li><a href=\"/").append(majorPath(p.getMajorDirection())).append("\">").append(esc(ctx.label("abr_major_direction", p.getMajorDirection())))
                    .append("方向全部项目</a></li>\n");
        }
        b.append("</ul>\n</section>\n");
        return b.toString();
    }

    private static String programDescription(Ctx ctx, Entry e, boolean stale)
    {
        AbrProgram p = e.program();
        String head = e.school().getSchoolName() + p.getProgramName() + (StringUtils.isBlank(p.getProgramNameEn()) ? "" : "（" + p.getProgramNameEn().trim() + "）");
        if (stale || e.requirement() == null)
        {
            return head + "的申请要求、截止日期、学费与官网链接。";
        }
        List<String> parts = new ArrayList<>();
        String ielts = AbrContentCards.value("ielts", p, e.requirement(), ctx.today);
        if (!UNVERIFIED.equals(ielts))
        {
            parts.add("雅思 " + ielts);
        }
        String deadline = AbrContentCards.value("deadline", p, e.requirement(), ctx.today);
        if (!UNVERIFIED.equals(deadline))
        {
            parts.add(deadline.matches("^\\d{4}-.*") ? "最近截止 " + deadline : deadline);
        }
        String tuition = AbrContentCards.value("tuition", p, null, ctx.today);
        if (!UNVERIFIED.equals(tuition))
        {
            parts.add("学费 " + tuition);
        }
        return head + "申请要求" + (parts.isEmpty() ? "" : "：" + String.join("，", parts)) + "。" + verifiedText(p) + "，以官网为准。";
    }

    private static ObjectNode programJsonLd(Ctx ctx, Entry e, boolean stale)
    {
        AbrProgram p = e.program();
        ObjectNode n = MAPPER.createObjectNode();
        n.put("@context", "https://schema.org");
        n.put("@type", "EducationalOccupationalProgram");
        n.put("name", e.school().getSchoolName() + " " + p.getProgramName());
        if (StringUtils.isNotBlank(p.getProgramNameEn()))
        {
            n.put("alternateName", p.getProgramNameEn().trim());
        }
        n.put("url", ctx.s.siteUrl() + programPath(p));
        ObjectNode provider = n.putObject("provider");
        provider.put("@type", "CollegeOrUniversity");
        provider.put("name", e.school().getSchoolName());
        if (StringUtils.isNotBlank(e.school().getSchoolNameEn()))
        {
            provider.put("alternateName", e.school().getSchoolNameEn().trim());
        }
        if (isHttp(e.school().getWebsite()))
        {
            provider.put("sameAs", e.school().getWebsite().trim());
        }
        ObjectNode address = provider.putObject("address");
        address.put("@type", "PostalAddress");
        address.put("addressCountry", ISO_COUNTRY.getOrDefault(p.getCountry(), p.getCountry()));
        if (StringUtils.isNotBlank(e.school().getCity()))
        {
            address.put("addressLocality", e.school().getCity().trim());
        }
        n.put("educationalCredentialAwarded", ctx.label("abr_degree", p.getDegree()));
        if (StringUtils.isNotBlank(p.getTeachingLanguage()) && !"other".equals(p.getTeachingLanguage()))
        {
            n.put("inLanguage", p.getTeachingLanguage());
        }
        if (isHttp(p.getWebsite()))
        {
            n.put("sameAs", p.getWebsite().trim());
        }
        if (!stale)
        {
            if (p.getDurationMonths() != null)
            {
                n.put("timeToComplete", "P" + p.getDurationMonths() + "M");
            }
            AbrLookupTools.Deadline d = e.requirement() == null ? null : AbrLookupTools.deadline(e.requirement().getDeadlines(), ctx.today);
            if (d != null && d.nextDate() != null)
            {
                n.put("applicationDeadline", d.nextDate().toString());
            }
            if (p.getTuition() != null && StringUtils.isNotBlank(p.getCurrency()))
            {
                ObjectNode offer = n.putObject("offers");
                offer.put("@type", "Offer");
                offer.put("category", "Tuition");
                ObjectNode price = offer.putObject("priceSpecification");
                price.put("@type", "UnitPriceSpecification");
                price.put("price", p.getTuition().setScale(0, RoundingMode.HALF_UP));
                price.put("priceCurrency", p.getCurrency().trim());
                price.put("unitText", "YEAR");
            }
        }
        LocalDate verified = day(p.getVerifiedTime());
        if (verified != null)
        {
            n.put("dateModified", verified.toString());
        }
        return n;
    }

    // ---------------------------------------------------------------- 聚合页

    private static Page countryPage(Ctx ctx, String code, List<Entry> members)
    {
        String name = ctx.country(code);
        StringBuilder b = new StringBuilder();
        b.append(breadcrumb(List.of(link("", "首页"), text(name))));
        b.append("<main>\n<h1>").append(esc(name)).append("留学项目申请要求与截止日期</h1>\n");
        b.append("<p class=\"meta\">共 ").append(members.size()).append(" 个项目 · 数字取自院校库，超过 90 天未核对的显示“以官网为准”</p>\n");
        Map<Long, List<Entry>> bySchool = new LinkedHashMap<>();
        members.forEach(e -> bySchool.computeIfAbsent(e.program().getSchoolId(), k -> new ArrayList<>()).add(e));
        for (List<Entry> group : bySchool.values())
        {
            AbrSchool s = group.get(0).school();
            b.append("<section>\n<h2>").append(esc(s.getSchoolName())).append(StringUtils.isBlank(s.getSchoolNameEn()) ? ""
                    : " <span class=\"en\" lang=\"en\">" + esc(s.getSchoolNameEn()) + "</span>").append("</h2>\n");
            b.append(programTable(ctx, group, false));
            b.append("</section>\n");
        }
        b.append(homeCta(ctx));
        b.append("</main>\n");
        String path = countryPath(code);
        return new Page(path + ".html", KIND_COUNTRY, html(ctx, name + "留学项目申请要求与截止日期",
                name + members.size() + " 个留学项目的雅思要求、最近截止日与官网链接，数据由院校库生成并注明核对日期。", path,
                List.of(breadcrumbJsonLd(ctx, List.of(new String[] { "首页", "" }, new String[] { name, path }))), b.toString()));
    }

    private static Page majorPage(Ctx ctx, String code, List<Entry> members)
    {
        String name = ctx.label("abr_major_direction", code);
        StringBuilder b = new StringBuilder();
        b.append(breadcrumb(List.of(link("", "首页"), text(name))));
        b.append("<main>\n<h1>").append(esc(name)).append("方向留学项目</h1>\n");
        b.append("<p class=\"meta\">共 ").append(members.size()).append(" 个项目 · 数字取自院校库，超过 90 天未核对的显示“以官网为准”</p>\n");
        Map<String, List<Entry>> byCountry = new LinkedHashMap<>();
        members.forEach(e -> byCountry.computeIfAbsent(e.program().getCountry(), k -> new ArrayList<>()).add(e));
        byCountry.forEach((country, group) -> {
            b.append("<section>\n<h2><a href=\"/").append(countryPath(country)).append("\">").append(esc(ctx.country(country))).append("</a></h2>\n");
            b.append(programTable(ctx, group, true));
            b.append("</section>\n");
        });
        b.append(homeCta(ctx));
        b.append("</main>\n");
        String path = majorPath(code);
        return new Page(path + ".html", KIND_MAJOR, html(ctx, name + "方向留学项目申请要求",
                name + "方向 " + members.size() + " 个留学项目的雅思要求、最近截止日与官网链接，数据由院校库生成并注明核对日期。", path,
                List.of(breadcrumbJsonLd(ctx, List.of(new String[] { "首页", "" }, new String[] { name, path }))), b.toString()));
    }

    /**
     * 截止月份聚合页上的一行：某项目某一轮
     */
    private record Round(Entry entry, LocalDate date, String name)
    {
    }

    /**
     * 未过期项目从今天起 12 个月内的轮次，按月分组（月份升序，月内按日期、校名）
     */
    private static Map<String, List<Round>> deadlineMonths(Ctx ctx, List<Entry> list)
    {
        LocalDate end = ctx.today.withDayOfMonth(1).plusMonths(DEADLINE_MONTHS);
        List<Round> rounds = new ArrayList<>();
        for (Entry e : list)
        {
            if (e.requirement() == null || AbrLookupTools.stale(e.program(), ctx.today))
            {
                continue;
            }
            for (JsonNode n : AbrAssessmentAnswers.object(e.requirement().getDeadlines()).path("rounds"))
            {
                LocalDate date = date(n.path("date").asString(""));
                if (date != null && !date.isBefore(ctx.today) && date.isBefore(end))
                {
                    rounds.add(new Round(e, date, n.path("name").asString("").trim()));
                }
            }
        }
        Comparator<Round> byEntry = Comparator.comparing(x -> x.entry(), ctx.order());
        rounds.sort(Comparator.comparing(Round::date).thenComparing(byEntry).thenComparing(Round::name));
        Map<String, List<Round>> months = new TreeMap<>();
        rounds.forEach(x -> months.computeIfAbsent(x.date().toString().substring(0, 7), k -> new ArrayList<>()).add(x));
        return months;
    }

    private static Page deadlinePage(Ctx ctx, String month, List<Round> rounds)
    {
        String name = monthLabel(month);
        StringBuilder b = new StringBuilder();
        b.append(breadcrumb(List.of(link("", "首页"), text(name + "截止"))));
        b.append("<main>\n<h1>").append(esc(name)).append("截止的留学项目</h1>\n");
        b.append("<p class=\"meta\">共 ").append(rounds.size()).append(" 个轮次 · 只列出 90 天内核对过官网的项目，截止日以官网为准</p>\n");
        b.append("<table>\n<thead><tr><th>截止日</th><th>项目</th><th>轮次</th><th>国家 / 地区</th></tr></thead>\n<tbody>\n");
        for (Round x : rounds)
        {
            b.append("<tr><td class=\"nw\">").append(x.date()).append("</td><td><a href=\"/").append(programPath(x.entry().program())).append("\">")
                    .append(esc(programName(x.entry()))).append("</a></td><td>").append(esc(x.name().isEmpty() ? "—" : x.name())).append("</td><td>")
                    .append(esc(ctx.country(x.entry().program().getCountry()))).append("</td></tr>\n");
        }
        b.append("</tbody>\n</table>\n");
        b.append(homeCta(ctx));
        b.append("</main>\n");
        String path = deadlinePath(month);
        return new Page(path + ".html", KIND_DEADLINE, html(ctx, name + "截止的留学项目",
                name + "截止申请的 " + rounds.size() + " 个留学项目轮次，按截止日排序，数据由院校库生成并注明核对日期。", path,
                List.of(breadcrumbJsonLd(ctx, List.of(new String[] { "首页", "" }, new String[] { name + "截止", path }))), b.toString()));
    }

    private static Page homePage(Ctx ctx, List<Entry> list, Map<String, List<Entry>> byCountry, List<String> majors, Map<String, List<Entry>> byMajor,
            Map<String, List<Round>> months)
    {
        StringBuilder b = new StringBuilder("<main>\n<h1>").append(esc(ctx.s.brand())).append(" · 留学项目库</h1>\n");
        b.append("<p>").append(list.size()).append(" 个留学项目的申请要求、截止日期与官网来源。每个数字都取自院校库，并注明核对日期；超过 90 天未核对的显示“以官网为准”。</p>\n");
        b.append(homeCta(ctx));
        b.append("<section>\n<h2>按国家 / 地区</h2>\n<ul class=\"links\">\n");
        byCountry.forEach((code, members) -> b.append("<li><a href=\"/").append(countryPath(code)).append("\">").append(esc(ctx.country(code)))
                .append("</a> <span class=\"count\">").append(members.size()).append("</span></li>\n"));
        b.append("</ul>\n</section>\n");
        if (!majors.isEmpty())
        {
            b.append("<section>\n<h2>按专业方向</h2>\n<ul class=\"links\">\n");
            for (String code : majors)
            {
                b.append("<li><a href=\"/").append(majorPath(code)).append("\">").append(esc(ctx.label("abr_major_direction", code)))
                        .append("</a> <span class=\"count\">").append(byMajor.get(code).size()).append("</span></li>\n");
            }
            b.append("</ul>\n</section>\n");
        }
        if (!months.isEmpty())
        {
            b.append("<section>\n<h2>按截止月份</h2>\n<ul class=\"links\">\n");
            months.forEach((month, rounds) -> b.append("<li><a href=\"/").append(deadlinePath(month)).append("\">").append(esc(monthLabel(month)))
                    .append("</a> <span class=\"count\">").append(rounds.size()).append("</span></li>\n"));
            b.append("</ul>\n</section>\n");
        }
        b.append("</main>\n");
        ObjectNode site = MAPPER.createObjectNode();
        site.put("@context", "https://schema.org");
        site.put("@type", "WebSite");
        site.put("name", ctx.s.brand() + " · 留学项目库");
        site.put("url", ctx.s.siteUrl());
        return new Page("index.html", KIND_HOME, html(ctx, ctx.s.brand() + " · 留学项目库：申请要求与截止日期",
                list.size() + " 个留学项目的申请要求、截止日期与官网来源，按国家 / 地区、专业方向、截止月份查看。", "", List.of(site), b.toString()));
    }

    private static Page notFoundPage(Ctx ctx)
    {
        String body = "<main>\n<h1>页面不存在</h1>\n<p>这个项目可能已下线或改名。<a href=\"/\">回到项目库首页</a></p>\n" + homeCta(ctx) + "</main>\n";
        return new Page("404.html", KIND_OTHER, html(ctx, "页面不存在", "页面不存在", null, List.of(), body));
    }

    private static String homeCta(Ctx ctx)
    {
        String link = AbrContentRules.toolLink(ctx.s.appUrl(), "quick", ctx.s.channel(), "home");
        return "<section class=\"cta\">\n<a class=\"btn primary\" href=\"" + esc(link) + "\">30 秒测一下我能申哪些学校</a>\n</section>\n";
    }

    private static String programTable(Ctx ctx, List<Entry> group, boolean withSchool)
    {
        StringBuilder b = new StringBuilder("<table>\n<thead><tr><th>项目</th><th class=\"nw\">学位</th><th>雅思</th><th>最近截止</th></tr></thead>\n<tbody>\n");
        for (Entry e : group)
        {
            AbrProgram p = e.program();
            boolean stale = AbrLookupTools.stale(p, ctx.today);
            String name = withSchool ? programName(e) : p.getProgramName();
            b.append("<tr><td><a href=\"/").append(programPath(p)).append("\">").append(esc(name)).append("</a></td><td class=\"nw\">")
                    .append(esc(ctx.label("abr_degree", p.getDegree()))).append("</td><td>")
                    .append(esc(stale || e.requirement() == null ? UNVERIFIED : AbrContentCards.value("ielts", p, e.requirement(), ctx.today))).append("</td><td>")
                    .append(esc(stale || e.requirement() == null ? UNVERIFIED : AbrContentCards.value("deadline", p, e.requirement(), ctx.today)))
                    .append("</td></tr>\n");
        }
        b.append("</tbody>\n</table>\n");
        return b.toString();
    }

    // ---------------------------------------------------------------- 站点地图、robots

    /**
     * 站点地图（只含 HTML 页面，不含 404）
     *
     * @param pages 页面
     * @param lastmod 路径 → 内容最后变化日期
     * @param siteUrl 站点地址
     * @return XML
     */
    static String sitemap(List<Page> pages, Map<String, LocalDate> lastmod, String siteUrl)
    {
        StringBuilder b = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (Page p : pages)
        {
            if (!isListed(p))
            {
                continue;
            }
            b.append("<url><loc>").append(esc(siteUrl + p.url())).append("</loc>");
            LocalDate d = lastmod.get(p.path());
            if (d != null)
            {
                b.append("<lastmod>").append(d).append("</lastmod>");
            }
            b.append("</url>\n");
        }
        return b.append("</urlset>\n").toString();
    }

    /**
     * robots.txt
     *
     * @param siteUrl 站点地址
     * @return 内容
     */
    static String robots(String siteUrl)
    {
        return "User-agent: *\nAllow: /\nDisallow: /404\n\nSitemap: " + siteUrl + "sitemap.xml\n";
    }

    /**
     * 是否列入站点地图、提交收录（HTML 页面，404 除外）
     *
     * @param p 页面
     * @return 是否列入
     */
    static boolean isListed(Page p)
    {
        return p.path().endsWith(".html") && !KIND_OTHER.equals(p.kind());
    }

    /**
     * 文件路径 → 页面地址：index.html → 空串，program/12.html → program/12
     *
     * @param path 文件路径
     * @return 地址
     */
    static String urlOf(String path)
    {
        if ("index.html".equals(path))
        {
            return "";
        }
        return path.endsWith(".html") ? path.substring(0, path.length() - 5) : path;
    }

    // ---------------------------------------------------------------- 页面骨架

    private static String html(Ctx ctx, String title, String description, String canonical, List<ObjectNode> jsonLd, String body)
    {
        StringBuilder b = new StringBuilder("<!doctype html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"utf-8\">\n");
        b.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        b.append("<title>").append(esc(title)).append(" | ").append(esc(ctx.s.brand())).append("</title>\n");
        b.append("<meta name=\"description\" content=\"").append(esc(description)).append("\">\n");
        if (canonical != null)
        {
            b.append("<link rel=\"canonical\" href=\"").append(esc(ctx.s.siteUrl() + canonical)).append("\">\n");
        }
        else
        {
            b.append("<meta name=\"robots\" content=\"noindex\">\n");
        }
        b.append("<link rel=\"stylesheet\" href=\"/assets/site.css?v=").append(VERSION).append("\">\n");
        for (ObjectNode n : jsonLd)
        {
            b.append("<script type=\"application/ld+json\">").append(MAPPER.writeValueAsString(n).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")).append("</script>\n");
        }
        b.append("</head>\n<body>\n<header><a class=\"brand\" href=\"/\">").append(esc(ctx.s.brand())).append("</a></header>\n");
        b.append(body);
        b.append("<footer>\n<p>本站项目数据取自工作室院校库，按官网核对后录入，不由 AI 生成；招生政策以各校官网最新公布为准。</p>\n<p>© ")
                .append(esc(ctx.s.brand())).append(" · <a href=\"/\">项目库首页</a></p>\n</footer>\n</body>\n</html>\n");
        return b.toString();
    }

    private static String breadcrumb(List<String> items)
    {
        return "<nav class=\"crumb\">" + String.join(" › ", items) + "</nav>\n";
    }

    private static String link(String path, String label)
    {
        return "<a href=\"/" + path + "\">" + esc(label) + "</a>";
    }

    private static String text(String label)
    {
        return "<span>" + esc(label) + "</span>";
    }

    private static ObjectNode breadcrumbJsonLd(Ctx ctx, List<String[]> items)
    {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("@context", "https://schema.org");
        n.put("@type", "BreadcrumbList");
        ArrayNode list = n.putArray("itemListElement");
        for (int i = 0; i < items.size(); i++)
        {
            ObjectNode item = list.addObject();
            item.put("@type", "ListItem");
            item.put("position", i + 1);
            item.put("name", items.get(i)[0]);
            item.put("item", ctx.s.siteUrl() + items.get(i)[1]);
        }
        return n;
    }

    private static String rows(List<String[]> rows)
    {
        StringBuilder b = new StringBuilder("<dl>\n");
        for (String[] r : rows)
        {
            b.append("<dt>").append(esc(r[0])).append("</dt><dd>").append(r[1]).append("</dd>\n");
        }
        return b.append("</dl>\n").toString();
    }

    private static String officialLink(String url, String prefix)
    {
        return isHttp(url) ? prefix + "<a href=\"" + esc(url.trim()) + "\" rel=\"nofollow noopener\" target=\"_blank\">项目官网</a>" : "";
    }

    // ---------------------------------------------------------------- 字段格式

    private static String otherLanguage(JsonNode v)
    {
        if (!v.isObject())
        {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (v.path("overall").isNumber())
        {
            parts.add(number(v.path("overall").decimalValue()) + (v.path("minSub").isNumber() ? "（单项 " + number(v.path("minSub").decimalValue()) + "）" : ""));
        }
        String level = v.path("level").asString("").trim();
        if (!level.isEmpty())
        {
            parts.add(level);
        }
        String note = v.path("note").asString("").trim();
        if (!note.isEmpty())
        {
            parts.add(note);
        }
        return String.join("，", parts);
    }

    private static String testDetail(JsonNode d)
    {
        List<String> parts = new ArrayList<>();
        if (d.path("gre").isNumber())
        {
            parts.add("GRE " + number(d.path("gre").decimalValue()));
        }
        if (d.path("gmat_focus").isNumber())
        {
            parts.add("GMAT Focus " + number(d.path("gmat_focus").decimalValue()));
        }
        if (d.path("gmat_classic").isNumber())
        {
            parts.add("GMAT " + number(d.path("gmat_classic").decimalValue()));
        }
        return String.join("，", parts);
    }

    private static String number(BigDecimal v)
    {
        return v.stripTrailingZeros().scale() <= 0 ? v.setScale(0, RoundingMode.HALF_UP).toPlainString() : v.stripTrailingZeros().toPlainString();
    }

    private static String verifiedText(AbrProgram p)
    {
        LocalDate d = day(p.getVerifiedTime());
        return d == null ? "尚未核对官网" : "数据核对于 " + d;
    }

    private static String programName(Entry e)
    {
        return e.school().getSchoolName() + " " + e.program().getProgramName();
    }

    private static String monthLabel(String month)
    {
        return month.substring(0, 4) + " 年 " + Integer.parseInt(month.substring(5, 7)) + " 月";
    }

    static String programPath(AbrProgram p)
    {
        return "program/" + p.getProgramId();
    }

    static String countryPath(String code)
    {
        return "country/" + StringUtils.defaultString(code).toLowerCase(Locale.ROOT);
    }

    static String majorPath(String code)
    {
        return "major/" + code;
    }

    static String deadlinePath(String month)
    {
        return "deadline/" + month;
    }

    private static boolean isHttp(String url)
    {
        return url != null && url.trim().matches("^https?://[^\\s\"'<>]+$");
    }

    private static LocalDate date(String text)
    {
        try
        {
            return LocalDate.parse(text);
        }
        catch (DateTimeParseException e)
        {
            return null;
        }
    }

    private static LocalDate day(Date d)
    {
        return d == null ? null : d.toInstant().atZone(AbrLookupTools.BEIJING).toLocalDate();
    }

    private static String joinNonBlank(String sep, String... parts)
    {
        List<String> list = new ArrayList<>();
        for (String p : parts)
        {
            if (StringUtils.isNotBlank(p))
            {
                list.add(p.trim());
            }
        }
        return String.join(sep, list);
    }

    /**
     * HTML / XML 转义
     *
     * @param s 文本
     * @return 转义后的文本
     */
    static String esc(String s)
    {
        if (s == null)
        {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    /**
     * 渲染上下文：字典标签、设置、今天
     */
    private static final class Ctx
    {
        final Map<String, Map<String, String>> dicts;

        final Settings s;

        final LocalDate today;

        Ctx(Map<String, Map<String, String>> dicts, Settings s, LocalDate today)
        {
            this.dicts = dicts;
            this.s = s;
            this.today = today;
        }

        String label(String type, String value)
        {
            if (value == null)
            {
                return "";
            }
            Map<String, String> m = dicts.get(type);
            return m == null ? value : m.getOrDefault(value, value);
        }

        String country(String code)
        {
            return label("abr_country", code);
        }

        int majorRank(String code)
        {
            return rank("abr_major_direction", code);
        }

        int rank(String type, String value)
        {
            Map<String, String> m = dicts.get(type);
            int i = 0;
            if (m != null)
            {
                for (String k : m.keySet())
                {
                    if (k.equals(value))
                    {
                        return i;
                    }
                    i++;
                }
            }
            return Integer.MAX_VALUE;
        }

        /** 项目排序：国家 / 地区（字典顺序）→ 院校英文名 → 院校 ID → 项目英文名 → 项目 ID */
        Comparator<Entry> order()
        {
            return Comparator.comparingInt((Entry e) -> rank("abr_country", e.program().getCountry()))
                    .thenComparing(e -> StringUtils.defaultString(e.program().getCountry()))
                    .thenComparing(e -> StringUtils.defaultString(e.school().getSchoolNameEn()).toLowerCase(Locale.ROOT))
                    .thenComparing(e -> e.school().getSchoolId())
                    .thenComparing(e -> StringUtils.defaultString(e.program().getProgramNameEn()).toLowerCase(Locale.ROOT))
                    .thenComparing(e -> e.program().getProgramId());
        }

        /** C 端工具入口：ch=渠道码，ct=p + 项目ID（项目页带来的访客按项目归因） */
        String toolLink(String hook, AbrProgram p)
        {
            return AbrContentRules.toolLink(s.appUrl(), hook, s.channel(), "p" + p.getProgramId());
        }

        String followLink(AbrProgram p)
        {
            String root = s.appUrl().endsWith("/") ? s.appUrl() : s.appUrl() + "/";
            return root + s.followPage() + "?programId=" + p.getProgramId() + "&ch=" + s.channel() + "&ct=p" + p.getProgramId();
        }
    }
}
