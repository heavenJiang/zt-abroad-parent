package com.ygb.abroad.growth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.domain.AbrContent;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.service.IAbrContentDesignService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * content_designer 的编排任务处理器：动作 render_cards，按确定性模板渲染内容的图卡（不调用大模型）
 *
 * 任务内容：params.content_id（或事件内容 event.content_id），可选 params.cards 图卡清单（为空时按内容现有图卡重新渲染）。
 * 模板失败时改用封面模板；内容已发布、已归档或不存在时跳过。
 *
 * @author ygb
 */
@Component
public class ContentDesignerHandler implements GrowthAgentHandler
{
    static final String AGENT = "content_designer";

    static final String ACTION_RENDER = "render_cards";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private IAbrContentDesignService designService;

    @Override
    public String agentCode()
    {
        return AGENT;
    }

    @Override
    public GrowthTaskOutcome handle(AbrGrowthTask task)
    {
        if (!ACTION_RENDER.equals(task.getAction()))
        {
            return GrowthTaskOutcome.skipped("不支持的动作：" + task.getAction());
        }
        JsonNode payload = MAPPER.readTree(StringUtils.defaultIfBlank(task.getPayload(), "{}"));
        JsonNode id = payload.path("params").path("content_id");
        if (!id.isIntegralNumber())
        {
            id = payload.path("event").path("content_id");
        }
        if (!id.isIntegralNumber())
        {
            return GrowthTaskOutcome.skipped("任务内容缺少 content_id");
        }
        JsonNode cards = payload.path("params").path("cards");
        AbrContent c;
        try
        {
            c = designService.renderCards(id.asLong(), cards.isArray() ? cards.toString() : null, true, AGENT);
        }
        catch (ServiceException e)
        {
            return GrowthTaskOutcome.skipped(e.getMessage());
        }
        int fallback = 0;
        JsonNode rendered = MAPPER.readTree(StringUtils.defaultIfBlank(c.getCards(), "[]"));
        for (JsonNode n : rendered)
        {
            fallback += n.path("fallback").asBoolean(false) ? 1 : 0;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content_id", c.getContentId());
        result.put("cards", rendered.size());
        result.put("fallback", fallback);
        return GrowthTaskOutcome.done(result);
    }
}
