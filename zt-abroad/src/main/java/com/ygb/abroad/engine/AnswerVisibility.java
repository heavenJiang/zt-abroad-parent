package com.ygb.abroad.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * 按问卷的显示条件（visible_if）筛出可见题目的答案
 * 
 * 问卷约定：题目被隐藏时答案留在草稿里，提交时剔除，不参与归一化与引擎计算。
 * 条件引用的题目本身被隐藏时，按“未作答”处理；反复计算直到可见集合不再变化。
 * 
 * @author ygb
 */
public final class AnswerVisibility
{
    private AnswerVisibility()
    {
    }

    /**
     * 筛出可见题目的答案
     * 
     * @param questions 问卷题目定义（questions 数组）
     * @param answers 已保存的答案（题目 id → 答案）
     * @return 可见题目的答案（保持问卷题目顺序）
     */
    public static Map<String, JsonNode> visibleAnswers(JsonNode questions, Map<String, JsonNode> answers)
    {
        Map<String, JsonNode> visible = new LinkedHashMap<>(answers);
        for (int round = 0; round <= questions.size(); round++)
        {
            Map<String, JsonNode> next = new LinkedHashMap<>();
            for (JsonNode q : questions)
            {
                String id = q.path("id").asString("");
                JsonNode answer = answers.get(id);
                if (answer != null && !answer.isNull() && isVisible(q.get("visible_if"), visible))
                {
                    next.put(id, answer);
                }
            }
            if (next.keySet().equals(visible.keySet()))
            {
                return next;
            }
            visible = next;
        }
        return visible;
    }

    /**
     * 判断显示条件是否成立
     * 
     * @param cond 条件（null 表示始终显示）
     * @param answers 当前视为可见的答案
     * @return 是否显示
     */
    public static boolean isVisible(JsonNode cond, Map<String, JsonNode> answers)
    {
        if (cond == null || cond.isNull())
        {
            return true;
        }
        if (cond.has("all"))
        {
            for (JsonNode c : cond.get("all"))
            {
                if (!isVisible(c, answers))
                {
                    return false;
                }
            }
            return true;
        }
        if (cond.has("any"))
        {
            for (JsonNode c : cond.get("any"))
            {
                if (isVisible(c, answers))
                {
                    return true;
                }
            }
            return false;
        }
        if (cond.has("not"))
        {
            return !isVisible(cond.get("not"), answers);
        }
        JsonNode answer = answers.get(cond.path("q").asString(""));
        String path = cond.path("path").asString("");
        if (answer != null && !path.isEmpty())
        {
            answer = answer.get(path);
        }
        return test(cond.path("op").asString(""), answer, cond.get("value"));
    }

    private static boolean test(String op, JsonNode answer, JsonNode value)
    {
        boolean answered = answer != null && !answer.isNull() && !(answer.isString() && answer.asString().isEmpty())
                && !(answer.isArray() && answer.isEmpty());
        switch (op)
        {
            case "answered":
                return answered;
            case "not_answered":
                return !answered;
            case "eq":
                return answered && same(answer, value);
            case "ne":
                return !answered || !same(answer, value);
            case "in":
                return answered && contains(value, answer);
            case "not_in":
                return !answered || !contains(value, answer);
            case "contains":
                return answered && answer.isArray() && contains(answer, value);
            case "contains_any":
                if (answered && answer.isArray() && value != null && value.isArray())
                {
                    for (JsonNode v : value)
                    {
                        if (contains(answer, v))
                        {
                            return true;
                        }
                    }
                }
                return false;
            case "gt":
            case "gte":
            case "lt":
            case "lte":
                return answered && compare(op, answer, value);
            default:
                return false;
        }
    }

    private static boolean compare(String op, JsonNode answer, JsonNode value)
    {
        BigDecimal a = number(answer);
        BigDecimal b = number(value);
        if (a == null || b == null)
        {
            return false;
        }
        int c = a.compareTo(b);
        return switch (op)
        {
            case "gt" -> c > 0;
            case "gte" -> c >= 0;
            case "lt" -> c < 0;
            default -> c <= 0;
        };
    }

    private static boolean contains(JsonNode list, JsonNode item)
    {
        if (list == null || !list.isArray())
        {
            return false;
        }
        for (JsonNode v : list)
        {
            if (same(v, item))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean same(JsonNode a, JsonNode b)
    {
        if (a == null || b == null)
        {
            return false;
        }
        if (a.isNumber() || b.isNumber())
        {
            BigDecimal x = number(a);
            BigDecimal y = number(b);
            return x != null && y != null && x.compareTo(y) == 0;
        }
        if (a.isValueNode() && b.isValueNode())
        {
            return a.asString("").equals(b.asString(""));
        }
        return a.equals(b);
    }

    /**
     * 取数值（数字或数字字符串）
     * 
     * @param node 节点
     * @return 数值，不是数字返回 null
     */
    static BigDecimal number(JsonNode node)
    {
        if (node == null || node.isNull())
        {
            return null;
        }
        if (node.isNumber())
        {
            return node.decimalValue();
        }
        if (node.isString())
        {
            try
            {
                return new BigDecimal(node.asString().trim());
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }
        return null;
    }

    /**
     * 取多选答案的字符串列表
     * 
     * @param node 答案
     * @return 列表（不是数组返回空列表）
     */
    static List<String> strings(JsonNode node)
    {
        if (node == null || !node.isArray())
        {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        node.forEach(v -> list.add(v.asString("")));
        return list;
    }
}
