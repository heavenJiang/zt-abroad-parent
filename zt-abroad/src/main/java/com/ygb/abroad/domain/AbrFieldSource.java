package com.ygb.abroad.domain;

import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 字段来源表 abr_field_source
 * 
 * @author ygb
 */
public class AbrFieldSource extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 来源ID */
    private Long sourceId;

    /** 记录所在表 */
    private String tableName;

    /** 记录ID */
    private Long recordId;

    /** 字段名（Java 属性名） */
    private String fieldName;

    /** 来源 URL */
    private String sourceUrl;

    /** 原文引句 */
    private String quote;

    /** 来源方式（0人工 1AI预填） */
    private String origin;

    /** 依据的存档 */
    private Long snapshotId;

    /** 存档标题（查询时带出） */
    private String snapshotTitle;

    /** 存档最近一次看到的时间（查询时带出） */
    private Date snapshotTime;

    /** 核对人 */
    private String verifiedBy;

    /** 核对时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date verifiedTime;

    public void setSourceId(Long sourceId)
    {
        this.sourceId = sourceId;
    }

    public Long getSourceId()
    {
        return sourceId;
    }

    public void setTableName(String tableName)
    {
        this.tableName = tableName;
    }

    public String getTableName()
    {
        return tableName;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public Long getRecordId()
    {
        return recordId;
    }

    public void setFieldName(String fieldName)
    {
        this.fieldName = fieldName;
    }

    @NotBlank(message = "来源字段名不能为空")
    public String getFieldName()
    {
        return fieldName;
    }

    public void setSourceUrl(String sourceUrl)
    {
        this.sourceUrl = sourceUrl;
    }

    @Size(max = 1000, message = "来源链接不能超过1000个字符")
    public String getSourceUrl()
    {
        return sourceUrl;
    }

    public void setQuote(String quote)
    {
        this.quote = quote;
    }

    public String getQuote()
    {
        return quote;
    }

    public void setOrigin(String origin)
    {
        this.origin = origin;
    }

    public String getOrigin()
    {
        return origin;
    }

    public void setVerifiedBy(String verifiedBy)
    {
        this.verifiedBy = verifiedBy;
    }

    public String getVerifiedBy()
    {
        return verifiedBy;
    }

    public void setVerifiedTime(Date verifiedTime)
    {
        this.verifiedTime = verifiedTime;
    }

    public Date getVerifiedTime()
    {
        return verifiedTime;
    }

    public Long getSnapshotId()
    {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId)
    {
        this.snapshotId = snapshotId;
    }

    public String getSnapshotTitle()
    {
        return snapshotTitle;
    }

    public void setSnapshotTitle(String snapshotTitle)
    {
        this.snapshotTitle = snapshotTitle;
    }

    public Date getSnapshotTime()
    {
        return snapshotTime;
    }

    public void setSnapshotTime(Date snapshotTime)
    {
        this.snapshotTime = snapshotTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("sourceId", getSourceId())
            .append("tableName", getTableName())
            .append("recordId", getRecordId())
            .append("fieldName", getFieldName())
            .append("sourceUrl", getSourceUrl())
            .append("quote", getQuote())
            .append("origin", getOrigin())
            .append("snapshotId", getSnapshotId())
            .append("snapshotTitle", getSnapshotTitle())
            .append("snapshotTime", getSnapshotTime())
            .append("verifiedBy", getVerifiedBy())
            .append("verifiedTime", getVerifiedTime())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
