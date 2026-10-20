package com.ygb.abroad.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 奖励 abr_reward
 *
 * @author ygb
 */
public class AbrReward extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 奖励ID */
    private Long rewardId;

    /** 学生账号 */
    private Long appUserId;

    /** 线索 */
    private Long leadId;

    /** 线索称呼 */
    private String leadName;

    /** 类型 */
    private String rewardType;

    /** 奖励内容 */
    private String title;

    /** 可抵扣的产品 */
    private String productCode;

    /** 状态 */
    private String status;

    /** 来源对象类型 */
    private String refType;

    /** 来源对象ID */
    private Long refId;

    /** 去重键 */
    private String dedupKey;

    /** 确认发放人 */
    private String confirmBy;

    /** 确认发放时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date confirmTime;

    /** 使用时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date usedTime;

    public Long getRewardId()
    {
        return rewardId;
    }

    public void setRewardId(Long rewardId)
    {
        this.rewardId = rewardId;
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

    public String getRewardType()
    {
        return rewardType;
    }

    public void setRewardType(String rewardType)
    {
        this.rewardType = rewardType;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getProductCode()
    {
        return productCode;
    }

    public void setProductCode(String productCode)
    {
        this.productCode = productCode;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getRefType()
    {
        return refType;
    }

    public void setRefType(String refType)
    {
        this.refType = refType;
    }

    public Long getRefId()
    {
        return refId;
    }

    public void setRefId(Long refId)
    {
        this.refId = refId;
    }

    public String getDedupKey()
    {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey)
    {
        this.dedupKey = dedupKey;
    }

    public String getConfirmBy()
    {
        return confirmBy;
    }

    public void setConfirmBy(String confirmBy)
    {
        this.confirmBy = confirmBy;
    }

    public Date getConfirmTime()
    {
        return confirmTime;
    }

    public void setConfirmTime(Date confirmTime)
    {
        this.confirmTime = confirmTime;
    }

    public Date getUsedTime()
    {
        return usedTime;
    }

    public void setUsedTime(Date usedTime)
    {
        this.usedTime = usedTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("rewardId", getRewardId())
            .append("appUserId", getAppUserId())
            .append("leadId", getLeadId())
            .append("leadName", getLeadName())
            .append("rewardType", getRewardType())
            .append("title", getTitle())
            .append("productCode", getProductCode())
            .append("status", getStatus())
            .append("refType", getRefType())
            .append("refId", getRefId())
            .append("dedupKey", getDedupKey())
            .append("confirmBy", getConfirmBy())
            .append("confirmTime", getConfirmTime())
            .append("usedTime", getUsedTime())
            .toString();
    }
}
