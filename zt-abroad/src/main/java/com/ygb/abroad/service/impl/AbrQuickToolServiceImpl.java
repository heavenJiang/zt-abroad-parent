package com.ygb.abroad.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrQuestionnaire;
import com.ygb.abroad.domain.AbrToolRun;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppQuickBody;
import com.ygb.abroad.domain.vo.AppQuickFormVo;
import com.ygb.abroad.domain.vo.AppQuickResultVo;
import com.ygb.abroad.engine.BackgroundProfile;
import com.ygb.abroad.engine.DegreeStrategy;
import com.ygb.abroad.engine.SelectionEngine;
import com.ygb.abroad.mapper.AbrQuestionnaireMapper;
import com.ygb.abroad.mapper.AbrToolRunMapper;
import com.ygb.abroad.service.IAbrEngineConfigService;
import com.ygb.abroad.service.IAbrPolicyService;
import com.ygb.abroad.service.IAbrProfileService;
import com.ygb.abroad.service.IAbrQuickToolService;
import com.ygb.abroad.service.IAbrSelectionService;
import com.ygb.abroad.service.IAbrVisitorService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 30 秒快测 服务层实现
 *
 * 题目、选项与换算表取自该学段启用的问卷，与完整测评同一口径；需先同意当前启用的隐私政策版本。
 * 结果不设留资墙：不登录也返回完整的档位、代表校与关键差距。
 *
 * @author ygb
 */
@Service
public class AbrQuickToolServiceImpl implements IAbrQuickToolService
{
    /** 不下发给学生的题目字段（归一化与预填说明，内部用） */
    private static final Set<String> INTERNAL_FIELDS = Set.of("maps_to", "prefill_from");

    /** 已解析问卷的缓存上限（问卷版本不可修改，按版本ID缓存是安全的） */
    private static final int CACHE_LIMIT = 64;

    private final Map<Long, AbrAssessmentAnswers.Questionnaire> parsed = new ConcurrentHashMap<>();

    @Autowired
    private AbrQuestionnaireMapper questionnaireMapper;

    @Autowired
    private AbrToolRunMapper toolRunMapper;

    @Autowired
    private IAbrEngineConfigService engineConfigService;

    @Autowired
    private IAbrPolicyService policyService;

    @Autowired
    private IAbrProfileService profileService;

    @Autowired
    private IAbrSelectionService selectionService;

    @Autowired
    private IAbrVisitorService visitorService;

    @Override
    public AppQuickFormVo selectForm(String degree)
    {
        String d = degree(degree);
        AbrQuestionnaire questionnaire = activeQuestionnaire(d);
        AbrAssessmentAnswers.Questionnaire q = questionnaire(questionnaire);
        Set<String> open = engineConfigService.selectActiveConfigs(d).keySet();
        AppQuickFormVo vo = new AppQuickFormVo();
        vo.setQuestionnaireId(questionnaire.getQuestionnaireId());
        vo.setPolicyCode(q.policyCode());
        List<JsonNode> questions = new ArrayList<>();
        for (String id : SelectionEngine.QUICK_QUESTIONS)
        {
            JsonNode question = q.questions().get(id);
            if (question == null)
            {
                throw new ServiceException("快测暂未开放，请稍后再试");
            }
            ObjectNode copy = (ObjectNode) question.deepCopy();
            INTERNAL_FIELDS.forEach(copy::remove);
            questions.add(copy);
        }
        vo.setQuestions(questions);
        List<AppQuickFormVo.Country> countries = new ArrayList<>();
        for (JsonNode c : q.root().path("countries"))
        {
            String value = c.path("value").asString("");
            countries.add(new AppQuickFormVo.Country(value, c.path("label").asString(value), open.contains(value)));
        }
        vo.setCountries(countries);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppQuickResultVo quick(AbrVisitor visitor, AppQuickBody body)
    {
        String d = degree(body.getDegree());
        AbrQuestionnaire questionnaire = activeQuestionnaire(d);
        AbrAssessmentAnswers.Questionnaire q = questionnaire(questionnaire);
        policyService.checkConsentVersion(q.policyCode(), body.getPolicyVersion());
        ObjectNode answers = AbrQuickTool.validate(q, body.getAnswers());

        Map<String, JsonNode> map = new LinkedHashMap<>();
        answers.properties().forEach(e -> map.put(e.getKey(), e.getValue()));
        BackgroundProfile p = profileService.normalizeQuickAnswers(questionnaire.getQuestionnaireId(), map);
        IAbrSelectionService.QuickRun run = selectionService.computeQuick(p, d);

        AbrToolRun toolRun = new AbrToolRun();
        toolRun.setTool(AbrQuickTool.TOOL);
        toolRun.setVisitorId(visitor.getVisitorId());
        toolRun.setDegree(d);
        toolRun.setQuestionnaireId(questionnaire.getQuestionnaireId());
        toolRun.setInput(answers.toString());
        toolRun.setResult(AbrQuickTool.trace(run).toString());
        toolRun.setEngineVersions(versions(run).toString());
        toolRun.setChannelCode(StringUtils.isBlank(body.getChannelCode()) ? visitor.getChannelCode()
                : AbrVisitorServiceImpl.channel(body.getChannelCode()));
        toolRun.setContentCode(AbrVisitorServiceImpl.code(body.getContentCode(), ""));
        toolRun.setClientPlatform(AbrVisitorServiceImpl.platform(body.getClientPlatform()));
        toolRun.setPolicyCode(q.policyCode());
        toolRun.setPolicyVersion(body.getPolicyVersion().trim());
        toolRunMapper.insertAbrToolRun(toolRun);

        // 渐进档案：快测四题写入访客档案，之后的完整测评据此预填
        ObjectNode patch = AbrAssessmentAnswers.profilePatch(q, answers);
        if (!patch.isEmpty())
        {
            visitorService.mergeVisitorProfile(visitor.getVisitorId(), patch.toString());
        }

        Map<String, String> labels = new LinkedHashMap<>();
        q.root().path("countries").forEach(c -> labels.put(c.path("value").asString(), c.path("label").asString()));
        return AbrQuickTool.build(run, p, labels, toolRun.getToolRunId());
    }

    private static ObjectNode versions(IAbrSelectionService.QuickRun run)
    {
        ObjectNode out = AbrAssessmentAnswers.object(null);
        run.engineVersions().forEach(out::put);
        return out;
    }

    /**
     * 学段：为空按硕士；引擎尚未实现的学段不提供快测
     */
    private static String degree(String degree)
    {
        String d = StringUtils.isBlank(degree) ? AbrSelectionServiceImpl.DEFAULT_DEGREE : degree;
        DegreeStrategy strategy = DegreeStrategy.of(d);
        if (strategy == null || !strategy.implemented())
        {
            throw new ServiceException("该学段暂不支持快测，可预约顾问评估");
        }
        return d;
    }

    private AbrQuestionnaire activeQuestionnaire(String degree)
    {
        AbrQuestionnaire questionnaire = questionnaireMapper.selectActiveAbrQuestionnaireByDegree(degree);
        if (questionnaire == null)
        {
            throw new ServiceException("快测暂未开放，请稍后再试");
        }
        return questionnaire;
    }

    private AbrAssessmentAnswers.Questionnaire questionnaire(AbrQuestionnaire questionnaire)
    {
        AbrAssessmentAnswers.Questionnaire q = parsed.get(questionnaire.getQuestionnaireId());
        if (q == null)
        {
            q = AbrAssessmentAnswers.parse(questionnaire.getDefinition());
            if (parsed.size() >= CACHE_LIMIT)
            {
                parsed.clear();
            }
            parsed.put(questionnaire.getQuestionnaireId(), q);
        }
        return q;
    }
}
