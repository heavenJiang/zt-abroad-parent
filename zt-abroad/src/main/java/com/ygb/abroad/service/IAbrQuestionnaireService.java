package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrQuestionnaire;
import com.ygb.abroad.domain.vo.AbrQuestionnaireCheck;

/**
 * 问卷版本 服务层
 * 
 * 问卷由 JSON 定义并按版本管理：导入只生成草稿；启用后同一编码的上一个启用版本自动停用；
 * 版本的定义导入后不可修改（测评答案以题目 id 为键，旧测评要能按原版本读取），要改问卷就导入新版本。
 * 
 * @author ygb
 */
public interface IAbrQuestionnaireService
{
    /**
     * 查询问卷版本列表（不含问卷定义）
     * 
     * @param questionnaire 查询条件
     * @return 问卷版本集合
     */
    public List<AbrQuestionnaire> selectAbrQuestionnaireList(AbrQuestionnaire questionnaire);

    /**
     * 按ID查询问卷版本（含问卷定义）
     * 
     * @param questionnaireId 问卷版本ID
     * @return 问卷版本
     */
    public AbrQuestionnaire selectAbrQuestionnaireById(Long questionnaireId);

    /**
     * 查询某编码当前启用的版本（含问卷定义），供测评使用
     * 
     * @param code 问卷编码
     * @return 启用的问卷版本，没有返回 null
     */
    public AbrQuestionnaire selectActiveAbrQuestionnaire(String code);

    /**
     * 校验问卷 JSON（导入前预览）
     * 
     * @param definition 问卷 JSON 文本
     * @return 校验结果
     */
    public AbrQuestionnaireCheck checkAbrQuestionnaire(String definition);

    /**
     * 导入问卷版本（校验通过后保存为草稿）
     * 
     * @param definition 问卷 JSON 文本
     * @param remark 备注
     * @param operator 操作人
     * @return 新的问卷版本
     */
    public AbrQuestionnaire importAbrQuestionnaire(String definition, String remark, String operator);

    /**
     * 启用问卷版本（同一编码的上一个启用版本改为已停用）
     * 
     * @param questionnaireId 问卷版本ID
     * @param operator 操作人
     * @return 结果
     */
    public int activateAbrQuestionnaire(Long questionnaireId, String operator);

    /**
     * 批量删除草稿（启用过的版本不能删除）
     * 
     * @param questionnaireIds 问卷版本ID
     * @param operator 操作人
     * @return 结果
     */
    public int deleteAbrQuestionnaireByIds(Long[] questionnaireIds, String operator);
}
