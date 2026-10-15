package com.ygb.abroad.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 我的选校单条目表 abr_plan_item
 * 
 * @author ygb
 */
public class AbrPlanItem extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 条目ID */
    private Long planItemId;

    /** 访客ID */
    private Long visitorId;

    /** 学生账号ID（登录后关联） */
    private Long appUserId;

    /** 项目ID */
    private Long programId;

    /** 加入来源（quick / assessment / program / tool） */
    private String source;

    /** 来源记录ID */
    private Long sourceRef;

    /** 加入时的分组 */
    private String tierSnapshot;

    /** 排序 */
    private Integer sortOrder;

    /** 院校名称（查询时关联） */
    private String schoolName;

    /** 项目名称（查询时关联） */
    private String programName;

    /** 国家 / 地区（查询时关联） */
    private String country;

    public void setPlanItemId(Long planItemId)
    {
        this.planItemId = planItemId;
    }

    public Long getPlanItemId()
    {
        return planItemId;
    }

    public void setVisitorId(Long visitorId)
    {
        this.visitorId = visitorId;
    }

    public Long getVisitorId()
    {
        return visitorId;
    }

    public void setAppUserId(Long appUserId)
    {
        this.appUserId = appUserId;
    }

    public Long getAppUserId()
    {
        return appUserId;
    }

    public void setProgramId(Long programId)
    {
        this.programId = programId;
    }

    public Long getProgramId()
    {
        return programId;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getSource()
    {
        return source;
    }

    public void setSourceRef(Long sourceRef)
    {
        this.sourceRef = sourceRef;
    }

    public Long getSourceRef()
    {
        return sourceRef;
    }

    public void setTierSnapshot(String tierSnapshot)
    {
        this.tierSnapshot = tierSnapshot;
    }

    public String getTierSnapshot()
    {
        return tierSnapshot;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSchoolName(String schoolName)
    {
        this.schoolName = schoolName;
    }

    public String getSchoolName()
    {
        return schoolName;
    }

    public void setProgramName(String programName)
    {
        this.programName = programName;
    }

    public String getProgramName()
    {
        return programName;
    }

    public void setCountry(String country)
    {
        this.country = country;
    }

    public String getCountry()
    {
        return country;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("planItemId", getPlanItemId())
            .append("visitorId", getVisitorId())
            .append("appUserId", getAppUserId())
            .append("programId", getProgramId())
            .append("source", getSource())
            .append("sourceRef", getSourceRef())
            .append("tierSnapshot", getTierSnapshot())
            .append("sortOrder", getSortOrder())
            .append("schoolName", getSchoolName())
            .append("programName", getProgramName())
            .append("country", getCountry())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
