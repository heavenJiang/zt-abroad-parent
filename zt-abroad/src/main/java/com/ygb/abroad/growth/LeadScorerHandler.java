package com.ygb.abroad.growth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.domain.AbrLead;
import com.ygb.abroad.service.IAbrLeadScoreService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * lead_scorer 的编排任务处理器（规则评分，不调用大模型）
 * <ul>
 * <li>score：新线索（事件 lead_captured，event.lead_id）、转化时刻与绑定手机号（任务的 visitor_id / app_user_id）；还没收口为线索的跳过（收口后由 lead_captured 评分）；</li>
 * <li>rescore_all：每天重算未签约、未流失的线索（活跃度随时间变化）。</li>
 * </ul>
 *
 * @author ygb
 */
@Component
public class LeadScorerHandler implements GrowthAgentHandler
{
    static final String ACTION_SCORE = "score";

    static final String ACTION_RESCORE_ALL = "rescore_all";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private IAbrLeadScoreService scoreService;

    @Override
    public String agentCode()
    {
        return IAbrLeadScoreService.AGENT;
    }

    @Override
    public GrowthTaskOutcome handle(AbrGrowthTask task)
    {
        if (ACTION_RESCORE_ALL.equals(task.getAction()))
        {
            return GrowthTaskOutcome.done(scoreService.rescoreAll(IAbrLeadScoreService.AGENT));
        }
        if (!ACTION_SCORE.equals(task.getAction()))
        {
            return GrowthTaskOutcome.skipped("不支持的动作：" + task.getAction());
        }
        JsonNode payload = MAPPER.readTree(StringUtils.defaultIfBlank(task.getPayload(), "{}"));
        AbrLead lead;
        try
        {
            JsonNode leadId = payload.path("event").path("lead_id");
            if (leadId.isIntegralNumber())
            {
                lead = scoreService.score(leadId.asLong(), IAbrLeadScoreService.AGENT);
            }
            else
            {
                Long visitorId = payload.path("visitor_id").isIntegralNumber() ? payload.path("visitor_id").asLong() : null;
                Long appUserId = payload.path("app_user_id").isIntegralNumber() ? payload.path("app_user_id").asLong() : null;
                lead = scoreService.scoreByVisitor(visitorId, appUserId, IAbrLeadScoreService.AGENT);
            }
        }
        catch (ServiceException e)
        {
            return GrowthTaskOutcome.skipped(e.getMessage());
        }
        if (lead == null)
        {
            return GrowthTaskOutcome.skipped("还没有收口为线索（收口后由 lead_captured 评分）");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lead_id", lead.getLeadId());
        result.put("score", lead.getScore());
        result.put("grade", lead.getGrade());
        return GrowthTaskOutcome.done(result);
    }
}
