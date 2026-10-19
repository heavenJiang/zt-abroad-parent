package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrBreakerRule;
import com.ygb.abroad.domain.AbrBreakerTrip;
import com.ygb.abroad.domain.AbrEvent;
import com.ygb.abroad.domain.AbrGrowthAgent;
import com.ygb.abroad.domain.AbrGrowthSetting;
import com.ygb.abroad.domain.AbrGrowthSignal;
import com.ygb.abroad.domain.AbrTouchLog;
import com.ygb.abroad.growth.TouchPolicy;
import com.ygb.abroad.mapper.AbrBreakerRuleMapper;
import com.ygb.abroad.mapper.AbrBreakerTripMapper;
import com.ygb.abroad.mapper.AbrGrowthAgentMapper;
import com.ygb.abroad.mapper.AbrGrowthSettingMapper;
import com.ygb.abroad.mapper.AbrGrowthSignalMapper;
import com.ygb.abroad.mapper.AbrTouchLogMapper;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.abroad.service.IAbrGrowthControlService;
import com.ygb.abroad.service.IAbrGrowthGuardService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 获客执行保障 管理服务实现
 *
 * @author ygb
 */
@Service
public class AbrGrowthControlServiceImpl implements IAbrGrowthControlService
{
    private static final Set<String> MODES = Set.of(AbrGrowthAgent.MODE_AUTO, AbrGrowthAgent.MODE_AUTO_SAMPLE, AbrGrowthAgent.MODE_CONFIRM,
            AbrGrowthAgent.MODE_OFF);

    /** 员工可录入的信号类型（合规拦截 / 通过、退订由系统写入） */
    private static final Set<String> MANUAL_SIGNALS = Set.of(AbrGrowthSignal.TYPE_COMPLAINT, AbrGrowthSignal.TYPE_PLATFORM_VIOLATION,
            AbrGrowthSignal.TYPE_NEGATIVE_REPLY);

    @Autowired
    private AbrGrowthSettingMapper settingMapper;

    @Autowired
    private AbrGrowthAgentMapper agentMapper;

    @Autowired
    private AbrBreakerRuleMapper ruleMapper;

    @Autowired
    private AbrBreakerTripMapper tripMapper;

    @Autowired
    private AbrGrowthSignalMapper signalMapper;

    @Autowired
    private AbrTouchLogMapper touchMapper;

    @Autowired
    private IAbrGrowthGuardService guardService;

    @Autowired
    private IAbrEventService eventService;

    @Override
    public AbrGrowthSetting selectSetting()
    {
        AbrGrowthSetting s = settingMapper.selectAbrGrowthSetting();
        if (s != null)
        {
            BigDecimal[] all = guardService.aiUsageToday().get("");
            s.setTodayCost(all[0]);
            s.setTodayTokens(all[1].longValue());
        }
        return s;
    }

    @Override
    public int updateSetting(AbrGrowthSetting setting)
    {
        if (TouchPolicy.parseTime(setting.getQuietStart()) == null || TouchPolicy.parseTime(setting.getQuietEnd()) == null)
        {
            throw new ServiceException("免打扰时间格式为 HH:mm");
        }
        noTags(setting.getRemark());
        return settingMapper.updateAbrGrowthSetting(setting);
    }

    @Override
    public boolean pauseGlobal(String reason, String operator)
    {
        String text = reason(reason);
        if (settingMapper.pauseGlobal(text, operator) == 0)
        {
            return false;
        }
        event(AbrEvent.TYPE_GROWTH_PAUSED, "global", null, text, operator);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resumeGlobal(String operator)
    {
        if (settingMapper.resumeGlobal(operator) == 0)
        {
            return false;
        }
        tripMapper.resolveTrips(AbrBreakerRule.SCOPE_GLOBAL, null, operator);
        event(AbrEvent.TYPE_GROWTH_RESUMED, "global", null, null, operator);
        return true;
    }

    @Override
    public List<AbrGrowthAgent> selectAgentList(AbrGrowthAgent agent)
    {
        List<AbrGrowthAgent> list = agentMapper.selectAbrGrowthAgentList(agent);
        Map<String, BigDecimal[]> usage = guardService.aiUsageToday();
        Map<String, String> blocked = guardService.blockedAgents();
        for (AbrGrowthAgent a : list)
        {
            BigDecimal[] u = usage.get(a.getAgentCode());
            a.setTodayCost(u == null ? BigDecimal.ZERO : u[0]);
            a.setTodayTokens(u == null ? 0L : u[1].longValue());
            a.setBlockReason(blocked.get(a.getAgentCode()));
        }
        return list;
    }

    @Override
    public AbrGrowthAgent selectAgent(String agentCode)
    {
        AbrGrowthAgent a = agentMapper.selectAbrGrowthAgentByCode(agentCode);
        if (a != null)
        {
            BigDecimal[] u = guardService.aiUsageToday().get(agentCode);
            a.setTodayCost(u == null ? BigDecimal.ZERO : u[0]);
            a.setTodayTokens(u == null ? 0L : u[1].longValue());
            a.setBlockReason(guardService.agentBlockReason(agentCode));
        }
        return a;
    }

    @Override
    public int updateAgent(AbrGrowthAgent agent)
    {
        if (agentMapper.selectAbrGrowthAgentByCode(agent.getAgentCode()) == null)
        {
            throw new ServiceException("Agent 不存在");
        }
        if (!MODES.contains(agent.getExecMode()))
        {
            throw new ServiceException("执行模式只能是 auto / auto_sample / confirm / off");
        }
        if (AbrGrowthAgent.MODE_AUTO_SAMPLE.equals(agent.getExecMode()) && (agent.getSampleRate() == null || agent.getSampleRate().signum() <= 0))
        {
            throw new ServiceException("全自动 + 抽检须设置大于 0 的抽检比例");
        }
        noTags(agent.getRemark());
        return agentMapper.updateAbrGrowthAgent(agent);
    }

    @Override
    public boolean pauseAgent(String agentCode, String reason, String operator)
    {
        requireAgent(agentCode);
        String text = reason(reason);
        if (agentMapper.pauseAgent(agentCode, text, operator) == 0)
        {
            return false;
        }
        event(AbrEvent.TYPE_GROWTH_PAUSED, AbrBreakerRule.SCOPE_AGENT, agentCode, text, operator);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resumeAgent(String agentCode, String operator)
    {
        requireAgent(agentCode);
        if (agentMapper.resumeAgent(agentCode, operator) == 0)
        {
            return false;
        }
        tripMapper.resolveTrips(AbrBreakerRule.SCOPE_AGENT, agentCode, operator);
        event(AbrEvent.TYPE_GROWTH_RESUMED, AbrBreakerRule.SCOPE_AGENT, agentCode, null, operator);
        return true;
    }

    @Override
    public List<AbrBreakerRule> selectRuleList(AbrBreakerRule rule)
    {
        return ruleMapper.selectAbrBreakerRuleList(rule);
    }

    @Override
    public AbrBreakerRule selectRuleById(Long ruleId)
    {
        return ruleMapper.selectAbrBreakerRuleById(ruleId);
    }

    @Override
    public boolean checkRuleCodeUnique(AbrBreakerRule rule)
    {
        AbrBreakerRule exist = ruleMapper.checkRuleCodeUnique(rule.getRuleCode());
        return exist == null || exist.getRuleId().equals(rule.getRuleId());
    }

    @Override
    public int insertRule(AbrBreakerRule rule)
    {
        checkRule(rule);
        return ruleMapper.insertAbrBreakerRule(rule);
    }

    @Override
    public int updateRule(AbrBreakerRule rule)
    {
        if (rule.getRuleId() == null || ruleMapper.selectAbrBreakerRuleById(rule.getRuleId()) == null)
        {
            throw new ServiceException("熔断规则不存在");
        }
        checkRule(rule);
        return ruleMapper.updateAbrBreakerRule(rule);
    }

    @Override
    public int deleteRuleByIds(Long[] ruleIds)
    {
        return ruleMapper.deleteAbrBreakerRuleByIds(ruleIds);
    }

    @Override
    public List<AbrBreakerTrip> selectTripList(AbrBreakerTrip trip)
    {
        return tripMapper.selectAbrBreakerTripList(trip);
    }

    @Override
    public List<AbrGrowthSignal> selectSignalList(AbrGrowthSignal signal)
    {
        return signalMapper.selectAbrGrowthSignalList(signal);
    }

    @Override
    public int insertManualSignal(AbrGrowthSignal signal, String operator)
    {
        if (!MANUAL_SIGNALS.contains(signal.getSignalType()))
        {
            throw new ServiceException("员工只能录入投诉、平台违规提示、差评回复");
        }
        if (StringUtils.isNotBlank(signal.getAgentCode()))
        {
            requireAgent(signal.getAgentCode());
        }
        signal.setDetail(AbrJsonFields.requireObject(signal.getDetail(), "详情"));
        noTags(signal.getDetail());
        signal.setSource("manual");
        signal.setCreateBy(operator);
        signal.setTaskId(null);
        return signalMapper.insertAbrGrowthSignal(signal);
    }

    @Override
    public List<AbrTouchLog> selectTouchLogList(AbrTouchLog log)
    {
        return touchMapper.selectAbrTouchLogList(log);
    }

    private void checkRule(AbrBreakerRule rule)
    {
        if (!AbrPlaybookServiceImpl.dictValues("abr_breaker_metric").contains(rule.getMetric()))
        {
            throw new ServiceException("指标“" + rule.getMetric() + "”不在字典 abr_breaker_metric 中");
        }
        if (!Set.of(AbrBreakerRule.SCOPE_AGENT, AbrBreakerRule.SCOPE_ACCOUNT, AbrBreakerRule.SCOPE_GLOBAL).contains(rule.getScope()))
        {
            throw new ServiceException("范围只能是 agent（逐个 Agent）、account（逐个渠道账号）或 global（全局）");
        }
        if (AbrBreakerRule.SCOPE_ACCOUNT.equals(rule.getScope()) && AbrGrowthGuardServiceImpl.ratio(rule.getMetric()))
        {
            throw new ServiceException("按渠道账号统计只支持计数类指标（投诉、平台违规提示、差评回复）");
        }
        if (!AbrBreakerRule.SCOPE_AGENT.equals(rule.getScope()))
        {
            rule.setAgentCode(null);
        }
        else if (StringUtils.isNotBlank(rule.getAgentCode()))
        {
            requireAgent(rule.getAgentCode());
        }
        else
        {
            rule.setAgentCode(null);
        }
        if (AbrGrowthGuardServiceImpl.ratio(rule.getMetric()) && rule.getThreshold().compareTo(BigDecimal.ONE) > 0)
        {
            throw new ServiceException("比率类指标的阈值为 0–1（如 0.05 表示 5%）");
        }
        if (!AbrGrowthGuardServiceImpl.ratio(rule.getMetric()) && rule.getThreshold().stripTrailingZeros().scale() > 0)
        {
            throw new ServiceException("计数类指标的阈值须为整数");
        }
        if (StringUtils.isNotBlank(rule.getStatus()) && !"0".equals(rule.getStatus()) && !"1".equals(rule.getStatus()))
        {
            throw new ServiceException("状态只能是 0（正常）或 1（停用）");
        }
        noTags(rule.getRuleName(), rule.getRemark());
    }

    private void requireAgent(String agentCode)
    {
        if (agentMapper.selectAbrGrowthAgentByCode(agentCode) == null)
        {
            throw new ServiceException("Agent “" + agentCode + "”不存在");
        }
    }

    private static String reason(String reason)
    {
        String text = StringUtils.trimToEmpty(reason);
        if (text.isEmpty())
        {
            throw new ServiceException("请填写暂停原因");
        }
        if (text.length() > 500)
        {
            throw new ServiceException("暂停原因最多 500 个字符");
        }
        noTags(text);
        return text;
    }

    private static void noTags(String... texts)
    {
        for (String text : texts)
        {
            if (StringUtils.isNotBlank(text) && AbrAssessmentAnswers.containsTag(JsonNodeFactory.instance.stringNode(text)))
            {
                throw new ServiceException("不能包含 < 加字母的写法（类似 HTML 标签）");
            }
        }
    }

    private void event(String type, String scope, String agentCode, String reason, String operator)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", scope);
        payload.put("agent_code", agentCode);
        payload.put("reason", reason);
        payload.put("operator", operator);
        eventService.recordSystemEvent(type, null, payload);
    }
}
