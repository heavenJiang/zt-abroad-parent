package com.ygb.abroad.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ygb.common.utils.StringUtils;

/**
 * 官方高校名录核对规则（纯计算，M2-13）：校名规范化、在名录原文中定位校名（原样截取作为引句）、在 OfS 名录表格中找学校
 *
 * @author ygb
 */
final class AbrRegistryRules
{
    /**
     * OfS 名录一行
     *
     * @param legalName 法定名称
     * @param tradingNames 对外使用名称
     * @param ukprn UKPRN
     * @param website 官网
     * @param line 存档中的原文行
     */
    record OfsRow(String legalName, List<String> tradingNames, String ukprn, String website, String line)
    {
    }

    private AbrRegistryRules()
    {
    }

    /**
     * 规范化：小写、统一撇号、& 视为 and、去掉开头的 the 与结尾的 “, the”、非字母数字视为空格
     */
    static String norm(String s)
    {
        String t = StringUtils.defaultString(s).replace('’', '\'').replace('‘', '\'').replace("&", " and ").toLowerCase(Locale.ROOT);
        t = t.replaceAll("[^a-z0-9']+", " ").trim();
        t = t.replaceFirst("^the ", "").replaceFirst(" the$", "");
        return t.replaceAll("\\s+", " ");
    }

    /**
     * 在名录原文中找校名，返回原文中的原样片段（便于在存档中高亮）；找不到为 null
     */
    static String quoteIn(String content, String name)
    {
        String n = norm(name);
        if (n.isEmpty() || StringUtils.isBlank(content))
        {
            return null;
        }
        StringBuilder re = new StringBuilder("(?i)(?<![a-z])(the\\s+)?");
        String[] words = n.split(" ");
        for (int i = 0; i < words.length; i++)
        {
            if (i > 0)
            {
                re.append("[\\s\\W_]+");
            }
            String w = words[i];
            re.append("and".equals(w) ? "(?:and|&)" : Pattern.quote(w).replace("'", "\\E['’]\\Q"));
        }
        re.append("(?![a-z])");
        Matcher m = Pattern.compile(re.toString()).matcher(content);
        return m.find() ? m.group() : null;
    }

    /**
     * 在 OfS 名录中找学校：法定名称或任一对外使用名称与校名一致；没有一致的再找法定名称包含校名的，唯一才算
     */
    static OfsRow findOfs(List<OfsRow> rows, String name)
    {
        String n = norm(name);
        List<OfsRow> exact = new ArrayList<>();
        List<OfsRow> contains = new ArrayList<>();
        for (OfsRow r : rows)
        {
            boolean hit = norm(r.legalName()).equals(n) || r.tradingNames().stream().anyMatch(t -> norm(t).equals(n));
            if (hit)
            {
                exact.add(r);
            }
            else if ((" " + norm(r.legalName()) + " ").contains(" " + n + " "))
            {
                contains.add(r);
            }
        }
        if (exact.size() == 1)
        {
            return exact.get(0);
        }
        return exact.isEmpty() && contains.size() == 1 ? contains.get(0) : null;
    }

    /**
     * 把 OfS 名录单元格里的多个对外名称（换行或分号分隔）拆开，去掉 “Not applicable”
     */
    static List<String> splitNames(String cell)
    {
        List<String> out = new ArrayList<>();
        for (String s : StringUtils.defaultString(cell).split("[\\r\\n;]+"))
        {
            String t = s.trim();
            if (!t.isEmpty() && !"not applicable".equalsIgnoreCase(t))
            {
                out.add(t);
            }
        }
        return out;
    }
}
