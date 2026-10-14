package com.ygb.abroad.ai.provider;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import com.ygb.abroad.ai.AiJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * OpenAI 兼容接口（DeepSeek、通义千问、Kimi、智谱等）：POST {base-url}/chat/completions，要求 JSON 输出
 * <p>
 * 超时、连接失败、HTTP 非 200、返回内容为空均抛 {@link AiProviderException}，由网关切换备用模型。
 * API Key 只放在请求头，错误信息中出现时替换为 ***，不写日志。
 * </p>
 *
 * @author ygb
 */
public class OpenAiCompatibleProvider implements AiProvider
{
    /** 连接超时上限 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client;

    public OpenAiCompatibleProvider()
    {
        this.client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .proxy(ProxySelector.getDefault())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 调用对话补全接口
     *
     * @param request 请求
     * @return 响应
     */
    @Override
    public AiModelResponse chat(AiModelRequest request)
    {
        String model = request.getProviderName() + ":" + request.getModel();
        String baseUrl = request.getProvider().getBaseUrl();
        String apiKey = request.getProvider().getApiKey();
        if (baseUrl == null || baseUrl.isBlank())
        {
            throw new AiProviderException("模型 " + model + " 未配置 base-url");
        }
        if (apiKey == null || apiKey.isBlank())
        {
            throw new AiProviderException("模型 " + model + " 未配置 API Key（环境变量）");
        }
        String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

        ObjectNode body = AiJson.MAPPER.createObjectNode();
        body.put("model", request.getModel());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.getSystemPrompt());
        messages.addObject().put("role", "user").put("content", request.getUserPrompt());
        body.put("temperature", request.getTemperature());
        if (request.getMaxTokens() > 0)
        {
            body.put("max_tokens", request.getMaxTokens());
        }
        body.putObject("response_format").put("type", "json_object");

        HttpRequest httpRequest;
        try
        {
            httpRequest = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(1000, request.getTimeoutMs())))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
        }
        catch (IllegalArgumentException e)
        {
            throw new AiProviderException("模型 " + model + " 的 base-url 不合法");
        }

        HttpResponse<String> response;
        try
        {
            response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        catch (HttpTimeoutException e)
        {
            throw new AiProviderException("模型 " + model + " 调用超时（" + request.getTimeoutMs() + " ms）", e);
        }
        catch (IOException e)
        {
            throw new AiProviderException("模型 " + model + " 连接失败：" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " " + redact(e.getMessage(), apiKey)), e);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new AiProviderException("模型 " + model + " 调用被中断", e);
        }

        String text = response.body() == null ? "" : response.body();
        if (response.statusCode() != 200)
        {
            throw new AiProviderException("模型 " + model + " 返回 HTTP " + response.statusCode() + "："
                    + AiJson.truncate(redact(text, apiKey), 300));
        }
        JsonNode json = AiJson.parseOrNull(text);
        if (json == null)
        {
            throw new AiProviderException("模型 " + model + " 响应不是合法 JSON");
        }
        JsonNode content = json.at("/choices/0/message/content");
        if (content.isMissingNode() || content.isNull() || content.asString().isBlank())
        {
            throw new AiProviderException("模型 " + model + " 返回内容为空");
        }
        JsonNode usage = json.path("usage");
        return new AiModelResponse(content.asString(), usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0));
    }

    private static String redact(String text, String apiKey)
    {
        if (text == null || apiKey == null || apiKey.isBlank())
        {
            return text;
        }
        return text.replace(apiKey, "***");
    }
}
