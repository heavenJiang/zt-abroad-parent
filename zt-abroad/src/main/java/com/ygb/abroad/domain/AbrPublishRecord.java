package com.ygb.abroad.domain;

import java.util.Date;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 发布记录表 abr_publish_record
 * 
 * @author ygb
 */
public class AbrPublishRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：待发布 */
    public static final String STATUS_SCHEDULED = "scheduled";

    /** 状态：已发布 */
    public static final String STATUS_PUBLISHED = "published";

    /** 状态：失败 */
    public static final String STATUS_FAILED = "failed";

    /** 状态：已撤回 */
    public static final String STATUS_WITHDRAWN = "withdrawn";

    /** 记录ID */
    private Long recordId;

    /** 内容ID */
    @NotNull(message = "内容不能为空")
    private Long contentId;

    /** 内容编号（查询带出） */
    private String contentCode;

    /** 标题（查询带出） */
    private String title;

    /** 账号ID */
    private Long accountId;

    /** 账号编码（查询带出） */
    private String accountCode;

    /** 账号名称（查询带出） */
    private String accountName;

    /** 发布方式 */
    private String publishMode;

    /** 状态 */
    private String status;

    /** 计划发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date plannedTime;

    /** 实际发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishedTime;

    /** 发布后的链接 */
    @Size(max = 500, message = "链接最多 500 个字符")
    private String postUrl;

    /** 平台内容ID */
    @Size(max = 128, message = "平台内容ID最多 128 个字符")
    private String platformPostId;

    /** 失败或撤回原因 */
    private String errorMsg;

    /** 效果数据（JSON） */
    private String metrics;

    /** 效果数据更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date metricsTime;

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public Long getRecordId()
    {
        return recordId;
    }

    public void setContentId(Long contentId)
    {
        this.contentId = contentId;
    }

    public Long getContentId()
    {
        return contentId;
    }

    public void setContentCode(String contentCode)
    {
        this.contentCode = contentCode;
    }

    public String getContentCode()
    {
        return contentCode;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getTitle()
    {
        return title;
    }

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

    public void setAccountName(String accountName)
    {
        this.accountName = accountName;
    }

    public String getAccountName()
    {
        return accountName;
    }

    public void setPublishMode(String publishMode)
    {
        this.publishMode = publishMode;
    }

    public String getPublishMode()
    {
        return publishMode;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setPlannedTime(Date plannedTime)
    {
        this.plannedTime = plannedTime;
    }

    public Date getPlannedTime()
    {
        return plannedTime;
    }

    public void setPublishedTime(Date publishedTime)
    {
        this.publishedTime = publishedTime;
    }

    public Date getPublishedTime()
    {
        return publishedTime;
    }

    public void setPostUrl(String postUrl)
    {
        this.postUrl = postUrl;
    }

    public String getPostUrl()
    {
        return postUrl;
    }

    public void setPlatformPostId(String platformPostId)
    {
        this.platformPostId = platformPostId;
    }

    public String getPlatformPostId()
    {
        return platformPostId;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setMetrics(String metrics)
    {
        this.metrics = metrics;
    }

    public String getMetrics()
    {
        return metrics;
    }

    public void setMetricsTime(Date metricsTime)
    {
        this.metricsTime = metricsTime;
    }

    public Date getMetricsTime()
    {
        return metricsTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("recordId", getRecordId())
            .append("contentId", getContentId())
            .append("contentCode", getContentCode())
            .append("title", getTitle())
            .append("accountId", getAccountId())
            .append("accountCode", getAccountCode())
            .append("accountName", getAccountName())
            .append("publishMode", getPublishMode())
            .append("status", getStatus())
            .append("plannedTime", getPlannedTime())
            .append("publishedTime", getPublishedTime())
            .append("postUrl", getPostUrl())
            .append("platformPostId", getPlatformPostId())
            .append("errorMsg", getErrorMsg())
            .append("metrics", getMetrics())
            .append("metricsTime", getMetricsTime())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
