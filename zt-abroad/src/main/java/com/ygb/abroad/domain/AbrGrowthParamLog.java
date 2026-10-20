package com.ygb.abroad.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * growth_analyst 调参记录（M9-04） abr_growth_param_log
 *
 * @author ygb
 */
public class AbrGrowthParamLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** ID */
    private Long logId;

    /** 参数 */
    private String paramKey;

    /** 范围（剧本编码） */
    private String scope;

    /** 范围名称 */
    private String scopeName;

    /** 调整前 JSON */
    private String oldValue;

    /** 调整后 JSON */
    private String newValue;

    /** 依据 */
    private String reason;

    /** 状态 */
    private String status;

    /** 所属周报 */
    private Long reportId;

    public Long getLogId()
    {
        return logId;
    }

    public void setLogId(Long logId)
    {
        this.logId = logId;
    }

    public String getParamKey()
    {
        return paramKey;
    }

    public void setParamKey(String paramKey)
    {
        this.paramKey = paramKey;
    }

    public String getScope()
    {
        return scope;
    }

    public void setScope(String scope)
    {
        this.scope = scope;
    }

    public String getScopeName()
    {
        return scopeName;
    }

    public void setScopeName(String scopeName)
    {
        this.scopeName = scopeName;
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

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getReportId()
    {
        return reportId;
    }

    public void setReportId(Long reportId)
    {
        this.reportId = reportId;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("logId", getLogId())
            .append("paramKey", getParamKey())
            .append("scope", getScope())
            .append("scopeName", getScopeName())
            .append("oldValue", getOldValue())
            .append("newValue", getNewValue())
            .append("reason", getReason())
            .append("status", getStatus())
            .append("reportId", getReportId())
            .toString();
    }
}
