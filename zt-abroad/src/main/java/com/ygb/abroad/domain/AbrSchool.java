package com.ygb.abroad.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.annotation.Excel;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 院校表 abr_school
 * 
 * @author ygb
 */
public class AbrSchool extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 院校ID */
    @Excel(name = "院校ID")
    private Long schoolId;

    /** 中文名 */
    @Excel(name = "中文名")
    private String schoolName;

    /** 英文名 */
    @Excel(name = "英文名")
    private String schoolNameEn;

    /** 国家（字典 abr_country） */
    @Excel(name = "国家")
    private String country;

    /** 城市 */
    @Excel(name = "城市")
    private String city;

    /** 榜单名次（JSON 数组） */
    private String rankings;

    /** 是否在教育部认可名单（Y是 N否） */
    @Excel(name = "教育部认可", readConverterExp = "Y=是,N=否")
    private String moeRecognized;

    /** 官网 */
    @Excel(name = "官网")
    private String website;

    /** 别名（JSON 数组） */
    private String aliases;

    /** 外部编号（JSON 对象，如 {"ipeds": "166027"}） */
    private String extIds;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    /** 项目数（列表统计，非表字段） */
    private Integer programCount;

    public void setSchoolId(Long schoolId)
    {
        this.schoolId = schoolId;
    }

    public Long getSchoolId()
    {
        return schoolId;
    }

    public void setSchoolName(String schoolName)
    {
        this.schoolName = schoolName;
    }

    @NotBlank(message = "中文名不能为空")
    @Size(max = 200, message = "中文名不能超过200个字符")
    public String getSchoolName()
    {
        return schoolName;
    }

    public void setSchoolNameEn(String schoolNameEn)
    {
        this.schoolNameEn = schoolNameEn;
    }

    @NotBlank(message = "英文名不能为空")
    @Size(max = 200, message = "英文名不能超过200个字符")
    public String getSchoolNameEn()
    {
        return schoolNameEn;
    }

    public void setCountry(String country)
    {
        this.country = country;
    }

    @NotBlank(message = "国家不能为空")
    public String getCountry()
    {
        return country;
    }

    public void setCity(String city)
    {
        this.city = city;
    }

    @Size(max = 100, message = "城市不能超过100个字符")
    public String getCity()
    {
        return city;
    }

    public void setRankings(String rankings)
    {
        this.rankings = rankings;
    }

    public String getRankings()
    {
        return rankings;
    }

    public void setMoeRecognized(String moeRecognized)
    {
        this.moeRecognized = moeRecognized;
    }

    public String getMoeRecognized()
    {
        return moeRecognized;
    }

    public void setWebsite(String website)
    {
        this.website = website;
    }

    @Size(max = 500, message = "官网链接不能超过500个字符")
    public String getWebsite()
    {
        return website;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setProgramCount(Integer programCount)
    {
        this.programCount = programCount;
    }

    public Integer getProgramCount()
    {
        return programCount;
    }

    public String getAliases()
    {
        return aliases;
    }

    public void setAliases(String aliases)
    {
        this.aliases = aliases;
    }

    public String getExtIds()
    {
        return extIds;
    }

    public void setExtIds(String extIds)
    {
        this.extIds = extIds;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("schoolId", getSchoolId())
            .append("schoolName", getSchoolName())
            .append("schoolNameEn", getSchoolNameEn())
            .append("country", getCountry())
            .append("city", getCity())
            .append("rankings", getRankings())
            .append("moeRecognized", getMoeRecognized())
            .append("website", getWebsite())
            .append("aliases", getAliases())
            .append("extIds", getExtIds())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .append("programCount", getProgramCount())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
