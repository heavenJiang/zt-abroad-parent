package com.ygb.abroad.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.annotation.Excel;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 国内高校层次表 abr_cn_university
 * 
 * @author ygb
 */
public class AbrCnUniversity extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 高校ID */
    @Excel(name = "高校ID")
    private Long cnUniversityId;

    /** 高校全称 */
    @Excel(name = "高校全称")
    private String universityName;

    /** 别名（| 分隔） */
    @Excel(name = "别名")
    private String aliases;

    /** 省份 */
    @Excel(name = "省份")
    private String province;

    /** 城市 */
    @Excel(name = "城市")
    private String city;

    /** 是否 C9（Y是 N否） */
    @Excel(name = "C9", readConverterExp = "Y=是,N=否")
    private String isC9;

    /** 是否 985（Y是 N否） */
    @Excel(name = "985", readConverterExp = "Y=是,N=否")
    private String is985;

    /** 是否 211（Y是 N否） */
    @Excel(name = "211", readConverterExp = "Y=是,N=否")
    private String is211;

    /** 是否双一流（Y是 N否） */
    @Excel(name = "双一流", readConverterExp = "Y=是,N=否")
    private String isDoubleFirstClass;

    /** 层次（字典 abr_undergrad_tier） */
    @Excel(name = "层次")
    private String tier;

    /** 校区类型（0校本部 1分校区） */
    @Excel(name = "校区", readConverterExp = "0=校本部,1=分校区")
    private String campusType;

    /** 数据来源 */
    @Excel(name = "来源")
    private String source;

    /** 说明 */
    @Excel(name = "说明")
    private String note;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    public void setCnUniversityId(Long cnUniversityId)
    {
        this.cnUniversityId = cnUniversityId;
    }

    public Long getCnUniversityId()
    {
        return cnUniversityId;
    }

    public void setUniversityName(String universityName)
    {
        this.universityName = universityName;
    }

    @NotBlank(message = "高校全称不能为空")
    @Size(max = 100, message = "高校全称不能超过100个字符")
    public String getUniversityName()
    {
        return universityName;
    }

    public void setAliases(String aliases)
    {
        this.aliases = aliases;
    }

    @Size(max = 500, message = "别名不能超过500个字符")
    public String getAliases()
    {
        return aliases;
    }

    public void setProvince(String province)
    {
        this.province = province;
    }

    public String getProvince()
    {
        return province;
    }

    public void setCity(String city)
    {
        this.city = city;
    }

    public String getCity()
    {
        return city;
    }

    public void setIsC9(String isC9)
    {
        this.isC9 = isC9;
    }

    public String getIsC9()
    {
        return isC9;
    }

    public void setIs985(String is985)
    {
        this.is985 = is985;
    }

    public String getIs985()
    {
        return is985;
    }

    public void setIs211(String is211)
    {
        this.is211 = is211;
    }

    public String getIs211()
    {
        return is211;
    }

    public void setIsDoubleFirstClass(String isDoubleFirstClass)
    {
        this.isDoubleFirstClass = isDoubleFirstClass;
    }

    public String getIsDoubleFirstClass()
    {
        return isDoubleFirstClass;
    }

    public void setTier(String tier)
    {
        this.tier = tier;
    }

    @NotBlank(message = "层次不能为空")
    public String getTier()
    {
        return tier;
    }

    public void setCampusType(String campusType)
    {
        this.campusType = campusType;
    }

    public String getCampusType()
    {
        return campusType;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getSource()
    {
        return source;
    }

    public void setNote(String note)
    {
        this.note = note;
    }

    public String getNote()
    {
        return note;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("cnUniversityId", getCnUniversityId())
            .append("universityName", getUniversityName())
            .append("aliases", getAliases())
            .append("province", getProvince())
            .append("city", getCity())
            .append("isC9", getIsC9())
            .append("is985", getIs985())
            .append("is211", getIs211())
            .append("isDoubleFirstClass", getIsDoubleFirstClass())
            .append("tier", getTier())
            .append("campusType", getCampusType())
            .append("source", getSource())
            .append("note", getNote())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
