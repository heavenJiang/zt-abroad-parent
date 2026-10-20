package com.ygb.abroad.domain;

import java.io.Serializable;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 项目采集站点（M2-08） abr_crawl_site
 *
 * @author ygb
 */
public class AbrCrawlSite implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 站点ID */
    private Long siteId;

    /** 学校 */
    private Long schoolId;

    /** 学校中文名（查询带出） */
    private String schoolName;

    /** 学校英文名（查询带出） */
    private String schoolNameEn;

    /** 国家（查询带出） */
    private String country;

    /** 官网 */
    private String baseUrl;

    /** 状态（0 采集 1 暂停） */
    private String status;

    /** 站点地图 JSON */
    private String sitemaps;

    /** robots 抓取间隔（秒） */
    private Integer crawlDelay;

    /** 最近一次发现时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastDiscoverTime;

    /** 最近一次发现的结果 */
    private String lastDiscoverResult;

    /** 页面数（查询带出） */
    private Integer pageCount;

    /** 已采集的项目页数（查询带出） */
    private Integer programCount;

    /** 待抓取数（查询带出） */
    private Integer pendingCount;

    /** 失败与 robots 不允许数（查询带出） */
    private Integer problemCount;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

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

    public String getSchoolNameEn()
    {
        return schoolNameEn;
    }

    public void setSchoolNameEn(String schoolNameEn)
    {
        this.schoolNameEn = schoolNameEn;
    }

    public String getCountry()
    {
        return country;
    }

    public void setCountry(String country)
    {
        this.country = country;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getSitemaps()
    {
        return sitemaps;
    }

    public void setSitemaps(String sitemaps)
    {
        this.sitemaps = sitemaps;
    }

    public Integer getCrawlDelay()
    {
        return crawlDelay;
    }

    public void setCrawlDelay(Integer crawlDelay)
    {
        this.crawlDelay = crawlDelay;
    }

    public Date getLastDiscoverTime()
    {
        return lastDiscoverTime;
    }

    public void setLastDiscoverTime(Date lastDiscoverTime)
    {
        this.lastDiscoverTime = lastDiscoverTime;
    }

    public String getLastDiscoverResult()
    {
        return lastDiscoverResult;
    }

    public void setLastDiscoverResult(String lastDiscoverResult)
    {
        this.lastDiscoverResult = lastDiscoverResult;
    }

    public Integer getPageCount()
    {
        return pageCount;
    }

    public void setPageCount(Integer pageCount)
    {
        this.pageCount = pageCount;
    }

    public Integer getProgramCount()
    {
        return programCount;
    }

    public void setProgramCount(Integer programCount)
    {
        this.programCount = programCount;
    }

    public Integer getPendingCount()
    {
        return pendingCount;
    }

    public void setPendingCount(Integer pendingCount)
    {
        this.pendingCount = pendingCount;
    }

    public Integer getProblemCount()
    {
        return problemCount;
    }

    public void setProblemCount(Integer problemCount)
    {
        this.problemCount = problemCount;
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
            .append("siteId", getSiteId())
            .append("schoolId", getSchoolId())
            .append("schoolName", getSchoolName())
            .append("schoolNameEn", getSchoolNameEn())
            .append("country", getCountry())
            .append("baseUrl", getBaseUrl())
            .append("status", getStatus())
            .append("sitemaps", getSitemaps())
            .append("crawlDelay", getCrawlDelay())
            .append("lastDiscoverTime", getLastDiscoverTime())
            .append("lastDiscoverResult", getLastDiscoverResult())
            .append("pageCount", getPageCount())
            .append("programCount", getProgramCount())
            .append("pendingCount", getPendingCount())
            .append("problemCount", getProblemCount())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
