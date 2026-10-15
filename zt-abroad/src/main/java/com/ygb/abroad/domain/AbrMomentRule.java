package com.ygb.abroad.domain;

import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 转化时刻规则表 abr_moment_rule
 * 
 * @author ygb
 */
public class AbrMomentRule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 规则ID */
    private Long ruleId;

    /** 时刻编码 */
    private String momentCode;

    /** 时刻名称 */
    private String momentName;

    /** 推荐的产品编码（M5-06 产品配置） */
    @Size(max = 64, message = "产品编码最多 64 个字符")
    private String recommendProduct;

    /** 推荐的服务名称（为空时只记录事件） */
    @Size(max = 100, message = "服务名称最多 100 个字符")
    private String recommendLabel;

    /** 推荐文案 */
    @Size(max = 300, message = "推荐文案最多 300 个字符")
    private String recommendText;

    /** 阈值参数（JSON 对象） */
    private String ruleParams;

    /** 状态（0正常 1停用） */
    private String status;

    public Long getRuleId()
    {
        return ruleId;
    }

    public void setRuleId(Long ruleId)
    {
        this.ruleId = ruleId;
    }

    public String getMomentCode()
    {
        return momentCode;
    }

    public void setMomentCode(String momentCode)
    {
        this.momentCode = momentCode;
    }

    public String getMomentName()
    {
        return momentName;
    }

    public void setMomentName(String momentName)
    {
        this.momentName = momentName;
    }

    public String getRecommendProduct()
    {
        return recommendProduct;
    }

    public void setRecommendProduct(String recommendProduct)
    {
        this.recommendProduct = recommendProduct;
    }

    public String getRecommendLabel()
    {
        return recommendLabel;
    }

    public void setRecommendLabel(String recommendLabel)
    {
        this.recommendLabel = recommendLabel;
    }

    public String getRecommendText()
    {
        return recommendText;
    }

    public void setRecommendText(String recommendText)
    {
        this.recommendText = recommendText;
    }

    public String getRuleParams()
    {
        return ruleParams;
    }

    public void setRuleParams(String ruleParams)
    {
        this.ruleParams = ruleParams;
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
            .append("momentCode", getMomentCode())
            .append("momentName", getMomentName())
            .append("recommendProduct", getRecommendProduct())
            .append("recommendLabel", getRecommendLabel())
            .append("recommendText", getRecommendText())
            .append("ruleParams", getRuleParams())
            .append("status", getStatus())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
