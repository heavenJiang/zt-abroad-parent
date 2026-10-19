package com.ygb.abroad.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 评测集样例 abr_ai_eval_case（spec、badOutput 为 JSON 文本）
 *
 * @author ygb
 */
public class AbrAiEvalCase extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 来源：手工 */
    public static final String SOURCE_MANUAL = "manual";

    /** 来源：抽检不合格 */
    public static final String SOURCE_SAMPLE = "sample";

    /** 来源：确认拦截 */
    public static final String SOURCE_EXCEPTION = "exception";

    /** 状态：启用 */
    public static final String STATUS_ACTIVE = "0";

    /** 状态：待完善 */
    public static final String STATUS_DRAFT = "1";

    /** 样例ID */
    private Long caseId;

    /** Agent 编码 */
    @NotBlank(message = "Agent 不能为空")
    private String agentCode;

    /** 样例名称 */
    @NotBlank(message = "样例名称不能为空")
    @Size(max = 200, message = "样例名称最多 200 个字符")
    private String caseName;

    /** 来源 */
    private String source;

    /** 来源的队列记录 */
    private Long reviewId;

    /** 样例定义（JSON 对象） */
    private String spec;

    /** 不合格输出（JSON） */
    private String badOutput;

    /** 状态（0 启用 / 1 待完善） */
    private String status;

    public Long getCaseId()
    {
        return caseId;
    }

    public void setCaseId(Long caseId)
    {
        this.caseId = caseId;
    }

    public String getAgentCode()
    {
        return agentCode;
    }

    public void setAgentCode(String agentCode)
    {
        this.agentCode = agentCode;
    }

    public String getCaseName()
    {
        return caseName;
    }

    public void setCaseName(String caseName)
    {
        this.caseName = caseName;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public Long getReviewId()
    {
        return reviewId;
    }

    public void setReviewId(Long reviewId)
    {
        this.reviewId = reviewId;
    }

    public String getSpec()
    {
        return spec;
    }

    public void setSpec(String spec)
    {
        this.spec = spec;
    }

    public String getBadOutput()
    {
        return badOutput;
    }

    public void setBadOutput(String badOutput)
    {
        this.badOutput = badOutput;
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
            .append("caseId", getCaseId())
            .append("agentCode", getAgentCode())
            .append("caseName", getCaseName())
            .append("source", getSource())
            .append("reviewId", getReviewId())
            .append("spec", getSpec())
            .append("badOutput", getBadOutput())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
