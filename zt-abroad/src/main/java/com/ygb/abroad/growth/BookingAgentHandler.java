package com.ygb.abroad.growth;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.service.IAbrBookingAgentService;
import com.ygb.abroad.service.IAbrMessageService;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * booking_agent 任务处理器：confirm 预约确认、notify_change 变更通知、remind 开始前提醒（定时）、invite / recommend 邀约
 *
 * @author ygb
 */
@Component
public class BookingAgentHandler implements GrowthAgentHandler
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private IAbrBookingAgentService bookingAgentService;

    @Override
    public String agentCode()
    {
        return IAbrBookingAgentService.AGENT;
    }

    @Override
    public GrowthTaskOutcome handle(AbrGrowthTask task)
    {
        JsonNode payload = MAPPER.readTree(StringUtils.defaultIfBlank(task.getPayload(), "{}"));
        JsonNode event = payload.path("event");
        JsonNode params = payload.path("params");
        switch (StringUtils.defaultString(task.getAction()))
        {
            case "confirm":
                return message(bookingAgentService.confirm(id(event, "booking_id"), task.getTaskId()));
            case "notify_change":
                return message(bookingAgentService.notifyChange(id(event, "booking_id"), event.path("change").asString(""), task.getTaskId()));
            case "remind":
                return GrowthTaskOutcome.done(bookingAgentService.remindDue(params.path("hours_before").asInt(24), task.getTaskId()));
            case "invite":
            case "recommend":
                Map<String, Object> r = bookingAgentService.invite(id(event, "lead_id"), id(payload, "app_user_id"), id(payload, "visitor_id"),
                        StringUtils.trimToNull(event.path("moment").asString("")), params.path("product").asString(""), task.getTaskId());
                String action = String.valueOf(r.get("action"));
                if ("deferred".equals(action) && r.get("deferUntil") instanceof Date until)
                {
                    return GrowthTaskOutcome.deferred(until, String.valueOf(r.get("reason")));
                }
                return "skipped".equals(action) ? GrowthTaskOutcome.skipped(String.valueOf(r.get("reason"))) : GrowthTaskOutcome.done(r);
            default:
                return GrowthTaskOutcome.skipped("不支持的动作：" + task.getAction());
        }
    }

    private static GrowthTaskOutcome message(IAbrMessageService.Result r)
    {
        if (r == null)
        {
            return GrowthTaskOutcome.skipped("不需要发送（预约不存在、状态已变或该变更不通知）");
        }
        if ("deferred".equals(r.status()) && r.deferUntil() != null)
        {
            return GrowthTaskOutcome.deferred(r.deferUntil(), r.reason());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", r.status());
        out.put("channel", r.channel());
        out.put("sendId", r.sendId());
        out.put("reason", r.reason());
        return GrowthTaskOutcome.done(out);
    }

    private static Long id(JsonNode node, String field)
    {
        JsonNode n = node.path(field);
        return n.isIntegralNumber() ? n.asLong() : null;
    }
}
