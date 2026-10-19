package com.ygb.abroad.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 合规词表 abr_compliance_rule（platforms 为 JSON 数组文本）
 *
 * @author ygb
 */
public class AbrComplianceRule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 规则ID */
    private Long ruleId;

    /** 类别（字典 abr_compliance_category） */
    @NotBlank(message = "类别不能为空")
    private String category;

    /** 匹配方式（word / exception / regex） */
    @NotBlank(message = "匹配方式不能为空")
    private String matchType;

    /** 词、例外短语或正则 */
    @NotBlank(message = "内容不能为空")
    @Size(max = 200, message = "内容最多 200 个字符")
    private String pattern;

    /** 动作（block / cite） */
    private String action;

    /** 生效平台（JSON 数组，空为全部） */
    private String platforms;

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

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getMatchType()
    {
        return matchType;
    }

    public void setMatchType(String matchType)
    {
        this.matchType = matchType;
    }

    public String getPattern()
    {
        return pattern;
    }

    public void setPattern(String pattern)
    {
        this.pattern = pattern;
    }

    public String getAction()
    {
        return action;
    }

    public void setAction(String action)
    {
        this.action = action;
    }

    public String getPlatforms()
    {
        return platforms;
    }

    public void setPlatforms(String platforms)
    {
        this.platforms = platforms;
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
            .append("category", getCategory())
            .append("matchType", getMatchType())
            .append("pattern", getPattern())
            .append("action", getAction())
            .append("platforms", getPlatforms())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
