package com.ygb.abroad.integration.channel;

/**
 * 渠道发送结果
 *
 * @param status sent 已发送 / mock 模拟发送 / failed 失败 / off 渠道未开通
 * @param platformMsgId 平台消息ID（可为空）
 * @param error 失败原因（平台错误码与说明）
 * @author ygb
 */
public record ChannelSendResult(String status, String platformMsgId, String error)
{
    public static final String SENT = "sent";

    public static final String MOCK = "mock";

    public static final String FAILED = "failed";

    public static final String OFF = "off";

    public static ChannelSendResult sent(String msgId)
    {
        return new ChannelSendResult(SENT, msgId == null ? "" : msgId, "");
    }

    public static ChannelSendResult mock()
    {
        return new ChannelSendResult(MOCK, "", "");
    }

    public static ChannelSendResult failed(String error)
    {
        return new ChannelSendResult(FAILED, "", error == null ? "" : error);
    }

    public static ChannelSendResult off(String why)
    {
        return new ChannelSendResult(OFF, "", why == null ? "" : why);
    }
}
