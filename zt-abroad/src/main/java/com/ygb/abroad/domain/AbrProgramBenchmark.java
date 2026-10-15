package com.ygb.abroad.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 项目录取参考线表 abr_program_benchmark
 * 
 * @author ygb
 */
public class AbrProgramBenchmark extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 参考线ID */
    private Long benchmarkId;

    /** 项目ID */
    private Long programId;

    /** 院校名（关联查询，只读） */
    private String schoolName;

    /** 项目名（关联查询，只读） */
    private String programName;

    /** 国家 / 地区（关联查询，只读；也用作查询条件） */
    private String country;

    /** 项目难度档（关联查询，只读） */
    private String difficulty;

    /** 参考线 T（0–100） */
    private BigDecimal refLine;

    /** 稳定度 a（为空时用国家默认值） */
    private BigDecimal stability;

    /** 来源（字典 abr_benchmark_source） */
    private String source;

    /** 样本数 */
    private Integer sampleSize;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public void setBenchmarkId(Long benchmarkId)
    {
        this.benchmarkId = benchmarkId;
    }

    public Long getBenchmarkId()
    {
        return benchmarkId;
    }

    public void setProgramId(Long programId)
    {
        this.programId = programId;
    }

    @NotNull(message = "项目不能为空")
    public Long getProgramId()
    {
        return programId;
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

    public void setDifficulty(String difficulty)
    {
        this.difficulty = difficulty;
    }

    public String getDifficulty()
    {
        return difficulty;
    }

    public void setRefLine(BigDecimal refLine)
    {
        this.refLine = refLine;
    }

    @NotNull(message = "参考线 T 不能为空")
    @DecimalMin(value = "0", message = "参考线 T 应在 0–100 之间")
    @DecimalMax(value = "100", message = "参考线 T 应在 0–100 之间")
    public BigDecimal getRefLine()
    {
        return refLine;
    }

    public void setStability(BigDecimal stability)
    {
        this.stability = stability;
    }

    @DecimalMin(value = "0.0001", message = "稳定度 a 应大于 0")
    @DecimalMax(value = "5", message = "稳定度 a 不能大于 5")
    public BigDecimal getStability()
    {
        return stability;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    @NotBlank(message = "来源不能为空")
    public String getSource()
    {
        return source;
    }

    public void setSampleSize(Integer sampleSize)
    {
        this.sampleSize = sampleSize;
    }

    @Min(value = 0, message = "样本数不能为负")
    public Integer getSampleSize()
    {
        return sampleSize;
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
            .append("benchmarkId", getBenchmarkId())
            .append("programId", getProgramId())
            .append("schoolName", getSchoolName())
            .append("programName", getProgramName())
            .append("country", getCountry())
            .append("difficulty", getDifficulty())
            .append("refLine", getRefLine())
            .append("stability", getStability())
            .append("source", getSource())
            .append("sampleSize", getSampleSize())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
