package com.ygb.abroad.service.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrAssessment;
import com.ygb.abroad.domain.AbrQuestionnaire;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppAnswersBody;
import com.ygb.abroad.domain.vo.AppAssessmentBody;
import com.ygb.abroad.domain.vo.AppAssessmentVo;
import com.ygb.abroad.domain.vo.AppQuestionnaireVo;
import com.ygb.abroad.mapper.AbrAssessmentMapper;
import com.ygb.abroad.mapper.AbrQuestionnaireMapper;
import com.ygb.abroad.service.IAbrAssessmentService;
import com.ygb.abroad.service.IAbrVisitorService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.node.ObjectNode;

/**
 * 测评 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrAssessmentServiceImpl implements IAbrAssessmentService
{
    /** 已解析问卷的缓存上限（问卷版本不可修改，按版本ID缓存是安全的） */
    private static final int CACHE_LIMIT = 64;

    private final Map<Long, AbrAssessmentAnswers.Questionnaire> parsed = new ConcurrentHashMap<>();

    @Autowired
    private AbrAssessmentMapper assessmentMapper;

    @Autowired
    private AbrQuestionnaireMapper questionnaireMapper;

    @Autowired
    private IAbrVisitorService visitorService;

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
        AbrQuestionnaire questionnaire = activeQuestionnaire(DEFAULT_QUESTIONNAIRE_CODE);
        AbrAssessmentAnswers.Questionnaire q = questionnaire(questionnaire);
        ObjectNode prefill = AbrAssessmentAnswers.prefill(q, AbrAssessmentAnswers.object(visitor.getProfile()));

        AbrAssessment assessment = new AbrAssessment();
        assessment.setVisitorId(visitor.getVisitorId());
        assessment.setQuestionnaireId(questionnaire.getQuestionnaireId());
        assessment.setQuestionnaireCode(questionnaire.getCode());
        assessment.setQuestionnaireVersion(questionnaire.getVersion());
        assessment.setRequestId(body.getRequestId());
        assessment.setChannelCode(StringUtils.isBlank(body.getChannelCode()) ? visitor.getChannelCode()
                : AbrVisitorServiceImpl.channel(body.getChannelCode()));
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
        return toVo(assessmentMapper.selectAbrAssessmentById(assessment.getAssessmentId()));
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
