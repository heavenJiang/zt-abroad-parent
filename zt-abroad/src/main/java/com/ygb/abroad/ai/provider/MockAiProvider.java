package com.ygb.abroad.ai.provider;

import java.util.Map;
import com.ygb.abroad.ai.AiConstants;
import com.ygb.abroad.ai.AiTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * mock 模型：离线、确定性，返回提示词目录下的 mock.json（字符串中的 {{变量}} 用去标识化后的输入渲染）
 * <p>
 * 输入 _mock 模拟故障：timeout / error（仅主模型失败）、timeout_all / error_all（主备都失败）、
 * invalid_json（每次都返回非 JSON）、schema_fail_once（第 1 轮返回 {}，第 2 轮正常）、
 * banned（在第一个字符串字段后追加“保证录取”）。token 按字符数 / 2 估算。
 * </p>
 *
 * @author ygb
 */
public class MockAiProvider implements AiProvider
{
    /** 模拟违禁内容时追加的文字 */
    public static final String BANNED_SUFFIX = "，保证录取";

    /**
     * 返回 mock 输出
     *
     * @param request 请求
     * @return 响应
     */
    @Override
    public AiModelResponse chat(AiModelRequest request)
    {
        Map<String, Object> input = request.getInput();
        Object modeValue = input == null ? null : input.get(AiConstants.MOCK_KEY);
        String mode = modeValue == null ? "" : modeValue.toString();
        String model = request.getProviderName() + ":" + request.getModel();
        if (("timeout".equals(mode) && request.isPrimary()) || "timeout_all".equals(mode))
        {
            throw new AiProviderException("模型 " + model + " 调用超时（" + request.getTimeoutMs() + " ms，mock 模拟）");
        }
        if (("error".equals(mode) && request.isPrimary()) || "error_all".equals(mode))
        {
            throw new AiProviderException("模型 " + model + " 返回 HTTP 503（mock 模拟）");
        }
        String content;
        if ("invalid_json".equals(mode))
        {
            content = "这不是 JSON {";
        }
        else if ("schema_fail_once".equals(mode) && request.getAttempt() == 1)
        {
            content = "{}";
        }
        else
        {
            JsonNode mock = request.getPrompt() == null ? null : request.getPrompt().getMock();
            if (mock == null)
            {
                throw new AiProviderException("模型 " + model + " 返回内容为空（提示词目录下没有 mock.json）");
            }
            JsonNode output = render(mock, input);
            if ("banned".equals(mode))
            {
                appendToFirstString(output, BANNED_SUFFIX);
            }
            content = output.toString();
        }
        int promptChars = length(request.getSystemPrompt()) + length(request.getUserPrompt());
        return new AiModelResponse(content, (promptChars + 1) / 2, (content.length() + 1) / 2);
    }

    private static int length(String s)
    {
        return s == null ? 0 : s.length();
    }

    private static JsonNode render(JsonNode node, Map<String, Object> vars)
    {
        JsonNodeFactory f = JsonNodeFactory.instance;
        if (node.isString())
        {
            return f.stringNode(AiTemplate.render(node.asString(), vars));
        }
        if (node.isObject())
        {
            ObjectNode result = f.objectNode();
            for (Map.Entry<String, JsonNode> e : node.properties())
            {
                result.set(e.getKey(), render(e.getValue(), vars));
            }
            return result;
        }
        if (node.isArray())
        {
            ArrayNode result = f.arrayNode();
            for (JsonNode item : node)
            {
                result.add(render(item, vars));
            }
            return result;
        }
        return node.deepCopy();
    }

    private static boolean appendToFirstString(JsonNode node, String suffix)
    {
        if (node.isObject())
        {
            ObjectNode obj = (ObjectNode) node;
            for (Map.Entry<String, JsonNode> e : obj.properties())
            {
                if (e.getValue().isString())
                {
                    obj.put(e.getKey(), e.getValue().asString() + suffix);
                    return true;
                }
                if (appendToFirstString(e.getValue(), suffix))
                {
                    return true;
                }
            }
        }
        else if (node.isArray())
        {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++)
            {
                if (arr.get(i).isString())
                {
                    arr.set(i, JsonNodeFactory.instance.stringNode(arr.get(i).asString() + suffix));
                    return true;
                }
                if (appendToFirstString(arr.get(i), suffix))
                {
                    return true;
                }
            }
        }
        return false;
    }
}
