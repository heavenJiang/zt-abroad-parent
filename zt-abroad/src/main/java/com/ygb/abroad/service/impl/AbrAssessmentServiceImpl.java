package com.ygb.abroad.service.impl;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrAssessment;
import com.ygb.abroad.domain.AbrAssessmentResult;
import com.ygb.abroad.domain.AbrEngineConfig;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrQuestionnaire;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppAnswersBody;
import com.ygb.abroad.domain.vo.AppAssessmentBody;
import com.ygb.abroad.domain.vo.AppAssessmentVo;
import com.ygb.abroad.domain.vo.AppDegreeVo;
import com.ygb.abroad.domain.vo.AppPosterBody;
import com.ygb.abroad.domain.vo.AppQuestionnaireVo;
import com.ygb.abroad.domain.vo.AppReportVo;
import com.ygb.abroad.engine.DegreeStrategy;
import com.ygb.abroad.growth.card.Card;
import com.ygb.abroad.growth.card.CardRenderer;
import com.ygb.abroad.mapper.AbrAssessmentMapper;
import com.ygb.abroad.mapper.AbrEngineConfigMapper;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.mapper.AbrQuestionnaireMapper;
import com.ygb.abroad.mapper.AbrShareMapper;
import com.ygb.abroad.service.IAbrAssessmentService;
import com.ygb.abroad.service.IAbrPolicyService;
import com.ygb.abroad.service.IAbrReportExplanationService;
import com.ygb.abroad.service.IAbrSelectionService;
import com.ygb.abroad.service.IAbrVisitorService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DictUtils;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 测评 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrAssessmentServiceImpl implements IAbrAssessmentService
{
    private static final Logger log = LoggerFactory.getLogger(AbrAssessmentServiceImpl.class);

    /** 已解析问卷的缓存上限（问卷版本不可修改，按版本ID缓存是安全的） */
    private static final int CACHE_LIMIT = 64;

    private final Map<Long, AbrAssessmentAnswers.Questionnaire> parsed = new ConcurrentHashMap<>();

    @Autowired
    private AbrAssessmentMapper assessmentMapper;

    @Autowired
    private AbrQuestionnaireMapper questionnaireMapper;

    @Autowired
    private IAbrVisitorService visitorService;

    @Autowired
    private IAbrSelectionService selectionService;

    @Autowired
    private AbrEngineConfigMapper engineConfigMapper;

    @Autowired
    private IAbrPolicyService policyService;

    @Autowired
    private AbrProgramMapper programMapper;

    @Autowired
    private IAbrReportExplanationService explanationService;

    /** 分享码字符（去掉易混的 0 O 1 l I） */
    private static final String SHARE_CODE_CHARS = "23456789abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";

    /** 分享码长度 */
    private static final int SHARE_CODE_LENGTH = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private AbrShareMapper shareMapper;

    /** C 端地址（分享海报二维码） */
    @Value("${abroad.app.url:http://localhost:5173/}")
    private String appUrl;

    /** 品牌名（分享海报顶部） */
    @Value("${abroad.app.brand-name:留学工作室}")
    private String brandName;

    /** 图卡字体族 */
    @Value("${abroad.card.font-family:}")
    private String cardFontFamily;

    private volatile CardRenderer renderer;

    /** 落地页显示测评人数的下限（人数少时不显示，避免冷启动期数字难看） */
    @Value("${abroad.app.landing-count-min:100}")
    private long landingCountMin;

    @Override
    public AppQuestionnaireVo selectCurrentQuestionnaire(String code)
    {
        return toVo(activeQuestionnaire(code));
    }

    @Override
    public AppQuestionnaireVo selectQuestionnaire(Long questionnaireId)
    {
        AbrQuestionnaire questionnaire = questionnaireMapper.selectAbrQuestionnaireById(questionnaireId);
        if (questionnaire == null || AbrQuestionnaire.STATUS_DRAFT.equals(questionnaire.getPublishStatus()))
        {
            throw new ServiceException("问卷不存在");
        }
        return toVo(questionnaire);
    }

    @Override
    public AppAssessmentVo createAssessment(AbrVisitor visitor, AppAssessmentBody body)
    {
        AbrAssessment existing = assessmentMapper.selectAbrAssessmentByRequest(visitor.getVisitorId(), body.getRequestId());
        if (existing != null)
        {
            return toVo(existing);
        }
        String degree = StringUtils.isBlank(body.getDegree()) ? DEFAULT_DEGREE : body.getDegree();
        AbrQuestionnaire questionnaire = questionnaireMapper.selectActiveAbrQuestionnaireByDegree(degree);
        if (questionnaire == null)
        {
            throw new ServiceException("该学段的测评暂未开放，可预约顾问评估");
        }
        AbrAssessmentAnswers.Questionnaire q = questionnaire(questionnaire);
        // 同意的必须是当前启用的隐私政策版本（隐私政策更新后旧页面提交会被拒绝，学生需重新阅读）
        policyService.checkConsentVersion(q.policyCode(), body.getPolicyVersion());
        ObjectNode prefill = AbrAssessmentAnswers.prefill(q, AbrAssessmentAnswers.object(visitor.getProfile()));
        List<String> prefilled = new ArrayList<>(prefill.propertyNames());
        AbrAssessmentAnswers.prefillDegree(q, prefill, degree);

        AbrAssessment assessment = new AbrAssessment();
        assessment.setVisitorId(visitor.getVisitorId());
        assessment.setQuestionnaireId(questionnaire.getQuestionnaireId());
        assessment.setQuestionnaireCode(questionnaire.getCode());
        assessment.setQuestionnaireVersion(questionnaire.getVersion());
        assessment.setDegree(degree);
        assessment.setRequestId(body.getRequestId());
        assessment.setChannelCode(StringUtils.isBlank(body.getChannelCode()) ? visitor.getChannelCode()
                : AbrVisitorServiceImpl.channel(body.getChannelCode()));
        assessment.setContentCode(AbrVisitorServiceImpl.code(body.getContentCode(), ""));
        assessment.setClientPlatform(AbrVisitorServiceImpl.platform(body.getClientPlatform()));
        assessment.setPolicyCode(q.policyCode());
        assessment.setPolicyVersion(body.getPolicyVersion().trim());
        assessment.setAnswers(prefill.toString());
        try
        {
            assessmentMapper.insertAbrAssessment(assessment);
        }
        catch (DuplicateKeyException e)
        {
            // 同一请求并发提交：返回先写入的那一条
            return toVo(assessmentMapper.selectAbrAssessmentByRequest(visitor.getVisitorId(), body.getRequestId()));
        }
        AppAssessmentVo vo = toVo(assessmentMapper.selectAbrAssessmentById(assessment.getAssessmentId()));
        vo.setPrefilled(prefilled);
        return vo;
    }

    @Override
    public AppAssessmentVo selectAssessment(AbrVisitor visitor, Long assessmentId)
    {
        return toVo(owned(visitor, assessmentId));
    }

    @Override
    public AppAssessmentVo selectCurrentAssessment(AbrVisitor visitor, String code)
    {
        AbrAssessment assessment = assessmentMapper.selectLatestInProgressAssessment(visitor.getVisitorId(),
                StringUtils.isBlank(code) ? DEFAULT_QUESTIONNAIRE_CODE : code);
        return assessment == null ? null : toVo(assessment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppAssessmentVo saveAnswers(AbrVisitor visitor, Long assessmentId, AppAnswersBody body)
    {
        AbrAssessment assessment = owned(visitor, assessmentId);
        if (!AbrAssessment.STATUS_IN_PROGRESS.equals(assessment.getAssessmentStatus()))
        {
            throw new ServiceException("测评已结束，不能再修改答案");
        }
        AbrQuestionnaire questionnaire = questionnaireMapper.selectAbrQuestionnaireById(assessment.getQuestionnaireId());
        if (questionnaire == null)
        {
            throw new ServiceException("问卷不存在");
        }
        AbrAssessmentAnswers.Questionnaire q = questionnaire(questionnaire);
        AbrAssessmentAnswers.Outcome outcome = AbrAssessmentAnswers.merge(q, AbrAssessmentAnswers.object(assessment.getAnswers()), body.getAnswers());
        if (outcome.clearAll())
        {
            // 终止且不保存答案（如未满 14 周岁）：测评答案和访客档案都清空
            assessmentMapper.terminateAbrAssessment(assessmentId, outcome.flags().toString(), true);
            visitorService.clearVisitorProfile(visitor.getVisitorId());
        }
        else
        {
            int rows = assessmentMapper.saveAbrAssessmentAnswers(assessmentId, outcome.set().toString(),
                    AbrAssessmentAnswers.toJsonArray(outcome.removed()), outcome.flags().toString(), body.getCurrentScreen());
            if (rows == 0)
            {
                throw new ServiceException("测评已结束，不能再修改答案");
            }
            ObjectNode profilePatch = AbrAssessmentAnswers.profilePatch(q, outcome.set());
            if (!profilePatch.isEmpty())
            {
                visitorService.mergeVisitorProfile(visitor.getVisitorId(), profilePatch.toString());
            }
            if (outcome.terminated())
            {
                assessmentMapper.terminateAbrAssessment(assessmentId, "{}", false);
            }
        }
        return toVo(assessmentMapper.selectAbrAssessmentById(assessmentId));
    }

    @Override
    public AppQuestionnaireVo selectCurrentQuestionnaireByDegree(String degree)
    {
        AbrQuestionnaire questionnaire = questionnaireMapper.selectActiveAbrQuestionnaireByDegree(degree);
        if (questionnaire == null)
        {
            throw new ServiceException("该学段的测评暂未开放，可预约顾问评估");
        }
        return toVo(questionnaire);
    }

    @Override
    public Long selectSubmittedCount()
    {
        long count = assessmentMapper.countSubmittedAbrAssessment();
        return count >= landingCountMin ? count : null;
    }

    @Override
    public List<AppDegreeVo> selectDegrees()
    {
        List<AppDegreeVo> list = new ArrayList<>();
        for (DegreeStrategy s : DegreeStrategy.values())
        {
            AppDegreeVo vo = new AppDegreeVo();
            vo.setDegree(s.degree());
            vo.setLabel(StringUtils.defaultIfBlank(DictUtils.getDictLabel("abr_degree", s.degree()), s.degree()));
            vo.setMethod(s.method());
            List<String> countries = new ArrayList<>();
            if (s.implemented())
            {
                AbrEngineConfig query = new AbrEngineConfig();
                query.setDegree(s.degree());
                query.setPublishStatus(AbrEngineConfig.STATUS_ACTIVE);
                engineConfigMapper.selectAbrEngineConfigList(query).forEach(c -> countries.add(c.getCountry()));
            }
            vo.setOpenCountries(countries);
            boolean hasQuestionnaire = questionnaireMapper.selectActiveAbrQuestionnaireByDegree(s.degree()) != null;
            vo.setMode(!hasQuestionnaire ? AppDegreeVo.MODE_CONSULTANT
                    : countries.isEmpty() ? AppDegreeVo.MODE_QUESTIONNAIRE : AppDegreeVo.MODE_ASSESSMENT);
            list.add(vo);
        }
        return list;
    }

    @Override
    public AppAssessmentVo submitAssessment(AbrVisitor visitor, Long assessmentId)
    {
        AbrAssessment assessment = owned(visitor, assessmentId);
        if (!AbrAssessment.STATUS_IN_PROGRESS.equals(assessment.getAssessmentStatus()))
        {
            throw new ServiceException(AbrAssessment.STATUS_SUBMITTED.equals(assessment.getAssessmentStatus()) ? "测评已提交" : "测评已结束，不能提交");
        }
        AbrQuestionnaire questionnaire = questionnaireMapper.selectAbrQuestionnaireById(assessment.getQuestionnaireId());
        if (questionnaire == null)
        {
            throw new ServiceException("问卷不存在");
        }
        AbrAssessmentAnswers.Submission submission = AbrAssessmentAnswers.submission(questionnaire(questionnaire),
                AbrAssessmentAnswers.object(assessment.getAnswers()));
        if (!submission.missingRequired().isEmpty())
        {
            throw new ServiceException("还有必答题没有完成：" + StringUtils.join(submission.missingRequired(), "、"));
        }
        if (assessmentMapper.submitAbrAssessment(assessmentId, submission.answers().toString()) == 0)
        {
            throw new ServiceException("测评已结束，不能提交");
        }
        boolean ready = false;
        try
        {
            // 至少一个目标国家 / 地区有启用的引擎配置时才算生成了结果
            AbrAssessmentResult result = selectionService.computeAssessment(assessmentId, "");
            ready = result != null && StringUtils.isNotBlank(result.getEngineVersions()) && !"{}".equals(result.getEngineVersions());
        }
        catch (RuntimeException e)
        {
            // 提交已成功；计算失败（如引擎配置有误）记日志，由顾问在管理端重新计算
            log.warn("测评提交后自动计算选校结果失败 assessmentId={}：{}", assessmentId, e.getMessage());
        }
        if (ready)
        {
            try
            {
                // 提交后立即开始生成解读，学生打开报告页时多数已完成
                selectReport(visitor, assessmentId);
            }
            catch (RuntimeException e)
            {
                log.warn("测评提交后启动解读失败 assessmentId={}：{}", assessmentId, e.getMessage());
            }
        }
        AppAssessmentVo vo = toVo(assessmentMapper.selectAbrAssessmentById(assessmentId));
        vo.setResultReady(ready);
        return vo;
    }

    @Override
    public AppReportVo selectReport(AbrVisitor visitor, Long assessmentId)
    {
        ReportContext ctx = reportContext(visitor, assessmentId);
        return explanationService.explain(ctx.report(), ctx.degreeLabel(), ctx.fieldLabel());
    }

    @Override
    public byte[] renderPoster(AbrVisitor visitor, Long assessmentId, AppPosterBody body)
    {
        ReportContext ctx = reportContext(visitor, assessmentId);
        if (!AbrAssessmentReport.MODE_FULL.equals(ctx.report().mode()))
        {
            throw new ServiceException("选校结果还没有生成，暂不能生成分享图");
        }
        boolean minor = ctx.flags().path("is_minor").asBoolean(false);
        boolean showSchool = !minor && body.isShowSchool();
        boolean showGpa = !minor && body.isShowGpa();
        String options = "{\"showSchool\":" + showSchool + ",\"showGpa\":" + showGpa + "}";
        String shareCode;
        try
        {
            shareCode = shareMapper.upsertAbrShare(AbrReportPoster.KIND, visitor.getVisitorId(), assessmentId, newShareCode(), options);
        }
        catch (DuplicateKeyException e)
        {
            // 随机分享码与已有记录相同（极少见）：换一个再试
            shareCode = shareMapper.upsertAbrShare(AbrReportPoster.KIND, visitor.getVisitorId(), assessmentId, newShareCode(), options);
        }
        Set<String> skipped = new HashSet<>();
        ctx.report().skippedCountries().forEach(c -> skipped.add(c.country()));
        List<String> countries = new ArrayList<>();
        for (JsonNode c : ctx.answers().path("target_countries"))
        {
            if (!skipped.contains(c.asString("")))
            {
                countries.add(countryLabel(ctx.q(), c.asString("")));
            }
        }
        String qrUrl = appUrl + (appUrl.contains("?") ? "&" : "?") + "ch=share&ct=" + shareCode;
        Card card = AbrReportPoster.card(ctx.report(), ctx.answers(), minor, showSchool, showGpa, countries, ctx.fieldLabel(), ctx.degreeLabel(),
                brandName, qrUrl);
        return cardRenderer().png(card);
    }

    /**
     * 报告与组装所需的上下文（本人、已提交）
     */
    private ReportContext reportContext(AbrVisitor visitor, Long assessmentId)
    {
        AbrAssessment assessment = owned(visitor, assessmentId);
        if (!AbrAssessment.STATUS_SUBMITTED.equals(assessment.getAssessmentStatus()))
        {
            throw new ServiceException("测评尚未提交，提交后才能查看报告");
        }
        AbrQuestionnaire questionnaire = questionnaireMapper.selectAbrQuestionnaireById(assessment.getQuestionnaireId());
        if (questionnaire == null)
        {
            throw new ServiceException("问卷不存在");
        }
        AbrAssessmentResult result = selectionService.selectAbrAssessmentResult(assessmentId);
        Map<Long, AbrProgram> programs = new HashMap<>();
        List<Long> ids = AbrAssessmentReport.programIds(result);
        if (!ids.isEmpty())
        {
            programMapper.selectAbrProgramByIds(ids).forEach(p -> programs.put(p.getProgramId(), p));
        }
        AbrAssessmentAnswers.Questionnaire q = questionnaire(questionnaire);
        ObjectNode answers = AbrAssessmentAnswers.object(assessment.getAnswers());
        ObjectNode flags = AbrAssessmentAnswers.object(assessment.getFlags());
        AppReportVo report = AbrAssessmentReport.build(assessmentId, q, answers, flags, result, programs, LocalDate.now(AbrLookupTools.BEIJING));
        String degreeLabel = StringUtils.defaultIfBlank(DictUtils.getDictLabel("abr_degree", assessment.getDegree()), assessment.getDegree());
        return new ReportContext(report, q, answers, flags, degreeLabel, optionLabel(q, "target_field", answers.path("target_field").asString("")));
    }

    /**
     * 报告上下文
     */
    private record ReportContext(AppReportVo report, AbrAssessmentAnswers.Questionnaire q, ObjectNode answers, ObjectNode flags,
            String degreeLabel, String fieldLabel)
    {
    }

    /**
     * 国家 / 地区名称（问卷顶层 countries）
     */
    private static String countryLabel(AbrAssessmentAnswers.Questionnaire q, String country)
    {
        for (JsonNode c : q.root().path("countries"))
        {
            if (country.equals(c.path("value").asString()))
            {
                return c.path("label").asString(country);
            }
        }
        return country;
    }

    /**
     * 新分享码：10 位随机字母数字
     */
    private static String newShareCode()
    {
        StringBuilder sb = new StringBuilder(SHARE_CODE_LENGTH);
        for (int i = 0; i < SHARE_CODE_LENGTH; i++)
        {
            sb.append(SHARE_CODE_CHARS.charAt(RANDOM.nextInt(SHARE_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 图卡渲染器（首次使用时选字体）
     */
    private CardRenderer cardRenderer()
    {
        CardRenderer r = renderer;
        if (r == null)
        {
            r = new CardRenderer(cardFontFamily);
            renderer = r;
        }
        return r;
    }

    /**
     * 单选题选项的名称（没有该选项时返回编码本身）
     */
    private static String optionLabel(AbrAssessmentAnswers.Questionnaire q, String questionId, String value)
    {
        JsonNode question = q.questions().get(questionId);
        if (question != null)
        {
            for (JsonNode o : question.path("options"))
            {
                if (value.equals(o.path("value").asString()))
                {
                    return o.path("label").asString(value);
                }
            }
        }
        return value;
    }

    @Override
    public List<AbrAssessment> selectAbrAssessmentList(AbrAssessment assessment)
    {
        return assessmentMapper.selectAbrAssessmentList(assessment);
    }

    @Override
    public AbrAssessment selectAbrAssessmentById(Long assessmentId)
    {
        return assessmentMapper.selectAbrAssessmentById(assessmentId);
    }

    private AbrQuestionnaire activeQuestionnaire(String code)
    {
        AbrQuestionnaire questionnaire = questionnaireMapper.selectActiveAbrQuestionnaire(code);
        if (questionnaire == null)
        {
            throw new ServiceException("测评暂未开放，请稍后再试");
        }
        return questionnaire;
    }

    private AbrAssessment owned(AbrVisitor visitor, Long assessmentId)
    {
        AbrAssessment assessment = assessmentId == null ? null : assessmentMapper.selectAbrAssessmentById(assessmentId);
        if (assessment == null || !assessment.getVisitorId().equals(visitor.getVisitorId()))
        {
            throw new ServiceException("测评不存在");
        }
        return assessment;
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

    private static AppQuestionnaireVo toVo(AbrQuestionnaire questionnaire)
    {
        AppQuestionnaireVo vo = new AppQuestionnaireVo();
        vo.setQuestionnaireId(questionnaire.getQuestionnaireId());
        vo.setCode(questionnaire.getCode());
        vo.setVersion(questionnaire.getVersion());
        vo.setTitle(questionnaire.getTitle());
        vo.setDefinition(AbrAssessmentAnswers.object(questionnaire.getDefinition()));
        return vo;
    }

    private static AppAssessmentVo toVo(AbrAssessment assessment)
    {
        AppAssessmentVo vo = new AppAssessmentVo();
        vo.setAssessmentId(assessment.getAssessmentId());
        vo.setQuestionnaireId(assessment.getQuestionnaireId());
        vo.setQuestionnaireVersion(assessment.getQuestionnaireVersion());
        vo.setStatus(assessment.getAssessmentStatus());
        vo.setCurrentScreen(assessment.getCurrentScreen());
        vo.setAnswers(AbrAssessmentAnswers.object(assessment.getAnswers()));
        vo.setFlags(AbrAssessmentAnswers.object(assessment.getFlags()));
        vo.setCreateTime(assessment.getCreateTime());
        vo.setUpdateTime(assessment.getUpdateTime());
        return vo;
    }
}
