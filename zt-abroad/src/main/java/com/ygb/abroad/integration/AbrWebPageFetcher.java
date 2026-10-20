package com.ygb.abroad.integration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 安全的官网页面抓取（AI 预填用）
 * <ul>
 * <li>只允许 http / https；解析域名后拒绝本机、内网、链路本地、组播、未指定等地址（IPv4、IPv6），防止 SSRF；</li>
 * <li>最多跟随 3 次重定向，每一跳都重新校验地址；连接超时 10 秒，总超时 15 秒；响应体最多 2 MB；</li>
 * <li>只接受 text/html、application/xhtml+xml、text/plain；字符集取响应头，没有则取 meta 标签，默认 UTF-8；</li>
 * <li>HTML 转纯文本后截断到配置的最大字符数（默认 30000）。</li>
 * </ul>
 * <p>
 * 已知局限：地址校验与实际连接各做一次域名解析，理论上存在 DNS 重绑定窗口；生产环境建议同时在网络出口限制内网访问。
 * </p>
 *
 * @author ygb
 */
@Component
public class AbrWebPageFetcher
{
    private static final Logger log = LoggerFactory.getLogger(AbrWebPageFetcher.class);

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; AbroadCatalogBot/1.0; +manual-data-entry)";

    /** meta 中的字符集：&lt;meta charset="x"&gt; 或 content="text/html; charset=x" */
    private static final Pattern META_CHARSET = Pattern.compile("<meta[^>]+charset\\s*=\\s*[\"']?\\s*([A-Za-z0-9_\\-:.]+)",
            Pattern.CASE_INSENSITIVE);

    private final AbrFetchProperties properties;

    private final HttpClient client;

    /**
     * 创建抓取器
     *
     * @param properties 抓取配置
     */
    public AbrWebPageFetcher(AbrFetchProperties properties)
    {
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(ProxySelector.getDefault())
                .build();
    }

    /**
     * 抓取页面并转为纯文本
     *
     * @param url 页面链接
     * @return 页面
     * @throws AbrFetchException 抓取失败（消息为中文原因）
     */
    public AbrWebPage fetch(String url)
    {
        return fetch(url, 0);
    }

    /**
     * 抓取页面并转为纯文本，同时保留原始 HTML（项目采集发现链接用）
     *
     * @param url 页面链接
     * @param maxChars 纯文本最大字符数（0 为按配置）
     * @return 页面
     * @throws AbrFetchException 抓取失败（消息为中文原因）
     */
    public AbrWebPage fetch(String url, int maxChars)
    {
        long start = System.nanoTime();
        long deadline = start + TimeUnit.MILLISECONDS.toNanos(properties.getTotalTimeoutMs());
        URI uri = parse(url);
        int redirects = 0;
        while (true)
        {
            checkTarget(uri);
            long remainingMs = remainingMs(deadline);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofMillis(remainingMs))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.1")
                    .header("Accept-Language", "en,zh-CN;q=0.8,zh;q=0.7")
                    .build();
            CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request, this::bodyHandler);
            HttpResponse<byte[]> response = await(future, remainingMs, uri);
            int status = response.statusCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308)
            {
                Optional<String> location = response.headers().firstValue("location");
                if (location.isEmpty() || location.get().isBlank())
                {
                    throw new AbrFetchException(AbrFetchException.OPEN_FAILED, "页面打不开：网站返回了重定向（HTTP " + status + "）但没有给出新地址");
                }
                if (redirects >= properties.getMaxRedirects())
                {
                    throw new AbrFetchException(AbrFetchException.OPEN_FAILED,
                            "页面打不开：重定向超过 " + properties.getMaxRedirects() + " 次，请直接粘贴最终的项目页面链接");
                }
                uri = resolveRedirect(uri, location.get().trim());
                redirects++;
                continue;
            }
            checkStatus(status);
            String mime = mime(response.headers().firstValue("content-type").orElse(""));
            checkContentType(mime);
            byte[] body = response.body() == null ? new byte[0] : response.body();
            if (body.length > properties.getMaxBytes())
            {
                throw tooLarge();
            }
            if (mime.isEmpty() && startsWith(body, "%PDF"))
            {
                checkContentType("application/pdf");
            }
            return toPage(url, uri, mime, response.headers().firstValue("content-type").orElse(""), body, redirects, start, maxChars);
        }
    }

    /**
     * 校验地址：协议、主机名，以及（不允许内网时）解析后的全部 IP
     *
     * @param uri 地址
     */
    void checkTarget(URI uri)
    {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme))
        {
            throw new AbrFetchException(AbrFetchException.INVALID_URL, "链接格式不正确：只支持 http:// 或 https:// 开头的网页地址");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank())
        {
            throw new AbrFetchException(AbrFetchException.INVALID_URL, "链接格式不正确：缺少网站域名");
        }
        if (uri.getRawUserInfo() != null)
        {
            throw new AbrFetchException(AbrFetchException.INVALID_URL, "链接格式不正确：不支持带用户名、密码的链接");
        }
        if (properties.isAllowPrivateHosts())
        {
            return;
        }
        InetAddress[] addresses;
        try
        {
            addresses = InetAddress.getAllByName(host);
        }
        catch (UnknownHostException e)
        {
            throw new AbrFetchException(AbrFetchException.OPEN_FAILED, "页面打不开：域名 " + displayHost(host) + " 无法解析，请检查链接是否正确");
        }
        for (InetAddress address : addresses)
        {
            if (isBlocked(address))
            {
                throw new AbrFetchException(AbrFetchException.BLOCKED,
                        "不能抓取该地址：" + displayHost(host) + " 指向本机或内网，出于安全原因只允许抓取公网网页");
            }
        }
    }

    /**
     * 地址是否不允许访问（单元测试可覆盖以模拟“公网”地址）
     *
     * @param address 解析得到的地址
     * @return true 不允许
     */
    protected boolean isBlocked(InetAddress address)
    {
        return isBlockedAddress(address);
    }

    /**
     * 是否为不允许访问的地址：本机、内网、链路本地、组播、未指定、运营商级 NAT、保留地址，以及嵌入了这些 IPv4 的 IPv6
     *
     * @param address 地址
     * @return true 不允许
     */
    public static boolean isBlockedAddress(InetAddress address)
    {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress())
        {
            return true;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address)
        {
            return isBlockedIpv4(b);
        }
        if (address instanceof Inet6Address)
        {
            int first = b[0] & 0xff;
            // fc00::/7 唯一本地地址
            if ((first & 0xfe) == 0xfc)
            {
                return true;
            }
            // ::/96（含 :: 与 ::1、IPv4 兼容地址）与 ::ffff:0:0/96（IPv4 映射）：按内嵌 IPv4 判断
            boolean zero80 = true;
            for (int k = 0; k < 10; k++)
            {
                if (b[k] != 0)
                {
                    zero80 = false;
                    break;
                }
            }
            if (zero80 && ((b[10] == 0 && b[11] == 0) || ((b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff)))
            {
                return isBlockedIpv4(new byte[] { b[12], b[13], b[14], b[15] }) || (b[12] == 0 && b[13] == 0 && b[14] == 0);
            }
            // 64:ff9b::/96 NAT64：按内嵌 IPv4 判断
            if (b[0] == 0 && (b[1] & 0xff) == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b)
            {
                return isBlockedIpv4(new byte[] { b[12], b[13], b[14], b[15] });
            }
            // 2002::/16 6to4：按内嵌 IPv4 判断
            if ((b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x02)
            {
                return isBlockedIpv4(new byte[] { b[2], b[3], b[4], b[5] });
            }
            // 文档地址 2001:db8::/32
            return (b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8;
        }
        return true;
    }

    private static boolean isBlockedIpv4(byte[] b)
    {
        int a0 = b[0] & 0xff;
        int a1 = b[1] & 0xff;
        int a2 = b[2] & 0xff;
        return a0 == 0                                  // 0.0.0.0/8 未指定
                || a0 == 10                             // 10.0.0.0/8
                || a0 == 127                            // 127.0.0.0/8 本机
                || (a0 == 100 && a1 >= 64 && a1 <= 127) // 100.64.0.0/10 运营商级 NAT
                || (a0 == 169 && a1 == 254)             // 169.254.0.0/16 链路本地（含云厂商元数据地址）
                || (a0 == 172 && a1 >= 16 && a1 <= 31)  // 172.16.0.0/12
                || (a0 == 192 && a1 == 168)             // 192.168.0.0/16
                || (a0 == 192 && a1 == 0 && a2 == 0)    // 192.0.0.0/24 协议分配
                || (a0 == 192 && a1 == 0 && a2 == 2)    // 192.0.2.0/24 文档
                || (a0 == 198 && (a1 == 18 || a1 == 19)) // 198.18.0.0/15 基准测试
                || (a0 == 198 && a1 == 51 && a2 == 100) // 198.51.100.0/24 文档
                || (a0 == 203 && a1 == 0 && a2 == 113)  // 203.0.113.0/24 文档
                || a0 >= 224;                           // 组播、保留、广播
    }

    /**
     * 解析链接
     */
    private static URI parse(String url)
    {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty())
        {
            throw new AbrFetchException(AbrFetchException.INVALID_URL, "请填写项目官网链接");
        }
        if (u.length() > 1000)
        {
            throw new AbrFetchException(AbrFetchException.INVALID_URL, "链接过长（超过 1000 个字符）");
        }
        try
        {
            int hash = u.indexOf('#');
            // 片段（#entry-requirements）不发送给服务器
            URI uri = new URI((hash >= 0 ? u.substring(0, hash) : u).replace(" ", "%20"));
            if (uri.getScheme() == null)
            {
                throw new AbrFetchException(AbrFetchException.INVALID_URL, "链接格式不正确：请以 http:// 或 https:// 开头");
            }
            return uri;
        }
        catch (URISyntaxException e)
        {
            throw new AbrFetchException(AbrFetchException.INVALID_URL, "链接格式不正确：" + e.getReason());
        }
    }

    private static URI resolveRedirect(URI base, String location)
    {
        try
        {
            URI target = base.resolve(new URI(location.replace(" ", "%20")));
            // 片段不发送给服务器
            String text = target.toString();
            int hash = text.indexOf('#');
            return hash >= 0 ? new URI(text.substring(0, hash)) : target;
        }
        catch (URISyntaxException | IllegalArgumentException e)
        {
            throw new AbrFetchException(AbrFetchException.OPEN_FAILED, "页面打不开：网站重定向到了无效地址");
        }
    }

    private HttpResponse<byte[]> await(CompletableFuture<HttpResponse<byte[]>> future, long remainingMs, URI uri)
    {
        try
        {
            return future.get(remainingMs, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e)
        {
            future.cancel(true);
            throw timeout();
        }
        catch (InterruptedException e)
        {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AbrFetchException(AbrFetchException.TIMEOUT, "页面抓取已取消");
        }
        catch (ExecutionException e)
        {
            Throwable cause = knownCause(e);
            if (cause instanceof TooLargeException)
            {
                throw tooLarge();
            }
            if (cause instanceof HttpConnectTimeoutException)
            {
                throw new AbrFetchException(AbrFetchException.TIMEOUT,
                        "页面抓取超时：" + (properties.getConnectTimeoutMs() / 1000) + " 秒内未能连接到网站 " + displayHost(uri.getHost()));
            }
            if (cause instanceof HttpTimeoutException)
            {
                throw timeout();
            }
            if (cause instanceof SSLException)
            {
                throw new AbrFetchException(AbrFetchException.OPEN_FAILED, "页面打不开：HTTPS 加密连接失败（证书或协议错误）");
            }
            if (cause instanceof ConnectException)
            {
                throw new AbrFetchException(AbrFetchException.OPEN_FAILED, "页面打不开：无法连接到网站 " + displayHost(uri.getHost()));
            }
            log.warn("抓取页面失败 url={}：{}", uri, cause.toString());
            throw new AbrFetchException(AbrFetchException.OPEN_FAILED, "页面打不开：连接中断（" + cause.getClass().getSimpleName() + "）");
        }
    }

    /**
     * 在异常链中找第一个可识别的原因（超限、超时、TLS、连接失败），找不到返回最内层原因
     */
    private static Throwable knownCause(Throwable e)
    {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        for (Throwable t = cause; t != null; t = t.getCause() == t ? null : t.getCause())
        {
            if (t instanceof TooLargeException || t instanceof HttpTimeoutException || t instanceof SSLException || t instanceof ConnectException)
            {
                return t;
            }
            cause = t;
        }
        return cause;
    }

    private void checkStatus(int status)
    {
        if (status == 401 || status == 403 || status == 407)
        {
            throw new AbrFetchException(AbrFetchException.DENIED, "页面需要登录或被拒绝访问（HTTP " + status + "），请在浏览器中打开后手动录入");
        }
        if (status == 429)
        {
            throw new AbrFetchException(AbrFetchException.DENIED, "页面被拒绝访问：网站限制了访问频率（HTTP 429），请稍后再试或手动录入");
        }
        if (status == 404 || status == 410)
        {
            throw new AbrFetchException(AbrFetchException.OPEN_FAILED, "页面打不开：页面不存在（HTTP " + status + "），请检查链接");
        }
        if (status < 200 || status >= 300)
        {
            throw new AbrFetchException(AbrFetchException.OPEN_FAILED, "页面打不开：网站返回 HTTP " + status);
        }
    }

    private static void checkContentType(String mime)
    {
        if (mime.isEmpty() || "text/html".equals(mime) || "application/xhtml+xml".equals(mime) || "text/plain".equals(mime))
        {
            return;
        }
        if ("application/pdf".equals(mime) || mime.endsWith("/x-pdf"))
        {
            throw new AbrFetchException(AbrFetchException.NOT_HTML, "不是网页：链接指向 PDF 文件，暂不支持从 PDF 预填，请打开 PDF 手动录入");
        }
        throw new AbrFetchException(AbrFetchException.NOT_HTML, "不是网页：链接返回的是 " + mime + "，只支持 HTML 网页");
    }

    /**
     * 响应体处理：非 2xx、内容类型不支持、声明长度超限时不读正文；读取时超过上限立即中断
     */
    private HttpResponse.BodySubscriber<byte[]> bodyHandler(HttpResponse.ResponseInfo info)
    {
        int status = info.statusCode();
        if (status < 200 || status >= 300)
        {
            return new DiscardingSubscriber();
        }
        String mime = mime(info.headers().firstValue("content-type").orElse(""));
        try
        {
            checkContentType(mime);
        }
        catch (AbrFetchException e)
        {
            return new DiscardingSubscriber();
        }
        long declared = info.headers().firstValueAsLong("content-length").orElse(-1L);
        if (declared > properties.getMaxBytes())
        {
            return new LimitedSubscriber(-1);
        }
        return new LimitedSubscriber(properties.getMaxBytes());
    }

    private AbrWebPage toPage(String requestUrl, URI finalUri, String mime, String contentTypeHeader, byte[] body, int redirects, long start,
            int maxChars)
    {
        Charset charset = charset(contentTypeHeader, body);
        String raw = new String(body, charset);
        if (!raw.isEmpty() && raw.charAt(0) == '\uFEFF')
        {
            raw = raw.substring(1);
        }
        boolean html = !"text/plain".equals(mime);
        String text = html ? AbrHtmlText.toText(raw) : AbrHtmlText.normalize(raw);
        AbrWebPage page = new AbrWebPage();
        page.setRequestUrl(requestUrl.trim());
        page.setFinalUrl(finalUri.toString());
        page.setContentType(mime.isEmpty() ? "text/html" : mime);
        page.setCharset(charset.name());
        page.setTitle(html ? AbrHtmlText.title(raw) : "");
        int max = maxChars > 0 ? maxChars : properties.getMaxChars() > 0 ? properties.getMaxChars() : AbrFetchProperties.DEFAULT_MAX_CHARS;
        if (text.length() > max)
        {
            int cut = Character.isHighSurrogate(text.charAt(max - 1)) ? max - 1 : max;
            text = text.substring(0, cut);
            page.setTruncated(true);
        }
        page.setText(text);
        page.setHtml(html ? raw : null);
        page.setBytes(body.length);
        page.setRedirects(redirects);
        page.setFetchedAt(new Date());
        page.setElapsedMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return page;
    }

    /**
     * 字符集：响应头 → BOM → meta 标签 → UTF-8
     *
     * @param contentType Content-Type 响应头
     * @param body 响应体
     * @return 字符集
     */
    static Charset charset(String contentType, byte[] body)
    {
        String fromHeader = param(contentType, "charset");
        Charset c = forName(fromHeader);
        if (c != null)
        {
            return c;
        }
        if (body.length >= 3 && (body[0] & 0xff) == 0xEF && (body[1] & 0xff) == 0xBB && (body[2] & 0xff) == 0xBF)
        {
            return StandardCharsets.UTF_8;
        }
        if (body.length >= 2 && (body[0] & 0xff) == 0xFE && (body[1] & 0xff) == 0xFF)
        {
            return StandardCharsets.UTF_16BE;
        }
        if (body.length >= 2 && (body[0] & 0xff) == 0xFF && (body[1] & 0xff) == 0xFE)
        {
            return StandardCharsets.UTF_16LE;
        }
        String head = new String(body, 0, Math.min(body.length, 4096), StandardCharsets.ISO_8859_1);
        Matcher m = META_CHARSET.matcher(head);
        if (m.find())
        {
            c = forName(m.group(1));
            if (c != null)
            {
                return c;
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static Charset forName(String name)
    {
        if (name == null || name.isBlank())
        {
            return null;
        }
        String n = name.trim().replace("\"", "").replace("'", "");
        // 按 WHATWG 约定：gb2312 / gbk 按 GB18030 解码，iso-8859-1 按 windows-1252 解码
        String lower = n.toLowerCase(Locale.ROOT);
        if ("gb2312".equals(lower) || "gbk".equals(lower) || "x-gbk".equals(lower))
        {
            n = "GB18030";
        }
        else if ("iso-8859-1".equals(lower) || "latin1".equals(lower) || "us-ascii".equals(lower))
        {
            n = "windows-1252";
        }
        try
        {
            return Charset.isSupported(n) ? Charset.forName(n) : null;
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    private static String mime(String contentType)
    {
        int semi = contentType.indexOf(';');
        return (semi >= 0 ? contentType.substring(0, semi) : contentType).trim().toLowerCase(Locale.ROOT);
    }

    private static String param(String contentType, String name)
    {
        if (contentType == null)
        {
            return null;
        }
        for (String part : contentType.split(";"))
        {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).trim().equalsIgnoreCase(name))
            {
                return part.substring(eq + 1).trim();
            }
        }
        return null;
    }

    private static boolean startsWith(byte[] body, String prefix)
    {
        if (body.length < prefix.length())
        {
            return false;
        }
        for (int k = 0; k < prefix.length(); k++)
        {
            if (body[k] != prefix.charAt(k))
            {
                return false;
            }
        }
        return true;
    }

    private static String displayHost(String host)
    {
        return host == null ? "" : host;
    }

    private static long remainingMs(long deadline)
    {
        long ms = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (ms <= 0)
        {
            throw timeoutStatic();
        }
        return ms;
    }

    private AbrFetchException timeout()
    {
        return new AbrFetchException(AbrFetchException.TIMEOUT,
                "页面抓取超时：" + (properties.getTotalTimeoutMs() / 1000) + " 秒内没有取完页面，请稍后重试或手动录入");
    }

    private static AbrFetchException timeoutStatic()
    {
        return new AbrFetchException(AbrFetchException.TIMEOUT, "页面抓取超时，请稍后重试或手动录入");
    }

    private AbrFetchException tooLarge()
    {
        return new AbrFetchException(AbrFetchException.TOO_LARGE,
                "页面太大：超过 " + (properties.getMaxBytes() / 1024 / 1024) + " MB，请换成具体的项目页面链接或手动录入");
    }

    /**
     * 响应体超过上限
     */
    static class TooLargeException extends IOException
    {
        private static final long serialVersionUID = 1L;

        TooLargeException()
        {
            super("响应体超过上限");
        }
    }

    /**
     * 不读正文：订阅后立即取消，结果为 null
     */
    static class DiscardingSubscriber implements HttpResponse.BodySubscriber<byte[]>
    {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();

        @Override
        public CompletionStage<byte[]> getBody()
        {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription)
        {
            subscription.cancel();
            result.complete(null);
        }

        @Override
        public void onNext(List<ByteBuffer> item)
        {
        }

        @Override
        public void onError(Throwable throwable)
        {
            result.complete(null);
        }

        @Override
        public void onComplete()
        {
            result.complete(null);
        }
    }

    /**
     * 限长读取：超过上限立即取消订阅并以 TooLargeException 结束（max &lt; 0 表示声明长度已超限，直接结束）
     */
    static class LimitedSubscriber implements HttpResponse.BodySubscriber<byte[]>
    {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private final int max;

        private Flow.Subscription subscription;

        LimitedSubscriber(int max)
        {
            this.max = max;
        }

        @Override
        public CompletionStage<byte[]> getBody()
        {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription s)
        {
            this.subscription = s;
            if (max < 0)
            {
                s.cancel();
                result.completeExceptionally(new TooLargeException());
                return;
            }
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items)
        {
            if (result.isDone())
            {
                return;
            }
            for (ByteBuffer item : items)
            {
                int len = item.remaining();
                if (buffer.size() + len > max)
                {
                    subscription.cancel();
                    result.completeExceptionally(new TooLargeException());
                    return;
                }
                byte[] chunk = new byte[len];
                item.get(chunk);
                buffer.write(chunk, 0, len);
            }
        }

        @Override
        public void onError(Throwable throwable)
        {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete()
        {
            result.complete(buffer.toByteArray());
        }
    }
}
