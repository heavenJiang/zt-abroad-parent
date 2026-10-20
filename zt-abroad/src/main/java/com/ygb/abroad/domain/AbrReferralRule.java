package com.ygb.abroad.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 推荐奖励规则 abr_referral_rule
 *
 * @author ygb
 */
public class AbrReferralRule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 规则ID */
    private Long ruleId;

    /** 推荐人类型 */
    private String referrerType;

    /** 里程碑 */
    private String milestone;

    /** 推荐人奖励 */
    private String rewardTitle;

    /** 推荐人奖励可抵扣的产品 */
    private String rewardProduct;

    /** 被推荐人奖励 */
    private String refereeTitle;

    /** 每天上限 */
    private Integer dailyCap;

    /** 状态 */
    private String status;

    public Long getRuleId()
    {
        return ruleId;
    }

    public void setRuleId(Long ruleId)
    {
        this.ruleId = ruleId;
    }

    public String getReferrerType()
    {
        return referrerType;
    }

    public void setReferrerType(String referrerType)
    {
        this.referrerType = referrerType;
    }

    public String getMilestone()
    {
        return milestone;
    }

    public void setMilestone(String milestone)
    {
        this.milestone = milestone;
    }

    public String getRewardTitle()
    {
        return rewardTitle;
    }

    public void setRewardTitle(String rewardTitle)
    {
        this.rewardTitle = rewardTitle;
    }

    public String getRewardProduct()
    {
        return rewardProduct;
    }

    public void setRewardProduct(String rewardProduct)
    {
        this.rewardProduct = rewardProduct;
    }

    public String getRefereeTitle()
    {
        return refereeTitle;
    }

    public void setRefereeTitle(String refereeTitle)
    {
        this.refereeTitle = refereeTitle;
    }

    public Integer getDailyCap()
    {
        return dailyCap;
    }

    public void setDailyCap(Integer dailyCap)
    {
        this.dailyCap = dailyCap;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("ruleId", getRuleId())
            .append("referrerType", getReferrerType())
            .append("milestone", getMilestone())
            .append("rewardTitle", getRewardTitle())
            .append("rewardProduct", getRewardProduct())
            .append("refereeTitle", getRefereeTitle())
            .append("dailyCap", getDailyCap())
            .append("status", getStatus())
            .toString();
    }
}
