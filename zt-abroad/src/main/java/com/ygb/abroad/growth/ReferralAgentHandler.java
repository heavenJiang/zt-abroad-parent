package com.ygb.abroad.growth;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.service.IAbrReferralService;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * referral_agent 任务处理器：attribute 推荐归因（线索收口）、assessment 有效测评判断（测评提交）、signed 签约奖励、invite_code 签约满月发推荐码
 *
 * @author ygb
 */
@Component
public class ReferralAgentHandler implements GrowthAgentHandler
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private IAbrReferralService referralService;

    @Override
    public String agentCode()
    {
        return IAbrReferralService.AGENT;
    }

    @Override
    public GrowthTaskOutcome handle(AbrGrowthTask task)
    {
        JsonNode payload = MAPPER.readTree(StringUtils.defaultIfBlank(task.getPayload(), "{}"));
        JsonNode event = payload.path("event");
        Map<String, Object> r = switch (StringUtils.defaultString(task.getAction()))
        {
            case "attribute" -> referralService.attribute(id(event, "lead_id"));
            case "assessment" -> referralService.assessmentSubmitted(id(event, "assessment_id"));
            case "signed" -> referralService.signed(id(event, "lead_id"));
            case "invite_code" -> referralService.inviteCode(id(event, "lead_id"), task.getTaskId());
            default -> null;
        };
        if (r == null)
        {
            return GrowthTaskOutcome.skipped("不支持的动作：" + task.getAction());
        }
        return "skipped".equals(r.get("action")) ? GrowthTaskOutcome.skipped(String.valueOf(r.get("reason"))) : GrowthTaskOutcome.done(r);
    }

    private static Long id(JsonNode node, String field)
    {
        JsonNode n = node.path(field);
        return n.isIntegralNumber() ? n.asLong() : null;
    }
}
