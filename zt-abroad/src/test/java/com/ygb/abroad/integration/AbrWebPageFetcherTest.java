package com.ygb.abroad.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 页面抓取测试：地址拦截（SSRF）、重定向逐跳校验、大小与类型限制、字符集、超时
 *
 * @author ygb
 */
class AbrWebPageFetcherTest
{
    private static HttpServer server;

    private static String base;

    /** 每个路径被请求的次数 */
    private static final Map<String, AtomicInteger> HITS = new ConcurrentHashMap<>();

    private static final String PAGE = "<!DOCTYPE html><html><head><title>MSc Finance &amp; Banking</title>"
            + "<script>var ielts = 'IELTS 5.5';</script><style>.x{}</style></head><body>"
            + "<nav><ul><li>Home</li><li>Study</li></ul></nav>"
            + "<h1>MSc Finance</h1><p>International students: &pound;38,500&nbsp;per year</p>"
            + "<p>IELTS 7.0 overall with no less than 6.5 in each component.</p>"
            + "<footer>Copyright footer text</footer></body></html>";

    @BeforeAll
    static void start() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/", AbrWebPageFetcherTest::handle);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stop()
    {
        server.stop(0);
    }

    private static void handle(HttpExchange ex) throws IOException
    {
        String path = ex.getRequestURI().getPath();
        HITS.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
        int port = server.getAddress().getPort();
        switch (path)
        {
            case "/page":
                send(ex, 200, "text/html; charset=utf-8", PAGE.getBytes(StandardCharsets.UTF_8));
                break;
            case "/gbk":
                send(ex, 200, "text/html", ("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=gbk\"></head>"
                        + "<body><p>雅思总分 6.5，单项不低于 6.0</p></body></html>").getBytes(Charset.forName("GBK")));
                break;
            case "/plain":
                send(ex, 200, "text/plain; charset=utf-8", "Line one\n\n   Line   two  ".getBytes(StandardCharsets.UTF_8));
                break;
            case "/pdf":
                send(ex, 200, "application/pdf", "%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII));
                break;
            case "/image":
                send(ex, 200, "image/png", new byte[] { (byte) 0x89, 'P', 'N', 'G' });
                break;
            case "/sniff-pdf":
                send(ex, 200, null, "%PDF-1.4 no content type".getBytes(StandardCharsets.US_ASCII));
                break;
            case "/big":
                byte[] big = new byte[2 * 1024 * 1024 + 10];
                Arrays.fill(big, (byte) 'a');
                send(ex, 200, "text/html", big);
                break;
            case "/big-chunked":
                ex.getResponseHeaders().add("Content-Type", "text/html");
                ex.sendResponseHeaders(200, 0);
                try (OutputStream out = ex.getResponseBody())
                {
                    byte[] chunk = new byte[64 * 1024];
                    Arrays.fill(chunk, (byte) 'b');
                    for (int k = 0; k < 40; k++)
                    {
                        out.write(chunk);
                    }
                }
                catch (IOException e)
                {
                    // 客户端超限后断开
                }
                break;
            case "/missing":
                send(ex, 404, "text/html", "not found".getBytes(StandardCharsets.UTF_8));
                break;
            case "/login":
                send(ex, 403, "text/html", "forbidden".getBytes(StandardCharsets.UTF_8));
                break;
            case "/error":
                send(ex, 500, "text/html", "oops".getBytes(StandardCharsets.UTF_8));
                break;
            case "/slow":
                try
                {
                    Thread.sleep(3000);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
                send(ex, 200, "text/html", PAGE.getBytes(StandardCharsets.UTF_8));
                break;
            case "/r1":
                redirect(ex, "/r2");
                break;
            case "/r2":
                redirect(ex, base + "/r3");
                break;
            case "/r3":
                redirect(ex, "r4");
                break;
            case "/r4":
                redirect(ex, "/page");
                break;
            case "/to-private":
                redirect(ex, "http://127.0.0.2:" + port + "/page");
                break;
            case "/to-ipv6-loopback":
                redirect(ex, "http://[::1]:" + port + "/page");
                break;
            case "/to-ftp":
                redirect(ex, "ftp://example.com/file");
                break;
            default:
                send(ex, 404, "text/plain", new byte[0]);
        }
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException
    {
        if (contentType != null)
        {
            ex.getResponseHeaders().add("Content-Type", contentType);
        }
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = ex.getResponseBody())
        {
            out.write(body);
        }
        catch (IOException e)
        {
            // 客户端提前断开
        }
    }

    private static void redirect(HttpExchange ex, String location) throws IOException
    {
        ex.getResponseHeaders().add("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    private static AbrWebPageFetcher fetcher(boolean allowPrivate)
    {
        AbrFetchProperties p = new AbrFetchProperties();
        p.setAllowPrivateHosts(allowPrivate);
        return new AbrWebPageFetcher(p);
    }

    /**
     * 只把 127.0.0.1 视为“公网”的抓取器：用来验证重定向的每一跳都重新校验
     */
    private static AbrWebPageFetcher publicLoopbackFetcher()
    {
        AbrFetchProperties p = new AbrFetchProperties();
        p.setAllowPrivateHosts(false);
        return new AbrWebPageFetcher(p)
        {
            @Override
            protected boolean isBlocked(InetAddress address)
            {
                return !"127.0.0.1".equals(address.getHostAddress()) && super.isBlocked(address);
            }
        };
    }

    private static String reason(Runnable r)
    {
        AbrFetchException e = assertThrows(AbrFetchException.class, r::run);
        return e.getReason() + "|" + e.getMessage();
    }

    @Test
    void fetchesHtmlAndConvertsToText()
    {
        AbrWebPage page = fetcher(true).fetch(base + "/page#entry-requirements");
        assertEquals("MSc Finance & Banking", page.getTitle());
        assertEquals("text/html", page.getContentType());
        assertEquals("UTF-8", page.getCharset());
        assertTrue(page.getText().contains("International students: £38,500 per year"), page.getText());
        assertTrue(page.getText().contains("IELTS 7.0 overall"), page.getText());
        assertFalse(page.getText().contains("IELTS 5.5"), "script 内容应去掉");
        assertFalse(page.getText().contains("Home"), "nav 内容应去掉");
        assertFalse(page.getText().contains("Copyright"), "footer 内容应去掉");
        assertEquals(base + "/page", page.getFinalUrl());
        assertFalse(page.isTruncated());
        assertTrue(page.getFetchedAt() != null && page.getElapsedMs() >= 0);
    }

    @Test
    void charsetFromMetaAndPlainText()
    {
        AbrWebPage gbk = fetcher(true).fetch(base + "/gbk");
        assertEquals("GB18030", gbk.getCharset());
        assertTrue(gbk.getText().contains("雅思总分 6.5，单项不低于 6.0"), gbk.getText());
        AbrWebPage plain = fetcher(true).fetch(base + "/plain");
        assertEquals("text/plain", plain.getContentType());
        assertEquals("Line one\nLine two", plain.getText());
    }

    @Test
    void truncatesToMaxChars()
    {
        AbrFetchProperties p = new AbrFetchProperties();
        p.setAllowPrivateHosts(true);
        p.setMaxChars(20);
        AbrWebPage page = new AbrWebPageFetcher(p).fetch(base + "/page");
        assertEquals(20, page.getText().length());
        assertTrue(page.isTruncated());
    }

    @Test
    void blocksPrivateAndLoopbackWhenNotAllowed()
    {
        AbrWebPageFetcher strict = fetcher(false);
        assertTrue(reason(() -> strict.fetch(base + "/page")).startsWith(AbrFetchException.BLOCKED + "|不能抓取该地址"));
        assertTrue(reason(() -> strict.fetch("http://localhost:" + server.getAddress().getPort() + "/page")).startsWith(AbrFetchException.BLOCKED));
        assertTrue(reason(() -> strict.fetch("http://[::1]/")).startsWith(AbrFetchException.BLOCKED));
        assertTrue(reason(() -> strict.fetch("http://169.254.169.254/latest/meta-data/")).startsWith(AbrFetchException.BLOCKED));
        assertTrue(reason(() -> strict.fetch("http://10.1.2.3/")).startsWith(AbrFetchException.BLOCKED));
        assertTrue(reason(() -> strict.fetch("http://0.0.0.0/")).startsWith(AbrFetchException.BLOCKED));
        assertTrue(reason(() -> strict.fetch("http://2130706433/")).startsWith(AbrFetchException.BLOCKED), "十进制写法的 127.0.0.1");
        assertTrue(reason(() -> strict.fetch("http://[::ffff:192.168.1.1]/")).startsWith(AbrFetchException.BLOCKED));
        assertEquals(0, HITS.getOrDefault("/never", new AtomicInteger()).get());
    }

    @Test
    void rejectsNonHttpSchemesAndBadUrls()
    {
        AbrWebPageFetcher f = fetcher(true);
        assertTrue(reason(() -> f.fetch("ftp://example.com/x")).startsWith(AbrFetchException.INVALID_URL));
        assertTrue(reason(() -> f.fetch("file:///etc/passwd")).startsWith(AbrFetchException.INVALID_URL));
        assertTrue(reason(() -> f.fetch("javascript:alert(1)")).startsWith(AbrFetchException.INVALID_URL));
        assertTrue(reason(() -> f.fetch("www.example.com/msc")).startsWith(AbrFetchException.INVALID_URL));
        assertTrue(reason(() -> f.fetch("http://user:pass@127.0.0.1/")).startsWith(AbrFetchException.INVALID_URL));
        assertTrue(reason(() -> f.fetch("  ")).startsWith(AbrFetchException.INVALID_URL));
        assertTrue(reason(() -> f.fetch("http://exa mple.com:99999999/")).startsWith(AbrFetchException.INVALID_URL));
    }

    @Test
    void followsUpToThreeRedirects()
    {
        HITS.clear();
        // /r3 → r4（相对路径）→ /page：2 次
        AbrWebPage page = fetcher(true).fetch(base + "/r3");
        assertEquals(base + "/page", page.getFinalUrl());
        assertEquals(2, page.getRedirects());
        // /r2 → 绝对地址 /r3 → r4 → /page：3 次，允许
        AbrWebPage three = fetcher(true).fetch(base + "/r2");
        assertEquals(3, three.getRedirects());
        assertTrue(three.getText().contains("MSc Finance"));
        // /r1：4 次，超过上限
        String r = reason(() -> fetcher(true).fetch(base + "/r1"));
        assertTrue(r.startsWith(AbrFetchException.OPEN_FAILED + "|页面打不开：重定向超过 3 次"), r);

        AbrFetchProperties p = new AbrFetchProperties();
        p.setAllowPrivateHosts(true);
        p.setMaxRedirects(2);
        assertTrue(reason(() -> new AbrWebPageFetcher(p).fetch(base + "/r2")).contains("重定向超过 2 次"));
    }

    @Test
    void reChecksEveryRedirectHop()
    {
        AbrWebPageFetcher f = publicLoopbackFetcher();
        // 127.0.0.1 视为公网：直接抓取成功
        assertTrue(f.fetch(base + "/page").getText().contains("MSc Finance"));
        HITS.clear();
        String r = reason(() -> f.fetch(base + "/to-private"));
        assertTrue(r.startsWith(AbrFetchException.BLOCKED), r);
        assertEquals(1, HITS.get("/to-private").get());
        assertEquals(null, HITS.get("/page"), "被拦截的重定向目标不能被请求");
        assertTrue(reason(() -> f.fetch(base + "/to-ipv6-loopback")).startsWith(AbrFetchException.BLOCKED));
        assertTrue(reason(() -> f.fetch(base + "/to-ftp")).startsWith(AbrFetchException.INVALID_URL));
    }

    @Test
    void contentTypeLimits()
    {
        AbrWebPageFetcher f = fetcher(true);
        String pdf = reason(() -> f.fetch(base + "/pdf"));
        assertTrue(pdf.startsWith(AbrFetchException.NOT_HTML + "|不是网页") && pdf.contains("PDF"), pdf);
        assertTrue(reason(() -> f.fetch(base + "/sniff-pdf")).contains("PDF"));
        String image = reason(() -> f.fetch(base + "/image"));
        assertTrue(image.startsWith(AbrFetchException.NOT_HTML) && image.contains("image/png"), image);
    }

    @Test
    void sizeLimit()
    {
        AbrWebPageFetcher f = fetcher(true);
        String declared = reason(() -> f.fetch(base + "/big"));
        assertTrue(declared.startsWith(AbrFetchException.TOO_LARGE + "|页面太大"), declared);
        String streamed = reason(() -> f.fetch(base + "/big-chunked"));
        assertTrue(streamed.startsWith(AbrFetchException.TOO_LARGE + "|页面太大"), streamed);
    }

    @Test
    void statusErrors()
    {
        AbrWebPageFetcher f = fetcher(true);
        String missing = reason(() -> f.fetch(base + "/missing"));
        assertTrue(missing.startsWith(AbrFetchException.OPEN_FAILED + "|页面打不开") && missing.contains("404"), missing);
        String login = reason(() -> f.fetch(base + "/login"));
        assertTrue(login.startsWith(AbrFetchException.DENIED) && login.contains("需要登录或被拒绝"), login);
        assertTrue(reason(() -> f.fetch(base + "/error")).contains("HTTP 500"));
        // 本机没有监听的端口：连接失败
        String refused = reason(() -> f.fetch("http://127.0.0.1:1/"));
        assertTrue(refused.startsWith(AbrFetchException.OPEN_FAILED + "|页面打不开"), refused);
    }

    @Test
    void totalTimeout()
    {
        AbrFetchProperties p = new AbrFetchProperties();
        p.setAllowPrivateHosts(true);
        p.setTotalTimeoutMs(1000);
        long start = System.currentTimeMillis();
        String r = reason(() -> new AbrWebPageFetcher(p).fetch(base + "/slow"));
        assertTrue(r.startsWith(AbrFetchException.TIMEOUT) && r.contains("超时"), r);
        assertTrue(System.currentTimeMillis() - start < 2500, "应在总超时后立即返回");
    }

    @Test
    void addressClassification() throws UnknownHostException
    {
        String[] blocked = { "127.0.0.1", "127.8.9.10", "10.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.0.1", "169.254.169.254",
            "100.64.0.1", "0.0.0.0", "224.0.0.1", "255.255.255.255", "198.18.0.1", "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1",
            "ff02::1", "::ffff:10.0.0.1", "::ffff:127.0.0.1", "64:ff9b::a00:1", "2002:c0a8:0101::1", "fec0::1", "2001:db8::1" };
        for (String a : blocked)
        {
            assertTrue(AbrWebPageFetcher.isBlockedAddress(InetAddress.getByName(a)), a + " 应被拦截");
        }
        String[] allowed = { "8.8.8.8", "93.184.216.34", "172.32.0.1", "100.128.0.1", "2606:4700::6810:85e5", "64:ff9b::808:808",
            "2002:0808:0808::1" };
        for (String a : allowed)
        {
            assertFalse(AbrWebPageFetcher.isBlockedAddress(InetAddress.getByName(a)), a + " 应允许");
        }
    }

    @Test
    void charsetDetection()
    {
        assertEquals("UTF-8", AbrWebPageFetcher.charset("text/html; charset=UTF-8", new byte[0]).name());
        assertEquals("GB18030", AbrWebPageFetcher.charset("text/html; charset=\"gb2312\"", new byte[0]).name());
        assertEquals("windows-1252", AbrWebPageFetcher.charset("text/html; charset=iso-8859-1", new byte[0]).name());
        assertEquals("Shift_JIS", AbrWebPageFetcher.charset("text/html", "<meta charset=\"shift_jis\">".getBytes(StandardCharsets.US_ASCII)).name());
        assertEquals("UTF-8", AbrWebPageFetcher.charset("text/html; charset=nonsense", "<p>x</p>".getBytes(StandardCharsets.US_ASCII)).name());
    }
}
