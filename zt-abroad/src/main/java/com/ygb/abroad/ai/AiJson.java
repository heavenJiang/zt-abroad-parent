package com.ygb.abroad.ai;

import java.math.BigDecimal;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 网关内部的 JSON 工具
 *
 * @author ygb
 */
public final class AiJson
{
    /** 共享的 ObjectMapper（线程安全） */
    public static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private AiJson()
    {
    }

    /**
     * 解析 JSON，非法返回 null；兼容模型输出外包 ```json 代码块
     *
     * @param text 文本
     * @return JSON 节点，非法或为空返回 null
     */
    public static JsonNode parseOrNull(String text)
    {
        if (text == null)
        {
            return null;
        }
        String s = text.trim();
        if (s.startsWith("```"))
        {
            int firstLine = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (firstLine > 0 && end > firstLine)
            {
                s = s.substring(firstLine + 1, end).trim();
            }
        }
        if (s.isEmpty())
        {
            return null;
        }
        try
        {
            return MAPPER.readTree(s);
        }
        catch (JacksonException e)
        {
            return null;
        }
    }

    /**
     * 序列化为 JSON 文本
     *
     * @param value 对象
     * @return JSON 文本
     */
    public static String toJson(Object value)
    {
        return MAPPER.writeValueAsString(value);
    }

    /**
     * 对象转 JSON 节点
     *
     * @param value 对象
     * @return JSON 节点
     */
    public static JsonNode toTree(Object value)
    {
        return MAPPER.valueToTree(value);
    }

    /**
     * 两个 JSON 值是否相等（数字按数值比较，1 与 1.0 相等）
     *
     * @param a 值 a
     * @param b 值 b
     * @return true 相等
     */
    public static boolean jsonEquals(JsonNode a, JsonNode b)
    {
        if (a == null || b == null)
        {
            return a == b;
        }
        if (a.isNumber() && b.isNumber())
        {
            return a.decimalValue().compareTo(b.decimalValue()) == 0;
        }
        if (a.isArray() && b.isArray())
        {
            if (a.size() != b.size())
            {
                return false;
            }
            for (int i = 0; i < a.size(); i++)
            {
                if (!jsonEquals(a.get(i), b.get(i)))
                {
                    return false;
                }
            }
            return true;
        }
        if (a.isObject() && b.isObject())
        {
            if (a.size() != b.size())
            {
                return false;
            }
            for (String name : a.propertyNames())
            {
                if (!b.has(name) || !jsonEquals(a.get(name), b.get(name)))
                {
                    return false;
                }
            }
            return true;
        }
        return a.equals(b);
    }

    /**
     * 截断文本
     *
     * @param text 文本
     * @param max 最大长度
     * @return 截断后的文本（null 返回空串）
     */
    public static String truncate(String text, int max)
    {
        if (text == null)
        {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    /**
     * 数字转 BigDecimal（null 视为 0）
     *
     * @param value 数字
     * @return BigDecimal
     */
    public static BigDecimal nz(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }
}
