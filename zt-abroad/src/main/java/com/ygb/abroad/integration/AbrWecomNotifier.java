package com.ygb.abroad.integration;

import org.springframework.stereotype.Component;

/**
 * 企业微信应用消息（给员工的提醒）
 * <p>
 * 企业微信认证与自建应用随 M0-01 开通后在这里接入（应用 AgentId、Secret 只从配置读取，按员工的企业微信账号发送）；
 * 未接入时返回 off，提醒只在管理后台站内显示。
 * </p>
 *
 * @author ygb
 */
@Component
public class AbrWecomNotifier
{
    /**
     * 发送应用消息
     *
     * @param userId 员工（sys_user）
     * @param title 标题
     * @param content 内容（不含手机号）
     * @return 结果（off 未接入）
     */
    public String send(Long userId, String title, String content)
    {
        return "off";
    }
}
