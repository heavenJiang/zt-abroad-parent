package com.ygb.abroad.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 收录提交测试（本机 HTTP 桩，不访问外网）：未配置不提交、百度推送正文与参数、IndexNow 正文与已删除地址、条数上限、密钥格式
 *
 * @author ygb
 */
class AbrSearchSubmitterTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static HttpServer server;

    private static String base;

    private static final Map<String, String[]> REQUESTS = new ConcurrentHashMap<>();

    @BeforeAll
    static void start() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            REQUESTS.put(ex.getRequestURI().getPath(), new String[] { ex.getRequestURI().getRawQuery(), body, ex.getRequestHeaders().getFirst("Content-Type") });
            byte[] out = "/baidu".equals(ex.getRequestURI().getPath()) ? "{\"remain\":98,\"success\":2}".getBytes(StandardCharsets.UTF_8) : new byte[0];
            ex.sendResponseHeaders("/baidu".equals(ex.getRequestURI().getPath()) ? 200 : 202, out.length == 0 ? -1 : out.length);
            try (OutputStream os = ex.getResponseBody())
            {
                os.write(out);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stop()
    {
        server.stop(0);
    }

    private static AbrSearchSubmitter submitter(String baiduToken, String indexNowKey, int maxUrls) throws Exception
    {
        AbrSearchSubmitter s = new AbrSearchSubmitter();
        set(s, "baiduToken", baiduToken);
        set(s, "baiduEndpoint", base + "/baidu");
        set(s, "indexNowKey", indexNowKey);
        set(s, "indexNowEndpoint", base + "/indexnow");
        set(s, "maxUrls", maxUrls);
        return s;
    }

    private static void set(Object target, String name, Object value) throws Exception
    {
        Field f = AbrSearchSubmitter.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void offWhenNotConfigured() throws Exception
    {
        REQUESTS.clear();
        Map<String, Object> r = submitter("", "", 500).submit("https://www.example.com/", List.of("https://www.example.com/program/1"), List.of());
        assertEquals("off", r.get("baidu"));
        assertEquals("off", r.get("indexnow"));
        assertEquals(0, r.get("count"));
        assertTrue(REQUESTS.isEmpty());
    }

    @Test
    void submitsChangedAndRemoved() throws Exception
    {
        REQUESTS.clear();
        AbrSearchSubmitter s = submitter("tok123", "abcdef0123456789", 500);
        Map<String, Object> r = s.submit("https://www.example.com/", List.of("https://www.example.com/program/1", "https://www.example.com/"),
                List.of("https://www.example.com/program/9"));
        String[] baidu = REQUESTS.get("/baidu");
        assertEquals("site=https%3A%2F%2Fwww.example.com&token=tok123", baidu[0]);
        assertEquals("https://www.example.com/program/1\nhttps://www.example.com/", baidu[1]);
        assertEquals(200, ((Map<?, ?>) r.get("baidu")).get("status"));
        JsonNode body = MAPPER.readTree(REQUESTS.get("/indexnow")[1]);
        assertEquals("www.example.com", body.path("host").asString(""));
        assertEquals("abcdef0123456789", body.path("key").asString(""));
        assertEquals("https://www.example.com/abcdef0123456789.txt", body.path("keyLocation").asString(""));
        assertEquals(3, body.path("urlList").size(), "IndexNow 含已删除的地址");
        assertEquals(202, ((Map<?, ?>) r.get("indexnow")).get("status"));
        assertEquals(3, r.get("count"));
    }

    @Test
    void capsUrlsAndChecksKey() throws Exception
    {
        REQUESTS.clear();
        AbrSearchSubmitter s = submitter("tok", "bad key!", 1);
        assertNull(s.indexNowKey());
        Map<String, Object> r = s.submit("https://www.example.com/", List.of("https://www.example.com/a", "https://www.example.com/b"), List.of());
        assertEquals("https://www.example.com/a", REQUESTS.get("/baidu")[1]);
        assertEquals(1, r.get("truncated"));
        assertTrue(r.get("indexnow").toString().contains("密钥格式不对"));
    }
}
