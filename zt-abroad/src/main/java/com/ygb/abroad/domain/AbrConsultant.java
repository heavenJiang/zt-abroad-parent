package com.ygb.abroad.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 参与线索分配的顾问 abr_consultant（countries、degrees 为 JSON 数组文本）
 *
 * @author ygb
 */
public class AbrConsultant extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** ID */
    private Long consultantId;

    /** 员工 */
    private Long userId;

    /** 员工姓名（查询带出） */
    private String nickName;

    /** 擅长国家 / 地区（JSON 数组） */
    private String countries;

    /** 擅长学段（JSON 数组） */
    private String degrees;

    /** 负责上限 */
    private Integer maxActive;

    /** 是否接单（Y / N） */
    private String accepting;

    /** 最近一次被自动分配的时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastAssignedTime;

    /** 进行中线索数（查询带出） */
    private Integer activeCount;

    public Long getConsultantId()
    {
        return consultantId;
    }

    public void setConsultantId(Long consultantId)
    {
        this.consultantId = consultantId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getNickName()
    {
        return nickName;
    }

    public void setNickName(String nickName)
    {
        this.nickName = nickName;
    }

    public String getCountries()
    {
        return countries;
    }

    public void setCountries(String countries)
    {
        this.countries = countries;
    }

    public String getDegrees()
    {
        return degrees;
    }

    public void setDegrees(String degrees)
    {
        this.degrees = degrees;
    }

    public Integer getMaxActive()
    {
        return maxActive;
    }

    public void setMaxActive(Integer maxActive)
    {
        this.maxActive = maxActive;
    }

    public String getAccepting()
    {
        return accepting;
    }

    public void setAccepting(String accepting)
    {
        this.accepting = accepting;
    }

    public Date getLastAssignedTime()
    {
        return lastAssignedTime;
    }

    public void setLastAssignedTime(Date lastAssignedTime)
    {
        this.lastAssignedTime = lastAssignedTime;
    }

    public Integer getActiveCount()
    {
        return activeCount;
    }

    public void setActiveCount(Integer activeCount)
    {
        this.activeCount = activeCount;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("consultantId", getConsultantId())
            .append("userId", getUserId())
            .append("nickName", getNickName())
            .append("countries", getCountries())
            .append("degrees", getDegrees())
            .append("maxActive", getMaxActive())
            .append("accepting", getAccepting())
            .append("lastAssignedTime", getLastAssignedTime())
            .append("activeCount", getActiveCount())
            .toString();
    }
}
