package com.ygb.abroad.growth.compliance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 合规词表（纯计算）：词、例外短语、正则三种规则，按平台生效
 * <ul>
 * <li>词：忽略大小写与空白匹配（“加 V”与“加v”相同）；</li>
 * <li>例外短语：同一类别内，落在例外短语里的命中不算（如“第一轮”不命中“第一”）；</li>
 * <li>正则：忽略大小写；同样受同类别例外短语约束；</li>
 * <li>平台：规则的平台为空时对所有平台与消息生效，否则只对所列平台生效。</li>
 * </ul>
 *
 * @author ygb
 */
public final class ComplianceRuleSet
{
    /** 匹配方式：词 */
    public static final String MATCH_WORD = "word";

    /** 匹配方式：例外短语 */
    public static final String MATCH_EXCEPTION = "exception";

    /** 匹配方式：正则 */
    public static final String MATCH_REGEX = "regex";

    /** 动作：拦截 */
    public static final String ACTION_BLOCK = "block";

    /** 动作：有引用依据放行，否则拦截 */
    public static final String ACTION_CITE = "cite";

    /** 命中时截取的上下文字数（前后各） */
    private static final int SNIPPET_CONTEXT = 12;

    /**
     * 一条规则
     *
     * @param ruleId 规则ID（可为 null）
     * @param category 类别（字典 abr_compliance_category）
     * @param matchType 匹配方式
     * @param pattern 词、例外短语或正则
     * @param action 动作（例外短语忽略）
     * @param platforms 生效平台（空为全部）
     */
    public record Rule(Long ruleId, String category, String matchType, String pattern, String action, Set<String> platforms)
    {
    }

    /**
     * 一次命中
     *
     * @param rule 规则
     * @param field 字段
     * @param matched 原文中命中的文字
     * @param snippet 上下文
     */
    public record Hit(Rule rule, String field, String matched, String snippet)
    {
    }

    private record Compiled(Rule rule, Pattern pattern)
    {
    }

    private final List<Compiled> rules = new ArrayList<>();

    private final Map<String, List<Compiled>> exceptions = new LinkedHashMap<>();

    /**
     * 编译规则
     *
     * @param list 规则（停用的由调用方先去掉）
     */
    public ComplianceRuleSet(List<Rule> list)
    {
        for (Rule r : list)
        {
            Compiled c = new Compiled(r, compile(r.matchType(), r.pattern()));
            if (MATCH_EXCEPTION.equals(r.matchType()))
            {
                exceptions.computeIfAbsent(r.category(), k -> new ArrayList<>()).add(c);
            }
            else
            {
                rules.add(c);
            }
        }
    }

    /**
     * 规则数（不含例外短语）
     *
     * @return 条数
     */
    public int size()
    {
        return rules.size();
    }

    /**
     * 把词或正则编译为匹配式；词按字逐个转义，字之间允许空白
     *
     * @param matchType 匹配方式
     * @param pattern 内容
     * @return 匹配式
     * @throws PatternSyntaxException 正则写法有误
     */
    public static Pattern compile(String matchType, String pattern)
    {
        if (MATCH_REGEX.equals(matchType))
        {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }
        StringBuilder sb = new StringBuilder();
        pattern.codePoints().filter(cp -> !Character.isWhitespace(cp)).forEach(cp -> {
            if (sb.length() > 0)
            {
                sb.append("\\s*");
            }
            sb.append(Pattern.quote(new String(Character.toChars(cp))));
        });
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /**
     * 检查一段文字
     *
     * @param field 字段名（写入命中记录）
     * @param text 文字
     * @param platform 平台（null 只用全平台规则）
     * @return 命中（同一规则在同一字段只记一次）
     */
    public List<Hit> scan(String field, String text, String platform)
    {
        List<Hit> hits = new ArrayList<>();
        if (text == null || text.isEmpty())
        {
            return hits;
        }
        Map<String, List<int[]>> spans = new LinkedHashMap<>();
        for (Compiled c : rules)
        {
            if (!applies(c.rule(), platform))
            {
                continue;
            }
            List<int[]> excepted = spans.computeIfAbsent(c.rule().category(), k -> exceptionSpans(k, text, platform));
            Matcher m = c.pattern().matcher(text);
            while (m.find())
            {
                if (m.end() == m.start())
                {
                    continue;
                }
                if (!inside(excepted, m.start(), m.end()))
                {
                    hits.add(new Hit(c.rule(), field, m.group(), snippet(text, m.start(), m.end())));
                    break;
                }
            }
        }
        return hits;
    }

    private List<int[]> exceptionSpans(String category, String text, String platform)
    {
        List<int[]> spans = new ArrayList<>();
        for (Compiled c : exceptions.getOrDefault(category, List.of()))
        {
            if (!applies(c.rule(), platform))
            {
                continue;
            }
            Matcher m = c.pattern().matcher(text);
            while (m.find())
            {
                spans.add(new int[] { m.start(), m.end() });
            }
        }
        return spans;
    }

    private static boolean applies(Rule r, String platform)
    {
        return r.platforms() == null || r.platforms().isEmpty() || (platform != null && r.platforms().contains(platform));
    }

    private static boolean inside(List<int[]> spans, int start, int end)
    {
        for (int[] s : spans)
        {
            if (s[0] <= start && end <= s[1])
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 命中处前后各若干字的上下文（换行替换为空格）
     *
     * @param text 原文
     * @param start 开始
     * @param end 结束
     * @return 上下文
     */
    static String snippet(String text, int start, int end)
    {
        int from = Math.max(0, start - SNIPPET_CONTEXT);
        int to = Math.min(text.length(), end + SNIPPET_CONTEXT);
        return (from > 0 ? "…" : "") + text.substring(from, to).replaceAll("\\s+", " ") + (to < text.length() ? "…" : "");
    }
}
