package com.ygb.abroad.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 分享记录表 abr_share
 * 
 * @author ygb
 */
public class AbrShare extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 分享ID */
    private Long shareId;

    /** 分享码 */
    private String shareCode;

    /** 分享对象类型（report_poster / plan_view / plan_card） */
    private String kind;

    /** 分享人访客ID */
    private Long visitorId;

    /** 分享对象ID */
    private Long refId;

    /** 是否已撤销 */
    private Boolean revoked;

    /** 查看次数 */
    private Integer viewCount;

    /** 最近查看时间 */
    private Date lastViewTime;

    public Long getShareId()
    {
        return shareId;
    }

    public void setShareId(Long shareId)
    {
        this.shareId = shareId;
    }

    public String getShareCode()
    {
        return shareCode;
    }

    public void setShareCode(String shareCode)
    {
        this.shareCode = shareCode;
    }

    public String getKind()
    {
        return kind;
    }

    public void setKind(String kind)
    {
        this.kind = kind;
    }

    public Long getVisitorId()
    {
        return visitorId;
    }

    public void setVisitorId(Long visitorId)
    {
        this.visitorId = visitorId;
    }

    public Long getRefId()
    {
        return refId;
    }

    public void setRefId(Long refId)
    {
        this.refId = refId;
    }

    public Boolean getRevoked()
    {
        return revoked;
    }

    public void setRevoked(Boolean revoked)
    {
        this.revoked = revoked;
    }

    public Integer getViewCount()
    {
        return viewCount;
    }

    public void setViewCount(Integer viewCount)
    {
        this.viewCount = viewCount;
    }

    public Date getLastViewTime()
    {
        return lastViewTime;
    }

    public void setLastViewTime(Date lastViewTime)
    {
        this.lastViewTime = lastViewTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("shareId", getShareId())
            .append("shareCode", getShareCode())
            .append("kind", getKind())
            .append("visitorId", getVisitorId())
            .append("refId", getRefId())
            .append("revoked", getRevoked())
            .append("viewCount", getViewCount())
            .append("lastViewTime", getLastViewTime())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
