package com.ygb.abroad.growth;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.service.IAbrNurtureService;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * nurture_agent 任务处理器：enroll 入组（参数 sequence；对象取事件的 lead_id，没有时取学生账号或访客）、run 推进到期的入组（定时）
 *
 * @author ygb
 */
@Component
public class NurtureAgentHandler implements GrowthAgentHandler
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private IAbrNurtureService nurtureService;

    @Override
    public String agentCode()
    {
        return IAbrNurtureService.AGENT;
    }

    @Override
    public GrowthTaskOutcome handle(AbrGrowthTask task)
    {
        JsonNode payload = MAPPER.readTree(StringUtils.defaultIfBlank(task.getPayload(), "{}"));
        switch (StringUtils.defaultString(task.getAction()))
        {
            case "enroll":
                String sequence = payload.path("params").path("sequence").asString("");
                String type;
                Long id;
                if (payload.path("event").path("lead_id").isIntegralNumber())
                {
                    type = "lead";
                    id = payload.path("event").path("lead_id").asLong();
                }
                else if (payload.path("app_user_id").isIntegralNumber())
                {
                    type = "app_user";
                    id = payload.path("app_user_id").asLong();
                }
                else if (payload.path("visitor_id").isIntegralNumber())
                {
                    type = "visitor";
                    id = payload.path("visitor_id").asLong();
                }
                else
                {
                    return GrowthTaskOutcome.skipped("任务内容缺少入组对象");
                }
                Long enrollmentId = nurtureService.enroll(sequence, type, id);
                if (enrollmentId == null)
                {
                    return GrowthTaskOutcome.skipped("序列不存在、已停用、没有步骤，或对象已入组过：" + sequence);
                }
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("enrollmentId", enrollmentId);
                r.put("sequence", sequence);
                r.put("targetType", type);
                r.put("targetId", id);
                return GrowthTaskOutcome.done(r);
            case "run":
                return GrowthTaskOutcome.done(nurtureService.runDue(playbooks(task.getPlaybookCodes()), task.getTaskId()));
            default:
                return GrowthTaskOutcome.skipped("不支持的动作：" + task.getAction());
        }
    }

    private static Set<String> playbooks(String json)
    {
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode n : MAPPER.readTree(StringUtils.defaultIfBlank(json, "[]")))
        {
            if (n.isString())
            {
                out.add(n.asString());
            }
        }
        return out;
    }
}
