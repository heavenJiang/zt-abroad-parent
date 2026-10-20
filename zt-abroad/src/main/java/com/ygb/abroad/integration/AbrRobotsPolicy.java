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

    record Cached(List<Rule> rules, boolean allowAll, boolean denyAll, long at, List<String> sitemaps, int crawlDelay)
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
        Cached c = cached(url);
        if (c == null || c.denyAll())
        {
            return false;
        }
        if (c.allowAll())
        {
            return true;
        }
        URI uri = URI.create(url);
        String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        return decide(c.rules(), path);
    }

    /**
     * 站点 robots.txt 中声明的站点地图
     *
     * @param url 站点内任一网址
     * @return 站点地图网址（没有为空）
     */
    public List<String> sitemaps(String url)
    {
        Cached c = cached(url);
        return c == null ? List.of() : c.sitemaps();
    }

    /**
     * 站点要求的抓取间隔（秒；没有声明为 0）
     */
    public int crawlDelay(String url)
    {
        Cached c = cached(url);
        return c == null ? 0 : c.crawlDelay();
    }

    private Cached cached(String url)
    {
        URI uri;
        try
        {
            uri = URI.create(url);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())))
        {
            return null;
        }
        String site = uri.getScheme() + "://" + uri.getRawAuthority();
        Cached c = cache.get(site);
        if (c == null || System.currentTimeMillis() - c.at() > TTL_MS)
        {
            c = load(site, uri.getHost());
            cache.put(site, c);
        }
        return c;
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
                    return new Cached(List.of(), false, true, now, List.of(), 0);
                }
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(site + "/robots.txt")).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (compatible; " + AGENT + "/1.0)").GET().build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            int code = resp.statusCode();
            if (code == 404 || code == 410)
            {
                return new Cached(List.of(), true, false, now, List.of(), 0);
            }
            if (code != 200)
            {
                return new Cached(List.of(), false, true, now, List.of(), 0);
            }
            byte[] body = resp.body();
            String text = new String(body, 0, Math.min(body.length, MAX_BYTES), StandardCharsets.UTF_8);
            return new Cached(parse(text, AGENT), false, false, now, sitemapLines(text), crawlDelay(text, AGENT));
        }
        catch (Exception e)
        {
            if (e instanceof InterruptedException)
            {
                Thread.currentThread().interrupt();
            }
            return new Cached(List.of(), false, true, now, List.of(), 0);
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

    /**
     * robots.txt 中的 Sitemap 行（与规则组无关）
     */
    static List<String> sitemapLines(String text)
    {
        List<String> out = new ArrayList<>();
        for (String raw : text.split("\\r?\\n"))
        {
            String line = raw.trim();
            if (line.toLowerCase(Locale.ROOT).startsWith("sitemap:"))
            {
                String u = line.substring("sitemap:".length()).trim();
                if (u.startsWith("http") && out.size() < 50)
                {
                    out.add(u);
                }
            }
        }
        return out;
    }

    /**
     * 适用于本系统的 Crawl-delay（专门的组优先，否则 *；没有为 0，最多 60 秒）
     */
    static int crawlDelay(String text, String agent)
    {
        String me = agent.toLowerCase(Locale.ROOT);
        int mine = -1;
        int star = -1;
        List<String> agents = new ArrayList<>();
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
                    agents = new ArrayList<>();
                    inRules = false;
                }
                agents.add(value.toLowerCase(Locale.ROOT));
            }
            else if ("allow".equals(key) || "disallow".equals(key))
            {
                inRules = true;
            }
            else if ("crawl-delay".equals(key))
            {
                inRules = true;
                int d;
                try
                {
                    d = (int) Math.ceil(Double.parseDouble(value));
                }
                catch (NumberFormatException e)
                {
                    continue;
                }
                for (String a : agents)
                {
                    if (!a.equals("*") && me.contains(a))
                    {
                        mine = d;
                    }
                    else if (a.equals("*"))
                    {
                        star = d;
                    }
                }
            }
        }
        int d = mine >= 0 ? mine : Math.max(star, 0);
        return Math.min(d, 60);
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
