package com.ygb.abroad.mapper;

import java.util.List;
import com.ygb.abroad.domain.AbrMomentRule;

/**
 * 转化时刻规则 数据层
 * 
 * @author ygb
 */
public interface AbrMomentRuleMapper
{
    /**
     * 查询规则列表
     * 
     * @param rule 查询条件（状态）
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
     * 修改规则（时刻编码与名称不可改）
     * 
     * @param rule 规则
     * @return 结果
     */
    public int updateAbrMomentRule(AbrMomentRule rule);
}
