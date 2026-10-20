package com.ygb.abroad.integration.channel;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ygb.common.utils.StringUtils;

/**
 * 服务号模板消息（接收方为服务号 openid；跳转为网页地址）。AppID / AppSecret 与服务号网页授权共用 abroad.wechat.oa.*
 *
 * @author ygb
 */
@Component
public class WechatOaTemplateAdapter extends WechatTemplateSender
{
    @Value("${abroad.wechat.oa.app-id:}")
    private String appId;

    @Value("${abroad.wechat.oa.app-secret:}")
    private String appSecret;

    @Value("${abroad.wechat.api-base:https://api.weixin.qq.com}")
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
        return SERVICE_ACCOUNT;
    }

    @Override
    String path()
    {
        return "/cgi-bin/message/template/send";
    }

    @Override
    String tokenName()
    {
        return "oa";
    }

    @Override
    String apiBase()
    {
        return apiBase;
    }

    @Override
    String appId()
    {
        return appId;
    }

    @Override
    String appSecret()
    {
        return appSecret;
    }

    @Override
    boolean mock()
    {
        return mock;
    }

    @Override
    WechatTokenStore tokens()
    {
        return tokens;
    }

    @Override
    ChannelHttp http()
    {
        return http;
    }

    @Override
    void decorate(Map<String, Object> body, OutboundMessage message)
    {
        if (StringUtils.startsWithAny(message.link(), "https://", "http://"))
        {
            body.put("url", message.link());
        }
    }
}
