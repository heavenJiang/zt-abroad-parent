package com.ygb.abroad.domain.vo;

/**
 * C 端：创建访客的请求体（入口链接参数）
 * 
 * @author ygb
 */
public class AppVisitorBody
{
    /** 渠道码（链接参数 ch 或小程序码 scene） */
    private String channelCode;

    /** 内容编号 */
    private String contentCode;

    /** 端（h5 / mp_weixin） */
    private String clientPlatform;

    public String getChannelCode()
    {
        return channelCode;
    }

    public void setChannelCode(String channelCode)
    {
        this.channelCode = channelCode;
    }

    public String getContentCode()
    {
        return contentCode;
    }

    public void setContentCode(String contentCode)
    {
        this.contentCode = contentCode;
    }

    public String getClientPlatform()
    {
        return clientPlatform;
    }

    public void setClientPlatform(String clientPlatform)
    {
        this.clientPlatform = clientPlatform;
    }
}
