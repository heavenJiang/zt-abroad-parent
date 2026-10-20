package com.ygb.abroad.domain;

import java.io.Serializable;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 院校库字段变化历史（M2-09） abr_field_history
 *
 * @author ygb
 */
public class AbrFieldHistory implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** ID */
    private Long historyId;

    /** 表 */
    private String tableName;

    /** 记录ID */
    private Long recordId;

    /** 字段（Java 属性名） */
    private String fieldName;

    /** 旧值 */
    private String oldValue;

    /** 新值 */
    private String newValue;

    /** 依据的存档 */
    private Long snapshotId;

    /** 存档是否仍在（查询时带出） */
    private Boolean snapshotAvailable;

    /** 来源网址 */
    private String sourceUrl;

    /** 原文引句 */
    private String quote;

    /** 生效方式 */
    private String changeMode;

    /** 状态（applied / rolled_back） */
    private String status;

    /** 操作人或 Agent */
    private String operator;

    /** 变化时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getHistoryId()
    {
        return historyId;
    }

    public void setHistoryId(Long historyId)
    {
        this.historyId = historyId;
    }

    public String getTableName()
    {
        return tableName;
    }

    public void setTableName(String tableName)
    {
        this.tableName = tableName;
    }

    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public String getFieldName()
    {
        return fieldName;
    }

    public void setFieldName(String fieldName)
    {
        this.fieldName = fieldName;
    }

    public String getOldValue()
    {
        return oldValue;
    }

    public void setOldValue(String oldValue)
    {
        this.oldValue = oldValue;
    }

    public String getNewValue()
    {
        return newValue;
    }

    public void setNewValue(String newValue)
    {
        this.newValue = newValue;
    }

    public Long getSnapshotId()
    {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId)
    {
        this.snapshotId = snapshotId;
    }

    public Boolean getSnapshotAvailable()
    {
        return snapshotAvailable;
    }

    public void setSnapshotAvailable(Boolean snapshotAvailable)
    {
        this.snapshotAvailable = snapshotAvailable;
    }

    public String getSourceUrl()
    {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl)
    {
        this.sourceUrl = sourceUrl;
    }

    public String getQuote()
    {
        return quote;
    }

    public void setQuote(String quote)
    {
        this.quote = quote;
    }

    public String getChangeMode()
    {
        return changeMode;
    }

    public void setChangeMode(String changeMode)
    {
        this.changeMode = changeMode;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getOperator()
    {
        return operator;
    }

    public void setOperator(String operator)
    {
        this.operator = operator;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("historyId", getHistoryId())
            .append("tableName", getTableName())
            .append("recordId", getRecordId())
            .append("fieldName", getFieldName())
            .append("oldValue", getOldValue())
            .append("newValue", getNewValue())
            .append("snapshotId", getSnapshotId())
            .append("snapshotAvailable", getSnapshotAvailable())
            .append("sourceUrl", getSourceUrl())
            .append("quote", getQuote())
            .append("changeMode", getChangeMode())
            .append("status", getStatus())
            .append("operator", getOperator())
            .append("createTime", getCreateTime())
            .toString();
    }
}
