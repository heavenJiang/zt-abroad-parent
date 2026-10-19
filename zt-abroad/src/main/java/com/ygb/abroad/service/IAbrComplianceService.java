package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrComplianceRule;
import com.ygb.abroad.domain.AbrContent;
import com.ygb.abroad.growth.compliance.ComplianceChecker;

/**
 * 合规审查 compliance_checker 服务层：合规词表维护；对内容和消息做合规闸门检查（词表、事实核对、AI 生成标识）
 *
 * @author ygb
 */
public interface IAbrComplianceService
{
    /**
     * 查询规则列表
     *
     * @param rule 查询条件
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
     * 同类别、同匹配方式下内容是否唯一（忽略大小写）
     *
     * @param rule 规则
     * @return 是否唯一
     */
    public boolean checkRuleUnique(AbrComplianceRule rule);

    /**
     * 新增规则（校验类别、匹配方式、动作、平台与正则写法）
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

    /**
     * 检查一条内容并写入闸门结果（通过 → 可发布或待人工确认；拦截 → 草稿，明细写明命中项与修改建议）
     *
     * @param contentId 内容ID
     * @param checker 检查人（compliance_checker 或员工账号）
     * @return 写入后的内容
     */
    public AbrContent checkContent(Long contentId, String checker);

    /**
     * 只检查、不保存：按稿件字段（标题、正文、话题、引用依据、平台、是否 AI 生成、内容类型）检查
     *
     * @param content 稿件
     * @return 检查结果
     */
    public ComplianceChecker.Result checkDraft(AbrContent content);

    /**
     * 检查一条对外消息（M8 培育、邀约、自动回复发出前调用；不保存，由调用方记录）
     *
     * @param platform 渠道或平台编码（null 只用全平台规则）
     * @param text 消息文字
     * @param citationsJson 引用依据（JSON 数组，可为空）
     * @param aiGenerated 是否由大模型生成（是则须带消息 AI 标识）
     * @return 检查结果
     */
    public ComplianceChecker.Result checkMessage(String platform, String text, String citationsJson, boolean aiGenerated);
}
