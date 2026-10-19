package com.ygb.abroad.growth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.domain.AbrContent;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.growth.compliance.ComplianceChecker;
import com.ygb.abroad.service.IAbrComplianceService;
import com.ygb.abroad.service.IAbrContentService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * compliance_checker 的编排任务处理器：动作 check_content，对待过闸门的内容做合规检查并写入闸门结果（确定性规则，不调用大模型）
 *
 * 任务内容：params.content_id 或事件内容 event.content_id（content_gate_requested 事件，内容新增或改稿后写入）。
 * 内容已检查过（闸门结果不是未检查）、已发布、已归档或不存在时跳过，重复执行不会覆盖员工的审核结果。
 *
 * @author ygb
 */
@Component
public class ComplianceCheckerHandler implements GrowthAgentHandler
{
    static final String ACTION_CHECK = "check_content";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private IAbrComplianceService complianceService;

    @Autowired
    private IAbrContentService contentService;

    @Override
    public String agentCode()
    {
        return ComplianceChecker.AGENT;
    }

    @Override
    public GrowthTaskOutcome handle(AbrGrowthTask task)
    {
        if (!ACTION_CHECK.equals(task.getAction()))
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
        AbrContent before = contentService.selectAbrContentById(id.asLong());
        if (before == null)
        {
            return GrowthTaskOutcome.skipped("内容不存在");
        }
        if (!AbrContent.GATE_PENDING.equals(before.getGateStatus()))
        {
            return GrowthTaskOutcome.skipped("内容已过闸门：" + before.getGateStatus());
        }
        AbrContent c;
        try
        {
            c = complianceService.checkContent(before.getContentId(), ComplianceChecker.AGENT);
        }
        catch (ServiceException e)
        {
            return GrowthTaskOutcome.skipped(e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content_id", c.getContentId());
        result.put("gate_status", c.getGateStatus());
        result.put("blocked", MAPPER.readTree(StringUtils.defaultIfBlank(c.getGateResult(), "{}")).path("blocked").size());
        return GrowthTaskOutcome.done(result);
    }
}
