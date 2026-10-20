package com.ygb.abroad.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import com.ygb.abroad.service.IAbrMessageService;

/**
 * 企业微信应用消息（给员工的提醒）：经统一发送接口（M6-05）以模板 staff_alert 发出，成员账号取员工登录名；
 * 企业微信未配置或模板未启用时返回 off，提醒只在管理后台站内显示
 *
 * @author ygb
 */
@Component
public class AbrWecomNotifier
{
    @Lazy
    @Autowired
    private IAbrMessageService messageService;

    /**
     * 发送应用消息
     *
     * @param userId 员工（sys_user）
     * @param title 标题
     * @param content 内容（不含手机号）
     * @return 结果（sent / mock / failed / off / no_channel，失败时带原因）
     */
    public String send(Long userId, String title, String content)
    {
        IAbrMessageService.Result r = messageService.sendToStaff(userId, title, content);
        return r.delivered() || r.reason().isEmpty() ? r.status() : r.status() + "：" + r.reason();
    }
}
