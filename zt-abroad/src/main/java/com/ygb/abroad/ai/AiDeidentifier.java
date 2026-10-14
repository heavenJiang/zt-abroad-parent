package com.ygb.abroad.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 去标识化：把姓名、手机号、身份证号、邮箱、微信号替换为占位符（[姓名1]、[手机1]…），返回后再还原
 * <p>
 * 一次调用一个实例；映射只保存在内存中，不落库、不写日志。同一个值在一次调用中始终对应同一个占位符。
 * </p>
 *
 * @author ygb
 */
public class AiDeidentifier
{
    /** 18 位身份证号（含出生日期校验） */
    private static final Pattern ID_CARD = Pattern.compile(
            "(?<![0-9A-Za-z])[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx](?![0-9A-Za-z])");

    /** 邮箱 */
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");

    /** 中国大陆手机号（可带 +86 与 - 或空格分隔） */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?86[\\-\\s]?)?1[3-9]\\d(?:[\\-\\s]?\\d{4}){2}(?!\\d)");

    /** 带标签的微信号：微信 / 微信号 / wechat / weixin / wx / vx 后接冒号或空格 */
    private static final Pattern WECHAT = Pattern.compile(
            "(?i)((?:微信号?|(?<![A-Za-z])(?:wechat|weixin|wx|vx))(?:\\s*(?:id|号))?(?:\\s*[:：]\\s*|\\s+))([A-Za-z][-_A-Za-z0-9]{5,19})");

    /** 微信内部 ID：wxid_xxx */
    private static final Pattern WXID = Pattern.compile("(?<![A-Za-z0-9_])wxid_[A-Za-z0-9]{6,24}");

    /** 占位符 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\[(姓名|手机|证件|邮箱|微信)\\d+\\]");

    /** 占位符 → 原值 */
    private final Map<String, String> placeholderToValue = new LinkedHashMap<>();

    /** 类别 + 原值 → 占位符 */
    private final Map<String, String> valueToPlaceholder = new HashMap<>();

    /** 类别 → 已分配序号 */
    private final Map<String, Integer> counters = new HashMap<>();

    /** 已知姓名，长的在前（避免“张三丰”被“张三”拆开） */
    private final List<String> names = new ArrayList<>();

    /**
     * 创建去标识化器
     *
     * @param knownNames 需要替换的姓名（空白忽略）
     */
    public AiDeidentifier(Collection<String> knownNames)
    {
        if (knownNames != null)
        {
            for (String name : knownNames)
            {
                if (name != null && !name.isBlank() && !names.contains(name.trim()))
                {
                    names.add(name.trim());
                }
            }
        }
        names.sort(Comparator.comparingInt(String::length).reversed());
    }

    /**
     * 替换一段文本中的个人信息
     *
     * @param text 原文
     * @return 去标识化后的文本
     */
    public String mask(String text)
    {
        if (text == null || text.isEmpty())
        {
            return text;
        }
        String s = replaceAll(text, ID_CARD, m -> placeholder("证件", m.group()));
        s = replaceAll(s, EMAIL, m -> placeholder("邮箱", m.group()));
        s = replaceAll(s, PHONE, m -> placeholder("手机", m.group()));
        s = replaceAll(s, WECHAT, m -> m.group(1) + placeholder("微信", m.group(2)));
        s = replaceAll(s, WXID, m -> placeholder("微信", m.group()));
        for (String name : names)
        {
            if (s.contains(name))
            {
                s = s.replace(name, placeholder("姓名", name));
            }
        }
        return s;
    }

    /**
     * 递归替换输入中的所有字符串值（Map、List、JsonNode；键不替换）
     *
     * @param value 输入
     * @return 去标识化后的副本
     */
    @SuppressWarnings("unchecked")
    public Object maskValue(Object value)
    {
        if (value instanceof String)
        {
            return mask((String) value);
        }
        if (value instanceof Map)
        {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet())
            {
                result.put(e.getKey(), maskValue(e.getValue()));
            }
            return result;
        }
        if (value instanceof Collection)
        {
            List<Object> result = new ArrayList<>();
            for (Object item : (Collection<Object>) value)
            {
                result.add(maskValue(item));
            }
            return result;
        }
        if (value instanceof JsonNode)
        {
            return transform((JsonNode) value, this::mask);
        }
        return value;
    }

    /**
     * 去标识化输入 Map
     *
     * @param input 输入
     * @return 去标识化后的副本
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> maskMap(Map<String, Object> input)
    {
        if (input == null)
        {
            return new LinkedHashMap<>();
        }
        return (Map<String, Object>) maskValue(input);
    }

    /**
     * 还原文本中的占位符（未知占位符保留原样）
     *
     * @param text 文本
     * @return 还原后的文本
     */
    public String restore(String text)
    {
        if (text == null || placeholderToValue.isEmpty())
        {
            return text;
        }
        return replaceAll(text, PLACEHOLDER, m -> placeholderToValue.getOrDefault(m.group(), m.group()));
    }

    /**
     * 还原 JSON 中所有字符串值的占位符
     *
     * @param node JSON
     * @return 还原后的副本
     */
    public JsonNode restore(JsonNode node)
    {
        return node == null ? null : transform(node, this::restore);
    }

    /**
     * 已分配的占位符个数（测试与日志用，不暴露原值）
     *
     * @return 个数
     */
    public int size()
    {
        return placeholderToValue.size();
    }

    private String placeholder(String kind, String value)
    {
        String key = kind + "\u0000" + value;
        String existing = valueToPlaceholder.get(key);
        if (existing != null)
        {
            return existing;
        }
        int n = counters.merge(kind, 1, Integer::sum);
        String ph = "[" + kind + n + "]";
        valueToPlaceholder.put(key, ph);
        placeholderToValue.put(ph, value);
        return ph;
    }

    private static String replaceAll(String text, Pattern pattern, Function<Matcher, String> replacer)
    {
        Matcher m = pattern.matcher(text);
        StringBuilder sb = null;
        while (m.find())
        {
            if (sb == null)
            {
                sb = new StringBuilder();
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacer.apply(m)));
        }
        if (sb == null)
        {
            return text;
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static JsonNode transform(JsonNode node, Function<String, String> fn)
    {
        JsonNodeFactory f = JsonNodeFactory.instance;
        if (node.isString())
        {
            return f.stringNode(fn.apply(node.asString()));
        }
        if (node.isObject())
        {
            ObjectNode result = f.objectNode();
            for (Map.Entry<String, JsonNode> e : node.properties())
            {
                result.set(e.getKey(), transform(e.getValue(), fn));
            }
            return result;
        }
        if (node.isArray())
        {
            ArrayNode result = f.arrayNode();
            for (JsonNode item : node)
            {
                result.add(transform(item, fn));
            }
            return result;
        }
        return node.deepCopy();
    }
}
