package com.ygb.abroad.service;

import java.math.BigDecimal;
import java.util.Map;
import com.ygb.abroad.domain.AbrGrowthSignal;
import com.ygb.abroad.growth.OutboundDecision;
import com.ygb.abroad.growth.TouchDecision;
import com.ygb.abroad.growth.TouchRequest;

/**
 * 获客执行保障 服务层（运行时检查）：暂停与停用、预算闸、执行模式、频控与免打扰、退订、熔断
 *
 * 获客 Agent 的处理器在对外发出前依次调用：outbound（按执行模式决定直接发、发并抽检、人工确认）→ checkTouch（逐个对象）
 * → 发送 → recordTouch；投诉、差评等由 reportSignal 上报，编排器每轮调用 evaluateBreakers。
 *
 * @author ygb
 */
public interface IAbrGrowthGuardService
{
    /**
     * Agent 现在不能运行的原因（全局暂停、未配置、停用、暂停、超出每天 AI 预算）
     * 
     * @param agentCode Agent 编码
     * @return 原因，能运行返回 null
     */
    public String agentBlockReason(String agentCode);

    /**
     * 今天（北京时间）各获客 Agent 的 AI 用量，不含管理端试用与评测
     * 
     * @return Agent 编码 → [费用（元）, token]；空串键为全部获客 Agent 合计
     */
    public Map<String, BigDecimal[]> aiUsageToday();

    /**
     * 全部获客 Agent 不能运行的原因
     * 
     * @return Agent 编码 → 原因（能运行的不在其中）
     */
    public Map<String, String> blockedAgents();

    /**
     * Agent 执行模式是否为停用（停用时触发规则不生成任务）
     * 
     * @param agentCode Agent 编码
     * @return 是否停用
     */
    public boolean modeOff(String agentCode);

    /**
     * 对外内容或消息按执行模式的发出方式
     * 
     * @param agentCode Agent 编码
     * @param sampleKey 抽检样本键（如任务ID、内容编号）
     * @return 发出方式
     */
    public OutboundDecision outbound(String agentCode, String sampleKey);

    /**
     * 触达一个对象前的判断：暂停 → 营销类的退订、未成年人、每周上限 → 免打扰；不能发时记一条被拦下的触达
     * 
     * @param request 触达
     * @return 判断结果
     */
    public TouchDecision checkTouch(TouchRequest request);

    /**
     * 记录一次触达结果（发出或发送失败）
     * 
     * @param request 触达
     * @param status 状态（sent / failed）
     * @param reason 失败原因（可为 null）
     */
    public void recordTouch(TouchRequest request, String status, String reason);

    /**
     * 上报熔断信号（投诉、平台违规提示、差评回复、合规拦截 / 通过）
     * 
     * @param signal 信号
     */
    public void reportSignal(AbrGrowthSignal signal);

    /**
     * 对象是否已退订营销消息
     * 
     * @param targetType 对象类型
     * @param targetId 对象ID
     * @return 是否退订
     */
    public boolean marketingOptedOut(String targetType, Long targetId);

    /**
     * 退订或恢复订阅营销消息（立即生效）；本人退订记一条退订信号，归到 7 天内最近一次触达该对象的 Agent
     * 
     * @param targetType 对象类型
     * @param targetId 对象ID
     * @param optout true 退订 / false 恢复
     * @param source 来源（user 本人 / admin 员工代为）
     * @param operator 操作人
     * @param reason 原因（可为 null）
     * @return 状态是否有变化
     */
    public boolean setMarketingOptout(String targetType, Long targetId, boolean optout, String source, String operator, String reason);

    /**
     * 评估熔断规则：达到阈值的暂停对应 Agent 或全局，写触发记录与 circuit_breaker_tripped 事件
     * 
     * @return 本次触发的条数
     */
    public int evaluateBreakers();
}
