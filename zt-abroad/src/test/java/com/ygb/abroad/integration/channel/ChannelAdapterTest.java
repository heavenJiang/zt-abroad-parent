package com.ygb.abroad.integration.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 渠道适配器测试（本机 HTTP 桩，不访问外网）：服务号、小程序、企业微信的请求体，令牌失效清缓存重试一次，平台错误码，未配置为 off，mock 不发请求，脱敏
 *
 * @author ygb
 */
class ChannelAdapterTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static HttpServer server;

    private static String base;

    /** 每次请求：路径?查询 与 正文 */
    private static final List<String[]> REQUESTS = new ArrayList<>();

    /** 依次返回的响应（用完后返回最后一个） */
    private static final List<String> RESPONSES = new ArrayList<>();

    @BeforeAll
    static void start() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String out;
            synchronized (REQUESTS)
            {
                REQUESTS.add(new String[] { ex.getRequestURI().getPath() + "?" + ex.getRequestURI().getRawQuery(), body });
                out = RESPONSES.size() > 1 ? RESPONSES.remove(0) : RESPONSES.get(0);
            }
            byte[] bytes = out.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody())
            {
                os.write(bytes);
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

    @BeforeEach
    void reset()
    {
        REQUESTS.clear();
        RESPONSES.clear();
    }

    /** 不用 Redis 的令牌桩：每次清缓存后令牌编号加一 */
    static class FakeTokens extends WechatTokenStore
    {
        final AtomicInteger version = new AtomicInteger(1);

        final List<String> evicted = new ArrayList<>();

        @Override
        public String wechat(String apiBase, String name, String appId, String secret)
        {
            return name + "-token-" + version.get();
        }

        @Override
        public String wecom(String apiBase, String corpId, String secret)
        {
            return "wecom-token-" + version.get();
        }

        @Override
        public void evict(String name)
        {
            evicted.add(name);
            version.incrementAndGet();
        }
    }

    private static void set(Object target, Class<?> cls, String name, Object value) throws Exception
    {
        Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static WechatOaTemplateAdapter oa(String appId, boolean mock, FakeTokens tokens) throws Exception
    {
        WechatOaTemplateAdapter a = new WechatOaTemplateAdapter();
        set(a, WechatOaTemplateAdapter.class, "appId", appId);
        set(a, WechatOaTemplateAdapter.class, "appSecret", "secret");
        set(a, WechatOaTemplateAdapter.class, "apiBase", base);
        set(a, WechatOaTemplateAdapter.class, "mock", mock);
        set(a, WechatOaTemplateAdapter.class, "tokens", tokens);
        set(a, WechatOaTemplateAdapter.class, "http", new ChannelHttp());
        return a;
    }

    private static OutboundMessage msg(String link)
    {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("thing1", "选校咨询");
        fields.put("time2", "2026-11-01 10:00");
        return new OutboundMessage("oOPENID123456", "TPL_1", fields, "你已预约选校咨询", link);
    }

    @Test
    void oaSendsTemplateMessage() throws Exception
    {
        RESPONSES.add("{\"errcode\":0,\"errmsg\":\"ok\",\"msgid\":200228332}");
        ChannelSendResult r = oa("wxapp", false, new FakeTokens()).send(msg("https://app.example.com/plan"));
        assertEquals(ChannelSendResult.SENT, r.status());
        assertEquals("200228332", r.platformMsgId());
        assertEquals(1, REQUESTS.size());
        assertEquals("/cgi-bin/message/template/send?access_token=oa-token-1", REQUESTS.get(0)[0]);
        JsonNode body = MAPPER.readTree(REQUESTS.get(0)[1]);
        assertEquals("oOPENID123456", body.path("touser").asString());
        assertEquals("TPL_1", body.path("template_id").asString());
        assertEquals("https://app.example.com/plan", body.path("url").asString());
        assertEquals("选校咨询", body.path("data").path("thing1").path("value").asString());
        assertEquals("2026-11-01 10:00", body.path("data").path("time2").path("value").asString());
    }

    @Test
    void oaIgnoresNonWebLink() throws Exception
    {
        RESPONSES.add("{\"errcode\":0,\"msgid\":1}");
        oa("wxapp", false, new FakeTokens()).send(msg("pages/plan/index"));
        assertTrue(MAPPER.readTree(REQUESTS.get(0)[1]).path("url").isMissingNode());
    }

    @Test
    void tokenInvalidEvictsAndRetriesOnce() throws Exception
    {
        RESPONSES.add("{\"errcode\":40001,\"errmsg\":\"invalid credential\"}");
        RESPONSES.add("{\"errcode\":0,\"msgid\":7}");
        FakeTokens tokens = new FakeTokens();
        ChannelSendResult r = oa("wxapp", false, tokens).send(msg(null));
        assertEquals(ChannelSendResult.SENT, r.status());
        assertEquals(List.of("oa"), tokens.evicted);
        assertEquals(2, REQUESTS.size());
        assertTrue(REQUESTS.get(1)[0].endsWith("access_token=oa-token-2"));
    }

    @Test
    void tokenInvalidTwiceFails() throws Exception
    {
        RESPONSES.add("{\"errcode\":42001,\"errmsg\":\"access_token expired\"}");
        ChannelSendResult r = oa("wxapp", false, new FakeTokens()).send(msg(null));
        assertEquals(ChannelSendResult.FAILED, r.status());
        assertEquals(2, REQUESTS.size());
        assertTrue(r.error().startsWith("42001"));
    }

    @Test
    void platformErrorIsFailedWithCode() throws Exception
    {
        RESPONSES.add("{\"errcode\":43004,\"errmsg\":\"require subscribe\"}");
        ChannelSendResult r = oa("wxapp", false, new FakeTokens()).send(msg(null));
        assertEquals(ChannelSendResult.FAILED, r.status());
        assertEquals("43004 require subscribe", r.error());
    }

    @Test
    void notConfiguredIsOffAndMockSendsNothing() throws Exception
    {
        WechatOaTemplateAdapter off = oa("", false, new FakeTokens());
        assertFalse(off.configured());
        assertEquals(ChannelSendResult.OFF, off.send(msg(null)).status());
        WechatOaTemplateAdapter mock = oa("", true, new FakeTokens());
        assertTrue(mock.configured());
        assertEquals(ChannelSendResult.MOCK, mock.send(msg(null)).status());
        assertEquals(0, REQUESTS.size());
    }

    @Test
    void missingTemplateIdFailsWithoutRequest() throws Exception
    {
        ChannelSendResult r = oa("wxapp", false, new FakeTokens()).send(new OutboundMessage("oOPENID", "", Map.of(), "x", null));
        assertEquals(ChannelSendResult.FAILED, r.status());
        assertEquals(0, REQUESTS.size());
    }

    @Test
    void miniSendsPageAndState() throws Exception
    {
        RESPONSES.add("{\"errcode\":0,\"errmsg\":\"ok\"}");
        MiniSubscribeAdapter a = new MiniSubscribeAdapter();
        set(a, MiniSubscribeAdapter.class, "appId", "wxmini");
        set(a, MiniSubscribeAdapter.class, "appSecret", "s");
        set(a, MiniSubscribeAdapter.class, "state", "trial");
        set(a, MiniSubscribeAdapter.class, "apiBase", base);
        set(a, MiniSubscribeAdapter.class, "mock", false);
        set(a, MiniSubscribeAdapter.class, "tokens", new FakeTokens());
        set(a, MiniSubscribeAdapter.class, "http", new ChannelHttp());
        assertEquals(ChannelSendResult.SENT, a.send(msg("pages/plan/index?id=1")).status());
        assertEquals("/cgi-bin/message/subscribe/send?access_token=mini-token-1", REQUESTS.get(0)[0]);
        JsonNode body = MAPPER.readTree(REQUESTS.get(0)[1]);
        assertEquals("pages/plan/index?id=1", body.path("page").asString());
        assertEquals("trial", body.path("miniprogram_state").asString());
    }

    private static WecomAppAdapter wecom() throws Exception
    {
        WecomAppAdapter a = new WecomAppAdapter();
        set(a, WecomAppAdapter.class, "corpId", "ww1");
        set(a, WecomAppAdapter.class, "agentId", "1000002");
        set(a, WecomAppAdapter.class, "secret", "s");
        set(a, WecomAppAdapter.class, "apiBase", base);
        set(a, WecomAppAdapter.class, "mock", false);
        set(a, WecomAppAdapter.class, "tokens", new FakeTokens());
        set(a, WecomAppAdapter.class, "http", new ChannelHttp());
        return a;
    }

    @Test
    void wecomSendsText() throws Exception
    {
        RESPONSES.add("{\"errcode\":0,\"errmsg\":\"ok\",\"invaliduser\":\"\",\"msgid\":\"xyz\"}");
        ChannelSendResult r = wecom().send(new OutboundMessage("zhangsan", null, Map.of(), "新线索（A 类）\n请在 10:00 前联系", null));
        assertEquals(ChannelSendResult.SENT, r.status());
        assertEquals("xyz", r.platformMsgId());
        JsonNode body = MAPPER.readTree(REQUESTS.get(0)[1]);
        assertEquals("zhangsan", body.path("touser").asString());
        assertEquals("text", body.path("msgtype").asString());
        assertEquals("1000002", body.path("agentid").asString());
        assertTrue(body.path("text").path("content").asString().startsWith("新线索"));
    }

    @Test
    void wecomInvalidUserFails() throws Exception
    {
        RESPONSES.add("{\"errcode\":0,\"errmsg\":\"ok\",\"invaliduser\":\"nobody\"}");
        ChannelSendResult r = wecom().send(new OutboundMessage("nobody", null, Map.of(), "x", null));
        assertEquals(ChannelSendResult.FAILED, r.status());
        assertTrue(r.error().contains("nobody"));
    }

    @Test
    void smsIsOffUnlessMock() throws Exception
    {
        SmsAdapter sms = new SmsAdapter();
        assertFalse(sms.configured());
        assertEquals(ChannelSendResult.OFF, sms.send(new OutboundMessage("13800001234", "SMS_1", Map.of(), "x", null)).status());
        set(sms, SmsAdapter.class, "mock", true);
        assertEquals(ChannelSendResult.MOCK, sms.send(new OutboundMessage("13800001234", "SMS_1", Map.of(), "x", null)).status());
    }

    @Test
    void masking()
    {
        assertEquals("*******1234", ChannelMask.recipient("13800001234"));
        assertEquals("oOP***456", ChannelMask.recipient("oOPENID123456"));
        assertEquals("z***", ChannelMask.recipient("zhang"));
        assertEquals("", ChannelMask.recipient(null));
        assertTrue(WechatTokenStore.tokenInvalid(40014));
        assertFalse(WechatTokenStore.tokenInvalid(43004));
    }
}
