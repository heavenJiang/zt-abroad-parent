package com.ygb.abroad.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;

/**
 * 归一化换算表（来自问卷定义的 normalization 节与题目选项），只读
 * 
 * @author ygb
 */
public final class NormalizationRules
{
    /** 均分换算：分制 → 折线点（[原始分, 百分制]），identity 与 proportional 不需要点 */
    private final Map<String, List<BigDecimal[]>> gpaPoints;

    /** 均分换算方式：分制 → identity / piecewise_linear / proportional_low_confidence */
    private final Map<String, String> gpaMethods;

    /** 英语换算：考试 → (雅思分段 → 最低原始分)；null 表示没有对照表 */
    private final Map<String, TreeMap<BigDecimal, BigDecimal>> englishTables;

    /** 专业方向 → 专业大类 */
    private final Map<String, String> fieldGroups;

    private NormalizationRules(Map<String, List<BigDecimal[]>> gpaPoints, Map<String, String> gpaMethods,
            Map<String, TreeMap<BigDecimal, BigDecimal>> englishTables, Map<String, String> fieldGroups)
    {
        this.gpaPoints = gpaPoints;
        this.gpaMethods = gpaMethods;
        this.englishTables = englishTables;
        this.fieldGroups = fieldGroups;
    }

    /**
     * 从问卷定义解析换算表
     * 
     * @param questionnaire 问卷定义根节点
     * @return 换算表
     */
    public static NormalizationRules parse(JsonNode questionnaire)
    {
        JsonNode normalization = questionnaire.path("normalization");
        Map<String, List<BigDecimal[]>> points = new HashMap<>();
        Map<String, String> methods = new HashMap<>();
        for (Map.Entry<String, JsonNode> e : normalization.path("gpa_to_percent").path("scales").properties())
        {
            methods.put(e.getKey(), e.getValue().path("method").asString(""));
            List<BigDecimal[]> list = new ArrayList<>();
            for (JsonNode p : e.getValue().path("points"))
            {
                list.add(new BigDecimal[] { p.get(0).decimalValue(), p.get(1).decimalValue() });
            }
            list.sort((a, b) -> a[0].compareTo(b[0]));
            points.put(e.getKey(), Collections.unmodifiableList(list));
        }
        Map<String, TreeMap<BigDecimal, BigDecimal>> tables = new HashMap<>();
        for (Map.Entry<String, JsonNode> e : normalization.path("english_to_ielts").path("tables").properties())
        {
            JsonNode thresholds = e.getValue().get("thresholds");
            if ("identity".equals(e.getValue().path("method").asString("")))
            {
                tables.put(e.getKey(), new TreeMap<>());
            }
            else if (thresholds != null && thresholds.isObject())
            {
                TreeMap<BigDecimal, BigDecimal> table = new TreeMap<>();
                for (Map.Entry<String, JsonNode> t : thresholds.properties())
                {
                    table.put(new BigDecimal(t.getKey()), t.getValue().decimalValue());
                }
                tables.put(e.getKey(), table);
            }
            else
            {
                tables.put(e.getKey(), null);
            }
        }
        Map<String, String> groups = new HashMap<>();
        for (JsonNode q : questionnaire.path("questions"))
        {
            if ("target_field".equals(q.path("id").asString("")))
            {
                for (JsonNode o : q.path("options"))
                {
                    if (o.hasNonNull("group"))
                    {
                        groups.put(o.path("value").asString(""), o.path("group").asString(""));
                    }
                }
            }
        }
        return new NormalizationRules(points, methods, tables, groups);
    }

    /**
     * 均分的换算方式
     * 
     * @param scale 分制
     * @return 方式，未定义返回 null
     */
    public String gpaMethod(String scale)
    {
        return gpaMethods.get(scale);
    }

    /**
     * 均分折线点
     * 
     * @param scale 分制
     * @return 折线点（按原始分升序），未定义返回空列表
     */
    public List<BigDecimal[]> gpaPoints(String scale)
    {
        return gpaPoints.getOrDefault(scale, List.of());
    }

    /**
     * 是否有该考试的对照表
     * 
     * @param test 考试类型
     * @return 有对照表（含雅思本身）为 true
     */
    public boolean hasEnglishTable(String test)
    {
        return englishTables.get(test) != null;
    }

    /**
     * 英语对照表（雅思分段 → 最低原始分）；雅思本身返回空表
     * 
     * @param test 考试类型
     * @return 对照表，没有返回 null
     */
    public TreeMap<BigDecimal, BigDecimal> englishTable(String test)
    {
        return englishTables.get(test);
    }

    /**
     * 专业方向所属大类
     * 
     * @param field 专业方向
     * @return 大类，未知返回 null
     */
    public String fieldGroup(String field)
    {
        return fieldGroups.get(field);
    }
}
