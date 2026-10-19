package com.ygb.abroad.service.impl;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrMomentRule;
import com.ygb.abroad.mapper.AbrMomentRuleMapper;
import com.ygb.abroad.mapper.AbrProductMapper;
import com.ygb.abroad.service.IAbrMomentRuleService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 转化时刻规则 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrMomentRuleServiceImpl implements IAbrMomentRuleService
{
    /** 推荐文案中不允许出现的价格写法（价格只来自产品配置，见《获客系统设计》） */
    private static final Pattern PRICE = Pattern.compile("[¥￥$]|\\d+\\s*元");

    @Autowired
    private AbrMomentRuleMapper ruleMapper;

    @Autowired
    private AbrProductMapper productMapper;

    @Override
    public List<AbrMomentRule> selectAbrMomentRuleList(AbrMomentRule rule)
    {
        return ruleMapper.selectAbrMomentRuleList(rule);
    }

    @Override
    public AbrMomentRule selectAbrMomentRuleById(Long ruleId)
    {
        return ruleMapper.selectAbrMomentRuleById(ruleId);
    }

    @Override
    public int updateAbrMomentRule(AbrMomentRule rule)
    {
        if (rule.getRuleId() == null || ruleMapper.selectAbrMomentRuleById(rule.getRuleId()) == null)
        {
            throw new ServiceException("规则不存在");
        }
        String params = AbrJsonFields.requireObject(rule.getRuleParams(), "阈值参数");
        rule.setRuleParams(params == null ? "{}" : params);
        for (String text : new String[] { rule.getRecommendLabel(), rule.getRecommendText() })
        {
            if (StringUtils.isNotBlank(text) && PRICE.matcher(text).find())
            {
                throw new ServiceException("推荐的服务名称和文案中不要写价格（价格在产品配置中维护）");
            }
            if (StringUtils.isNotBlank(text) && AbrAssessmentAnswers.containsTag(JsonNodeFactory.instance.stringNode(text)))
            {
                throw new ServiceException("推荐的服务名称和文案不能包含 < 加字母的写法");
            }
        }
        rule.setRecommendLabel(StringUtils.trimToNull(rule.getRecommendLabel()));
        rule.setRecommendText(StringUtils.trimToNull(rule.getRecommendText()));
        rule.setRecommendProduct(StringUtils.trimToNull(rule.getRecommendProduct()));
        if (rule.getRecommendProduct() != null && productMapper.selectAbrProductByCode(rule.getRecommendProduct()) == null)
        {
            throw new ServiceException("推荐的产品“" + rule.getRecommendProduct() + "”不存在（在“产品与报价”中维护）");
        }
        return ruleMapper.updateAbrMomentRule(rule);
    }
}
