package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrMomentRule;

/**
 * 转化时刻规则 服务层
 * 
 * @author ygb
 */
public interface IAbrMomentRuleService
{
    /**
     * 查询规则列表
     * 
     * @param rule 查询条件
     * @return 规则集合
     */
    public List<AbrMomentRule> selectAbrMomentRuleList(AbrMomentRule rule);

    /**
     * 按ID查询规则
     * 
     * @param ruleId 规则ID
     * @return 规则
     */
    public AbrMomentRule selectAbrMomentRuleById(Long ruleId);

    /**
     * 修改规则（推荐的服务、文案、阈值、状态；时刻编码不可改）
     * 
     * @param rule 规则
     * @return 结果
     */
    public int updateAbrMomentRule(AbrMomentRule rule);
}
