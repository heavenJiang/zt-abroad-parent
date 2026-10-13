package com.ygb.abroad.service.impl;

import java.util.Map;
import java.util.Set;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 院校库 jsonb 字段的格式校验
 * 
 * @author ygb
 */
final class AbrJsonFields
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 本科层次编码，与问卷、选校引擎一致（字典 abr_undergrad_tier） */
    static final Set<String> UNDERGRAD_TIERS = Set.of("c9", "985", "211", "double_first_class", "non_key", "overseas");

    private AbrJsonFields()
    {
    }

    /**
     * 解析 JSON，空串视为未填写
     * 
     * @param json JSON 文本
     * @param label 字段中文名（用于提示）
     * @return 解析结果，未填写返回 null
     */
    static JsonNode parse(String json, String label)
    {
        if (StringUtils.isBlank(json))
        {
            return null;
        }
        try
        {
            return MAPPER.readTree(json);
        }
        catch (JacksonException e)
        {
            throw new ServiceException(label + "不是合法的 JSON");
        }
    }

    /**
     * 校验为 JSON 对象（可为空）
     * 
     * @param json JSON 文本
     * @param label 字段中文名
     * @return 规范化后的 JSON 文本，未填写返回 null
     */
    static String requireObject(String json, String label)
    {
        JsonNode node = parse(json, label);
        if (node == null)
        {
            return null;
        }
        if (!node.isObject())
        {
            throw new ServiceException(label + "应为 JSON 对象");
        }
        return node.isEmpty() ? null : node.toString();
    }

    /**
     * 校验为 JSON 数组（可为空）
     * 
     * @param json JSON 文本
     * @param label 字段中文名
     * @return 规范化后的 JSON 文本，未填写返回 null
     */
    static String requireArray(String json, String label)
    {
        JsonNode node = parse(json, label);
        if (node == null)
        {
            return null;
        }
        if (!node.isArray())
        {
            throw new ServiceException(label + "应为 JSON 数组");
        }
        return node.toString();
    }

    /**
     * 校验按本科层次的最低均分：键为层次编码，值为 0–100 的数字
     * 
     * @param json JSON 文本
     * @return 规范化后的 JSON 文本，未填写返回 null
     */
    static String requireMinGpa(String json)
    {
        String normalized = requireObject(json, "最低均分");
        if (normalized == null)
        {
            return null;
        }
        for (Map.Entry<String, JsonNode> e : parse(normalized, "最低均分").properties())
        {
            if (!UNDERGRAD_TIERS.contains(e.getKey()))
            {
                throw new ServiceException("最低均分中的本科层次 '" + e.getKey() + "' 不存在");
            }
            JsonNode v = e.getValue();
            if (!v.isNumber() || v.asDouble() < 0 || v.asDouble() > 100)
            {
                throw new ServiceException("最低均分应为 0–100 的数字（层次 " + e.getKey() + "）");
            }
        }
        return normalized;
    }
}
