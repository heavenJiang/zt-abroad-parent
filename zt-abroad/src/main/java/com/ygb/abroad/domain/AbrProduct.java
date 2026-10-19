package com.ygb.abroad.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 产品与报价 abr_product（degrees、countries 为 JSON 数组文本；价格可为空）
 *
 * @author ygb
 */
public class AbrProduct extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 类别：服务（确认收款后线索进入签约） */
    public static final String CATEGORY_SERVICE = "service";

    /** 产品ID */
    private Long productId;

    /** 产品编码 */
    @NotBlank(message = "产品编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$", message = "产品编码只能用小写字母、数字和下划线，以字母开头，2–64 位")
    private String productCode;

    /** 产品名称 */
    @NotBlank(message = "产品名称不能为空")
    @Size(max = 100, message = "产品名称最多 100 个字")
    private String productName;

    /** 类别 */
    private String category;

    /** 适用学段（JSON 数组） */
    private String degrees;

    /** 适用国家 / 地区（JSON 数组） */
    private String countries;

    /** 介绍 */
    @Size(max = 1000, message = "介绍最多 1000 个字")
    private String description;

    /** 价格下限 */
    private BigDecimal priceMin;

    /** 价格上限 */
    private BigDecimal priceMax;

    /** 币种 */
    private String currency;

    /** 价格说明 */
    @Size(max = 200, message = "价格说明最多 200 个字")
    private String priceNote;

    /** 是否可在线预约（Y / N） */
    private String bookable;

    /** 预约时长（分钟） */
    private Integer durationMinutes;

    /** 显示顺序 */
    private Integer sortOrder;

    /** 状态 */
    private String status;

    public Long getProductId()
    {
        return productId;
    }

    public void setProductId(Long productId)
    {
        this.productId = productId;
    }

    public String getProductCode()
    {
        return productCode;
    }

    public void setProductCode(String productCode)
    {
        this.productCode = productCode;
    }

    public String getProductName()
    {
        return productName;
    }

    public void setProductName(String productName)
    {
        this.productName = productName;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getDegrees()
    {
        return degrees;
    }

    public void setDegrees(String degrees)
    {
        this.degrees = degrees;
    }

    public String getCountries()
    {
        return countries;
    }

    public void setCountries(String countries)
    {
        this.countries = countries;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public BigDecimal getPriceMin()
    {
        return priceMin;
    }

    public void setPriceMin(BigDecimal priceMin)
    {
        this.priceMin = priceMin;
    }

    public BigDecimal getPriceMax()
    {
        return priceMax;
    }

    public void setPriceMax(BigDecimal priceMax)
    {
        this.priceMax = priceMax;
    }

    public String getCurrency()
    {
        return currency;
    }

    public void setCurrency(String currency)
    {
        this.currency = currency;
    }

    public String getPriceNote()
    {
        return priceNote;
    }

    public void setPriceNote(String priceNote)
    {
        this.priceNote = priceNote;
    }

    public String getBookable()
    {
        return bookable;
    }

    public void setBookable(String bookable)
    {
        this.bookable = bookable;
    }

    public Integer getDurationMinutes()
    {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes)
    {
        this.durationMinutes = durationMinutes;
    }

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
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
            .append("productId", getProductId())
            .append("productCode", getProductCode())
            .append("productName", getProductName())
            .append("category", getCategory())
            .append("degrees", getDegrees())
            .append("countries", getCountries())
            .append("description", getDescription())
            .append("priceMin", getPriceMin())
            .append("priceMax", getPriceMax())
            .append("currency", getCurrency())
            .append("priceNote", getPriceNote())
            .append("bookable", getBookable())
            .append("durationMinutes", getDurationMinutes())
            .append("sortOrder", getSortOrder())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}
