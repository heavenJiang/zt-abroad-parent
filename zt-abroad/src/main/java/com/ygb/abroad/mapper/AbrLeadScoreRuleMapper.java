package com.ygb.abroad.mapper;

import java.util.List;
import com.ygb.abroad.domain.AbrLeadScoreRule;

/**
 * 线索评分规则 数据层
 *
 * @author ygb
 */
public interface AbrLeadScoreRuleMapper
{
    /**
     * 查询全部规则（按显示顺序）
     *
     * @return 规则集合
     */
    public List<AbrLeadScoreRule> selectAbrLeadScoreRuleList();

    /**
     * 按ID查询规则
     *
     * @param ruleId 规则ID
     * @return 规则
     */
    public AbrLeadScoreRule selectAbrLeadScoreRuleById(Long ruleId);

    /**
     * 修改规则（参数、状态、备注；编码与名称固定）
     *
     * @param rule 规则
     * @return 结果
     */
    public int updateAbrLeadScoreRule(AbrLeadScoreRule rule);
}
