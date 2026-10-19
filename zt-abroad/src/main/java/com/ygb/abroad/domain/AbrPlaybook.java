package com.ygb.abroad.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 营销剧本表 abr_playbook（数组、对象类字段为 JSON 文本）
 * 
 * @author ygb
 */
public class AbrPlaybook extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 剧本ID */
    private Long playbookId;

    /** 剧本编码 */
    @NotBlank(message = "剧本编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$", message = "剧本编码只能用小写字母、数字和下划线，以字母开头，2–64 位")
    private String playbookCode;

    /** 剧本名称 */
    @NotBlank(message = "剧本名称不能为空")
    @Size(max = 100, message = "剧本名称最多 100 个字符")
    private String playbookName;

    /** 适用学段（JSON 数组，空为全部学段） */
    private String degrees;

    /** 主推国家 / 地区（JSON 数组，空为全部） */
    private String countries;

    /** 目标人群 */
    @Size(max = 300, message = "目标人群最多 300 个字符")
    private String audience;

    /** 内容重点 */
    @Size(max = 500, message = "内容重点最多 500 个字符")
    private String contentFocus;

    /** 主推钩子（JSON 数组） */
    private String hookTools;

    /** 主推产品编码（JSON 数组） */
    private String products;

    /** 培育动作 */
    @Size(max = 500, message = "培育动作最多 500 个字符")
    private String nurtureActions;

    /** 内容配比（JSON 对象：内容类型 → 百分比） */
    private String contentMix;

    /** 备用选题（JSON 字符串数组） */
    private String backupTopics;

    /** 优先级（大的在前） */
    @Min(value = 0, message = "优先级为 0–999")
    @Max(value = 999, message = "优先级为 0–999")
    private Integer priority;

    /** 状态（0正常 1停用） */
    private String status;

    public void setPlaybookId(Long playbookId)
    {
        this.playbookId = playbookId;
    }

    public Long getPlaybookId()
    {
        return playbookId;
    }

    public void setPlaybookCode(String playbookCode)
    {
        this.playbookCode = playbookCode;
    }

    public String getPlaybookCode()
    {
        return playbookCode;
    }

    public void setPlaybookName(String playbookName)
    {
        this.playbookName = playbookName;
    }

    public String getPlaybookName()
    {
        return playbookName;
    }

    public void setDegrees(String degrees)
    {
        this.degrees = degrees;
    }

    public String getDegrees()
    {
        return degrees;
    }

    public void setCountries(String countries)
    {
        this.countries = countries;
    }

    public String getCountries()
    {
        return countries;
    }

    public void setAudience(String audience)
    {
        this.audience = audience;
    }

    public String getAudience()
    {
        return audience;
    }

    public void setContentFocus(String contentFocus)
    {
        this.contentFocus = contentFocus;
    }

    public String getContentFocus()
    {
        return contentFocus;
    }

    public void setHookTools(String hookTools)
    {
        this.hookTools = hookTools;
    }

    public String getHookTools()
    {
        return hookTools;
    }

    public void setProducts(String products)
    {
        this.products = products;
    }

    public String getProducts()
    {
        return products;
    }

    public void setNurtureActions(String nurtureActions)
    {
        this.nurtureActions = nurtureActions;
    }

    public String getNurtureActions()
    {
        return nurtureActions;
    }

    public void setContentMix(String contentMix)
    {
        this.contentMix = contentMix;
    }

    public String getContentMix()
    {
        return contentMix;
    }

    public void setBackupTopics(String backupTopics)
    {
        this.backupTopics = backupTopics;
    }

    public String getBackupTopics()
    {
        return backupTopics;
    }

    public void setPriority(Integer priority)
    {
        this.priority = priority;
    }

    public Integer getPriority()
    {
        return priority;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("playbookId", getPlaybookId())
            .append("playbookCode", getPlaybookCode())
            .append("playbookName", getPlaybookName())
            .append("degrees", getDegrees())
            .append("countries", getCountries())
            .append("audience", getAudience())
            .append("contentFocus", getContentFocus())
            .append("hookTools", getHookTools())
            .append("products", getProducts())
            .append("nurtureActions", getNurtureActions())
            .append("contentMix", getContentMix())
            .append("backupTopics", getBackupTopics())
            .append("priority", getPriority())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
