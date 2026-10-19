package com.ygb.abroad.integration;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import jakarta.annotation.PostConstruct;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 微信服务号网页授权（H5 登录）：拼授权地址、用 code 换 openid / unionid
 * <ul>
 * <li>AppID 与 AppSecret 只从配置读取（abroad.wechat.oa.*）；未配置时提示“服务号未开通”（M0-01）；</li>
 * <li>授权范围 snsapi_base（静默授权，只取 openid；服务号绑定开放平台时可能同时返回 unionid）；</li>
 * <li>mock=true 时不访问微信：code 为 mock_ 开头的直接作为 openid，只用于本地开发，启动时打印警告。</li>
 * </ul>
 *
 * @author ygb
 */
@Component
public class AbrWechatOaAuthClient
{
    private static final Logger log = LoggerFactory.getLogger(AbrWechatOaAuthClient.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** mock 模式下可用的 code */
    static final Pattern MOCK_CODE = Pattern.compile("^mock_[A-Za-z0-9_-]{1,40}$");

    /** 微信 code 的格式（字母、数字、下划线、短横线） */
    static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]{8,128}$");

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    /**
     * 微信返回的身份
     *
     * @param openid 服务号 openid
     * @param unionid unionid（可为 null）
     */
    public record Identity(String openid, String unionid)
    {
    }

    @Value("${abroad.wechat.oa.app-id:}")
    private String appId;

    @Value("${abroad.wechat.oa.app-secret:}")
    private String appSecret;

    @Value("${abroad.wechat.oa.mock:false}")
    private boolean mock;

    @Value("${abroad.wechat.oa.authorize-endpoint:https://open.weixin.qq.com/connect/oauth2/authorize}")
    private String authorizeEndpoint;

    @Value("${abroad.wechat.oa.token-endpoint:https://api.weixin.qq.com/sns/oauth2/access_token}")
    private String tokenEndpoint;

    @PostConstruct
    void warnMock()
    {
        if (mock)
        {
            log.warn("服务号网页授权为 mock 模式（abroad.wechat.oa.mock=true）：mock_ 开头的 code 可直接登录，只能用于本地开发");
        }
    }

    /**
     * 是否可用（已配置或 mock）
     *
     * @return 是否可用
     */
    public boolean enabled()
    {
        return mock || (StringUtils.isNotBlank(appId) && StringUtils.isNotBlank(appSecret));
    }

    /**
     * 是否为 mock 模式
     *
     * @return 是否 mock
     */
    public boolean isMock()
    {
        return mock;
    }

    /**
     * 网页授权地址（snsapi_base）
     *
     * @param redirectUri 授权后回到的页面（调用方已校验在 C 端域名内）
     * @param state 防伪随机串
     * @return 地址
     */
    public String authorizeUrl(String redirectUri, String state)
    {
        requireEnabled();
        if (mock)
        {
            // mock：直接回到页面并带上 mock code
            String sep = redirectUri.contains("?") ? "&" : "?";
            return redirectUri + sep + "code=mock_" + state.substring(0, 8) + "&state=" + state;
        }
        return authorizeEndpoint + "?appid=" + URLEncoder.encode(appId.trim(), StandardCharsets.UTF_8) + "&redirect_uri="
                + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) + "&response_type=code&scope=snsapi_base&state="
                + URLEncoder.encode(state, StandardCharsets.UTF_8) + "#wechat_redirect";
    }

    /**
     * 用授权 code 换身份
     *
     * @param code 授权 code
     * @return 身份
     */
    public Identity exchange(String code)
    {
        requireEnabled();
        String c = StringUtils.trimToEmpty(code);
        if (mock)
        {
            if (!MOCK_CODE.matcher(c).matches())
            {
                throw new ServiceException("mock 模式下 code 须以 mock_ 开头");
            }
            return new Identity(c, null);
        }
        if (!CODE.matcher(c).matches())
        {
            throw new ServiceException("授权 code 格式不对，请重新登录");
        }
        String uri = tokenEndpoint + "?appid=" + URLEncoder.encode(appId.trim(), StandardCharsets.UTF_8) + "&secret="
                + URLEncoder.encode(appSecret.trim(), StandardCharsets.UTF_8) + "&code=" + URLEncoder.encode(c, StandardCharsets.UTF_8)
                + "&grant_type=authorization_code";
        try
        {
            HttpResponse<String> resp = client.send(HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(8)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode n = MAPPER.readTree(StringUtils.defaultIfBlank(resp.body(), "{}"));
            String openid = n.path("openid").asString("");
            if (resp.statusCode() != 200 || n.path("errcode").asInt(0) != 0 || openid.isEmpty())
            {
                log.warn("服务号网页授权失败：HTTP {} errcode={} errmsg={}", resp.statusCode(), n.path("errcode").asInt(0), n.path("errmsg").asString(""));
                throw new ServiceException("微信登录失败，请重新进入页面");
            }
            String unionid = StringUtils.trimToNull(n.path("unionid").asString(""));
            return new Identity(openid, unionid);
        }
        catch (IOException e)
        {
            log.warn("服务号网页授权请求失败：{}", e.toString());
            throw new ServiceException("微信登录暂时不可用，请稍后再试");
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new ServiceException("微信登录被中断，请重试");
        }
    }

    private void requireEnabled()
    {
        if (!enabled())
        {
            throw new ServiceException("服务号登录尚未开通（待 M0-01 服务号认证后配置 abroad.wechat.oa.app-id / app-secret）");
        }
    }
}
