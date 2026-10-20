package com.ygb.abroad.domain;

import java.io.Serializable;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 项目采集页面（M2-08） abr_crawl_page
 *
 * @author ygb
 */
public class AbrCrawlPage implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long pageId;

    /** 站点 */
    private Long siteId;

    /** 学校 */
    private Long schoolId;

    /** 学校中文名（查询带出） */
    private String schoolName;

    /** 网址 */
    private String url;

    /** 类型 */
    private String kind;

    /** 层数 */
    private Integer depth;

    /** 发现途径 */
    private String foundFrom;

    /** 链接文字 */
    private String linkText;

    /** 状态 */
    private String status;

    /** 页面标题 */
    private String title;

    /** 最近一次存档 */
    private Long snapshotId;

    /** 最近一次内容哈希 */
    private String contentHash;

    /** 项目 */
    private Long programId;

    /** 项目英文名（查询带出） */
    private String programNameEn;

    /** 项目状态（查询带出） */
    private String programStatus;

    /** 申请要求 */
    private Long requirementId;

    /** 通过校验的字段数 */
    private Integer fieldsFound;

    /** 逐字段结果 JSON */
    private String result;

    /** 连续失败次数 */
    private Integer failCount;

    /** 原因 */
    private String reason;

    /** 最近一次抓取时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date fetchedTime;

    /** 下次抓取时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextFetchTime;

    /** 发现时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Long getPageId()
    {
        return pageId;
    }

    public void setPageId(Long pageId)
    {
        this.pageId = pageId;
    }

    public Long getSiteId()
    {
        return siteId;
    }

    public void setSiteId(Long siteId)
    {
        this.siteId = siteId;
    }

    public Long getSchoolId()
    {
        return schoolId;
    }

    public void setSchoolId(Long schoolId)
    {
        this.schoolId = schoolId;
    }

    public String getSchoolName()
    {
        return schoolName;
    }

    public void setSchoolName(String schoolName)
    {
        this.schoolName = schoolName;
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public String getKind()
    {
        return kind;
    }

    public void setKind(String kind)
    {
        this.kind = kind;
    }

    public Integer getDepth()
    {
        return depth;
    }

    public void setDepth(Integer depth)
    {
        this.depth = depth;
    }

    public String getFoundFrom()
    {
        return foundFrom;
    }

    public void setFoundFrom(String foundFrom)
    {
        this.foundFrom = foundFrom;
    }

    public String getLinkText()
    {
        return linkText;
    }

    public void setLinkText(String linkText)
    {
        this.linkText = linkText;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Long getSnapshotId()
    {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId)
    {
        this.snapshotId = snapshotId;
    }

    public String getContentHash()
    {
        return contentHash;
    }

    public void setContentHash(String contentHash)
    {
        this.contentHash = contentHash;
    }

    public Long getProgramId()
    {
        return programId;
    }

    public void setProgramId(Long programId)
    {
        this.programId = programId;
    }

    public String getProgramNameEn()
    {
        return programNameEn;
    }

    public void setProgramNameEn(String programNameEn)
    {
        this.programNameEn = programNameEn;
    }

    public String getProgramStatus()
    {
        return programStatus;
    }

    public void setProgramStatus(String programStatus)
    {
        this.programStatus = programStatus;
    }

    public Long getRequirementId()
    {
        return requirementId;
    }

    public void setRequirementId(Long requirementId)
    {
        this.requirementId = requirementId;
    }

    public Integer getFieldsFound()
    {
        return fieldsFound;
    }

    public void setFieldsFound(Integer fieldsFound)
    {
        this.fieldsFound = fieldsFound;
    }

    public String getResult()
    {
        return result;
    }

    public void setResult(String result)
    {
        this.result = result;
    }

    public Integer getFailCount()
    {
        return failCount;
    }

    public void setFailCount(Integer failCount)
    {
        this.failCount = failCount;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public Date getFetchedTime()
    {
        return fetchedTime;
    }

    public void setFetchedTime(Date fetchedTime)
    {
        this.fetchedTime = fetchedTime;
    }

    public Date getNextFetchTime()
    {
        return nextFetchTime;
    }

    public void setNextFetchTime(Date nextFetchTime)
    {
        this.nextFetchTime = nextFetchTime;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("pageId", getPageId())
            .append("siteId", getSiteId())
            .append("schoolId", getSchoolId())
            .append("schoolName", getSchoolName())
            .append("url", getUrl())
            .append("kind", getKind())
            .append("depth", getDepth())
            .append("foundFrom", getFoundFrom())
            .append("linkText", getLinkText())
            .append("status", getStatus())
            .append("title", getTitle())
            .append("snapshotId", getSnapshotId())
            .append("contentHash", getContentHash())
            .append("programId", getProgramId())
            .append("programNameEn", getProgramNameEn())
            .append("programStatus", getProgramStatus())
            .append("requirementId", getRequirementId())
            .append("fieldsFound", getFieldsFound())
            .append("failCount", getFailCount())
            .append("reason", getReason())
            .append("fetchedTime", getFetchedTime())
            .append("nextFetchTime", getNextFetchTime())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
