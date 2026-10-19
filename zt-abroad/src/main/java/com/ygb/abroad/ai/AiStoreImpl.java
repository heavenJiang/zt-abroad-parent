package com.ygb.abroad.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrAiAgent;
import com.ygb.abroad.domain.AbrAiEvalCase;
import com.ygb.abroad.domain.AbrAiCallLog;
import com.ygb.abroad.domain.AbrAiReview;
import com.ygb.abroad.mapper.AbrAiAgentMapper;
import com.ygb.abroad.mapper.AbrAiCallLogMapper;
import com.ygb.abroad.mapper.AbrAiEvalCaseMapper;
import com.ygb.abroad.mapper.AbrAiReviewMapper;
import tools.jackson.databind.JsonNode;

/**
 * 网关持久化：调用日志与审核记录在独立事务中写入，调用方事务回滚也保留
 *
 * @author ygb
 */
@Component
public class AiStoreImpl implements AiStore
{
    @Autowired
    private AbrAiAgentMapper agentMapper;

    @Autowired
    private AbrAiCallLogMapper callLogMapper;

    @Autowired
    private AbrAiReviewMapper reviewMapper;

    @Autowired
    private AbrAiEvalCaseMapper evalCaseMapper;

    /**
     * 预取调用ID
     *
     * @return 调用ID
     */
    @Override
    public Long nextCallId()
    {
        return callLogMapper.selectNextCallId();
    }

    /**
     * 按编码查询 Agent
     *
     * @param agentCode Agent 编码
     * @return Agent
     */
    @Override
    public AbrAiAgent selectAgentByCode(String agentCode)
    {
        return agentMapper.selectAbrAiAgentByCode(agentCode);
    }

    /**
     * 写调用日志（独立事务）
     *
     * @param log 调用日志
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertCallLog(AbrAiCallLog log)
    {
        callLogMapper.insertAbrAiCallLog(log);
    }

    /**
     * 写审核记录（独立事务）
     *
     * @param review 审核记录
     * @return 审核ID
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long insertReview(AbrAiReview review)
    {
        reviewMapper.insertAbrAiReview(review);
        return review.getReviewId();
    }

    /**
     * 数据库评测集中启用的样例（定义不是 JSON 对象的跳过）
     *
     * @param agentCode Agent 编码
     * @return 样例名 → 样例定义
     */
    @Override
    public List<Map.Entry<String, JsonNode>> selectEvalCases(String agentCode)
    {
        List<Map.Entry<String, JsonNode>> list = new ArrayList<>();
        for (AbrAiEvalCase c : evalCaseMapper.selectActiveByAgent(agentCode))
        {
            JsonNode spec = AiJson.parseOrNull(c.getSpec());
            if (spec != null && spec.isObject())
            {
                list.add(Map.entry("db-" + c.getCaseId(), spec));
            }
        }
        return list;
    }
}
