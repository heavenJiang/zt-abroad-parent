package com.ygb.abroad.integration;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * robots.txt 规则（自动抓取前检查；M2-09 / M2-12 / M7-07 共用）
 * <ul>
 * <li>按本系统的爬虫名 {@link #AGENT} 匹配规则组，没有专门的组时用 *；Allow / Disallow 取最长匹配，支持 * 与 $；</li>
 * <li>robots.txt 不存在（404 / 410）视为允许；401 / 403、服务器错误或打不开视为不允许（保守）；</li>
 * <li>结果按站点缓存 24 小时。</li>
 * </ul>
 *
 * @author ygb
 */
@Component
public class AbrRobotsPolicy
{
    /** 本系统在 robots.txt 中的爬虫名（与抓取时 User-Agent 中的名称一致） */
    public static final String AGENT = "AbroadCatalogBot";

    private static final long TTL_MS = 24L * 3600 * 1000;

    private static final int MAX_BYTES = 500_000;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private final AbrFetchProperties properties;

    /**
     * 创建 robots 规则检查（内网地址限制与页面抓取共用 abroad.ai.fetch.allow-private-hosts）
     *
     * @param properties 抓取配置
     */
    public AbrRobotsPolicy(AbrFetchProperties properties)
    {
        this.properties = properties;
    }

    record Rule(boolean allow, String path)
    {
    }

    record Cached(List<Rule> rules, boolean allowAll, boolean denyAll, long at)
    {
    }

    /**
     * 是否允许抓取
     *
     * @param url 网址
     * @return 允许为 true
     */
    public boolean allowed(String url)
    {
        URI uri;
        try
        {
            uri = URI.create(url);
        }
        catch (IllegalArgumentException e)
        {
            return false;
        }
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())))
        {
            return false;
        }
        String site = uri.getScheme() + "://" + uri.getRawAuthority();
        Cached c = cache.get(site);
        if (c == null || System.currentTimeMillis() - c.at() > TTL_MS)
        {
            c = load(site, uri.getHost());
            cache.put(site, c);
        }
        if (c.denyAll())
        {
            return false;
        }
        if (c.allowAll())
        {
            return true;
        }
        String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        return decide(c.rules(), path);
    }

    private Cached load(String site, String host)
    {
        long now = System.currentTimeMillis();
        try
        {
            for (InetAddress a : InetAddress.getAllByName(host))
            {
                if (!properties.isAllowPrivateHosts() && AbrWebPageFetcher.isBlockedAddress(a))
                {
                    return new Cached(List.of(), false, true, now);
                }
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(site + "/robots.txt")).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (compatible; " + AGENT + "/1.0)").GET().build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            int code = resp.statusCode();
            if (code == 404 || code == 410)
            {
                return new Cached(List.of(), true, false, now);
            }
            if (code != 200)
            {
                return new Cached(List.of(), false, true, now);
            }
            byte[] body = resp.body();
            String text = new String(body, 0, Math.min(body.length, MAX_BYTES), StandardCharsets.UTF_8);
            return new Cached(parse(text, AGENT), false, false, now);
        }
        catch (Exception e)
        {
            if (e instanceof InterruptedException)
            {
                Thread.currentThread().interrupt();
            }
            return new Cached(List.of(), false, true, now);
        }
    }

    /**
     * 解析 robots.txt，取适用于 agent 的规则组（有专门的组用专门的组，否则用 *）
     */
    static List<Rule> parse(String text, String agent)
    {
        String me = agent.toLowerCase(Locale.ROOT);
        List<Rule> mine = new ArrayList<>();
        List<Rule> star = new ArrayList<>();
        boolean mineFound = false;
        List<String> groupAgents = new ArrayList<>();
        List<Rule> group = new ArrayList<>();
        boolean inRules = false;
        for (String raw : (text + "\n").split("\\r?\\n"))
        {
            String line = raw.replaceAll("#.*$", "").trim();
            int colon = line.indexOf(':');
            if (colon <= 0)
            {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if ("user-agent".equals(key))
            {
                if (inRules)
                {
                    mineFound |= flush(groupAgents, group, me, mine, star);
                    groupAgents = new ArrayList<>();
                    group = new ArrayList<>();
                    inRules = false;
                }
                groupAgents.add(value.toLowerCase(Locale.ROOT));
            }
            else if ("allow".equals(key) || "disallow".equals(key))
            {
                inRules = true;
                if (!value.isEmpty())
                {
                    group.add(new Rule("allow".equals(key), value));
                }
            }
        }
        mineFound |= flush(groupAgents, group, me, mine, star);
        return mineFound ? mine : star;
    }

    private static boolean flush(List<String> agents, List<Rule> group, String me, List<Rule> mine, List<Rule> star)
    {
        boolean hit = false;
        for (String a : agents)
        {
            if (!a.equals("*") && me.contains(a))
            {
                mine.addAll(group);
                hit = true;
            }
            else if (a.equals("*"))
            {
                star.addAll(group);
            }
        }
        return hit;
    }

    /**
     * 最长匹配：匹配长度相同时 Allow 优先；没有匹配的规则为允许
     */
    static boolean decide(List<Rule> rules, String path)
    {
        int best = -1;
        boolean allow = true;
        for (Rule r : rules)
        {
            if (matches(r.path(), path))
            {
                int len = r.path().length();
                if (len > best || (len == best && r.allow()))
                {
                    best = len;
                    allow = r.allow();
                }
            }
        }
        return allow;
    }

    static boolean matches(String pattern, String path)
    {
        boolean anchored = pattern.endsWith("$");
        String p = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
        StringBuilder re = new StringBuilder("^");
        for (char ch : p.toCharArray())
        {
            re.append(ch == '*' ? ".*" : Pattern.quote(String.valueOf(ch)));
        }
        if (anchored)
        {
            re.append("$");
        }
        return Pattern.compile(re.toString()).matcher(path).find();
    }
}
