package com.ygb.abroad.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ygb.common.exception.ServiceException;
import jakarta.annotation.PostConstruct;

/**
 * 短信验证码发送（绑定手机号用）
 * <p>
 * 短信服务（签名、模板报备）随 M0-01 开通后在这里接入服务商；未接入时提示“短信服务未开通”。
 * mock=true 时不发短信，验证码写入日志，只用于本地开发，启动时打印警告。
 * </p>
 *
 * @author ygb
 */
@Component
public class AbrSmsClient
{
    private static final Logger log = LoggerFactory.getLogger(AbrSmsClient.class);

    @Value("${abroad.sms.mock:false}")
    private boolean mock;

    @PostConstruct
    void warnMock()
    {
        if (mock)
        {
            log.warn("短信为 mock 模式（abroad.sms.mock=true）：验证码只写入日志，只能用于本地开发");
        }
    }

    /**
     * 是否可用
     *
     * @return 是否可用
     */
    public boolean enabled()
    {
        return mock;
    }

    /**
     * 发送验证码
     *
     * @param phone 手机号
     * @param code 验证码
     */
    public void sendVerifyCode(String phone, String code)
    {
        if (!mock)
        {
            throw new ServiceException("短信服务尚未开通（待 M0-01 开通短信签名与模板），暂时不能绑定手机号");
        }
        log.warn("（mock）短信验证码 {}****{} → {}", phone.substring(0, 3), phone.substring(phone.length() - 4), code);
    }
}
