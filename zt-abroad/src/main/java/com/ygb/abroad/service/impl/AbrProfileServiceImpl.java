package com.ygb.abroad.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrAssessment;
import com.ygb.abroad.domain.AbrCnUniversity;
import com.ygb.abroad.domain.AbrProfile;
import com.ygb.abroad.domain.AbrQuestionnaire;
import com.ygb.abroad.engine.AnswerVisibility;
import com.ygb.abroad.engine.BackgroundProfile;
import com.ygb.abroad.engine.NormalizationRules;
import com.ygb.abroad.engine.ProfileNormalizer;
import com.ygb.abroad.mapper.AbrAssessmentMapper;
import com.ygb.abroad.mapper.AbrCnUniversityMapper;
import com.ygb.abroad.mapper.AbrProfileMapper;
import com.ygb.abroad.mapper.AbrQuestionnaireMapper;
import com.ygb.abroad.service.IAbrProfileService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 背景档案 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrProfileServiceImpl implements IAbrProfileService
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 北京时间（距入学月数按北京时间计算） */
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    /** 已解析问卷的缓存上限（问卷版本不可修改，按版本ID缓存是安全的） */
    private static final int CACHE_LIMIT = 64;

    private final Map<Long, Parsed> parsed = new ConcurrentHashMap<>();

    @Autowired
    private AbrProfileMapper profileMapper;

    @Autowired
    private AbrAssessmentMapper assessmentMapper;

    @Autowired
    private AbrQuestionnaireMapper questionnaireMapper;

    @Autowired
    private AbrCnUniversityMapper cnUniversityMapper;

    @Override
    public AbrProfile selectAbrProfileByAssessmentId(Long assessmentId)
    {
        return profileMapper.selectAbrProfileByAssessmentId(assessmentId);
    }

    @Override
    public AbrProfile normalizeAssessment(Long assessmentId, String operator)
    {
        AbrAssessment assessment = assessmentMapper.selectAbrAssessmentById(assessmentId);
        if (assessment == null)
        {
            throw new ServiceException("测评不存在");
        }
        if (AbrAssessment.STATUS_TERMINATED.equals(assessment.getAssessmentStatus()))
        {
            throw new ServiceException("测评已终止，不生成背景档案");
        }
        Parsed q = questionnaire(assessment.getQuestionnaireId());
        Map<String, JsonNode> answers = new LinkedHashMap<>();
        AbrAssessmentAnswers.object(assessment.getAnswers()).properties().forEach(e -> answers.put(e.getKey(), e.getValue()));
        Map<String, JsonNode> visible = AnswerVisibility.visibleAnswers(q.root().path("questions"), answers);
        BackgroundProfile bp = ProfileNormalizer.normalize(visible, q.rules(), this::tierOf, LocalDate.now(BEIJING));

        AbrProfile profile = new AbrProfile();
        profile.setAssessmentId(assessmentId);
        profile.setVisitorId(assessment.getVisitorId());
        profile.setQuestionnaireId(assessment.getQuestionnaireId());
        profile.setUndergradTier(bp.getUndergradTier());
        profile.setUndergradUniversityId(bp.getUndergradUniversityId());
        profile.setGpaPercent(bp.getGpaPercent());
        profile.setClassRank(bp.getClassRank());
        profile.setMajorRelevance(bp.getMajorRelevance());
        profile.setEnglishStatus(bp.getEnglishStatus());
        profile.setIeltsEquivTotal(bp.getIeltsEquivTotal());
        profile.setIeltsEquivMinSub(bp.getIeltsEquivMinSub());
        profile.setStdTestType(bp.getStdTestType());
        profile.setStdTestTotal(bp.getStdTestTotal());
        profile.setTargetDegree(bp.getTargetDegree());
        profile.setTargetField(bp.getTargetField());
        profile.setTargetFieldGroup(bp.getTargetFieldGroup());
        profile.setTargetCountries(StringUtils.join(bp.getTargetCountries(), ","));
        profile.setMonthsToIntake(bp.getMonthsToIntake());
        profile.setProfile(MAPPER.writeValueAsString(bp));
        profile.setWarnings(MAPPER.writeValueAsString(bp.getWarnings()));
        profile.setCreateBy(StringUtils.nvl(operator, ""));
        profileMapper.upsertAbrProfile(profile);
        return profileMapper.selectAbrProfileByAssessmentId(assessmentId);
    }

    /**
     * 国内高校ID → 层次；停用或不存在返回 null（按“待核定”处理）
     */
    private String tierOf(Long universityId)
    {
        AbrCnUniversity university = cnUniversityMapper.selectAbrCnUniversityById(universityId);
        return university == null || !"0".equals(university.getStatus()) ? null : university.getTier();
    }

    private Parsed questionnaire(Long questionnaireId)
    {
        Parsed p = parsed.get(questionnaireId);
        if (p == null)
        {
            AbrQuestionnaire questionnaire = questionnaireMapper.selectAbrQuestionnaireById(questionnaireId);
            if (questionnaire == null)
            {
                throw new ServiceException("问卷不存在");
            }
            JsonNode root;
            try
            {
                root = MAPPER.readTree(questionnaire.getDefinition());
            }
            catch (JacksonException e)
            {
                throw new ServiceException("问卷定义损坏");
            }
            p = new Parsed(root, NormalizationRules.parse(root));
            if (parsed.size() >= CACHE_LIMIT)
            {
                parsed.clear();
            }
            parsed.put(questionnaireId, p);
        }
        return p;
    }

    /**
     * 已解析的问卷与换算表
     */
    private record Parsed(JsonNode root, NormalizationRules rules)
    {
    }
}
