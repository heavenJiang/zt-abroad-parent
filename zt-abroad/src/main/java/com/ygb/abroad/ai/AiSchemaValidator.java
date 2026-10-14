package com.ygb.abroad.ai;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * 精简的 JSON Schema 校验器（只依赖 Jackson，供 AI 输出结构校验）
 * <p>
 * 支持：type（含 ["string","null"] 联合类型）、properties、required、additionalProperties（false 或子 Schema）、
 * items、minItems、maxItems、enum、minimum、maximum、minLength、maxLength。其他关键字忽略。
 * </p>
 *
 * @author ygb
 */
public final class AiSchemaValidator
{
    /** 最多返回的错误条数 */
    private static final int MAX_ERRORS = 20;

    private AiSchemaValidator()
    {
    }

    /**
     * 校验
     *
     * @param schema JSON Schema
     * @param data 待校验数据
     * @return 错误列表（中文，带 JSON Pointer 路径），为空表示通过
     */
    public static List<String> validate(JsonNode schema, JsonNode data)
    {
        List<String> errors = new ArrayList<>();
        if (data == null || data.isMissingNode())
        {
            errors.add("（根）：缺少数据");
            return errors;
        }
        check(schema, data, "", errors);
        return errors;
    }

    private static void check(JsonNode schema, JsonNode node, String path, List<String> errors)
    {
        if (schema == null || !schema.isObject() || errors.size() >= MAX_ERRORS)
        {
            return;
        }
        String where = path.isEmpty() ? "（根）" : path;
        JsonNode type = schema.get("type");
        if (type != null && !typeMatches(type, node))
        {
            errors.add(where + "：类型应为 " + (type.isArray() ? type.toString() : type.asString()) + "，实际为 " + typeOf(node));
            return;
        }
        JsonNode enumNode = schema.get("enum");
        if (enumNode != null && enumNode.isArray())
        {
            boolean found = false;
            for (JsonNode option : enumNode)
            {
                if (AiJson.jsonEquals(option, node))
                {
                    found = true;
                    break;
                }
            }
            if (!found)
            {
                errors.add(where + "：取值 " + node + " 不在允许范围 " + enumNode + " 内");
            }
        }
        if (node.isString())
        {
            int len = node.asString().codePointCount(0, node.asString().length());
            if (schema.has("minLength") && len < schema.get("minLength").asInt())
            {
                errors.add(where + "：长度 " + len + " 小于最小长度 " + schema.get("minLength").asInt());
            }
            if (schema.has("maxLength") && len > schema.get("maxLength").asInt())
            {
                errors.add(where + "：长度 " + len + " 超过最大长度 " + schema.get("maxLength").asInt());
            }
        }
        if (node.isNumber())
        {
            BigDecimal value = node.decimalValue();
            if (schema.has("minimum") && value.compareTo(schema.get("minimum").decimalValue()) < 0)
            {
                errors.add(where + "：数值 " + node + " 小于最小值 " + schema.get("minimum"));
            }
            if (schema.has("maximum") && value.compareTo(schema.get("maximum").decimalValue()) > 0)
            {
                errors.add(where + "：数值 " + node + " 大于最大值 " + schema.get("maximum"));
            }
        }
        if (node.isObject())
        {
            checkObject(schema, node, path, where, errors);
        }
        if (node.isArray())
        {
            if (schema.has("minItems") && node.size() < schema.get("minItems").asInt())
            {
                errors.add(where + "：元素个数 " + node.size() + " 少于 " + schema.get("minItems").asInt());
            }
            if (schema.has("maxItems") && node.size() > schema.get("maxItems").asInt())
            {
                errors.add(where + "：元素个数 " + node.size() + " 多于 " + schema.get("maxItems").asInt());
            }
            JsonNode items = schema.get("items");
            if (items != null && items.isObject())
            {
                for (int i = 0; i < node.size(); i++)
                {
                    check(items, node.get(i), path + "/" + i, errors);
                }
            }
        }
    }

    private static void checkObject(JsonNode schema, JsonNode node, String path, String where, List<String> errors)
    {
        JsonNode required = schema.get("required");
        if (required != null && required.isArray())
        {
            for (JsonNode name : required)
            {
                if (!node.has(name.asString()))
                {
                    errors.add(where + "：缺少必填字段 " + name.asString());
                }
            }
        }
        JsonNode properties = schema.get("properties");
        JsonNode additional = schema.get("additionalProperties");
        for (Map.Entry<String, JsonNode> e : node.properties())
        {
            String childPath = path + "/" + e.getKey().replace("~", "~0").replace("/", "~1");
            JsonNode propSchema = properties != null ? properties.get(e.getKey()) : null;
            if (propSchema != null)
            {
                check(propSchema, e.getValue(), childPath, errors);
            }
            else if (additional != null && additional.isBoolean() && !additional.asBoolean())
            {
                errors.add(where + "：不允许的字段 " + e.getKey());
            }
            else if (additional != null && additional.isObject())
            {
                check(additional, e.getValue(), childPath, errors);
            }
        }
    }

    private static boolean typeMatches(JsonNode type, JsonNode node)
    {
        if (type.isArray())
        {
            for (JsonNode t : type)
            {
                if (typeMatches(t.asString(), node))
                {
                    return true;
                }
            }
            return false;
        }
        return typeMatches(type.asString(), node);
    }

    private static boolean typeMatches(String type, JsonNode node)
    {
        switch (type)
        {
            case "object":
                return node.isObject();
            case "array":
                return node.isArray();
            case "string":
                return node.isString();
            case "number":
                return node.isNumber();
            case "integer":
                return node.isIntegralNumber() || (node.isNumber() && node.decimalValue().stripTrailingZeros().scale() <= 0);
            case "boolean":
                return node.isBoolean();
            case "null":
                return node.isNull();
            default:
                return false;
        }
    }

    private static String typeOf(JsonNode node)
    {
        if (node.isObject())
        {
            return "object";
        }
        if (node.isArray())
        {
            return "array";
        }
        if (node.isString())
        {
            return "string";
        }
        if (node.isIntegralNumber())
        {
            return "integer";
        }
        if (node.isNumber())
        {
            return "number";
        }
        if (node.isBoolean())
        {
            return "boolean";
        }
        return node.isNull() ? "null" : node.getNodeType().toString().toLowerCase();
    }
}
