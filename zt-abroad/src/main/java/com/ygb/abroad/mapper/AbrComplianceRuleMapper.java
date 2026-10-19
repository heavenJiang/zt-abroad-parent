package com.ygb.abroad.mapper;

import java.util.List;
import com.ygb.abroad.domain.AbrComplianceRule;

/**
 * 合规词表 数据层
 *
 * @author ygb
 */
public interface AbrComplianceRuleMapper
{
    /**
     * 查询规则列表
     *
     * @param rule 查询条件（类别、匹配方式、状态精确；内容模糊；params.platform 平台）
     * @return 规则集合
     */
    public List<AbrComplianceRule> selectAbrComplianceRuleList(AbrComplianceRule rule);

    /**
     * 按ID查询规则
     *
     * @param ruleId 规则ID
     * @return 规则
     */
    public AbrComplianceRule selectAbrComplianceRuleById(Long ruleId);

    /**
     * 查询启用的规则（闸门检查用）
     *
     * @return 规则集合
     */
    public List<AbrComplianceRule> selectActiveRules();

    /**
     * 同类别、同匹配方式下内容相同（忽略大小写）的规则
     *
     * @param rule 规则
     * @return 已有规则，没有返回 null
     */
    public AbrComplianceRule checkRuleUnique(AbrComplianceRule rule);

    /**
     * 新增规则
     *
     * @param rule 规则
     * @return 结果
     */
    public int insertAbrComplianceRule(AbrComplianceRule rule);

    /**
     * 修改规则
     *
     * @param rule 规则
     * @return 结果
     */
    public int updateAbrComplianceRule(AbrComplianceRule rule);

    /**
     * 批量删除规则
     *
     * @param ruleIds 规则ID
     * @return 结果
     */
    public int deleteAbrComplianceRuleByIds(Long[] ruleIds);
}
