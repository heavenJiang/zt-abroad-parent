package com.ygb.abroad.service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrMessageSend;
import com.ygb.abroad.domain.AbrMessageTemplate;

/**
 * 消息发送（M6-05 统一发送接口）：按业务编码选模板与学生可达的渠道，发出前经触达判断（暂停、退订、未成年人、频控、免打扰），
 * 营销类另过合规闸门；每次发送写 abr_message_send，发出、失败、被拦下另写触达记录
 *
 * @author ygb
 */
public interface IAbrMessageService
{
    /** 对象类型：员工 */
    public static final String TARGET_STAFF = "staff";

    /**
     * 发给学生的一条消息
     *
     * @param templateCode 业务编码
     * @param targetType 对象类型（app_user / lead / visitor）
     * @param targetId 对象ID
     * @param vars 变量（{appUrl} 自动提供）
     * @param agentCode 发出的 Agent（触达判断按它的暂停与执行状态）
     * @param taskId 编排任务ID（可为 null）
     * @param aiGenerated 文字是否由大模型生成（营销类合规检查用）
     * @return 结果
     */
    public Result sendToStudent(String templateCode, String targetType, Long targetId, Map<String, ?> vars, String agentCode, Long taskId,
            boolean aiGenerated);

    /**
     * 发给学生的一条消息，带引用依据（文字中有截止日、院校等事实时，营销类合规闸门按它核对）
     *
     * @param templateCode 业务编码
     * @param targetType 对象类型（app_user / lead / visitor）
     * @param targetId 对象ID
     * @param vars 变量
     * @param agentCode 发出的 Agent
     * @param taskId 编排任务ID（可为 null）
     * @param aiGenerated 文字是否由大模型生成
     * @param citationsJson 引用依据（JSON 数组，如 [{"type": "program", "id": 5}]；可为 null）
     * @return 结果
     */
    public Result sendToStudent(String templateCode, String targetType, Long targetId, Map<String, ?> vars, String agentCode, Long taskId,
            boolean aiGenerated, String citationsJson);

    /**
     * 发给员工的提醒（企业微信应用消息，模板 staff_alert；成员账号取员工登录名）
     *
     * @param userId 员工
     * @param title 标题
     * @param content 内容（不含手机号）
     * @return 结果
     */
    public Result sendToStaff(Long userId, String title, String content);

    /**
     * 员工测试发送（不经触达判断与合规闸门；对象为学生账号、线索或员工）
     *
     * @param templateId 模板ID
     * @param targetType 对象类型
     * @param targetId 对象ID
     * @param vars 变量
     * @param operator 操作人
     * @return 结果
     */
    public Result testSend(Long templateId, String targetType, Long targetId, Map<String, ?> vars, String operator);

    /**
     * 各渠道开通状态（是否配置凭据、是否 mock、启用的模板数）
     *
     * @return 列表
     */
    public List<Map<String, Object>> channelStatus();

    /**
     * 查询消息模板
     *
     * @param template 条件
     * @return 列表
     */
    public List<AbrMessageTemplate> selectTemplateList(AbrMessageTemplate template);

    /**
     * 消息模板详情
     *
     * @param templateId 模板ID
     * @return 模板
     */
    public AbrMessageTemplate selectTemplateById(Long templateId);

    /**
     * 新增或修改模板（校验字段映射、启用条件、同一业务编码与渠道唯一）
     *
     * @param template 模板
     * @return 行数
     */
    public int saveTemplate(AbrMessageTemplate template);

    /**
     * 删除模板
     *
     * @param templateIds 模板ID
     * @return 行数
     */
    public int deleteTemplates(Long[] templateIds);

    /**
     * 查询发送记录
     *
     * @param send 条件
     * @return 列表
     */
    public List<AbrMessageSend> selectSendList(AbrMessageSend send);

    /**
     * 发送结果
     *
     * @param status 结果（字典 abr_message_status）
     * @param channel 渠道（未选出时为空）
     * @param sendId 发送记录ID
     * @param reason 原因
     * @param deferUntil 推迟到（status 为 deferred 时）
     */
    public record Result(String status, String channel, Long sendId, String reason, Date deferUntil)
    {
        /**
         * 是否已发出（含 mock）
         *
         * @return 是否
         */
        public boolean delivered()
        {
            return "sent".equals(status) || "mock".equals(status);
        }
    }
}
