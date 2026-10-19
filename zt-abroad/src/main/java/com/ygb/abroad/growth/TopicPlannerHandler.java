package com.ygb.abroad.growth;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.domain.AbrEvent;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.service.IAbrTopicService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * topic_planner 的编排任务处理器：动作 plan_topics（每天定时、剧本切换）、node_topics（日历节点），为各账号补齐未来若干天的选题
 *
 * 剧本切换（事件 playbook_switched）时先取消旧剧本还没写稿的 AI 与备用选题再重排；日历节点已作为输入参与每次排期，节点当天只补空位。
 * 只补空位，重复执行不会重复排；正在排期时稍后重试。
 *
 * @author ygb
 */
@Component
public class TopicPlannerHandler implements GrowthAgentHandler
{
    static final String AGENT = "topic_planner";

    static final String ACTION_PLAN = "plan_topics";

    static final String ACTION_NODE = "node_topics";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private IAbrTopicService topicService;

    @Override
    public String agentCode()
    {
        return AGENT;
    }

    @Override
    public GrowthTaskOutcome handle(AbrGrowthTask task)
    {
        if (!ACTION_PLAN.equals(task.getAction()) && !ACTION_NODE.equals(task.getAction()))
        {
            return GrowthTaskOutcome.skipped("不支持的动作：" + task.getAction());
        }
        JsonNode payload = MAPPER.readTree(StringUtils.defaultIfBlank(task.getPayload(), "{}"));
        boolean replan = AbrEvent.TYPE_PLAYBOOK_SWITCHED.equals(payload.path("event_type").asString(""));
        Map<String, Object> result;
        try
        {
            result = topicService.plan(null, replan, AGENT);
        }
        catch (ServiceException e)
        {
            // 正在排期或 Agent 暂停：稍后重试
            return GrowthTaskOutcome.retry(e.getMessage());
        }
        return GrowthTaskOutcome.done(result);
    }
}
