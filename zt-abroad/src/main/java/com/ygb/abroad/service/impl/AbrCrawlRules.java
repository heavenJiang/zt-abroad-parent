package com.ygb.abroad.service.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ygb.abroad.integration.AbrHtmlText;
import com.ygb.common.utils.StringUtils;

/**
 * 项目采集的网址与链接规则（纯计算，M2-08）
 * <ul>
 * <li>只采本校站点（与官网同一可注册域名，如 ucl.ac.uk、hku.hk、nus.edu.sg）；</li>
 * <li>项目页：网址最后一段带硕士学位标记（msc、ma、master-of…），或链接文字带硕士学位；新闻、活动、研究、人员、博士、本科等排除；</li>
 * <li>目录页：网址含研究生授课、课程目录字样但没有具体项目，用来继续找项目链接（最多两层）。</li>
 * </ul>
 *
 * @author ygb
 */
final class AbrCrawlRules
{
    /** 链接：网址与文字 */
    record Link(String url, String text)
    {
    }

    static final String KIND_PROGRAM = "program";

    static final String KIND_DIRECTORY = "directory";

    private static final Pattern EXCLUDE = Pattern.compile(
            "(?i)/(news|events?|blogs?|stories|story|research|people|staff|profiles?|alumni|press|media-centre|jobs|vacancies|careers-service|library|"
                    + "phd|doctoral|doctorate|undergraduate|ug|foundation|short-courses?|cpd|executive-education|summer-school|search|login|"
                    + "tag|tags|category|feed|print|sitemap|cookies|privacy|accessibility|fees-and-funding|scholarships?|accommodation|engage|outreach|"
                    + "widening-participation|policy-lab|public-engagement|community|museums?|collections|exhibitions?|schools-and-colleges|"
                    + "postgraduate-research|research-degrees|research-programmes|pgr|research-areas|"
                    + "continuing-education|online-short-courses|open-days?|visit|whats-on|support|services|about|governance|giving|user|apply)(/|$|\\?)");

    private static final Pattern PATH_HINT = Pattern.compile("(?i)(study|course|programme|program|degree|postgraduate|graduate|masters?|taught|/pg|tpg|admission)");

    /** 网址最后一段中的硕士学位标记（以 - _ / 或首尾分隔） */
    private static final Pattern SLUG_DEGREE = Pattern.compile(
            "(?i)(^|[-_/.])(msc|ma|mres|meng|mba|llm|mfin|macc|mpa|mph|mpp|mphil|msw|med|mst|mfa|mdes|msocsc|mcomm|mcom|mds|mem|mfe|"
                    + "master-of|masters-of|masters-in|master-in)([-_/.]|$)");

    /** 目录页：同时有研究生授课类字样和课程类字样 */
    private static final Pattern DIRECTORY_LEVEL = Pattern.compile("(?i)(taught|postgraduate|masters|graduate|(^|[-_/])pg([-_/]|$)|tpg)");

    private static final Pattern DIRECTORY_COURSE = Pattern.compile("(?i)(course|programme|program|degree|study|studying)");

    private static final Pattern HREF = Pattern.compile("(?is)<a\\b[^>]*?href\\s*=\\s*([\"'])(.*?)\\1[^>]*>(.*?)</a>");

    private AbrCrawlRules()
    {
    }

    /**
     * 可注册域名（粗略）：二级为 ac / edu / co / gov / org 等的国家域名取后三段，否则取后两段
     */
    static String siteDomain(String url)
    {
        String host;
        try
        {
            host = URI.create(url).getHost();
        }
        catch (IllegalArgumentException e)
        {
            return "";
        }
        if (host == null)
        {
            return "";
        }
        String[] p = host.toLowerCase(Locale.ROOT).split("\\.");
        if (p.length >= 3 && Set.of("ac", "edu", "co", "gov", "org", "com", "net").contains(p[p.length - 2]) && p[p.length - 1].length() == 2)
        {
            return p[p.length - 3] + "." + p[p.length - 2] + "." + p[p.length - 1];
        }
        return p.length >= 2 ? p[p.length - 2] + "." + p[p.length - 1] : host;
    }

    static boolean sameSite(String url, String website)
    {
        String d = siteDomain(website);
        try
        {
            String host = URI.create(url).getHost();
            return !d.isEmpty() && host != null && (host.equalsIgnoreCase(d) || host.toLowerCase(Locale.ROOT).endsWith("." + d));
        }
        catch (IllegalArgumentException e)
        {
            return false;
        }
    }

    /**
     * 网址分类：program 项目页候选 / directory 目录页 / null 不采
     */
    static String classify(String url)
    {
        String path = path(url);
        if (path == null || EXCLUDE.matcher(path).find() || path.matches("(?i).*\\.(pdf|jpg|jpeg|png|gif|svg|zip|docx?|xlsx?|pptx?|mp4|ics)$"))
        {
            return null;
        }
        String last = lastSegment(path);
        if (!PATH_HINT.matcher(path).find())
        {
            return null;
        }
        if (SLUG_DEGREE.matcher(last).find())
        {
            return KIND_PROGRAM;
        }
        return DIRECTORY_LEVEL.matcher(path).find() && DIRECTORY_COURSE.matcher(path).find() ? KIND_DIRECTORY : null;
    }

    /**
     * 链接是否像项目：链接文字带硕士学位（且不是博士、本科、证书），或网址像项目页
     */
    static boolean programLink(Link l)
    {
        String text = StringUtils.defaultString(l.text());
        boolean textHit = text.length() <= 200 && AbrProgramRuleExtractor.MASTER.matcher(text).find()
                && !AbrProgramRuleExtractor.NOT_TAUGHT.matcher(text).find();
        if (AbrProgramRuleExtractor.NOT_TAUGHT.matcher(text).find() || AbrProgramRuleExtractor.NOT_NAME.matcher(text).find())
        {
            return false;
        }
        String kind = classify(l.url());
        return KIND_PROGRAM.equals(kind) || (textHit && kind != null) || (textHit && path(l.url()) != null && !EXCLUDE.matcher(path(l.url())).find());
    }

    /**
     * 页面中的链接（绝对网址、去掉 # 片段、按网址去重；文字为纯文本）
     */
    static List<Link> links(String html, String baseUrl)
    {
        Map<String, Link> out = new LinkedHashMap<>();
        if (StringUtils.isBlank(html))
        {
            return List.of();
        }
        URI base;
        try
        {
            base = URI.create(baseUrl);
        }
        catch (IllegalArgumentException e)
        {
            return List.of();
        }
        Matcher m = HREF.matcher(html);
        while (m.find() && out.size() < 5000)
        {
            String href = AbrHtmlText.decodeEntities(m.group(2).trim());
            if (href.isEmpty() || href.startsWith("#") || href.toLowerCase(Locale.ROOT).startsWith("javascript:") || href.startsWith("mailto:")
                    || href.startsWith("tel:"))
            {
                continue;
            }
            String abs;
            try
            {
                abs = base.resolve(href.replace(" ", "%20")).toString();
            }
            catch (IllegalArgumentException e)
            {
                continue;
            }
            int hash = abs.indexOf('#');
            if (hash >= 0)
            {
                abs = abs.substring(0, hash);
            }
            if (!abs.startsWith("http"))
            {
                continue;
            }
            String text = AbrHtmlText.decodeEntities(m.group(3).replaceAll("(?s)<[^>]+>", " ")).replaceAll("\\s+", " ").trim();
            out.putIfAbsent(abs, new Link(abs, StringUtils.substring(text, 0, 200)));
        }
        return new ArrayList<>(out.values());
    }

    /**
     * 网址规范化：同站的 http 改为 https，去掉末尾的 /（根路径除外）
     */
    static String normalize(String url)
    {
        String u = url.trim();
        if (u.startsWith("http://"))
        {
            u = "https://" + u.substring("http://".length());
        }
        int q = u.indexOf('?');
        String head = q >= 0 ? u.substring(0, q) : u;
        String tail = q >= 0 ? u.substring(q) : "";
        if (head.endsWith("/") && head.chars().filter(c -> c == '/').count() > 3)
        {
            head = head.substring(0, head.length() - 1);
        }
        return head + tail;
    }

    static String path(String url)
    {
        try
        {
            URI u = URI.create(url);
            String p = StringUtils.defaultString(u.getRawPath());
            return u.getRawQuery() == null ? p : p + "?" + u.getRawQuery();
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    private static String lastSegment(String path)
    {
        String p = path.replaceAll("/+$", "");
        int q = p.indexOf('?');
        String noQuery = q >= 0 ? p.substring(0, q) : p;
        int i = noQuery.lastIndexOf('/');
        return i >= 0 ? noQuery.substring(i + 1) : noQuery;
    }
}
