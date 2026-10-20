package com.ygb.abroad.growth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.domain.AbrGrowthReport;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.service.IAbrGrowthAnalystService;
import com.ygb.common.exception.ServiceException;

/**
 * growth_analyst 的编排任务处理器：动作 evaluate（每天评估 A/B 实验）、weekly_report（每周一生成上一周的周报并在边界内调整内容配比）
 *
 * 周报同一周重复执行只覆盖数据与摘要，调参每周只做一次；出错时保持上周参数。
 *
 * @author ygb
 */
@Component
public class GrowthAnalystHandler implements GrowthAgentHandler
{
    static final String AGENT = "growth_analyst";

    static final String ACTION_EVALUATE = "evaluate";

    static final String ACTION_WEEKLY = "weekly_report";

    @Autowired
    private IAbrGrowthAnalystService analystService;

    @Override
    public String agentCode()
    {
        return AGENT;
    }

    @Override
    public GrowthTaskOutcome handle(AbrGrowthTask task)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        if (ACTION_EVALUATE.equals(task.getAction()))
        {
            result.put("experiments", analystService.evaluateExperiments(AGENT));
            return GrowthTaskOutcome.done(result);
        }
        if (ACTION_WEEKLY.equals(task.getAction()))
        {
            try
            {
                AbrGrowthReport r = analystService.weekly(null, true, AGENT);
                result.put("reportId", r.getReportId());
                result.put("aiStatus", r.getAiStatus());
                return GrowthTaskOutcome.done(result);
            }
            catch (ServiceException e)
            {
                return GrowthTaskOutcome.skipped(e.getMessage());
            }
        }
        return GrowthTaskOutcome.skipped("不支持的动作：" + task.getAction());
    }
}
