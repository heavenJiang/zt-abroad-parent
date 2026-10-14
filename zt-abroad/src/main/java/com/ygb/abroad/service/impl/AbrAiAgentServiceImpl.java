package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.ai.AiConstants;
import com.ygb.abroad.ai.AiContext;
import com.ygb.abroad.ai.AiGateway;
import com.ygb.abroad.ai.AiPromptLoader;
import com.ygb.abroad.ai.AiResult;
import com.ygb.abroad.ai.provider.AiProviderRegistry;
import com.ygb.abroad.domain.AbrAiAgent;
import com.ygb.abroad.domain.vo.AbrAiTrialBody;
import com.ygb.abroad.mapper.AbrAiAgentMapper;
import com.ygb.abroad.service.IAbrAiAgentService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;

/**
 * AI Agent 配置 服务层处理
 *
 * @author ygb
 */
@Service
public class AbrAiAgentServiceImpl implements IAbrAiAgentService
{
    @Autowired
    private AbrAiAgentMapper agentMapper;

    @Autowired
    private AiPromptLoader promptLoader;

    @Autowired
    private AiProviderRegistry registry;

    @Autowired
    private AiGateway gateway;

    /**
     * 查询 Agent 列表（含提示词目录是否完整）
     *
     * @param agent 查询条件
     * @return Agent 集合
     */
    @Override
    public List<AbrAiAgent> selectAbrAiAgentList(AbrAiAgent agent)
    {
        List<AbrAiAgent> list = agentMapper.selectAbrAiAgentList(agent);
        for (AbrAiAgent a : list)
        {
            a.setPromptAvailable(promptLoader.exists(a.getAgentCode(), a.getPromptVersion()));
        }
        return list;
    }

    /**
     * 按ID查询 Agent
     *
     * @param agentId Agent ID
     * @return Agent
     */
    @Override
    public AbrAiAgent selectAbrAiAgentById(Long agentId)
    {
        AbrAiAgent agent = agentMapper.selectAbrAiAgentById(agentId);
        if (agent != null)
        {
            agent.setPromptAvailable(promptLoader.exists(agent.getAgentCode(), agent.getPromptVersion()));
        }
        return agent;
    }

    /**
     * 修改 Agent 配置
     *
     * @param agent Agent
     * @return 影响行数
     */
    @Override
    public int updateAbrAiAgent(AbrAiAgent agent)
    {
        AbrAiAgent old = agentMapper.selectAbrAiAgentById(agent.getAgentId());
        if (old == null)
        {
            throw new ServiceException("Agent 不存在");
        }
        if (StringUtils.isNotEmpty(agent.getAgentCode()) && !old.getAgentCode().equals(agent.getAgentCode()))
        {
            throw new ServiceException("Agent 编码不能修改");
        }
        if (!AiConstants.REVIEW_POLICIES.contains(agent.getReviewPolicy()))
        {
            throw new ServiceException("审核策略 '" + agent.getReviewPolicy() + "' 不存在");
        }
        if (AiConstants.POLICY_DIRECT_SAMPLE.equals(agent.getReviewPolicy())
                && (agent.getSampleRate() == null || agent.getSampleRate().compareTo(BigDecimal.ZERO) <= 0))
        {
            throw new ServiceException("审核策略为直接展示 + 抽检时，抽检比例应大于 0");
        }
        agent.setPrimaryModel(StringUtils.trim(agent.getPrimaryModel()));
        agent.setBackupModel(StringUtils.trim(agent.getBackupModel()));
        String error = registry.check(agent.getPrimaryModel());
        if (error != null)
        {
            throw new ServiceException("主模型：" + error);
        }
        if (StringUtils.isNotEmpty(agent.getBackupModel()))
        {
            error = registry.check(agent.getBackupModel());
            if (error != null)
            {
                throw new ServiceException("备用模型：" + error);
            }
        }
        agent.setPromptVersion(StringUtils.trim(agent.getPromptVersion()));
        if (!agent.getPromptVersion().matches("^[A-Za-z0-9_\\-]{1,32}$"))
        {
            throw new ServiceException("提示词版本只能包含字母、数字、下划线、中划线");
        }
        // 切换版本时，新版本目录必须完整（未切换的允许暂缺，业务 Agent 的提示词由各自任务卡补齐）
        if (!agent.getPromptVersion().equals(old.getPromptVersion()) && !promptLoader.exists(old.getAgentCode(), agent.getPromptVersion()))
        {
            throw new ServiceException("提示词目录 prompts/" + old.getAgentCode() + "/" + agent.getPromptVersion() + "/ 不存在或不完整");
        }
        if (!"0".equals(agent.getStatus()) && !"1".equals(agent.getStatus()))
        {
            agent.setStatus(old.getStatus());
        }
        return agentMapper.updateAbrAiAgent(agent);
    }

    /**
     * 试用 Agent
     *
     * @param agentCode Agent 编码
     * @param body 输入、姓名、事实
     * @param operator 操作人
     * @return 网关结果
     */
    @Override
    public AiResult trialAbrAiAgent(String agentCode, AbrAiTrialBody body, String operator)
    {
        Map<String, Object> input = body == null || body.getInput() == null ? new LinkedHashMap<>() : body.getInput();
        AiContext context = AiContext.of(AiConstants.BIZ_TRIAL, "", operator);
        context.setTrial(true);
        if (body != null)
        {
            context.setKnownNames(body.getKnownNames());
            context.setFacts(body.getFacts());
        }
        return gateway.invoke(agentCode, input, context);
    }
}
