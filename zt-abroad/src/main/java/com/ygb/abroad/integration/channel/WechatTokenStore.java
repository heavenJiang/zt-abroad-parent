package com.ygb.abroad.integration.channel;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.common.core.redis.RedisCache;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;

/**
 * 微信 / 企业微信接口调用凭据（access_token）：缓存在 Redis（提前 5 分钟过期），令牌失效（40001 / 42001 / 40014）时清掉重取
 *
 * @author ygb
 */
@Component
public class WechatTokenStore
{
    /** Redis 前缀 */
    static final String KEY = "abr_channel_token:";

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ChannelHttp http;

    /**
     * 取服务号 / 小程序的令牌（cgi-bin/token）
     *
     * @param apiBase 接口地址
     * @param name 缓存名（如 oa、mini）
     * @param appId AppID
     * @param secret AppSecret
     * @return 令牌
     * @throws IOException 取令牌失败
     */
    public String wechat(String apiBase, String name, String appId, String secret) throws IOException
    {
        return token(name, apiBase + "/cgi-bin/token?grant_type=client_credential&appid=" + enc(appId) + "&secret=" + enc(secret));
    }

    /**
     * 取企业微信应用的令牌（cgi-bin/gettoken）
     *
     * @param apiBase 接口地址
     * @param corpId 企业 ID
     * @param secret 应用 Secret
     * @return 令牌
     * @throws IOException 取令牌失败
     */
    public String wecom(String apiBase, String corpId, String secret) throws IOException
    {
        return token("wecom", apiBase + "/cgi-bin/gettoken?corpid=" + enc(corpId) + "&corpsecret=" + enc(secret));
    }

    /**
     * 清掉缓存的令牌（平台返回令牌失效时）
     *
     * @param name 缓存名
     */
    public void evict(String name)
    {
        redisCache.deleteObject(KEY + name);
    }

    /**
     * 是否为令牌失效的错误码
     *
     * @param errcode 错误码
     * @return 是否
     */
    public static boolean tokenInvalid(int errcode)
    {
        return errcode == 40001 || errcode == 42001 || errcode == 40014;
    }

    private String token(String name, String url) throws IOException
    {
        String cached = redisCache.getCacheObject(KEY + name);
        if (StringUtils.isNotBlank(cached))
        {
            return cached;
        }
        JsonNode r = http.get(url);
        String token = r.path("access_token").asString("");
        if (token.isEmpty())
        {
            throw new IOException("取 access_token 失败：" + r.path("errcode").asInt(-1) + " " + r.path("errmsg").asString(""));
        }
        int ttl = Math.max(60, r.path("expires_in").asInt(7200) - 300);
        redisCache.setCacheObject(KEY + name, token, ttl, TimeUnit.SECONDS);
        return token;
    }

    private static String enc(String s)
    {
        return URLEncoder.encode(StringUtils.trimToEmpty(s), StandardCharsets.UTF_8);
    }
}
