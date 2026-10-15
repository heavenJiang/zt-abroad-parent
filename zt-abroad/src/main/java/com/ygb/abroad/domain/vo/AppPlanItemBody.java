package com.ygb.abroad.domain.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * C 端：加入选校单的请求体
 * 
 * @author ygb
 */
public class AppPlanItemBody
{
    /** 项目ID */
    private Long programId;

    /** 来源快测的运行ID（从快测结果加入时带上，档位由后端从该记录取得） */
    private Long toolRunId;

    @NotNull(message = "缺少项目")
    @Positive(message = "项目不正确")
    public Long getProgramId()
    {
        return programId;
    }

    public void setProgramId(Long programId)
    {
        this.programId = programId;
    }

    @Positive(message = "快测记录不正确")
    public Long getToolRunId()
    {
        return toolRunId;
    }

    public void setToolRunId(Long toolRunId)
    {
        this.toolRunId = toolRunId;
    }
}
