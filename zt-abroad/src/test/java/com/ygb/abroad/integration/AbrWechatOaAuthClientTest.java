package com.ygb.abroad.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import com.ygb.common.exception.ServiceException;

/**
 * 服务号网页授权测试（本机 HTTP 桩，不访问微信）：未配置、授权地址、code 换 openid / unionid、微信报错、mock 模式
 *
 * @author ygb
 */
class AbrWechatOaAuthClientTest
{
    private static HttpServer server;

    private static String base;

    private static volatile String lastQuery;

    @BeforeAll
    static void start() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/token", ex -> {
            lastQuery = ex.getRequestURI().getRawQuery();
            String body = lastQuery.contains("code=badcode123")
                    ? "{\"errcode\":40029,\"errmsg\":\"invalid code\"}"
                    : "{\"access_token\":\"x\",\"openid\":\"o_abc\",\"unionid\":\"u_xyz\",\"scope\":\"snsapi_base\"}";
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
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

    private static AbrWechatOaAuthClient client(String appId, String secret, boolean mock) throws Exception
    {
        AbrWechatOaAuthClient c = new AbrWechatOaAuthClient();
        set(c, "appId", appId);
        set(c, "appSecret", secret);
        set(c, "mock", mock);
        set(c, "authorizeEndpoint", "https://open.weixin.qq.com/connect/oauth2/authorize");
        set(c, "tokenEndpoint", base + "/token");
        return c;
    }

    private static void set(Object target, String name, Object value) throws Exception
    {
        Field f = AbrWechatOaAuthClient.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void notConfigured() throws Exception
    {
        AbrWechatOaAuthClient c = client("", "", false);
        assertFalse(c.enabled());
        assertTrue(assertThrows(ServiceException.class, () -> c.exchange("abcdefgh123")).getMessage().contains("尚未开通"));
    }

    @Test
    void authorizeAndExchange() throws Exception
    {
        AbrWechatOaAuthClient c = client("wx123", "sec", false);
        String url = c.authorizeUrl("https://app.example.com/pages/login?from=plan", "stateabcdefghijklmnop");
        assertTrue(url.startsWith("https://open.weixin.qq.com/connect/oauth2/authorize?appid=wx123&redirect_uri=https%3A%2F%2Fapp.example.com"));
        assertTrue(url.endsWith("&response_type=code&scope=snsapi_base&state=stateabcdefghijklmnop#wechat_redirect"));
        AbrWechatOaAuthClient.Identity id = c.exchange("goodcode123");
        assertEquals("o_abc", id.openid());
        assertEquals("u_xyz", id.unionid());
        assertEquals("appid=wx123&secret=sec&code=goodcode123&grant_type=authorization_code", lastQuery);
        assertTrue(assertThrows(ServiceException.class, () -> c.exchange("badcode123")).getMessage().contains("微信登录失败"));
        assertThrows(ServiceException.class, () -> c.exchange("a b"));
    }

    @Test
    void mockMode() throws Exception
    {
        AbrWechatOaAuthClient c = client("", "", true);
        assertTrue(c.enabled());
        AbrWechatOaAuthClient.Identity id = c.exchange("mock_alice");
        assertEquals("mock_alice", id.openid());
        assertNull(id.unionid());
        assertThrows(ServiceException.class, () -> c.exchange("goodcode123"));
        assertTrue(c.authorizeUrl("https://app.example.com/login", "stateabcdefghijklmnop").startsWith("https://app.example.com/login?code=mock_stateabc&state="));
    }
}
