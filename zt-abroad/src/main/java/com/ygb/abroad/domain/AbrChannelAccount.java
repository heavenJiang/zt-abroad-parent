package com.ygb.abroad.domain;

import java.util.Date;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 渠道账号表 abr_channel_account（账号矩阵）
 * 
 * @author ygb
 */
public class AbrChannelAccount extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 发布方式：接口直发 */
    public static final String MODE_API = "api";

    /** 发布方式：一键发布包 */
    public static final String MODE_PACKAGE = "package";

    /** 账号ID */
    private Long accountId;

    /** 账号编码（链接渠道码 ch） */
    @NotBlank(message = "账号编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,32}$", message = "账号编码只能用字母、数字、_、-，2–32 位（作为链接渠道码）")
    private String accountCode;

    /** 平台 */
    @NotBlank(message = "平台不能为空")
    private String platform;

    /** 账号名称 */
    @NotBlank(message = "账号名称不能为空")
    @Size(max = 100, message = "账号名称最多 100 个字符")
    private String accountName;

    /** 负责人用户ID */
    private Long ownerUserId;

    /** 负责人姓名 */
    private String ownerName;

    /** 人设（studio / consultant） */
    private String persona;

    /** 人设说明 */
    @Size(max = 500, message = "人设说明最多 500 个字符")
    private String personaDesc;

    /** 接口授权状态 */
    private String authStatus;

    /** 发布方式（api / package） */
    private String publishMode;

    /** 每天发布上限 */
    @NotNull(message = "每天发布上限不能为空")
    @Min(value = 0, message = "每天发布上限为 0–50")
    @Max(value = 50, message = "每天发布上限为 0–50")
    private Integer dailyPublishCap;

    /** 暂停（Y / N） */
    private String paused;

    /** 暂停原因 */
    private String pauseReason;

    /** 暂停人 */
    private String pausedBy;

    /** 暂停时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pausedTime;

    /** 最近一次恢复时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date resumeTime;

    /** 状态（0正常 1停用） */
    private String status;

    public void setAccountId(Long accountId)
    {
        this.accountId = accountId;
    }

    public Long getAccountId()
    {
        return accountId;
    }

    public void setAccountCode(String accountCode)
    {
        this.accountCode = accountCode;
    }

    public String getAccountCode()
    {
        return accountCode;
    }

    public void setPlatform(String platform)
    {
        this.platform = platform;
    }

    public String getPlatform()
    {
        return platform;
    }

    public void setAccountName(String accountName)
    {
        this.accountName = accountName;
    }

    public String getAccountName()
    {
        return accountName;
    }

    public void setOwnerUserId(Long ownerUserId)
    {
        this.ownerUserId = ownerUserId;
    }

    public Long getOwnerUserId()
    {
        return ownerUserId;
    }

    public void setOwnerName(String ownerName)
    {
        this.ownerName = ownerName;
    }

    public String getOwnerName()
    {
        return ownerName;
    }

    public void setPersona(String persona)
    {
        this.persona = persona;
    }

    public String getPersona()
    {
        return persona;
    }

    public void setPersonaDesc(String personaDesc)
    {
        this.personaDesc = personaDesc;
    }

    public String getPersonaDesc()
    {
        return personaDesc;
    }

    public void setAuthStatus(String authStatus)
    {
        this.authStatus = authStatus;
    }

    public String getAuthStatus()
    {
        return authStatus;
    }

    public void setPublishMode(String publishMode)
    {
        this.publishMode = publishMode;
    }

    public String getPublishMode()
    {
        return publishMode;
    }

    public void setDailyPublishCap(Integer dailyPublishCap)
    {
        this.dailyPublishCap = dailyPublishCap;
    }

    public Integer getDailyPublishCap()
    {
        return dailyPublishCap;
    }

    public void setPaused(String paused)
    {
        this.paused = paused;
    }

    public String getPaused()
    {
        return paused;
    }

    public void setPauseReason(String pauseReason)
    {
        this.pauseReason = pauseReason;
    }

    public String getPauseReason()
    {
        return pauseReason;
    }

    public void setPausedBy(String pausedBy)
    {
        this.pausedBy = pausedBy;
    }

    public String getPausedBy()
    {
        return pausedBy;
    }

    public void setPausedTime(Date pausedTime)
    {
        this.pausedTime = pausedTime;
    }

    public Date getPausedTime()
    {
        return pausedTime;
    }

    public void setResumeTime(Date resumeTime)
    {
        this.resumeTime = resumeTime;
    }

    public Date getResumeTime()
    {
        return resumeTime;
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
            .append("accountId", getAccountId())
            .append("accountCode", getAccountCode())
            .append("platform", getPlatform())
            .append("accountName", getAccountName())
            .append("ownerUserId", getOwnerUserId())
            .append("ownerName", getOwnerName())
            .append("persona", getPersona())
            .append("personaDesc", getPersonaDesc())
            .append("authStatus", getAuthStatus())
            .append("publishMode", getPublishMode())
            .append("dailyPublishCap", getDailyPublishCap())
            .append("paused", getPaused())
            .append("pauseReason", getPauseReason())
            .append("pausedBy", getPausedBy())
            .append("pausedTime", getPausedTime())
            .append("resumeTime", getResumeTime())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
