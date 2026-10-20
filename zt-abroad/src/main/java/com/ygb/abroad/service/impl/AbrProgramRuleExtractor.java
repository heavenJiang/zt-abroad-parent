package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 项目页规则抽取（纯计算，M2-08）：从项目页纯文本中抽取格式固定的字段，输出与 catalog_extractor 相同的结构
 * {字段: {value, quote}}，再交给 {@link AbrAiPrefillChecker} 校验（引句须在原文中出现、取值合规）。
 * <ul>
 * <li>只抽能可靠识别的：项目英文名、学位（硕士）、学制、国际学生学费与币种、入学季、雅思总分与小分、托福、截止日、授课语言；</li>
 * <li>均分、先修课、跨专业等写法多样的字段不抽（交给大模型），宁可不抽也不猜；</li>
 * <li>引句取原文中的整行（过长时取匹配处前后的片段），便于核对。</li>
 * </ul>
 *
 * @author ygb
 */
final class AbrProgramRuleExtractor
{
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    /** 规则版本：规则改动后加一，已采集的页面在下次抓取时重新抽取 */
    static final int VERSION = 3;

    /** 引句最长字符数（与校验器一致） */
    static final int MAX_QUOTE = 480;

    /** 硕士学位标记（标题或项目名中出现即视为授课型硕士） */
    static final Pattern MASTER = Pattern.compile(
            "(?i)\\b(MSc|M\\.Sc\\.?|MA|M\\.A\\.|MRes|MEng|MBA|LLM|LL\\.M\\.?|MPA|MPH|MPP|MFin|MAcc|MArch|MEd|MSW|MSt|MFA|MMus|MDes|MMgt|MPS|MCom|MDS|MEM|MFE|MPhil|Masters? (?:of|in) [A-Z][A-Za-z]+)\\b");

    /** 不是项目名的标题（资助、学费、常见问题、申请指南、学生故事等） */
    static final Pattern NOT_NAME = Pattern.compile("(?i)\\b(funding|fees?|scholarships?|faqs?|how to|apply|applying|why|meet|matter|stories|student life|open day|events?|news)\\b");

    /** 研究型或非硕士（不采集） */
    static final Pattern NOT_TAUGHT = Pattern.compile("(?i)\\b(PhD|DPhil|Doctor|Doctorate|EdD|DBA|Bachelor|BSc|BA \\(Hons\\)|Undergraduate|Foundation|Short course|Masterclass(es)?)\\b");


    private static final Pattern IELTS_OVERALL = Pattern.compile("(?i)IELTS[^\\n]{0,80}?(?:overall|total|score|band|minimum|of|:)?\\s*(?:score\\s*)?(?:of\\s*)?([4-9](?:\\.[05])?)\\b");

    private static final Pattern SUB_CLAUSE = Pattern.compile("(?i)(each|every|all|any|component|components|band|bands|sub-?score|sub-?test|section|sections|skill|element|module|less than|lower than|below|remaining)");

    private static final Pattern IELTS_NUM = Pattern.compile("\\b([4-9](?:\\.[05])?)\\b");

    private static final Pattern TOEFL = Pattern.compile("(?i)TOEFL");

    /** TOEFL 总分：优先取 total / overall / score / minimum 之后的数 */
    private static final Pattern TOEFL_LABELLED = Pattern.compile("(?i)(?:total|overall|score|minimum)\\s*(?:score\\s*)?(?:of\\s*)?:?\\s*(\\d{2,3})\\b");

    private static final Pattern TWO_THREE_DIGITS = Pattern.compile("\\b(\\d{2,3})\\b");

    private static final Pattern DURATION = Pattern.compile(
            "(?i)\\b(\\d{1,2}(?:\\.\\d)?|one|two|three|1\\.5)\\s*(?:-|\\s)?\\s*(calendar\\s+)?(years?|months?)\\b");

    private static final Pattern MONEY = Pattern.compile("(£|GBP|HK\\$|HKD|S\\$|SGD|A\\$|AUD|AU\\$|US\\$|USD|\\$)\\s?(\\d{1,3}(?:,\\d{3})+|\\d{4,6})(?:\\.\\d{2})?");

    private static final Pattern DATE_DMY = Pattern.compile(
            "(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\\.?,?\\s+(20\\d{2})\\b");

    private static final Pattern DATE_MDY = Pattern.compile(
            "(?i)\\b(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(20\\d{2})\\b");

    private static final Pattern MONTH = Pattern.compile(
            "(?i)\\b(January|February|March|April|May|June|July|August|September|October|November|December)\\b");

    private AbrProgramRuleExtractor()
    {
    }

    /**
     * 页面是否为授课型硕士项目页：标题含硕士标记且不是博士 / 本科 / 证书等，正文提到入学要求或语言要求或学费
     *
     * @param title 页面标题
     * @param text 页面纯文本
     * @return 是项目页为 true
     */
    static boolean isProgramPage(String title, String text)
    {
        String name = programName(title);
        if (name == null || NOT_TAUGHT.matcher(name).find())
        {
            return false;
        }
        String t = StringUtils.defaultString(text).toLowerCase(Locale.ROOT);
        return t.contains("entry requirement") || t.contains("admission requirement") || t.contains("ielts") || t.contains("english language")
                || t.contains("tuition") || t.contains("fees");
    }

    private static final Pattern ONLINE = Pattern.compile("(?i)\\b(online|distance learning|distance-learning|by distance|e-learning|blended online)\\b");

    private static final Pattern ACADEMIC_YEAR = Pattern.compile("\\b(20\\d{2})\\s*[-/–]\\s*(?:20)?\\d{2}\\b");

    /**
     * 不采集的项目页：线上 / 远程项目（留服不认证线上学位）、标题为往年学年的旧页面
     *
     * @param title 页面标题
     * @param thisYear 今年
     * @return 原因；可以采集为 null
     */
    static String skipReason(String title, int thisYear)
    {
        String t = StringUtils.defaultString(title);
        if (ONLINE.matcher(t).find())
        {
            return "线上 / 远程项目不采集（留服不认证线上学位）";
        }
        Matcher y = ACADEMIC_YEAR.matcher(t);
        if (y.find() && Integer.parseInt(y.group(1)) < thisYear - 1)
        {
            return "页面为往年（" + y.group() + "），不采集";
        }
        return null;
    }

    /**
     * 从标题中取项目名：按 | – — - 分段，取第一个含硕士标记的段
     */
    static String programName(String title)
    {
        if (StringUtils.isBlank(title))
        {
            return null;
        }
        for (String seg : title.split("\\s+[|–—-]\\s+|\\s*\\|\\s*"))
        {
            String s = seg.trim();
            if (s.length() >= 4 && s.length() <= 200 && MASTER.matcher(s).find() && !NOT_NAME.matcher(s).find())
            {
                return s;
            }
        }
        return null;
    }

    /**
     * 抽取
     *
     * @param title 页面标题
     * @param text 页面纯文本（第一行为 “Title: 标题”，便于引句核对）
     * @param country 国家（决定 $ 的币种、默认授课语言）
     * @return {字段: {value, quote}}
     */
    static ObjectNode extract(String title, String text, String country)
    {
        ObjectNode out = F.objectNode();
        List<String> lines = lines(text);
        String name = programName(title);
        if (name != null)
        {
            put(out, "programNameEn", F.stringNode(name), name);
            put(out, "degree", F.stringNode("master"), name);
        }
        duration(out, lines);
        tuition(out, lines, country);
        intakes(out, lines);
        ielts(out, lines);
        toefl(out, lines);
        deadlines(out, lines);
        return out;
    }

    private static void put(ObjectNode out, String field, tools.jackson.databind.JsonNode value, String quote)
    {
        ObjectNode f = out.putObject(field);
        f.set("value", value);
        f.put("quote", quote);
    }

    static List<String> lines(String text)
    {
        List<String> out = new ArrayList<>();
        for (String l : StringUtils.defaultString(text).split("\\r?\\n"))
        {
            String t = l.trim();
            if (!t.isEmpty())
            {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * 引句：整行不超过上限时取整行，否则取匹配处前后的片段（在空白处截断）
     */
    static String quote(String line, int at)
    {
        if (line.length() <= MAX_QUOTE)
        {
            return line;
        }
        int from = Math.max(0, at - 160);
        int to = Math.min(line.length(), from + MAX_QUOTE - 20);
        while (from > 0 && !Character.isWhitespace(line.charAt(from)))
        {
            from++;
        }
        while (to < line.length() && to > from + 40 && !Character.isWhitespace(line.charAt(to - 1)))
        {
            to--;
        }
        return line.substring(from, to).trim();
    }

    private static void duration(ObjectNode out, List<String> lines)
    {
        for (int pass = 0; pass < 2; pass++)
        {
            for (int i = 0; i < lines.size(); i++)
            {
                String l = lines.get(i);
                String prev = i > 0 ? lines.get(i - 1).toLowerCase(Locale.ROOT) : "";
                String low = l.toLowerCase(Locale.ROOT);
                boolean strong = low.contains("full-time") || low.contains("full time") || low.startsWith("duration") || "duration".equals(prev)
                        || low.contains("| start date");
                if ((pass == 0 && !strong) || l.length() > 300)
                {
                    continue;
                }
                if (low.contains("part-time") && !low.contains("full-time"))
                {
                    continue;
                }
                Matcher m = DURATION.matcher(l);
                if (m.find())
                {
                    BigDecimal n = number(m.group(1));
                    boolean years = m.group(3).toLowerCase(Locale.ROOT).startsWith("year");
                    int months = years ? n.multiply(BigDecimal.valueOf(12)).intValue() : n.intValue();
                    if (months >= 6 && months <= 48)
                    {
                        put(out, "durationMonths", F.numberNode(months), quote(l, m.start()));
                        return;
                    }
                }
            }
        }
    }

    private static BigDecimal number(String s)
    {
        return switch (s.toLowerCase(Locale.ROOT))
        {
            case "one" -> BigDecimal.ONE;
            case "two" -> BigDecimal.valueOf(2);
            case "three" -> BigDecimal.valueOf(3);
            default -> new BigDecimal(s);
        };
    }

    private static void tuition(ObjectNode out, List<String> lines, String country)
    {
        for (int i = 0; i < lines.size(); i++)
        {
            String l = lines.get(i);
            // 标签与金额分两行（如 “Overseas tuition fees (2026/27)” 下一行 “£42,700”）
            if (!MONEY.matcher(l).find() && i + 1 < lines.size() && lines.get(i + 1).length() <= 40 && MONEY.matcher(lines.get(i + 1)).find())
            {
                String low0 = l.toLowerCase(Locale.ROOT);
                if ((low0.contains("international") || low0.contains("overseas") || low0.contains("non-local")) && low0.contains("fee")
                        && !low0.contains("deposit") && !low0.contains("application"))
                {
                    Matcher m = MONEY.matcher(lines.get(i + 1));
                    m.find();
                    BigDecimal amount = new BigDecimal(m.group(2).replace(",", ""));
                    String cur = currency(m.group(1), country);
                    if (cur != null && amount.compareTo(BigDecimal.valueOf(3000)) >= 0)
                    {
                        put(out, "tuition", F.numberNode(amount), lines.get(i + 1));
                        put(out, "currency", F.stringNode(cur), lines.get(i + 1));
                        return;
                    }
                }
            }
            String low = l.toLowerCase(Locale.ROOT);
            if (l.length() > 300 || !(low.contains("international") || low.contains("overseas") || low.contains("non-local") || low.contains("non-eu")))
            {
                continue;
            }
            if (low.contains("deposit") || low.contains("application fee") || low.contains("scholarship") || low.contains("discount"))
            {
                continue;
            }
            Matcher m = MONEY.matcher(l);
            if (m.find())
            {
                BigDecimal amount = new BigDecimal(m.group(2).replace(",", ""));
                String cur = currency(m.group(1), country);
                if (cur != null && amount.compareTo(BigDecimal.valueOf(3000)) >= 0)
                {
                    String q = quote(l, m.start());
                    put(out, "tuition", F.numberNode(amount), q);
                    put(out, "currency", F.stringNode(cur), q);
                    return;
                }
            }
        }
    }

    static String currency(String symbol, String country)
    {
        String s = symbol.toUpperCase(Locale.ROOT);
        return switch (s)
        {
            case "£", "GBP" -> "GBP";
            case "HK$", "HKD" -> "HKD";
            case "S$", "SGD" -> "SGD";
            case "A$", "AU$", "AUD" -> "AUD";
            case "US$", "USD" -> "USD";
            case "$" -> Map.of("HK", "HKD", "SG", "SGD", "AU", "AUD", "US", "USD").get(country);
            default -> null;
        };
    }

    private static void intakes(ObjectNode out, List<String> lines)
    {
        Set<String> codes = new LinkedHashSet<>();
        String q = null;
        for (int i = 0; i < lines.size(); i++)
        {
            String l = lines.get(i);
            String low = l.toLowerCase(Locale.ROOT);
            boolean label = low.startsWith("course starts") || low.startsWith("start date") || low.startsWith("intake") || low.startsWith("commencement")
                    || low.contains("| start date") || low.startsWith("entry:") || low.startsWith("starts") || low.startsWith("programme starts")
                    || low.startsWith("program starts") || low.startsWith("programme start") || low.startsWith("start of programme");
            if (!label || l.length() > 200)
            {
                continue;
            }
            String target = l;
            Matcher m = MONTH.matcher(target);
            if (!m.find() && i + 1 < lines.size())
            {
                target = lines.get(i + 1);
                m = MONTH.matcher(target);
                if (!m.find())
                {
                    continue;
                }
            }
            else if (!m.find(0))
            {
                continue;
            }
            m.reset();
            while (m.find())
            {
                codes.add(season(Month.valueOf(m.group(1).toUpperCase(Locale.ROOT))));
            }
            if (q == null)
            {
                q = target;
            }
        }
        if (!codes.isEmpty() && q != null)
        {
            ArrayNode arr = F.arrayNode();
            codes.forEach(arr::add);
            put(out, "intakes", arr, q.length() > MAX_QUOTE ? q.substring(0, MAX_QUOTE) : q);
        }
    }

    /**
     * 入学月份 → 入学季：9–10 月秋季，1–3 月春季，4–7 月夏季，11–12 月冬季
     */
    static String season(Month m)
    {
        return switch (m)
        {
            case SEPTEMBER, OCTOBER -> "fall";
            case JANUARY, FEBRUARY, MARCH -> "spring";
            case APRIL, MAY, JUNE, JULY, AUGUST -> "summer";
            default -> "winter";
        };
    }

    private static void ielts(ObjectNode out, List<String> lines)
    {
        for (String l : lines)
        {
            if (!l.toUpperCase(Locale.ROOT).contains("IELTS") || l.toLowerCase(Locale.ROOT).contains("one skill retake") && !l.matches("(?s).*\\d\\.\\d.*"))
            {
                continue;
            }
            Matcher m = IELTS_OVERALL.matcher(l);
            if (!m.find())
            {
                continue;
            }
            BigDecimal overall = new BigDecimal(m.group(1));
            if (overall.compareTo(BigDecimal.valueOf(5)) < 0 || overall.compareTo(BigDecimal.valueOf(8.5)) > 0)
            {
                continue;
            }
            // 小分：总分之后出现“each / component / no less than”等说法的分句中的最小数
            String rest = l.substring(m.end());
            BigDecimal min = null;
            Matcher sub = SUB_CLAUSE.matcher(rest);
            if (sub.find())
            {
                String clause = rest.substring(Math.max(0, sub.start() - 40), Math.min(rest.length(), sub.end() + 120));
                Matcher n = IELTS_NUM.matcher(clause);
                while (n.find())
                {
                    BigDecimal v = new BigDecimal(n.group(1));
                    if (v.compareTo(overall) <= 0 && v.compareTo(BigDecimal.valueOf(4.5)) >= 0 && (min == null || v.compareTo(min) < 0))
                    {
                        min = v;
                    }
                }
            }
            String q = quote(l, m.start());
            put(out, "ieltsOverall", F.numberNode(overall), q);
            if (min != null)
            {
                put(out, "ieltsMinSub", F.numberNode(min), q);
            }
            return;
        }
    }

    private static void toefl(ObjectNode out, List<String> lines)
    {
        for (String l : lines)
        {
            Matcher m = TOEFL.matcher(l);
            if (!m.find() || l.length() > 600)
            {
                continue;
            }
            String after = l.substring(m.end());
            Matcher num = TOEFL_LABELLED.matcher(after);
            int overall = -1;
            int end = -1;
            if (num.find() && Integer.parseInt(num.group(1)) >= 60 && Integer.parseInt(num.group(1)) <= 120)
            {
                overall = Integer.parseInt(num.group(1));
                end = num.end();
            }
            else
            {
                Matcher any = TWO_THREE_DIGITS.matcher(after);
                while (any.find())
                {
                    int v = Integer.parseInt(any.group(1));
                    if (v >= 60 && v <= 120)
                    {
                        overall = v;
                        end = any.end();
                        break;
                    }
                }
            }
            if (overall < 0)
            {
                continue;
            }
            Integer min = null;
            String rest = after.substring(end);
            Matcher sub = SUB_CLAUSE.matcher(rest);
            if (sub.find())
            {
                String clause = rest.substring(Math.max(0, sub.start() - 40), Math.min(rest.length(), sub.end() + 120));
                Matcher n = Pattern.compile("\\b(\\d{2})\\b").matcher(clause);
                while (n.find())
                {
                    int v = Integer.parseInt(n.group(1));
                    if (v >= 12 && v <= 30 && (min == null || v < min))
                    {
                        min = v;
                    }
                }
            }
            ObjectNode o = F.objectNode();
            ObjectNode t = o.putObject("toefl");
            t.put("overall", overall);
            if (min != null)
            {
                t.put("minSub", min);
            }
            put(out, "otherLanguage", o, quote(l, m.start()));
            return;
        }
    }

    private static void deadlines(ObjectNode out, List<String> lines)
    {
        List<LocalDate> found = new ArrayList<>();
        String q = null;
        for (int i = 0; i < lines.size(); i++)
        {
            String l = lines.get(i);
            String low = l.toLowerCase(Locale.ROOT);
            if (!isDeadlineLabel(low) || low.contains("scholarship") || low.contains("accommodation") || low.contains("deposit"))
            {
                continue;
            }
            // 同一行：截止字样之后的日期
            int at = deadlineIndex(low);
            List<LocalDate> same = dates(l.substring(at));
            String target = l;
            if (same.isEmpty() && l.length() <= 40)
            {
                // 表格：连续的短表头（如“Programme start date / Application deadline”），下面的值按列对应
                int start = i;
                while (start > 0 && i - start < 3 && isHeader(lines.get(start - 1)))
                {
                    start--;
                }
                int cols = i - start + 1;
                int col = i - start;
                for (int row = 0; row < 6; row++)
                {
                    int valueLine = i + 1 + row * cols + col;
                    if (valueLine >= lines.size())
                    {
                        break;
                    }
                    List<LocalDate> v = dates(lines.get(valueLine));
                    if (v.isEmpty())
                    {
                        break;
                    }
                    same.add(v.get(0));
                    if (target == l)
                    {
                        target = lines.get(valueLine);
                    }
                }
            }
            for (LocalDate d : same)
            {
                if (!found.contains(d) && found.size() < 6)
                {
                    found.add(d);
                }
            }
            if (!same.isEmpty() && q == null)
            {
                q = target.length() > MAX_QUOTE ? target.substring(0, MAX_QUOTE) : target;
            }
        }
        if (!found.isEmpty() && q != null)
        {
            ArrayNode rounds = F.arrayNode();
            for (int k = 0; k < found.size(); k++)
            {
                rounds.addObject().put("name", found.size() == 1 ? "Application deadline" : "Round " + (k + 1)).put("date", found.get(k).toString());
            }
            ObjectNode v = F.objectNode();
            v.put("rolling", false);
            v.set("rounds", rounds);
            put(out, "deadlines", v, q);
        }
    }

    private static boolean isDeadlineLabel(String low)
    {
        return low.contains("deadline") || low.contains("closing date") || low.contains("apply by") || low.contains("applications close");
    }

    private static int deadlineIndex(String low)
    {
        for (String k : List.of("deadline", "closing date", "apply by", "applications close"))
        {
            int i = low.indexOf(k);
            if (i >= 0)
            {
                return i;
            }
        }
        return 0;
    }

    /**
     * 表头行：短、没有数字的标签（如 “Programme start date”）
     */
    private static boolean isHeader(String line)
    {
        return line.length() <= 40 && !line.matches(".*\\d.*") && !line.endsWith(".") && HEADER_WORD.matcher(line).find();
    }

    /** 表头列名字样 */
    private static final Pattern HEADER_WORD = Pattern.compile("(?i)\\b(date|start|starts|round|intake|entry|open|opens|close|closes|decision|stage)\\b");

    static List<LocalDate> dates(String s)
    {
        List<LocalDate> out = new ArrayList<>();
        Matcher a = DATE_DMY.matcher(s);
        while (a.find())
        {
            LocalDate d = date(a.group(3), a.group(2), a.group(1));
            if (d != null)
            {
                out.add(d);
            }
        }
        Matcher b = DATE_MDY.matcher(s);
        while (b.find())
        {
            LocalDate d = date(b.group(3), b.group(1), b.group(2));
            if (d != null && !out.contains(d))
            {
                out.add(d);
            }
        }
        return out;
    }

    private static LocalDate date(String y, String mon, String d)
    {
        String m = mon.toLowerCase(Locale.ROOT);
        for (Month month : Month.values())
        {
            String full = month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase(Locale.ROOT);
            if (full.equals(m) || full.startsWith(m.length() >= 3 ? m.substring(0, 3) : m))
            {
                try
                {
                    return LocalDate.of(Integer.parseInt(y), month, Integer.parseInt(d));
                }
                catch (RuntimeException e)
                {
                    return null;
                }
            }
        }
        return null;
    }
}
