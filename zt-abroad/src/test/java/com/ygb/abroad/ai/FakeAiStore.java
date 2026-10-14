package com.ygb.abroad.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import com.ygb.abroad.domain.AbrAiAgent;
import com.ygb.abroad.domain.AbrAiCallLog;
import com.ygb.abroad.domain.AbrAiReview;

/**
 * 内存版网关存储（单元测试用）
 *
 * @author ygb
 */
public class FakeAiStore implements AiStore
{
    public final Map<String, AbrAiAgent> agents = new LinkedHashMap<>();

    public final List<AbrAiCallLog> logs = new ArrayList<>();

    public final List<AbrAiReview> reviews = new ArrayList<>();

    public boolean failLogWrite;

    private long seq = 100;

    /**
     * 新增 Agent
     */
    public AbrAiAgent agent(String code, String policy)
    {
        AbrAiAgent a = new AbrAiAgent();
        a.setAgentCode(code);
        a.setAgentName(code);
        a.setReviewPolicy(policy);
        a.setSampleRate(BigDecimal.ZERO);
        a.setPrimaryModel("mock:default");
        a.setBackupModel("mock:backup");
        a.setPromptVersion("v1");
        a.setTimeoutMs(5000);
        a.setMaxOutputTokens(500);
        a.setTemperature(BigDecimal.ZERO);
        a.setStatus("0");
        agents.put(code, a);
        return a;
    }

    public AbrAiCallLog lastLog()
    {
        return logs.get(logs.size() - 1);
    }

    @Override
    public Long nextCallId()
    {
        return ++seq;
    }

    @Override
    public AbrAiAgent selectAgentByCode(String agentCode)
    {
        return agents.get(agentCode);
    }

    @Override
    public void insertCallLog(AbrAiCallLog log)
    {
        if (failLogWrite)
        {
            throw new IllegalStateException("数据库不可用");
        }
        logs.add(log);
    }

    @Override
    public Long insertReview(AbrAiReview review)
    {
        review.setReviewId((long) (reviews.size() + 1));
        reviews.add(review);
        return review.getReviewId();
    }
}
