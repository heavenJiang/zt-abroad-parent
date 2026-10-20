package com.ygb.abroad.domain;

import java.math.BigDecimal;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 推荐人与专属推荐码 abr_referrer
 *
 * @author ygb
 */
public class AbrReferrer extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 推荐人ID */
    private Long referrerId;

    /** 类型 */
    private String referrerType;

    /** 称呼 */
    private String displayName;

    /** 推荐码 */
    private String refCode;

    /** 学生账号 */
    private Long appUserId;

    /** 线索 */
    private Long leadId;

    /** 状态 */
    private String status;

    /** 分成比例（%，只对合作方；为空用默认比例） */
    private BigDecimal commissionRate;

    /** 归因有效天数（只对合作方；为空用默认天数） */
    private Integer windowDays;

    /** 已进入人数 */
    private Integer registeredCount;

    /** 有效测评人数 */
    private Integer validCount;

    /** 已签约人数 */
    private Integer signedCount;

    public BigDecimal getCommissionRate()
    {
        return commissionRate;
    }

    public void setCommissionRate(BigDecimal commissionRate)
    {
        this.commissionRate = commissionRate;
    }

    public Integer getWindowDays()
    {
        return windowDays;
    }

    public void setWindowDays(Integer windowDays)
    {
        this.windowDays = windowDays;
    }

    public Long getReferrerId()
    {
        return referrerId;
    }

    public void setReferrerId(Long referrerId)
    {
        this.referrerId = referrerId;
    }

    public String getReferrerType()
    {
        return referrerType;
    }

    public void setReferrerType(String referrerType)
    {
        this.referrerType = referrerType;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public String getRefCode()
    {
        return refCode;
    }

    public void setRefCode(String refCode)
    {
        this.refCode = refCode;
    }

    public Long getAppUserId()
    {
        return appUserId;
    }

    public void setAppUserId(Long appUserId)
    {
        this.appUserId = appUserId;
    }

    public Long getLeadId()
    {
        return leadId;
    }

    public void setLeadId(Long leadId)
    {
        this.leadId = leadId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getRegisteredCount()
    {
        return registeredCount;
    }

    public void setRegisteredCount(Integer registeredCount)
    {
        this.registeredCount = registeredCount;
    }

    public Integer getValidCount()
    {
        return validCount;
    }

    public void setValidCount(Integer validCount)
    {
        this.validCount = validCount;
    }

    public Integer getSignedCount()
    {
        return signedCount;
    }

    public void setSignedCount(Integer signedCount)
    {
        this.signedCount = signedCount;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("referrerId", getReferrerId())
            .append("referrerType", getReferrerType())
            .append("displayName", getDisplayName())
            .append("refCode", getRefCode())
            .append("appUserId", getAppUserId())
            .append("leadId", getLeadId())
            .append("status", getStatus())
            .append("commissionRate", getCommissionRate())
            .append("windowDays", getWindowDays())
            .append("registeredCount", getRegisteredCount())
            .append("validCount", getValidCount())
            .append("signedCount", getSignedCount())
            .toString();
    }
}
