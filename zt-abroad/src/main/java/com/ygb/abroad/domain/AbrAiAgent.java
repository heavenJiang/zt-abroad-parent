package com.ygb.abroad.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.annotation.Excel;
import com.ygb.common.core.domain.BaseEntity;

/**
 * AI Agent 配置表 abr_ai_agent
 * 
 * @author ygb
 */
public class AbrAiAgent extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** Agent ID */
    @Excel(name = "Agent ID")
    private Long agentId;

    /** Agent 编码 */
    @Excel(name = "Agent 编码")
    private String agentCode;

    /** Agent 名称 */
    @Excel(name = "Agent 名称")
    private String agentName;

    /** 做什么 */
    private String description;

    /** 审核策略（字典 abr_ai_review_policy） */
    @Excel(name = "审核策略", dictType = "abr_ai_review_policy")
    private String reviewPolicy;

    /** 抽检比例 0–1 */
    private BigDecimal sampleRate;

    /** 主模型（provider:model） */
    @Excel(name = "主模型")
    private String primaryModel;

    /** 备用模型（provider:model，空表示不切换） */
    @Excel(name = "备用模型")
    private String backupModel;

    /** 生效提示词版本 */
    @Excel(name = "提示词版本")
    private String promptVersion;

    /** 单次模型调用超时（毫秒） */
    private Integer timeoutMs;

    /** 单次输出 token 上限 */
    private Integer maxOutputTokens;

    /** 采样温度 0–2 */
    private BigDecimal temperature;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 提示词目录是否完整（非表字段） */
    private Boolean promptAvailable;

    public void setAgentId(Long agentId)
    {
        this.agentId = agentId;
    }

    public Long getAgentId()
    {
        return agentId;
    }

    public void setAgentCode(String agentCode)
    {
        this.agentCode = agentCode;
    }

    public String getAgentCode()
    {
        return agentCode;
    }

    public void setAgentName(String agentName)
    {
        this.agentName = agentName;
    }

    @NotBlank(message = "Agent 名称不能为空")
    @Size(max = 100, message = "Agent 名称不能超过100个字符")
    public String getAgentName()
    {
        return agentName;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    @Size(max = 500, message = "说明不能超过500个字符")
    public String getDescription()
    {
        return description;
    }

    public void setReviewPolicy(String reviewPolicy)
    {
        this.reviewPolicy = reviewPolicy;
    }

    @NotBlank(message = "审核策略不能为空")
    public String getReviewPolicy()
    {
        return reviewPolicy;
    }

    public void setSampleRate(BigDecimal sampleRate)
    {
        this.sampleRate = sampleRate;
    }

    @DecimalMin(value = "0", message = "抽检比例应在 0–1 之间")
    @DecimalMax(value = "1", message = "抽检比例应在 0–1 之间")
    public BigDecimal getSampleRate()
    {
        return sampleRate;
    }

    public void setPrimaryModel(String primaryModel)
    {
        this.primaryModel = primaryModel;
    }

    @NotBlank(message = "主模型不能为空")
    @Size(max = 128, message = "主模型不能超过128个字符")
    public String getPrimaryModel()
    {
        return primaryModel;
    }

    public void setBackupModel(String backupModel)
    {
        this.backupModel = backupModel;
    }

    @Size(max = 128, message = "备用模型不能超过128个字符")
    public String getBackupModel()
    {
        return backupModel;
    }

    public void setPromptVersion(String promptVersion)
    {
        this.promptVersion = promptVersion;
    }

    @NotBlank(message = "提示词版本不能为空")
    @Size(max = 32, message = "提示词版本不能超过32个字符")
    public String getPromptVersion()
    {
        return promptVersion;
    }

    public void setTimeoutMs(Integer timeoutMs)
    {
        this.timeoutMs = timeoutMs;
    }

    @NotNull(message = "超时不能为空")
    @Min(value = 1000, message = "超时不能小于1000毫秒")
    @Max(value = 300000, message = "超时不能超过300000毫秒")
    public Integer getTimeoutMs()
    {
        return timeoutMs;
    }

    public void setMaxOutputTokens(Integer maxOutputTokens)
    {
        this.maxOutputTokens = maxOutputTokens;
    }

    @NotNull(message = "输出 token 上限不能为空")
    @Min(value = 1, message = "输出 token 上限不能小于1")
    @Max(value = 32000, message = "输出 token 上限不能超过32000")
    public Integer getMaxOutputTokens()
    {
        return maxOutputTokens;
    }

    public void setTemperature(BigDecimal temperature)
    {
        this.temperature = temperature;
    }

    @NotNull(message = "采样温度不能为空")
    @DecimalMin(value = "0", message = "采样温度应在 0–2 之间")
    @DecimalMax(value = "2", message = "采样温度应在 0–2 之间")
    public BigDecimal getTemperature()
    {
        return temperature;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setPromptAvailable(Boolean promptAvailable)
    {
        this.promptAvailable = promptAvailable;
    }

    public Boolean getPromptAvailable()
    {
        return promptAvailable;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("agentId", getAgentId())
            .append("agentCode", getAgentCode())
            .append("agentName", getAgentName())
            .append("description", getDescription())
            .append("reviewPolicy", getReviewPolicy())
            .append("sampleRate", getSampleRate())
            .append("primaryModel", getPrimaryModel())
            .append("backupModel", getBackupModel())
            .append("promptVersion", getPromptVersion())
            .append("timeoutMs", getTimeoutMs())
            .append("maxOutputTokens", getMaxOutputTokens())
            .append("temperature", getTemperature())
            .append("status", getStatus())
            .append("promptAvailable", getPromptAvailable())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
