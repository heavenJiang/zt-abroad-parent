package com.ygb.abroad.service.impl;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.ai.AiJson;
import com.ygb.abroad.ai.eval.AiEvalCases;
import com.ygb.abroad.domain.AbrAiEvalCase;
import com.ygb.abroad.domain.AbrAiReview;
import com.ygb.abroad.mapper.AbrAiAgentMapper;
import com.ygb.abroad.mapper.AbrAiEvalCaseMapper;
import com.ygb.abroad.mapper.AbrAiReviewMapper;
import com.ygb.abroad.service.IAbrEvalCaseService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 评测集 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrEvalCaseServiceImpl implements IAbrEvalCaseService
{
    /** 回流样例：快照字段 → 模型输出中的路径（内容类 Agent 的输出带 title、body；消息为 text） */
    static final Map<String, String> FIELD_PATHS = Map.of("title", "/title", "body", "/body", "text", "/text");

    @Autowired
    private AbrAiEvalCaseMapper evalCaseMapper;

    @Autowired
    private AbrAiAgentMapper agentMapper;

    @Autowired
    private AbrAiReviewMapper reviewMapper;

    @Override
    public List<AbrAiEvalCase> selectAbrAiEvalCaseList(AbrAiEvalCase evalCase)
    {
        return evalCaseMapper.selectAbrAiEvalCaseList(evalCase);
    }

    @Override
    public AbrAiEvalCase selectAbrAiEvalCaseById(Long caseId)
    {
        return evalCaseMapper.selectAbrAiEvalCaseById(caseId);
    }

    @Override
    public int insertAbrAiEvalCase(AbrAiEvalCase c)
    {
        requireAgent(c.getAgentCode());
        c.setSource(AbrAiEvalCase.SOURCE_MANUAL);
        c.setReviewId(null);
        c.setBadOutput(null);
        normalize(c);
        return evalCaseMapper.insertAbrAiEvalCase(c);
    }

    @Override
    public int updateAbrAiEvalCase(AbrAiEvalCase c)
    {
        AbrAiEvalCase before = c.getCaseId() == null ? null : evalCaseMapper.selectAbrAiEvalCaseById(c.getCaseId());
        if (before == null)
        {
            throw new ServiceException("样例不存在");
        }
        normalize(c);
        return evalCaseMapper.updateAbrAiEvalCase(c);
    }

    @Override
    public int deleteAbrAiEvalCaseByIds(Long[] caseIds)
    {
        return evalCaseMapper.deleteAbrAiEvalCaseByIds(caseIds);
    }

    @Override
    public Long createFromReview(AbrAiReview review, String source, String reason, String operator)
    {
        AbrAiEvalCase existing = evalCaseMapper.selectByReviewId(review.getReviewId());
        if (existing != null)
        {
            return existing.getCaseId();
        }
        JsonNode snapshot = AiJson.parseOrNull(review.getContent());
        String title = snapshot == null ? "" : snapshot.path("title").asString(snapshot.path("text").asString(""));
        String name = (AbrAiEvalCase.SOURCE_SAMPLE.equals(source) ? "抽检不合格：" : "确认拦截：") + StringUtils.substring(title, 0, 60);
        ObjectNode spec = AiEvalCases.fromFlags(name, AiJson.parseOrNull(review.getSourceInput()), AiJson.parseOrNull(review.getFlags()), FIELD_PATHS);
        AbrAiEvalCase c = new AbrAiEvalCase();
        c.setAgentCode(review.getAgentCode());
        c.setCaseName(StringUtils.substring(name, 0, 200));
        c.setSource(source);
        c.setReviewId(review.getReviewId());
        c.setSpec(spec.toString());
        c.setBadOutput(snapshot == null ? null : snapshot.toString());
        c.setStatus(AiEvalCases.runnable(spec) ? AbrAiEvalCase.STATUS_ACTIVE : AbrAiEvalCase.STATUS_DRAFT);
        c.setCreateBy(operator);
        c.setRemark(StringUtils.substring(reason, 0, 500));
        evalCaseMapper.insertAbrAiEvalCase(c);
        reviewMapper.updateEvalCase(review.getReviewId(), c.getCaseId());
        return c.getCaseId();
    }

    private void requireAgent(String agentCode)
    {
        boolean ai = agentCode != null && agentMapper.selectAbrAiAgentByCode(agentCode) != null;
        if (!ai && !AbrPlaybookServiceImpl.dictValues("abr_growth_agent").contains(agentCode))
        {
            throw new ServiceException("Agent“" + agentCode + "”不存在");
        }
    }

    /**
     * 校验定义写法；启用时须有模型输入与至少一条断言
     */
    private static void normalize(AbrAiEvalCase c)
    {
        JsonNode spec = AiJson.parseOrNull(StringUtils.defaultIfBlank(c.getSpec(), "{}"));
        List<String> errors = AiEvalCases.validate(spec);
        if (!errors.isEmpty())
        {
            throw new ServiceException("样例定义有误：" + String.join("；", errors));
        }
        c.setStatus(AbrAiEvalCase.STATUS_ACTIVE.equals(c.getStatus()) ? AbrAiEvalCase.STATUS_ACTIVE : AbrAiEvalCase.STATUS_DRAFT);
        if (AbrAiEvalCase.STATUS_ACTIVE.equals(c.getStatus()) && !AiEvalCases.runnable(spec))
        {
            throw new ServiceException("启用的样例须有模型输入 input 与至少一条断言 expect");
        }
        if (!spec.has("name"))
        {
            ((ObjectNode) spec).put("name", c.getCaseName());
        }
        c.setSpec(spec.toString());
        if (StringUtils.isNotBlank(c.getRemark()) && c.getRemark().length() > 500)
        {
            throw new ServiceException("备注最多 500 个字符");
        }
    }
}
