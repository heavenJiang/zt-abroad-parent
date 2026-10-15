package com.ygb.abroad.service.impl;

import java.time.LocalDate;
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
import com.ygb.abroad.domain.vo.AppIeltsVo;
import com.ygb.abroad.domain.vo.AppOpenProgramsVo;
import com.ygb.abroad.domain.vo.AppQuickFormVo;
import com.ygb.abroad.domain.vo.AppRecognitionVo;
import com.ygb.abroad.domain.vo.AppToolBody;
import com.ygb.abroad.engine.BackgroundProfile;
import com.ygb.abroad.engine.DegreeStrategy;
import com.ygb.abroad.mapper.AbrQuestionnaireMapper;
import com.ygb.abroad.mapper.AbrToolRunMapper;
import com.ygb.abroad.service.IAbrEngineConfigService;
import com.ygb.abroad.service.IAbrLookupToolService;
import com.ygb.abroad.service.IAbrPolicyService;
import com.ygb.abroad.service.IAbrProfileService;
import com.ygb.abroad.service.IAbrSelectionService;
import com.ygb.abroad.service.IAbrVisitorService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 查询类入口工具 服务层实现
 *
 * 本校认可度、雅思够不够按已开放（有启用引擎配置）的国家 / 地区计算，口径与引擎硬门槛一致；现在还能申只取院校库截止日，不依赖引擎配置。
 * 本校认可度、雅思够不够采集个人信息，须同意当前隐私政策版本；现在还能申不采集个人信息，不要求同意，也不写访客档案。
 *
 * @author ygb
 */
@Service
public class AbrLookupToolServiceImpl implements IAbrLookupToolService
{
    /** 不下发给学生的题目字段 */
    private static final Set<String> INTERNAL_FIELDS = Set.of("maps_to", "prefill_from");

    /** 不在输入页展示的题目（由工具自动填写） */
    private static final Set<String> HIDDEN = Set.of("english_status");

    /** 已解析问卷的缓存上限 */
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
    public AppQuickFormVo selectForm(String tool, String degree)
    {
        List<String> ids = questions(tool);
        String d = degree(degree);
        AbrQuestionnaire questionnaire = activeQuestionnaire(d);
        AbrAssessmentAnswers.Questionnaire q = questionnaire(questionnaire);
        Set<String> open = AbrLookupTools.OPEN.equals(tool) ? null : engineConfigService.selectActiveConfigs(d).keySet();
        AppQuickFormVo vo = new AppQuickFormVo();
        vo.setQuestionnaireId(questionnaire.getQuestionnaireId());
        vo.setPolicyCode(AbrLookupTools.OPEN.equals(tool) ? null : q.policyCode());
        List<JsonNode> list = new ArrayList<>();
        for (String id : ids)
        {
            JsonNode question = q.questions().get(id);
            if (question == null)
            {
                throw new ServiceException("该工具暂未开放，请稍后再试");
            }
            if (!HIDDEN.contains(id))
            {
                ObjectNode copy = (ObjectNode) question.deepCopy();
                INTERNAL_FIELDS.forEach(copy::remove);
                list.add(copy);
            }
        }
        vo.setQuestions(list);
        List<AppQuickFormVo.Country> countries = new ArrayList<>();
        for (JsonNode c : q.root().path("countries"))
        {
            String value = c.path("value").asString("");
            countries.add(new AppQuickFormVo.Country(value, c.path("label").asString(value), open == null || open.contains(value)));
        }
        vo.setCountries(countries);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Object run(String tool, AbrVisitor visitor, AppToolBody body)
    {
        List<String> ids = questions(tool);
        String d = degree(body.getDegree());
        AbrQuestionnaire questionnaire = activeQuestionnaire(d);
        AbrAssessmentAnswers.Questionnaire q = questionnaire(questionnaire);
        boolean personal = !AbrLookupTools.OPEN.equals(tool);
        if (personal)
        {
            if (StringUtils.isBlank(body.getPolicyVersion()))
            {
                throw new ServiceException("请先同意隐私说明");
            }
            policyService.checkConsentVersion(q.policyCode(), body.getPolicyVersion());
        }
        ObjectNode answers = switch (tool)
        {
            case AbrLookupTools.RECOGNITION -> AbrLookupTools.validateRecognition(body.getAnswers());
            case AbrLookupTools.IELTS -> AbrLookupTools.validateIelts(q, body.getAnswers());
            default -> AbrLookupTools.validateOpen(q, body.getAnswers());
        };
        Map<String, JsonNode> map = new LinkedHashMap<>();
        answers.properties().forEach(e -> map.put(e.getKey(), e.getValue()));
        BackgroundProfile p = profileService.normalizeToolAnswers(questionnaire.getQuestionnaireId(), map, ids);
        Map<String, String> labels = new LinkedHashMap<>();
        q.root().path("countries").forEach(c -> labels.put(c.path("value").asString(), c.path("label").asString()));
        LocalDate today = LocalDate.now(AbrLookupTools.BEIJING);

        Object result;
        Map<Long, String> outcomes = new LinkedHashMap<>();
        IAbrSelectionService.CatalogSlice slice;
        switch (tool)
        {
            case AbrLookupTools.RECOGNITION:
                slice = selectionService.selectCatalogSlice(new ArrayList<>(labels.keySet()), d, null, true);
                AppRecognitionVo recognition = AbrLookupTools.recognition(slice, p, labels, today, null);
                recognition.countries().forEach(c -> c.programs().forEach(x -> outcomes.put(x.programId(), x.status())));
                result = recognition;
                break;
            case AbrLookupTools.IELTS:
                slice = selectionService.selectCatalogSlice(p.getTargetCountries(), d, p.getTargetFieldGroup(), true);
                AppIeltsVo ielts = AbrLookupTools.ielts(slice, p, labels, today, null);
                ielts.countries().forEach(c -> c.programs().forEach(x -> outcomes.put(x.programId(), x.outcome())));
                result = ielts;
                break;
            default:
                slice = selectionService.selectCatalogSlice(p.getTargetCountries(), d, p.getTargetFieldGroup(), false);
                AppOpenProgramsVo open = AbrLookupTools.open(slice, labels, today, null);
                open.programs().forEach(x -> outcomes.put(x.programId(), x.nextDeadline() == null ? "open" : "open:" + x.nextDeadline()));
                result = open;
                break;
        }

        AbrToolRun toolRun = new AbrToolRun();
        toolRun.setTool(tool);
        toolRun.setVisitorId(visitor.getVisitorId());
        toolRun.setDegree(d);
        toolRun.setQuestionnaireId(questionnaire.getQuestionnaireId());
        toolRun.setInput(answers.toString());
        toolRun.setResult(AbrLookupTools.trace(outcomes, slice).toString());
        ObjectNode versions = AbrAssessmentAnswers.object(null);
        slice.configs().forEach((c, cfg) -> versions.put(c, cfg.path("version").asString("")));
        toolRun.setEngineVersions(versions.toString());
        toolRun.setChannelCode(StringUtils.isBlank(body.getChannelCode()) ? visitor.getChannelCode()
                : AbrVisitorServiceImpl.channel(body.getChannelCode()));
        toolRun.setContentCode(AbrVisitorServiceImpl.code(body.getContentCode(), ""));
        toolRun.setClientPlatform(AbrVisitorServiceImpl.platform(body.getClientPlatform()));
        toolRun.setPolicyCode(personal ? q.policyCode() : "");
        toolRun.setPolicyVersion(personal ? body.getPolicyVersion().trim() : "");
        toolRunMapper.insertAbrToolRun(toolRun);

        if (personal)
        {
            // 渐进档案：之后的完整测评据此预填
            ObjectNode patch = AbrAssessmentAnswers.profilePatch(q, answers);
            if (!patch.isEmpty())
            {
                visitorService.mergeVisitorProfile(visitor.getVisitorId(), patch.toString());
            }
        }
        Long id = toolRun.getToolRunId();
        if (result instanceof AppRecognitionVo r)
        {
            return r.withToolRunId(id);
        }
        if (result instanceof AppIeltsVo r)
        {
            return r.withToolRunId(id);
        }
        return ((AppOpenProgramsVo) result).withToolRunId(id);
    }

    private static List<String> questions(String tool)
    {
        List<String> ids = AbrLookupTools.QUESTIONS.get(tool);
        if (ids == null)
        {
            throw new ServiceException("工具不存在");
        }
        return ids;
    }

    /**
     * 学段：为空按硕士；引擎尚未实现的学段暂不提供工具
     */
    private static String degree(String degree)
    {
        String d = StringUtils.isBlank(degree) ? AbrSelectionServiceImpl.DEFAULT_DEGREE : degree;
        DegreeStrategy strategy = DegreeStrategy.of(d);
        if (strategy == null || !strategy.implemented())
        {
            throw new ServiceException("该学段暂不支持此工具，可预约顾问评估");
        }
        return d;
    }

    private AbrQuestionnaire activeQuestionnaire(String degree)
    {
        AbrQuestionnaire questionnaire = questionnaireMapper.selectActiveAbrQuestionnaireByDegree(degree);
        if (questionnaire == null)
        {
            throw new ServiceException("该工具暂未开放，请稍后再试");
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
