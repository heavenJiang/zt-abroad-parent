package com.ygb.abroad.ai.eval;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ygb.abroad.ai.AiJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 评测样例定义（纯计算）：校验写法；由不合格样本的命中项生成断言
 * <p>
 * 定义与 prompts/&lt;agent&gt;/&lt;版本&gt;/eval/*.json 相同：{"name", "input", "knownNames", "facts", "expectStatus", "expect": [断言]}。
 * 只有带模型输入、且至少有一条断言的样例才能启用（否则评测时无从复现或无从判断）。
 * </p>
 *
 * @author ygb
 */
public final class AiEvalCases
{
    /** 支持的断言 */
    public static final Set<String> ASSERTIONS = Set.of("equals", "contains", "notContains", "exists", "notExists");

    /** 每个样例最多生成的断言数 */
    static final int MAX_GENERATED = 20;

    private AiEvalCases()
    {
    }

    /**
     * 校验样例定义
     *
     * @param spec 定义
     * @return 错误说明，空表示通过
     */
    public static List<String> validate(JsonNode spec)
    {
        List<String> errors = new ArrayList<>();
        if (spec == null || !spec.isObject())
        {
            errors.add("样例定义必须是 JSON 对象");
            return errors;
        }
        if (spec.has("input") && !spec.get("input").isObject())
        {
            errors.add("input 必须是对象");
        }
        if (spec.has("expectStatus") && !spec.get("expectStatus").isString())
        {
            errors.add("expectStatus 必须是字符串");
        }
        if (spec.has("knownNames") && !spec.get("knownNames").isArray())
        {
            errors.add("knownNames 必须是数组");
        }
        if (spec.has("facts") && !spec.get("facts").isArray())
        {
            errors.add("facts 必须是数组");
        }
        JsonNode expect = spec.path("expect");
        if (spec.has("expect") && !expect.isArray())
        {
            errors.add("expect 必须是数组");
            return errors;
        }
        int i = 0;
        for (JsonNode a : expect)
        {
            i++;
            if (!a.isObject() || !a.path("path").isString())
            {
                errors.add("第 " + i + " 条断言须为对象且有 path");
                continue;
            }
            String path = a.path("path").asString();
            if (!path.isEmpty() && !path.startsWith("/"))
            {
                errors.add("第 " + i + " 条断言的 path 须为 JSON Pointer（以 / 开头）");
            }
            long kinds = a.properties().stream().map(Map.Entry::getKey).filter(ASSERTIONS::contains).count();
            if (kinds != 1)
            {
                errors.add("第 " + i + " 条断言须有且只有一个 " + String.join(" / ", ASSERTIONS.stream().sorted().toList()));
            }
        }
        return errors;
    }

    /**
     * 是否可以启用：有非空的模型输入，且至少一条断言
     *
     * @param spec 定义
     * @return 是否可启用
     */
    public static boolean runnable(JsonNode spec)
    {
        return spec != null && spec.path("input").isObject() && !spec.path("input").isEmpty() && spec.path("expect").isArray()
                && !spec.path("expect").isEmpty();
    }

    /**
     * 由命中项生成样例：每个命中的文字生成一条 notContains 断言（字段按 fieldPaths 映射到输出路径，映射不到的不生成）
     *
     * @param name 样例名称
     * @param input 模型输入（可为 null）
     * @param flags 命中项数组（[{"field", "matched"}...]）
     * @param fieldPaths 字段 → 输出中的 JSON Pointer
     * @return 定义
     */
    public static ObjectNode fromFlags(String name, JsonNode input, JsonNode flags, Map<String, String> fieldPaths)
    {
        ObjectNode spec = AiJson.MAPPER.createObjectNode();
        spec.put("name", name);
        if (input != null && input.isObject())
        {
            spec.set("input", input);
        }
        spec.put("expectStatus", "success");
        ArrayNode expect = spec.putArray("expect");
        Set<String> seen = new LinkedHashSet<>();
        if (flags != null && flags.isArray())
        {
            for (JsonNode f : flags)
            {
                String path = fieldPaths.get(f.path("field").asString(""));
                String matched = f.path("matched").asString("").trim();
                if (path == null || matched.isEmpty() || !seen.add(path + "\u0000" + matched) || expect.size() >= MAX_GENERATED)
                {
                    continue;
                }
                ObjectNode a = expect.addObject();
                a.put("path", path);
                a.put("notContains", matched);
            }
        }
        return spec;
    }
}
