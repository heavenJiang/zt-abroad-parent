package com.ygb.abroad.integration.channel;

import java.util.Map;

/**
 * 发给渠道适配器的一条消息（已渲染好变量）
 *
 * @param recipient 接收方（服务号 / 小程序为 openid，企业微信为成员账号，短信为手机号）
 * @param platformTemplateId 平台模板 ID（企业微信不用）
 * @param fields 平台字段 → 文字（服务号、小程序、短信用）
 * @param text 消息文字（企业微信用）
 * @param link 跳转（服务号为网页地址、小程序为页面路径；可为空）
 * @author ygb
 */
public record OutboundMessage(String recipient, String platformTemplateId, Map<String, String> fields, String text, String link)
{
}
