package com.ygb.abroad.integration.channel;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ygb.common.utils.StringUtils;

/**
 * 小程序订阅消息（接收方为小程序 openid；学生须在小程序内订阅过该模板，一次订阅一次可发）。跳转为小程序页面路径
 *
 * @author ygb
 */
@Component
public class MiniSubscribeAdapter extends WechatTemplateSender
{
    @Value("${abroad.wechat.mini.app-id:}")
    private String appId;

    @Value("${abroad.wechat.mini.app-secret:}")
    private String appSecret;

    /** 跳转的小程序版本：formal 正式版 / trial 体验版 / developer 开发版 */
    @Value("${abroad.wechat.mini.state:formal}")
    private String state;

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
        return MP_SUBSCRIBE;
    }

    @Override
    String path()
    {
        return "/cgi-bin/message/subscribe/send";
    }

    @Override
    String tokenName()
    {
        return "mini";
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
        if (StringUtils.isNotBlank(message.link()) && !StringUtils.startsWithAny(message.link(), "https://", "http://"))
        {
            body.put("page", message.link());
        }
        body.put("miniprogram_state", state);
        body.put("lang", "zh_CN");
    }
}
