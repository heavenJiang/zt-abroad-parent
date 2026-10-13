package com.ygb.abroad.domain;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.annotation.Excel;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 项目表 abr_program
 * 
 * @author ygb
 */
public class AbrProgram extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 项目ID */
    @Excel(name = "项目ID")
    private Long programId;

    /** 院校ID */
    private Long schoolId;

    /** 院校中文名（关联查询） */
    @Excel(name = "院校")
    private String schoolName;

    /** 院校英文名（关联查询） */
    private String schoolNameEn;

    /** 国家（关联院校，查询条件） */
    @Excel(name = "国家")
    private String country;

    /** 中文名 */
    @Excel(name = "项目中文名")
    private String programName;

    /** 英文名 */
    @Excel(name = "项目英文名")
    private String programNameEn;

    /** 学位（字典 abr_degree） */
    @Excel(name = "学位")
    private String degree;

    /** 学科大类（字典 abr_field_group） */
    @Excel(name = "学科大类")
    private String fieldGroup;

    /** 专业方向（字典 abr_major_direction） */
    @Excel(name = "专业方向")
    private String majorDirection;

    /** 学制（月） */
    @Excel(name = "学制（月）")
    private Integer durationMonths;

    /** 入学季（字典 abr_intake，逗号分隔） */
    @Excel(name = "入学季")
    private String intakes;

    /** 学费（每年） */
    @Excel(name = "学费")
    private BigDecimal tuition;

    /** 学费币种（ISO 4217） */
    @Excel(name = "币种")
    private String currency;

    /** 授课语言（字典 abr_teaching_language） */
    @Excel(name = "授课语言")
    private String teachingLanguage;

    /** 项目官网链接 */
    @Excel(name = "官网")
    private String website;

    /** 是否需要 APS（Y是 N否） */
    @Excel(name = "需要APS", readConverterExp = "Y=是,N=否")
    private String apsRequired;

    /** 难度档 D1–D5（字典 abr_program_difficulty） */
    @Excel(name = "难度档")
    private String difficulty;

    /** 最近核对人 */
    @Excel(name = "核对人")
    private String verifiedBy;

    /** 最近核对时间 */
    @Excel(name = "核对时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date verifiedTime;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public void setProgramId(Long programId)
    {
        this.programId = programId;
    }

    public Long getProgramId()
    {
        return programId;
    }

    public void setSchoolId(Long schoolId)
    {
        this.schoolId = schoolId;
    }

    @NotNull(message = "院校不能为空")
    public Long getSchoolId()
    {
        return schoolId;
    }

    public void setSchoolName(String schoolName)
    {
        this.schoolName = schoolName;
    }

    public String getSchoolName()
    {
        return schoolName;
    }

    public void setSchoolNameEn(String schoolNameEn)
    {
        this.schoolNameEn = schoolNameEn;
    }

    public String getSchoolNameEn()
    {
        return schoolNameEn;
    }

    public void setCountry(String country)
    {
        this.country = country;
    }

    public String getCountry()
    {
        return country;
    }

    public void setProgramName(String programName)
    {
        this.programName = programName;
    }

    @Size(max = 200, message = "中文名不能超过200个字符")
    public String getProgramName()
    {
        return programName;
    }

    public void setProgramNameEn(String programNameEn)
    {
        this.programNameEn = programNameEn;
    }

    @NotBlank(message = "英文名不能为空")
    @Size(max = 300, message = "英文名不能超过300个字符")
    public String getProgramNameEn()
    {
        return programNameEn;
    }

    public void setDegree(String degree)
    {
        this.degree = degree;
    }

    @NotBlank(message = "学位不能为空")
    public String getDegree()
    {
        return degree;
    }

    public void setFieldGroup(String fieldGroup)
    {
        this.fieldGroup = fieldGroup;
    }

    @NotBlank(message = "学科大类不能为空")
    public String getFieldGroup()
    {
        return fieldGroup;
    }

    public void setMajorDirection(String majorDirection)
    {
        this.majorDirection = majorDirection;
    }

    public String getMajorDirection()
    {
        return majorDirection;
    }

    public void setDurationMonths(Integer durationMonths)
    {
        this.durationMonths = durationMonths;
    }

    @Min(value = 1, message = "学制至少1个月")
    @Max(value = 120, message = "学制不能超过120个月")
    public Integer getDurationMonths()
    {
        return durationMonths;
    }

    public void setIntakes(String intakes)
    {
        this.intakes = intakes;
    }

    public String getIntakes()
    {
        return intakes;
    }

    public void setTuition(BigDecimal tuition)
    {
        this.tuition = tuition;
    }

    @DecimalMin(value = "0", message = "学费不能为负数")
    public BigDecimal getTuition()
    {
        return tuition;
    }

    public void setCurrency(String currency)
    {
        this.currency = currency;
    }

    @Size(max = 3, message = "币种为3位代码")
    public String getCurrency()
    {
        return currency;
    }

    public void setTeachingLanguage(String teachingLanguage)
    {
        this.teachingLanguage = teachingLanguage;
    }

    public String getTeachingLanguage()
    {
        return teachingLanguage;
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

    public void setApsRequired(String apsRequired)
    {
        this.apsRequired = apsRequired;
    }

    public String getApsRequired()
    {
        return apsRequired;
    }

    public void setDifficulty(String difficulty)
    {
        this.difficulty = difficulty;
    }

    public String getDifficulty()
    {
        return difficulty;
    }

    public void setVerifiedBy(String verifiedBy)
    {
        this.verifiedBy = verifiedBy;
    }

    public String getVerifiedBy()
    {
        return verifiedBy;
    }

    public void setVerifiedTime(Date verifiedTime)
    {
        this.verifiedTime = verifiedTime;
    }

    public Date getVerifiedTime()
    {
        return verifiedTime;
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

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("programId", getProgramId())
            .append("schoolId", getSchoolId())
            .append("schoolName", getSchoolName())
            .append("schoolNameEn", getSchoolNameEn())
            .append("country", getCountry())
            .append("programName", getProgramName())
            .append("programNameEn", getProgramNameEn())
            .append("degree", getDegree())
            .append("fieldGroup", getFieldGroup())
            .append("majorDirection", getMajorDirection())
            .append("durationMonths", getDurationMonths())
            .append("intakes", getIntakes())
            .append("tuition", getTuition())
            .append("currency", getCurrency())
            .append("teachingLanguage", getTeachingLanguage())
            .append("website", getWebsite())
            .append("apsRequired", getApsRequired())
            .append("difficulty", getDifficulty())
            .append("verifiedBy", getVerifiedBy())
            .append("verifiedTime", getVerifiedTime())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
