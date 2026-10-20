package com.ygb.abroad.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 被推荐人 abr_referral
 *
 * @author ygb
 */
public class AbrReferral extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** ID */
    private Long referralId;

    /** 推荐人 */
    private Long referrerId;

    /** 推荐人称呼 */
    private String referrerName;

    /** 推荐码 */
    private String refCode;

    /** 被推荐的访客 */
    private Long visitorId;

    /** 被推荐人的学生账号 */
    private Long appUserId;

    /** 被推荐人的线索 */
    private Long leadId;

    /** 被推荐人称呼 */
    private String leadName;

    /** 状态 */
    private String status;

    /** 无效原因 */
    private String invalidReason;

    /** 成为有效测评的时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date validTime;

    /** 签约时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date signedTime;

    public Long getReferralId()
    {
        return referralId;
    }

    public void setReferralId(Long referralId)
    {
        this.referralId = referralId;
    }

    public Long getReferrerId()
    {
        return referrerId;
    }

    public void setReferrerId(Long referrerId)
    {
        this.referrerId = referrerId;
    }

    public String getReferrerName()
    {
        return referrerName;
    }

    public void setReferrerName(String referrerName)
    {
        this.referrerName = referrerName;
    }

    public String getRefCode()
    {
        return refCode;
    }

    public void setRefCode(String refCode)
    {
        this.refCode = refCode;
    }

    public Long getVisitorId()
    {
        return visitorId;
    }

    public void setVisitorId(Long visitorId)
    {
        this.visitorId = visitorId;
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

    public String getLeadName()
    {
        return leadName;
    }

    public void setLeadName(String leadName)
    {
        this.leadName = leadName;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getInvalidReason()
    {
        return invalidReason;
    }

    public void setInvalidReason(String invalidReason)
    {
        this.invalidReason = invalidReason;
    }

    public Date getValidTime()
    {
        return validTime;
    }

    public void setValidTime(Date validTime)
    {
        this.validTime = validTime;
    }

    public Date getSignedTime()
    {
        return signedTime;
    }

    public void setSignedTime(Date signedTime)
    {
        this.signedTime = signedTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("referralId", getReferralId())
            .append("referrerId", getReferrerId())
            .append("referrerName", getReferrerName())
            .append("refCode", getRefCode())
            .append("visitorId", getVisitorId())
            .append("appUserId", getAppUserId())
            .append("leadId", getLeadId())
            .append("leadName", getLeadName())
            .append("status", getStatus())
            .append("invalidReason", getInvalidReason())
            .append("validTime", getValidTime())
            .append("signedTime", getSignedTime())
            .toString();
    }
}
