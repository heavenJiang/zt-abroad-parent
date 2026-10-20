package com.ygb.abroad.integration.channel;

/**
 * 渠道适配器：一个渠道一个实现，统一发送接口；凭据只从配置（环境变量）读取，未配置时 {@link #configured()} 为 false、发送结果为 off
 *
 * @author ygb
 */
public interface ChannelAdapter
{
    /** 服务号模板消息 */
    String SERVICE_ACCOUNT = "service_account";

    /** 小程序订阅消息 */
    String MP_SUBSCRIBE = "mp_subscribe";

    /** 企业微信应用消息（发给员工） */
    String WECOM = "wecom";

    /** 短信 */
    String SMS = "sms";

    /**
     * 渠道编码
     *
     * @return 编码
     */
    String channel();

    /**
     * 是否已配置凭据（mock 模式视为已配置）
     *
     * @return 是否
     */
    boolean configured();

    /**
     * 发送一条消息（不抛异常，失败写在结果里）
     *
     * @param message 消息
     * @return 结果
     */
    ChannelSendResult send(OutboundMessage message);
}
