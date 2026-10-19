package com.ygb.abroad.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 访客表 abr_visitor
 * 
 * @author ygb
 */
public class AbrVisitor extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 访客ID */
    private Long visitorId;

    /** 访客令牌 SHA-256（十六进制） */
    private String tokenHash;

    /** 首次进入的渠道码 */
    private String channelCode;

    /** 首次进入的内容编号 */
    private String contentCode;

    /** 首次进入的端（h5 / mp_weixin） */
    private String clientPlatform;

    /** 渐进档案（JSON：题目 id → 最近一次答案） */
    private String profile;

    /** 学生账号ID（登录后关联） */
    private Long appUserId;

    /** 合并进的主访客ID */
    private Long mergedInto;

    public Long getMergedInto()
    {
        return mergedInto;
    }

    public void setMergedInto(Long mergedInto)
    {
        this.mergedInto = mergedInto;
    }

    /** 最近访问时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastSeenTime;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public void setVisitorId(Long visitorId)
    {
        this.visitorId = visitorId;
    }

    public Long getVisitorId()
    {
        return visitorId;
    }

    public void setTokenHash(String tokenHash)
    {
        this.tokenHash = tokenHash;
    }

    public String getTokenHash()
    {
        return tokenHash;
    }

    public void setChannelCode(String channelCode)
    {
        this.channelCode = channelCode;
    }

    public String getChannelCode()
    {
        return channelCode;
    }

    public void setContentCode(String contentCode)
    {
        this.contentCode = contentCode;
    }

    public String getContentCode()
    {
        return contentCode;
    }

    public void setClientPlatform(String clientPlatform)
    {
        this.clientPlatform = clientPlatform;
    }

    public String getClientPlatform()
    {
        return clientPlatform;
    }

    public void setProfile(String profile)
    {
        this.profile = profile;
    }

    public String getProfile()
    {
        return profile;
    }

    public void setAppUserId(Long appUserId)
    {
        this.appUserId = appUserId;
    }

    public Long getAppUserId()
    {
        return appUserId;
    }

    public void setLastSeenTime(Date lastSeenTime)
    {
        this.lastSeenTime = lastSeenTime;
    }

    public Date getLastSeenTime()
    {
        return lastSeenTime;
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
            .append("visitorId", getVisitorId())
            .append("tokenHash", getTokenHash())
            .append("channelCode", getChannelCode())
            .append("contentCode", getContentCode())
            .append("clientPlatform", getClientPlatform())
            .append("profile", getProfile())
            .append("appUserId", getAppUserId())
            .append("lastSeenTime", getLastSeenTime())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
