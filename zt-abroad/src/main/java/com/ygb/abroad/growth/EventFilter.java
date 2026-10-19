package com.ygb.abroad.growth;

import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * 触发规则的事件过滤（纯 Java）：过滤条件是 JSON 对象，键为事件内容的顶层字段，值为要求相等的值或可选值数组
 *
 * 例：{"moment": ["imbalance", "repeated_edits"]} 只匹配这两类转化时刻；空对象匹配全部事件。
 * 数字与字符串按文本比较（1 与 "1" 相等）；事件内容缺少该字段时不匹配。
 *
 * @author ygb
 */
public final class EventFilter
{
    /** 单个可选值数组最多元素数 */
    private static final int MAX_VALUES = 50;

    private EventFilter()
    {
    }

    /**
     * 事件内容是否满足过滤条件
     *
     * @param filter 过滤条件（null 或空对象为不过滤）
     * @param payload 事件内容
     * @return 是否满足
     */
    public static boolean matches(JsonNode filter, JsonNode payload)
    {
        if (filter == null || !filter.isObject() || filter.isEmpty())
        {
            return true;
        }
        if (payload == null || !payload.isObject())
        {
            return false;
        }
        for (Map.Entry<String, JsonNode> e : filter.properties())
        {
            JsonNode actual = payload.get(e.getKey());
            if (actual == null || actual.isNull() || !(actual.isValueNode()))
            {
                return false;
            }
            String text = actual.asString();
            JsonNode expected = e.getValue();
            boolean hit = false;
            if (expected.isArray())
            {
                for (JsonNode v : expected)
                {
                    hit |= text.equals(v.asString());
                }
            }
            else
            {
                hit = text.equals(expected.asString());
            }
            if (!hit)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验过滤条件的格式
     *
     * @param filter 过滤条件
     * @return 错误说明，合法返回 null
     */
    public static String validate(JsonNode filter)
    {
        if (filter == null || filter.isNull())
        {
            return null;
        }
        if (!filter.isObject())
        {
            return "事件过滤必须是 JSON 对象";
        }
        for (Map.Entry<String, JsonNode> e : filter.properties())
        {
            JsonNode v = e.getValue();
            if (v.isArray())
            {
                if (v.isEmpty() || v.size() > MAX_VALUES)
                {
                    return "事件过滤“" + e.getKey() + "”的可选值须为 1–" + MAX_VALUES + " 个";
                }
                for (JsonNode item : v)
                {
                    if (!scalar(item))
                    {
                        return "事件过滤“" + e.getKey() + "”的可选值只能是字符串、数字或布尔值";
                    }
                }
            }
            else if (!scalar(v))
            {
                return "事件过滤“" + e.getKey() + "”的值只能是字符串、数字、布尔值或它们的数组";
            }
        }
        return null;
    }

    private static boolean scalar(JsonNode v)
    {
        return v.isString() || v.isNumber() || v.isBoolean();
    }
}
