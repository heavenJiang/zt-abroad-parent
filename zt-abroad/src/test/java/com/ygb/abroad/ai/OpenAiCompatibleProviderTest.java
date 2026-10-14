package com.ygb.abroad.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import com.ygb.abroad.ai.provider.AiModelRequest;
import com.ygb.abroad.ai.provider.AiModelResponse;
import com.ygb.abroad.ai.provider.AiProviderException;
import com.ygb.abroad.ai.provider.AiProviderRegistry;
import com.ygb.abroad.ai.provider.OpenAiCompatibleProvider;
import tools.jackson.databind.JsonNode;

/**
 * OpenAI 兼容供应商测试（本地 HttpServer 模拟接口）
 *
 * @author ygb
 */
class OpenAiCompatibleProviderTest
{
    private static final String KEY = "sk-test-secret-123456";

    private HttpServer server;

    private final AtomicReference<String> requestBody = new AtomicReference<>();

    private final AtomicReference<String> authHeader = new AtomicReference<>();

    private volatile int status = 200;

    private volatile String response;

    private volatile long delayMs;

    @BeforeEach
    void start() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            requestBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authHeader.set(ex.getRequestHeaders().getFirst("Authorization"));
            try
            {
                Thread.sleep(delayMs);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = ex.getResponseBody())
            {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stop()
    {
        server.stop(0);
    }

    private AiModelRequest request()
    {
        AiProperties.Provider p = new AiProperties.Provider();
        p.setType("openai-compatible");
        p.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        p.setApiKey(KEY);
        AiModelRequest r = new AiModelRequest();
        r.setProviderName("test");
        r.setProvider(p);
        r.setModel("m1");
        r.setSystemPrompt("系统");
        r.setUserPrompt("用户");
        r.setTimeoutMs(1000);
        r.setMaxTokens(100);
        r.setTemperature(0.2);
        return r;
    }

    @Test
    void parsesContentAndUsage()
    {
        response = "{\"choices\":[{\"message\":{\"content\":\"{\\\"a\\\":1}\"}}],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":30}}";
        AiModelResponse r = new OpenAiCompatibleProvider().chat(request());
        assertEquals("{\"a\":1}", r.getContent());
        assertEquals(120, r.getPromptTokens());
        assertEquals(30, r.getCompletionTokens());
        assertEquals("Bearer " + KEY, authHeader.get());
        JsonNode body = AiJson.parseOrNull(requestBody.get());
        assertEquals("m1", body.get("model").asString());
        assertEquals("json_object", body.at("/response_format/type").asString());
        assertEquals("system", body.at("/messages/0/role").asString());
        assertEquals("用户", body.at("/messages/1/content").asString());
        assertEquals(100, body.get("max_tokens").asInt());
    }

    @Test
    void serverErrorDoesNotLeakKey()
    {
        status = 503;
        response = "{\"error\":\"overloaded, key " + KEY + "\"}";
        AiProviderException e = assertThrows(AiProviderException.class, () -> new OpenAiCompatibleProvider().chat(request()));
        assertTrue(e.getMessage().contains("HTTP 503"), e.getMessage());
        assertFalse(e.getMessage().contains(KEY));
    }

    @Test
    void emptyContentTimeoutAndConfig()
    {
        response = "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}";
        assertTrue(assertThrows(AiProviderException.class, () -> new OpenAiCompatibleProvider().chat(request())).getMessage().contains("为空"));

        response = "{}";
        delayMs = 2500;
        assertTrue(assertThrows(AiProviderException.class, () -> new OpenAiCompatibleProvider().chat(request())).getMessage().contains("超时"));
        delayMs = 0;

        AiModelRequest noKey = request();
        noKey.getProvider().setApiKey("");
        assertTrue(assertThrows(AiProviderException.class, () -> new OpenAiCompatibleProvider().chat(noKey)).getMessage().contains("API Key"));

        AiModelRequest down = request();
        down.getProvider().setBaseUrl("http://127.0.0.1:1/v1");
        assertTrue(assertThrows(AiProviderException.class, () -> new OpenAiCompatibleProvider().chat(down)).getMessage().contains("连接失败"));
    }

    @Test
    void registryResolvesAndPrices()
    {
        AiProperties p = AiTestSupport.properties();
        AiProviderRegistry registry = new AiProviderRegistry(p);
        AiProviderRegistry.ModelRef ref = registry.resolve("mock:default");
        assertEquals(new BigDecimal("0.000160"), AiProviderRegistry.cost(ref, 100, 30));
        assertTrue(registry.check("mock").contains("格式"));
        assertTrue(registry.check("x:y").contains("未在 abroad.ai.providers 中配置"));
        assertTrue(registry.check("mock:none").contains("models"));
        assertEquals(null, registry.check("mock:backup"));
    }
}
