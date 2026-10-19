package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrBreakerRule;
import com.ygb.abroad.domain.AbrBreakerTrip;
import com.ygb.abroad.domain.AbrGrowthAgent;
import com.ygb.abroad.domain.AbrGrowthSetting;
import com.ygb.abroad.domain.AbrGrowthSignal;
import com.ygb.abroad.domain.AbrTouchLog;

/**
 * 获客执行保障 管理服务：全局设置与一键暂停、Agent 执行模式与预算、熔断规则、信号、触达记录
 * 
 * @author ygb
 */
public interface IAbrGrowthControlService
{
    /**
     * 全局设置（带出今天的 AI 用量）
     * 
     * @return 设置
     */
    public AbrGrowthSetting selectSetting();

    /**
     * 修改全局设置（每周触达上限、免打扰、每天 AI 预算）
     * 
     * @param setting 设置
     * @return 结果
     */
    public int updateSetting(AbrGrowthSetting setting);

    /**
     * 一键暂停全部获客 Agent
     * 
     * @param reason 原因
     * @param operator 操作人
     * @return 是否有变化（已暂停为 false）
     */
    public boolean pauseGlobal(String reason, String operator);

    /**
     * 恢复全部获客 Agent（同时把未处理的全局熔断记录标为已处理）
     * 
     * @param operator 操作人
     * @return 是否有变化（未暂停为 false）
     */
    public boolean resumeGlobal(String operator);

    /**
     * Agent 运行配置列表（带出今天的 AI 用量与当前不能运行的原因）
     * 
     * @param agent 查询条件
     * @return 配置集合
     */
    public List<AbrGrowthAgent> selectAgentList(AbrGrowthAgent agent);

    /**
     * Agent 运行配置
     * 
     * @param agentCode Agent 编码
     * @return 配置
     */
    public AbrGrowthAgent selectAgent(String agentCode);

    /**
     * 修改执行模式、抽检比例、预算
     * 
     * @param agent 配置
     * @return 结果
     */
    public int updateAgent(AbrGrowthAgent agent);

    /**
     * 暂停一个 Agent
     * 
     * @param agentCode Agent 编码
     * @param reason 原因
     * @param operator 操作人
     * @return 是否有变化
     */
    public boolean pauseAgent(String agentCode, String reason, String operator);

    /**
     * 恢复一个 Agent（同时把它未处理的熔断记录标为已处理）
     * 
     * @param agentCode Agent 编码
     * @param operator 操作人
     * @return 是否有变化
     */
    public boolean resumeAgent(String agentCode, String operator);

    /**
     * 熔断规则列表
     * 
     * @param rule 查询条件
     * @return 规则集合
     */
    public List<AbrBreakerRule> selectRuleList(AbrBreakerRule rule);

    /**
     * 熔断规则
     * 
     * @param ruleId 规则ID
     * @return 规则
     */
    public AbrBreakerRule selectRuleById(Long ruleId);

    /**
     * 规则编码是否唯一
     * 
     * @param rule 规则
     * @return true 唯一
     */
    public boolean checkRuleCodeUnique(AbrBreakerRule rule);

    /**
     * 新增熔断规则
     * 
     * @param rule 规则
     * @return 结果
     */
    public int insertRule(AbrBreakerRule rule);

    /**
     * 修改熔断规则
     * 
     * @param rule 规则
     * @return 结果
     */
    public int updateRule(AbrBreakerRule rule);

    /**
     * 删除熔断规则
     * 
     * @param ruleIds 规则ID
     * @return 结果
     */
    public int deleteRuleByIds(Long[] ruleIds);

    /**
     * 熔断与预算闸触发记录
     * 
     * @param trip 查询条件
     * @return 记录集合
     */
    public List<AbrBreakerTrip> selectTripList(AbrBreakerTrip trip);

    /**
     * 熔断信号
     * 
     * @param signal 查询条件
     * @return 信号集合
     */
    public List<AbrGrowthSignal> selectSignalList(AbrGrowthSignal signal);

    /**
     * 员工录入信号（如在平台后台看到违规提示、收到投诉；熔断演练也用它）
     * 
     * @param signal 信号
     * @param operator 操作人
     * @return 结果
     */
    public int insertManualSignal(AbrGrowthSignal signal, String operator);

    /**
     * 触达记录
     * 
     * @param log 查询条件
     * @return 记录集合
     */
    public List<AbrTouchLog> selectTouchLogList(AbrTouchLog log);
}
