package com.ygb.abroad.integration.channel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 渠道接口的 HTTP 调用（JSON 进出、5 秒连接 / 10 秒总超时、不跟随跳转）
 *
 * @author ygb
 */
@Component
public class ChannelHttp
{
    static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    /**
     * GET 取 JSON
     *
     * @param url 地址
     * @return 响应 JSON
     * @throws IOException 网络错误或响应不是 JSON
     */
    public JsonNode get(String url) throws IOException
    {
        return call(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build());
    }

    /**
     * POST JSON
     *
     * @param url 地址
     * @param body 请求体
     * @return 响应 JSON
     * @throws IOException 网络错误或响应不是 JSON
     */
    public JsonNode post(String url, Map<String, Object> body) throws IOException
    {
        String json = MAPPER.writeValueAsString(body);
        return call(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build());
    }

    private JsonNode call(HttpRequest request) throws IOException
    {
        try
        {
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2)
            {
                throw new IOException("HTTP " + resp.statusCode());
            }
            return MAPPER.readTree(resp.body());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断", e);
        }
        catch (JacksonException e)
        {
            throw new IOException("响应不是 JSON", e);
        }
    }
}
