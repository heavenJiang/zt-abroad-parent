package com.ygb.abroad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrQuestionnaire;
import com.ygb.abroad.domain.vo.AbrQuestionnaireCheck;
import com.ygb.abroad.mapper.AbrQuestionnaireMapper;
import com.ygb.abroad.service.IAbrQuestionnaireService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;

/**
 * 问卷版本 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrQuestionnaireServiceImpl implements IAbrQuestionnaireService
{
    @Autowired
    private AbrQuestionnaireMapper questionnaireMapper;

    @Override
    public List<AbrQuestionnaire> selectAbrQuestionnaireList(AbrQuestionnaire questionnaire)
    {
        return questionnaireMapper.selectAbrQuestionnaireList(questionnaire);
    }

    @Override
    public AbrQuestionnaire selectAbrQuestionnaireById(Long questionnaireId)
    {
        return questionnaireMapper.selectAbrQuestionnaireById(questionnaireId);
    }

    @Override
    public AbrQuestionnaire selectActiveAbrQuestionnaire(String code)
    {
        return questionnaireMapper.selectActiveAbrQuestionnaire(code);
    }

    @Override
    public AbrQuestionnaireCheck checkAbrQuestionnaire(String definition)
    {
        AbrQuestionnaireCheck check = AbrQuestionnaireValidator.check(definition);
        if (check.getCode() != null)
        {
            if (check.getVersion() != null)
            {
                AbrQuestionnaire existing = questionnaireMapper.selectAbrQuestionnaireByCodeVersion(check.getCode(), check.getVersion());
                check.setExistingStatus(existing == null ? null : existing.getPublishStatus());
            }
            AbrQuestionnaire active = questionnaireMapper.selectActiveAbrQuestionnaire(check.getCode());
            check.setActiveVersion(active == null ? null : active.getVersion());
        }
        return check;
    }

    @Override
    public AbrQuestionnaire importAbrQuestionnaire(String definition, String remark, String operator)
    {
        AbrQuestionnaireCheck check = checkAbrQuestionnaire(definition);
        if (!check.getErrors().isEmpty())
        {
            throw new ServiceException("问卷 JSON 校验未通过：" + StringUtils.join(check.getErrors(), "；"));
        }
        if (check.getExistingStatus() != null)
        {
            throw new ServiceException("问卷 " + check.getCode() + " 的版本 " + check.getVersion() + " 已存在，请在 JSON 中提高 version 后再导入");
        }
        AbrQuestionnaire questionnaire = new AbrQuestionnaire();
        questionnaire.setCode(check.getCode());
        questionnaire.setVersion(check.getVersion());
        questionnaire.setVersionLabel(StringUtils.nvl(check.getVersionLabel(), ""));
        questionnaire.setTitle(check.getTitle());
        questionnaire.setQuestionCount(check.getQuestionCount());
        questionnaire.setDefinition(AbrQuestionnaireValidator.normalize(definition));
        questionnaire.setCreateBy(operator);
        questionnaire.setRemark(remark);
        try
        {
            questionnaireMapper.insertAbrQuestionnaire(questionnaire);
        }
        catch (DuplicateKeyException e)
        {
            throw new ServiceException("问卷 " + check.getCode() + " 的版本 " + check.getVersion() + " 已存在");
        }
        questionnaire.setPublishStatus(AbrQuestionnaire.STATUS_DRAFT);
        return questionnaire;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int activateAbrQuestionnaire(Long questionnaireId, String operator)
    {
        AbrQuestionnaire questionnaire = questionnaireMapper.selectAbrQuestionnaireById(questionnaireId);
        if (questionnaire == null)
        {
            throw new ServiceException("问卷版本不存在");
        }
        if (AbrQuestionnaire.STATUS_ACTIVE.equals(questionnaire.getPublishStatus()))
        {
            throw new ServiceException("该版本已是启用状态");
        }
        // 入库后再校验一次：防止校验规则升级后启用不合格的旧草稿
        AbrQuestionnaireCheck check = AbrQuestionnaireValidator.check(questionnaire.getDefinition());
        if (!check.getErrors().isEmpty())
        {
            throw new ServiceException("该版本校验未通过，不能启用：" + StringUtils.join(check.getErrors(), "；"));
        }
        questionnaireMapper.retireActiveAbrQuestionnaire(questionnaire.getCode(), operator);
        int rows = questionnaireMapper.activateAbrQuestionnaire(questionnaireId, operator);
        if (rows == 0)
        {
            throw new ServiceException("启用失败，请刷新后重试");
        }
        return rows;
    }

    @Override
    public int deleteAbrQuestionnaireByIds(Long[] questionnaireIds, String operator)
    {
        for (Long id : questionnaireIds)
        {
            AbrQuestionnaire questionnaire = questionnaireMapper.selectAbrQuestionnaireById(id);
            if (questionnaire != null && !AbrQuestionnaire.STATUS_DRAFT.equals(questionnaire.getPublishStatus()))
            {
                throw new ServiceException("版本 " + questionnaire.getCode() + " v" + questionnaire.getVersion() + " 已启用过，不能删除");
            }
        }
        return questionnaireMapper.deleteDraftAbrQuestionnaireByIds(questionnaireIds, operator);
    }
}
