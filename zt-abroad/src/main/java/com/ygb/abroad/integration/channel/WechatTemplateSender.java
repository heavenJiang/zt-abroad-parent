package com.ygb.abroad.integration.channel;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;

/**
 * 服务号模板消息与小程序订阅消息的共同部分：取令牌、组 data、处理错误码（令牌失效清缓存重试一次）
 *
 * @author ygb
 */
abstract class WechatTemplateSender implements ChannelAdapter
{
    private static final Logger log = LoggerFactory.getLogger(WechatTemplateSender.class);

    /**
     * 平台接口路径（如 /cgi-bin/message/template/send）
     *
     * @return 路径
     */
    abstract String path();

    /**
     * 令牌缓存名
     *
     * @return 名称
     */
    abstract String tokenName();

    abstract String apiBase();

    abstract String appId();

    abstract String appSecret();

    abstract boolean mock();

    abstract WechatTokenStore tokens();

    abstract ChannelHttp http();

    /**
     * 补充请求体（跳转地址等）
     *
     * @param body 请求体
     * @param message 消息
     */
    abstract void decorate(Map<String, Object> body, OutboundMessage message);

    @Override
    public boolean configured()
    {
        return mock() || (StringUtils.isNotBlank(appId()) && StringUtils.isNotBlank(appSecret()));
    }

    @Override
    public ChannelSendResult send(OutboundMessage message)
    {
        if (mock())
        {
            log.info("（mock）{} → {} 模板 {} {}", channel(), ChannelMask.recipient(message.recipient()), message.platformTemplateId(), message.fields());
            return ChannelSendResult.mock();
        }
        if (!configured())
        {
            return ChannelSendResult.off("未配置 AppID / AppSecret");
        }
        if (StringUtils.isBlank(message.recipient()) || StringUtils.isBlank(message.platformTemplateId()))
        {
            return ChannelSendResult.failed("缺少接收方或平台模板 ID");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", message.recipient());
        body.put("template_id", message.platformTemplateId());
        Map<String, Object> data = new LinkedHashMap<>();
        message.fields().forEach((k, v) -> data.put(k, Map.of("value", v)));
        body.put("data", data);
        decorate(body, message);
        try
        {
            for (int attempt = 0; attempt < 2; attempt++)
            {
                String token = tokens().wechat(apiBase(), tokenName(), appId(), appSecret());
                JsonNode r = http().post(apiBase() + path() + "?access_token=" + token, body);
                int code = r.path("errcode").asInt(0);
                if (code == 0)
                {
                    return ChannelSendResult.sent(r.path("msgid").asString(""));
                }
                if (attempt == 0 && WechatTokenStore.tokenInvalid(code))
                {
                    tokens().evict(tokenName());
                    continue;
                }
                return ChannelSendResult.failed(code + " " + r.path("errmsg").asString(""));
            }
            return ChannelSendResult.failed("令牌失效");
        }
        catch (IOException e)
        {
            log.warn("{} 发送失败：{}", channel(), e.getMessage());
            return ChannelSendResult.failed(e.getMessage());
        }
    }
}
