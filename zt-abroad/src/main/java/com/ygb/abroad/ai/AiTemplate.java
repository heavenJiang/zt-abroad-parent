package com.ygb.abroad.ai;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * 提示词模板渲染：{{变量}} 替换为输入值
 * <p>
 * 支持点号路径（{{profile.gpa}}）；字符串原样填入，数字与布尔转文本，对象、数组序列化为 JSON；不存在的变量填空串。
 * </p>
 *
 * @author ygb
 */
public final class AiTemplate
{
    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_\\-]+(?:\\.[A-Za-z0-9_\\-]+)*)\\s*\\}\\}");

    private AiTemplate()
    {
    }

    /**
     * 渲染模板
     *
     * @param template 模板
     * @param vars 变量
     * @return 渲染结果
     */
    public static String render(String template, Map<String, Object> vars)
    {
        if (template == null)
        {
            return "";
        }
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find())
        {
            Object value = lookup(vars, m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(format(value)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 按点号路径取值
     */
    @SuppressWarnings("unchecked")
    private static Object lookup(Map<String, Object> vars, String path)
    {
        if (vars == null)
        {
            return null;
        }
        if (vars.containsKey(path))
        {
            return vars.get(path);
        }
        Object current = vars;
        for (String key : path.split("\\."))
        {
            if (current instanceof Map)
            {
                current = ((Map<String, Object>) current).get(key);
            }
            else if (current instanceof JsonNode)
            {
                current = ((JsonNode) current).get(key);
            }
            else
            {
                return null;
            }
            if (current == null)
            {
                return null;
            }
        }
        return current;
    }

    /**
     * 值转文本
     *
     * @param value 值
     * @return 文本
     */
    static String format(Object value)
    {
        if (value == null)
        {
            return "";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean)
        {
            return value.toString();
        }
        if (value instanceof JsonNode)
        {
            JsonNode node = (JsonNode) value;
            if (node.isNull() || node.isMissingNode())
            {
                return "";
            }
            return node.isValueNode() ? node.asString() : node.toString();
        }
        return AiJson.toJson(value);
    }
}
