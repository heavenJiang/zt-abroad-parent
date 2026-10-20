package com.ygb.abroad.integration.channel;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;

/**
 * 企业微信应用消息（发给员工；接收方为企业微信成员账号）。企业 ID、应用 AgentId、应用 Secret 只从配置读取（abroad.wecom.*）
 *
 * @author ygb
 */
@Component
public class WecomAppAdapter implements ChannelAdapter
{
    private static final Logger log = LoggerFactory.getLogger(WecomAppAdapter.class);

    @Value("${abroad.wecom.corp-id:}")
    private String corpId;

    @Value("${abroad.wecom.agent-id:}")
    private String agentId;

    @Value("${abroad.wecom.secret:}")
    private String secret;

    @Value("${abroad.wecom.api-base:https://qyapi.weixin.qq.com}")
    private String apiBase;

    @Value("${abroad.channel.mock:false}")
    private boolean mock;

    @Autowired
    private WechatTokenStore tokens;

    @Autowired
    private ChannelHttp http;

    @Override
    public String channel()
    {
        return WECOM;
    }

    @Override
    public boolean configured()
    {
        return mock || (StringUtils.isNotBlank(corpId) && StringUtils.isNotBlank(agentId) && StringUtils.isNotBlank(secret));
    }

    @Override
    public ChannelSendResult send(OutboundMessage message)
    {
        if (mock)
        {
            log.info("（mock）企业微信 → {}：{}", ChannelMask.recipient(message.recipient()), StringUtils.substring(message.text(), 0, 100));
            return ChannelSendResult.mock();
        }
        if (!configured())
        {
            return ChannelSendResult.off("未配置企业 ID / 应用 AgentId / 应用 Secret");
        }
        if (StringUtils.isBlank(message.recipient()) || StringUtils.isBlank(message.text()))
        {
            return ChannelSendResult.failed("缺少接收方或消息文字");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", message.recipient());
        body.put("msgtype", "text");
        body.put("agentid", agentId.trim());
        body.put("text", Map.of("content", message.text()));
        try
        {
            for (int attempt = 0; attempt < 2; attempt++)
            {
                String token = tokens.wecom(apiBase, corpId, secret);
                JsonNode r = http.post(apiBase + "/cgi-bin/message/send?access_token=" + token, body);
                int code = r.path("errcode").asInt(0);
                if (code == 0)
                {
                    String invalid = r.path("invaliduser").asString("");
                    return invalid.isEmpty() ? ChannelSendResult.sent(r.path("msgid").asString("")) : ChannelSendResult.failed("成员账号无效：" + invalid);
                }
                if (attempt == 0 && WechatTokenStore.tokenInvalid(code))
                {
                    tokens.evict("wecom");
                    continue;
                }
                return ChannelSendResult.failed(code + " " + r.path("errmsg").asString(""));
            }
            return ChannelSendResult.failed("令牌失效");
        }
        catch (IOException e)
        {
            log.warn("企业微信发送失败：{}", e.getMessage());
            return ChannelSendResult.failed(e.getMessage());
        }
    }
}
