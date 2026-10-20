package com.ygb.abroad.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;

/**
 * 增长周报规则（纯计算）：AI 摘要的校验（数字须来自数据、长度）与模板摘要
 *
 * @author ygb
 */
final class AbrGrowthReportRules
{
    /** 摘要最长字数 */
    static final int SUMMARY_MAX = 400;

    /** 每条要点 / 建议最长字数 */
    static final int ITEM_MAX = 120;

    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    private AbrGrowthReportRules()
    {
    }

    /**
     * 文本中出现的数字（去掉千分位，去掉小数末尾的 0）
     *
     * @param text 文本
     * @return 数字
     */
    static Set<String> numbers(String text)
    {
        Set<String> out = new LinkedHashSet<>();
        if (text == null)
        {
            return out;
        }
        Matcher m = NUMBER.matcher(text);
        while (m.find())
        {
            out.add(normalize(m.group()));
        }
        return out;
    }

    static String normalize(String n)
    {
        String s = n.replace(",", "");
        if (s.contains("."))
        {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    /**
     * 校验 AI 摘要：结构与长度、所有数字都在数据中出现
     *
     * @param out 模型输出 {summary, highlights[], suggestions[]}
     * @param allowed 数据中出现的数字
     * @return 不合格原因（合格为空）
     */
    static List<String> check(JsonNode out, Set<String> allowed)
    {
        List<String> errors = new ArrayList<>();
        String summary = out == null ? "" : out.path("summary").asString("").trim();
        if (summary.isEmpty() || summary.length() > SUMMARY_MAX)
        {
            errors.add("摘要为空或超长");
        }
        List<String> texts = new ArrayList<>();
        texts.add(summary);
        for (String field : List.of("highlights", "suggestions"))
        {
            JsonNode arr = out == null ? null : out.path(field);
            if (arr != null && arr.isArray())
            {
                for (JsonNode t : arr)
                {
                    String s = t.asString("").trim();
                    if (s.isEmpty() || s.length() > ITEM_MAX)
                    {
                        errors.add(field + " 有空条目或超长");
                    }
                    texts.add(s);
                }
            }
        }
        for (String t : texts)
        {
            for (String n : numbers(t))
            {
                if (!allowed.contains(n))
                {
                    errors.add("数字 " + n + " 不在数据中");
                }
            }
        }
        return errors;
    }

    /**
     * 模板摘要（AI 不可用或校验不通过时）
     *
     * @param thisWeek 本周漏斗合计
     * @param lastWeek 上周漏斗合计
     * @param active 本周每周活跃选校单
     * @param lastActive 上周每周活跃选校单
     * @param highlights 系统要点（最好的内容、熔断、实验、调参）
     * @return {summary, highlights, suggestions, source}
     */
    static Map<String, Object> template(Map<String, Object> thisWeek, Map<String, Object> lastWeek, long active, long lastActive, List<String> highlights)
    {
        String summary = "本周新访客 " + v(thisWeek, "visitors") + "（上周 " + v(lastWeek, "visitors") + "），用过工具 " + v(thisWeek, "toolUsers")
                + "，加入选校单 " + v(thisWeek, "planUsers") + "，预约 " + v(thisWeek, "bookedUsers") + "，签约 " + v(thisWeek, "signedUsers")
                + "；发布内容 " + v(thisWeek, "published") + " 条；每周活跃选校单 " + active + "（上周 " + lastActive + "）。";
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("summary", summary);
        n.put("highlights", highlights.stream().filter(StringUtils::isNotBlank).limit(5).toList());
        n.put("suggestions", List.of());
        n.put("source", "template");
        return n;
    }

    private static Object v(Map<String, Object> m, String k)
    {
        Object o = m == null ? null : m.get(k);
        return o == null ? 0 : o;
    }
}
