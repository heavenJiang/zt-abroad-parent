package com.ygb.abroad.domain.vo;

import java.util.Date;

/**
 * 线索收口请求：各来源（C 端事件、员工录入、表格导入、企业微信与私信接口）统一经 IAbrLeadService.capture 入库
 *
 * @param source 来源（字典 abr_lead_source）
 * @param touchType 触达记录类型（字典 abr_lead_touch_type；为 null 时只更新最近触达，不写记录）
 * @param channelCode 渠道码
 * @param contentCode 内容编号
 * @param displayName 称呼
 * @param phone 手机号（原样，去掉空格、短横线与 +86 后校验）
 * @param unionid 微信 unionid
 * @param appUserId 学生账号ID
 * @param visitorId 主访客ID
 * @param minor 是否未成年
 * @param occurredTime 发生时间（为 null 用当前时间）
 * @param summary 摘要（不含手机号等个人信息）
 * @param refType 关联对象类型
 * @param refId 关联对象ID
 * @param eventId 来源业务事件ID（同一事件只记一次）
 * @param operator 操作人
 * @author ygb
 */
public record AbrLeadCapture(String source, String touchType, String channelCode, String contentCode, String displayName, String phone, String unionid,
        Long appUserId, Long visitorId, boolean minor, Date occurredTime, String summary, String refType, Long refId, Long eventId, String operator)
{
}
