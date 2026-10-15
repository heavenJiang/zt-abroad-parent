package com.ygb.abroad.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 协议版本表 abr_policy
 * 
 * @author ygb
 */
public class AbrPolicy extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 启用 */
    public static final String STATUS_ACTIVE = "active";

    /** 协议版本ID */
    private Long policyId;

    /** 协议编码 */
    private String policyCode;

    /** 版本号 */
    private String version;

    /** 标题 */
    private String title;

    /** 全文（Markdown） */
    private String content;

    /** 是否草案（Y是 N否） */
    private String isDraft;

    /** 发布状态（draft / active / retired） */
    private String publishStatus;

    /** 生效日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date effectiveDate;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public void setPolicyId(Long policyId)
    {
        this.policyId = policyId;
    }

    public Long getPolicyId()
    {
        return policyId;
    }

    public void setPolicyCode(String policyCode)
    {
        this.policyCode = policyCode;
    }

    public String getPolicyCode()
    {
        return policyCode;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public String getVersion()
    {
        return version;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getTitle()
    {
        return title;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getContent()
    {
        return content;
    }

    public void setIsDraft(String isDraft)
    {
        this.isDraft = isDraft;
    }

    public String getIsDraft()
    {
        return isDraft;
    }

    public void setPublishStatus(String publishStatus)
    {
        this.publishStatus = publishStatus;
    }

    public String getPublishStatus()
    {
        return publishStatus;
    }

    public void setEffectiveDate(Date effectiveDate)
    {
        this.effectiveDate = effectiveDate;
    }

    public Date getEffectiveDate()
    {
        return effectiveDate;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("policyId", getPolicyId())
            .append("policyCode", getPolicyCode())
            .append("version", getVersion())
            .append("title", getTitle())
            .append("isDraft", getIsDraft())
            .append("publishStatus", getPublishStatus())
            .append("effectiveDate", getEffectiveDate())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
