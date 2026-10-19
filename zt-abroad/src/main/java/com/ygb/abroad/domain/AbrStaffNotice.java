package com.ygb.abroad.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 员工站内提醒 abr_staff_notice
 *
 * @author ygb
 */
public class AbrStaffNotice extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 提醒ID */
    private Long noticeId;

    /** 收件员工 */
    private Long userId;

    /** 类型 */
    private String noticeKind;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 相关线索 */
    private Long leadId;

    /** 去重键 */
    private String dedupKey;

    /** 是否已读 */
    private String readFlag;

    /** 已读时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date readTime;

    /** 企业微信发送结果 */
    private String channelResult;

    public Long getNoticeId()
    {
        return noticeId;
    }

    public void setNoticeId(Long noticeId)
    {
        this.noticeId = noticeId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getNoticeKind()
    {
        return noticeKind;
    }

    public void setNoticeKind(String noticeKind)
    {
        this.noticeKind = noticeKind;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public Long getLeadId()
    {
        return leadId;
    }

    public void setLeadId(Long leadId)
    {
        this.leadId = leadId;
    }

    public String getDedupKey()
    {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey)
    {
        this.dedupKey = dedupKey;
    }

    public String getReadFlag()
    {
        return readFlag;
    }

    public void setReadFlag(String readFlag)
    {
        this.readFlag = readFlag;
    }

    public Date getReadTime()
    {
        return readTime;
    }

    public void setReadTime(Date readTime)
    {
        this.readTime = readTime;
    }

    public String getChannelResult()
    {
        return channelResult;
    }

    public void setChannelResult(String channelResult)
    {
        this.channelResult = channelResult;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("noticeId", getNoticeId())
            .append("userId", getUserId())
            .append("noticeKind", getNoticeKind())
            .append("title", getTitle())
            .append("content", getContent())
            .append("leadId", getLeadId())
            .append("dedupKey", getDedupKey())
            .append("readFlag", getReadFlag())
            .append("readTime", getReadTime())
            .append("channelResult", getChannelResult())
            .toString();
    }
}
