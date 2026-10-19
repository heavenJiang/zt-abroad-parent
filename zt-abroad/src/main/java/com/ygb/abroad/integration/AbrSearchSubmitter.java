package com.ygb.abroad.integration;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 公开项目页收录提交：百度普通收录（API 推送）、IndexNow（必应等）
 * <ul>
 * <li>令牌与密钥只从配置读取（abroad.site.submit.*）；未配置时不提交，结果记为 off；</li>
 * <li>只提交本次新增或内容变化的地址（IndexNow 另含已删除的地址）；单次最多 max-urls 条；</li>
 * <li>提交失败不影响页面生成，结果写入生成记录。</li>
 * </ul>
 *
 * @author ygb
 */
@Component
public class AbrSearchSubmitter
{
    private static final Logger log = LoggerFactory.getLogger(AbrSearchSubmitter.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** IndexNow 密钥格式：8–128 位字母、数字、短横线 */
    static final Pattern INDEXNOW_KEY = Pattern.compile("^[A-Za-z0-9-]{8,128}$");

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();

    /** 百度普通收录 API 推送的令牌（搜索资源平台 → 普通收录 → API 提交） */
    @Value("${abroad.site.submit.baidu-token:}")
    private String baiduToken;

    /** 百度推送接口 */
    @Value("${abroad.site.submit.baidu-endpoint:http://data.zz.baidu.com/urls}")
    private String baiduEndpoint;

    /** IndexNow 密钥（同时作为站点根目录下的 {key}.txt 校验文件） */
    @Value("${abroad.site.submit.indexnow-key:}")
    private String indexNowKey;

    /** IndexNow 接口 */
    @Value("${abroad.site.submit.indexnow-endpoint:https://api.indexnow.org/indexnow}")
    private String indexNowEndpoint;

    /** 单次最多提交的地址数 */
    @Value("${abroad.site.submit.max-urls:500}")
    private int maxUrls;

    /**
     * 可用的 IndexNow 密钥
     *
     * @return 密钥；未配置或格式不对为 null
     */
    public String indexNowKey()
    {
        String key = StringUtils.trimToNull(indexNowKey);
        return key != null && INDEXNOW_KEY.matcher(key).matches() ? key : null;
    }

    /**
     * 是否配置了百度推送
     *
     * @return 是否配置
     */
    public boolean baiduEnabled()
    {
        return StringUtils.isNotBlank(baiduToken);
    }

    /**
     * 提交收录
     *
     * @param siteUrl 站点地址（以 / 结尾）
     * @param changed 新增或内容变化的地址（绝对地址）
     * @param removed 已删除的地址（绝对地址）
     * @return 结果：{"baidu":{...},"indexnow":{...}, "count": 提交的地址数}
     */
    public Map<String, Object> submit(String siteUrl, List<String> changed, List<String> removed)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> push = changed.size() > maxUrls ? changed.subList(0, maxUrls) : changed;
        int count = 0;
        if (!baiduEnabled())
        {
            result.put("baidu", "off");
        }
        else if (push.isEmpty())
        {
            result.put("baidu", "nothing");
        }
        else
        {
            result.put("baidu", baidu(siteUrl, push));
            count = Math.max(count, push.size());
        }
        String key = indexNowKey();
        List<String> all = new ArrayList<>(push);
        removed.stream().limit(Math.max(0, maxUrls - push.size())).forEach(all::add);
        if (key == null)
        {
            result.put("indexnow", StringUtils.isBlank(indexNowKey) ? "off" : "密钥格式不对（8–128 位字母、数字、短横线）");
        }
        else if (all.isEmpty())
        {
            result.put("indexnow", "nothing");
        }
        else
        {
            result.put("indexnow", indexNow(siteUrl, key, all));
            count = Math.max(count, all.size());
        }
        if (changed.size() > maxUrls)
        {
            result.put("truncated", changed.size() - maxUrls);
        }
        result.put("count", count);
        return result;
    }

    private Map<String, Object> baidu(String siteUrl, List<String> urls)
    {
        String site = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
        String uri = baiduEndpoint + "?site=" + URLEncoder.encode(site, StandardCharsets.UTF_8) + "&token=" + URLEncoder.encode(baiduToken.trim(), StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(15)).header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(String.join("\n", urls), StandardCharsets.UTF_8)).build();
        return send("baidu", req);
    }

    private Map<String, Object> indexNow(String siteUrl, String key, List<String> urls)
    {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("host", URI.create(siteUrl).getHost());
        body.put("key", key);
        body.put("keyLocation", siteUrl + key + ".txt");
        ArrayNode list = body.putArray("urlList");
        urls.forEach(list::add);
        HttpRequest req = HttpRequest.newBuilder(URI.create(indexNowEndpoint)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8").POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build();
        return send("indexnow", req);
    }

    private Map<String, Object> send(String name, HttpRequest req)
    {
        Map<String, Object> r = new LinkedHashMap<>();
        try
        {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            r.put("status", resp.statusCode());
            r.put("body", StringUtils.substring(StringUtils.defaultString(resp.body()), 0, 500));
            if (resp.statusCode() >= 300)
            {
                log.warn("{} 收录提交失败：HTTP {} {}", name, resp.statusCode(), r.get("body"));
            }
        }
        catch (IOException e)
        {
            r.put("error", StringUtils.substring(e.getClass().getSimpleName() + "：" + StringUtils.defaultString(e.getMessage()), 0, 500));
            log.warn("{} 收录提交失败：{}", name, e.toString());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            r.put("error", "interrupted");
        }
        return r;
    }
}
