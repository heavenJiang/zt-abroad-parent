package com.ygb.abroad.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * C 端：选校单条目
 * 
 * @author ygb
 */
public class AppPlanItemVo
{
    /** 项目ID */
    private Long programId;

    /** 院校 */
    private String schoolName;

    /** 项目 */
    private String programName;

    /** 国家 / 地区代码 */
    private String country;

    /** 国家 / 地区名称 */
    private String countryLabel;

    /** 加入时的档位（reach / match / safety / unrated 待定档 / needs_improvement 补足后可申；无为 null） */
    private String tier;

    /** 加入来源（quick / assessment / program / tool） */
    private String source;

    /** 加入时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date addedTime;

    public Long getProgramId()
    {
        return programId;
    }

    public void setProgramId(Long programId)
    {
        this.programId = programId;
    }

    public String getSchoolName()
    {
        return schoolName;
    }

    public void setSchoolName(String schoolName)
    {
        this.schoolName = schoolName;
    }

    public String getProgramName()
    {
        return programName;
    }

    public void setProgramName(String programName)
    {
        this.programName = programName;
    }

    public String getCountry()
    {
        return country;
    }

    public void setCountry(String country)
    {
        this.country = country;
    }

    public String getCountryLabel()
    {
        return countryLabel;
    }

    public void setCountryLabel(String countryLabel)
    {
        this.countryLabel = countryLabel;
    }

    public String getTier()
    {
        return tier;
    }

    public void setTier(String tier)
    {
        this.tier = tier;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public Date getAddedTime()
    {
        return addedTime;
    }

    public void setAddedTime(Date addedTime)
    {
        this.addedTime = addedTime;
    }
}
