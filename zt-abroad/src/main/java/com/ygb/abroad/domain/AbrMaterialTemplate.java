package com.ygb.abroad.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 默认材料模板表 abr_material_template（项目没有写材料要求时，按「国家 / 地区 × 学位」使用）
 * 
 * @author ygb
 */
public class AbrMaterialTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 模板ID */
    private Long templateId;

    /** 国家 / 地区（字典 abr_country） */
    private String country;

    /** 学位（字典 abr_degree） */
    private String degree;

    /** 材料（JSON 数组：[{code, count, note}]） */
    private String items;

    /** 状态（0正常 1停用） */
    private String status;

    public Long getTemplateId()
    {
        return templateId;
    }

    public void setTemplateId(Long templateId)
    {
        this.templateId = templateId;
    }

    @NotBlank(message = "请选择国家 / 地区")
    @Size(max = 8, message = "国家 / 地区不正确")
    public String getCountry()
    {
        return country;
    }

    public void setCountry(String country)
    {
        this.country = country;
    }

    @NotBlank(message = "请选择学位")
    @Size(max = 16, message = "学位不正确")
    public String getDegree()
    {
        return degree;
    }

    public void setDegree(String degree)
    {
        this.degree = degree;
    }

    @NotBlank(message = "请填写材料")
    public String getItems()
    {
        return items;
    }

    public void setItems(String items)
    {
        this.items = items;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("templateId", getTemplateId())
            .append("country", getCountry())
            .append("degree", getDegree())
            .append("items", getItems())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
