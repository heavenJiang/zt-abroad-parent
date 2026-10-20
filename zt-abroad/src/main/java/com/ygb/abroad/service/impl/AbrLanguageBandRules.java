package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学校英语等级说明页解析（纯计算，M2-08）：等级 → 雅思总分、最低小分与原文引句
 *
 * @author ygb
 */
final class AbrLanguageBandRules
{
    /**
     * 一个等级的要求
     *
     * @param overall 总分
     * @param minSub 最低小分（没写为 null）
     * @param quote 原文引句
     */
    record Band(BigDecimal overall, BigDecimal minSub, String quote)
    {
    }

    private static final Pattern SCORE = Pattern.compile("\\b([4-9](?:\\.[05])?)\\b");

    private AbrLanguageBandRules()
    {
    }

    /**
     * 解析
     *
     * @param text 说明页纯文本
     * @param style inline / section
     * @param test 考试小节标题（如 IELTS、IELTS (Academic)）
     * @param label 等级正则（如 Level [1-5]、Band [A-E]）
     * @return 等级 → 要求（按页面中第一次出现的为准）
     */
    static Map<String, Band> parse(String text, String style, String test, String label)
    {
        List<String> lines = AbrProgramRuleExtractor.lines(text);
        Map<String, Band> out = new LinkedHashMap<>();
        Pattern labelLine = Pattern.compile("(?i)^(" + label + ")\\s*(?:\\(.*?\\))?\\s*[:：]\\s*(.+)$");
        Pattern labelOnly = Pattern.compile("(?i)^(" + label + ")\\s*$");
        if ("inline".equals(style))
        {
            for (int i = 0; i < lines.size(); i++)
            {
                if (!lines.get(i).toUpperCase().contains(test.toUpperCase()) || lines.get(i).length() > 120)
                {
                    continue;
                }
                for (int j = i + 1; j < Math.min(lines.size(), i + 12); j++)
                {
                    Matcher m = labelLine.matcher(lines.get(j));
                    if (!m.find())
                    {
                        if (!out.isEmpty())
                        {
                            break;
                        }
                        continue;
                    }
                    Band b = scores(m.group(2), lines.get(j));
                    if (b != null)
                    {
                        out.putIfAbsent(normalizeLabel(m.group(1)), b);
                    }
                }
                if (!out.isEmpty())
                {
                    return out;
                }
            }
            return out;
        }
        String current = null;
        for (int i = 0; i < lines.size(); i++)
        {
            Matcher h = labelOnly.matcher(lines.get(i));
            if (h.find())
            {
                current = normalizeLabel(h.group(1));
                continue;
            }
            if (current != null && !out.containsKey(current) && lines.get(i).equalsIgnoreCase(test) && i + 1 < lines.size())
            {
                Band b = scores(lines.get(i + 1), lines.get(i + 1));
                if (b != null)
                {
                    out.put(current, b);
                }
            }
        }
        return out;
    }

    /**
     * 分数：第一个 4–9 的分数为总分，其余不高于总分的最小值为最低小分
     */
    static Band scores(String s, String quote)
    {
        Matcher m = SCORE.matcher(s);
        BigDecimal overall = null;
        BigDecimal min = null;
        while (m.find())
        {
            BigDecimal v = new BigDecimal(m.group(1));
            if (overall == null)
            {
                if (v.compareTo(BigDecimal.valueOf(5)) < 0)
                {
                    return null;
                }
                overall = v;
            }
            else if (v.compareTo(overall) <= 0 && (min == null || v.compareTo(min) < 0))
            {
                min = v;
            }
        }
        return overall == null ? null : new Band(overall, min, quote.length() > 480 ? quote.substring(0, 480) : quote);
    }

    /**
     * 等级名规范化：每个词首字母大写（“level 1” → “Level 1”，“band d” → “Band D”）
     */
    static String normalizeLabel(String s)
    {
        StringBuilder sb = new StringBuilder();
        for (String w : s.trim().split("\\s+"))
        {
            if (sb.length() > 0)
            {
                sb.append(' ');
            }
            sb.append(w.length() == 1 ? w.toUpperCase() : w.substring(0, 1).toUpperCase() + w.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
