package com.ygb.abroad.growth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.domain.AbrLeadCard;
import com.ygb.abroad.service.IAbrLeadCardService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * lead_card 的编排任务处理器：动作 generate（线索升到 A / B 时，事件 lead_grade_changed 的 lead_id；或任务参数 lead_id）
 *
 * 模型输入没有变化时不重复调用模型；模型失败时卡片只有基础信息。
 *
 * @author ygb
 */
@Component
public class LeadCardHandler implements GrowthAgentHandler
{
    static final String ACTION_GENERATE = "generate";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private IAbrLeadCardService cardService;

    @Override
    public String agentCode()
    {
        return IAbrLeadCardService.AGENT;
    }

    @Override
    public GrowthTaskOutcome handle(AbrGrowthTask task)
    {
        if (!ACTION_GENERATE.equals(task.getAction()))
        {
            return GrowthTaskOutcome.skipped("不支持的动作：" + task.getAction());
        }
        JsonNode payload = MAPPER.readTree(StringUtils.defaultIfBlank(task.getPayload(), "{}"));
        JsonNode id = payload.path("params").path("lead_id");
        if (!id.isIntegralNumber())
        {
            id = payload.path("event").path("lead_id");
        }
        if (!id.isIntegralNumber())
        {
            return GrowthTaskOutcome.skipped("任务内容缺少 lead_id");
        }
        String trigger = payload.path("params").path("trigger").asString("grade");
        AbrLeadCard card;
        try
        {
            card = cardService.generate(id.asLong(), trigger, false, IAbrLeadCardService.AGENT);
        }
        catch (ServiceException e)
        {
            return GrowthTaskOutcome.skipped(e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lead_id", card.getLeadId());
        result.put("card_status", card.getCardStatus());
        result.put("ai_status", card.getAiStatus());
        return GrowthTaskOutcome.done(result);
    }
}
