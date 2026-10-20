package com.ygb.abroad.domain;

import java.io.Serializable;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 源信息存档（M2-09） abr_source_snapshot
 *
 * @author ygb
 */
public class AbrSourceSnapshot implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 存档ID */
    private Long snapshotId;

    /** 请求的网址（不含密钥） */
    private String sourceUrl;

    /** 最终网址 */
    private String finalUrl;

    /** 来源类型（web / api / file） */
    private String sourceKind;

    /** HTTP 状态 */
    private Integer httpStatus;

    /** 内容类型 */
    private String contentType;

    /** 内容 SHA-256 */
    private String contentHash;

    /** 内容（列表查询时不带） */
    private String content;

    /** 内容字符数 */
    private Integer contentSize;

    /** 标题或说明 */
    private String title;

    /** 第一次抓到的时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date firstSeenTime;

    /** 最近一次抓到同样内容的时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastSeenTime;

    /** 抓到同样内容的次数 */
    private Integer seenCount;

    /** 抓取者 */
    private String fetchedBy;

    /** 备注 */
    private String remark;

    public Long getSnapshotId()
    {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId)
    {
        this.snapshotId = snapshotId;
    }

    public String getSourceUrl()
    {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl)
    {
        this.sourceUrl = sourceUrl;
    }

    public String getFinalUrl()
    {
        return finalUrl;
    }

    public void setFinalUrl(String finalUrl)
    {
        this.finalUrl = finalUrl;
    }

    public String getSourceKind()
    {
        return sourceKind;
    }

    public void setSourceKind(String sourceKind)
    {
        this.sourceKind = sourceKind;
    }

    public Integer getHttpStatus()
    {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus)
    {
        this.httpStatus = httpStatus;
    }

    public String getContentType()
    {
        return contentType;
    }

    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    public String getContentHash()
    {
        return contentHash;
    }

    public void setContentHash(String contentHash)
    {
        this.contentHash = contentHash;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public Integer getContentSize()
    {
        return contentSize;
    }

    public void setContentSize(Integer contentSize)
    {
        this.contentSize = contentSize;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Date getFirstSeenTime()
    {
        return firstSeenTime;
    }

    public void setFirstSeenTime(Date firstSeenTime)
    {
        this.firstSeenTime = firstSeenTime;
    }

    public Date getLastSeenTime()
    {
        return lastSeenTime;
    }

    public void setLastSeenTime(Date lastSeenTime)
    {
        this.lastSeenTime = lastSeenTime;
    }

    public Integer getSeenCount()
    {
        return seenCount;
    }

    public void setSeenCount(Integer seenCount)
    {
        this.seenCount = seenCount;
    }

    public String getFetchedBy()
    {
        return fetchedBy;
    }

    public void setFetchedBy(String fetchedBy)
    {
        this.fetchedBy = fetchedBy;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("snapshotId", getSnapshotId())
            .append("sourceUrl", getSourceUrl())
            .append("finalUrl", getFinalUrl())
            .append("sourceKind", getSourceKind())
            .append("httpStatus", getHttpStatus())
            .append("contentType", getContentType())
            .append("contentHash", getContentHash())
            .append("contentSize", getContentSize())
            .append("title", getTitle())
            .append("firstSeenTime", getFirstSeenTime())
            .append("lastSeenTime", getLastSeenTime())
            .append("seenCount", getSeenCount())
            .append("fetchedBy", getFetchedBy())
            .append("remark", getRemark())
            .toString();
    }
}
