package com.ygb.abroad.growth;

/**
 * 一次对外触达的描述（供触达判断与触达记录）
 *
 * @param agentCode 发出的 Agent
 * @param targetType 对象类型（visitor / app_user / lead）
 * @param targetId 对象ID
 * @param channel 渠道
 * @param category 类别（marketing 营销 / service 服务提醒）
 * @param taskId 编排任务ID（可为 null）
 * @param contentRef 内容引用（模板编码、内容编号等，可为 null）
 * @author ygb
 */
public record TouchRequest(String agentCode, String targetType, Long targetId, String channel, String category, Long taskId, String contentRef)
{
}
