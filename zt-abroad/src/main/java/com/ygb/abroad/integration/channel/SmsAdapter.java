package com.ygb.abroad.integration.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 短信（通知类）。服务商随 M0-01 选定（签名、模板报备）后在这里接入；未接入时结果为 off，mock 模式只写日志。
 * 手机号只在发送这一刻使用，不写入发送记录与日志（日志只留后四位）。
 *
 * @author ygb
 */
@Component
public class SmsAdapter implements ChannelAdapter
{
    private static final Logger log = LoggerFactory.getLogger(SmsAdapter.class);

    @Value("${abroad.channel.mock:false}")
    private boolean mock;

    @Override
    public String channel()
    {
        return SMS;
    }

    @Override
    public boolean configured()
    {
        return mock;
    }

    @Override
    public ChannelSendResult send(OutboundMessage message)
    {
        if (mock)
        {
            log.info("（mock）短信 → {} 模板 {} {}", ChannelMask.recipient(message.recipient()), message.platformTemplateId(), message.fields());
            return ChannelSendResult.mock();
        }
        return ChannelSendResult.off("短信服务商尚未接入（待 M0-01）");
    }
}
