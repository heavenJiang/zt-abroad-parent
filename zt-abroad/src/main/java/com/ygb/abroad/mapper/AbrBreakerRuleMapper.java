package com.ygb.abroad.mapper;

import java.util.List;
import com.ygb.abroad.domain.AbrBreakerRule;

/**
 * 熔断规则 数据层
 * 
 * @author ygb
 */
public interface AbrBreakerRuleMapper
{
    /**
     * 查询规则列表
     * 
     * @param rule 查询条件（指标、范围、状态）
     * @return 规则集合
     */
    public List<AbrBreakerRule> selectAbrBreakerRuleList(AbrBreakerRule rule);

    /**
     * 按ID查询规则
     * 
     * @param ruleId 规则ID
     * @return 规则
     */
    public AbrBreakerRule selectAbrBreakerRuleById(Long ruleId);

    /**
     * 按编码查询规则（校验唯一）
     * 
     * @param ruleCode 规则编码
     * @return 规则
     */
    public AbrBreakerRule checkRuleCodeUnique(String ruleCode);

    /**
     * 新增规则
     * 
     * @param rule 规则
     * @return 结果
     */
    public int insertAbrBreakerRule(AbrBreakerRule rule);

    /**
     * 修改规则
     * 
     * @param rule 规则
     * @return 结果
     */
    public int updateAbrBreakerRule(AbrBreakerRule rule);

    /**
     * 批量删除规则
     * 
     * @param ruleIds 规则ID
     * @return 结果
     */
    public int deleteAbrBreakerRuleByIds(Long[] ruleIds);
}
