package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrQuestionnaire;

/**
 * 问卷版本 数据层
 * 
 * @author ygb
 */
public interface AbrQuestionnaireMapper
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
     * 查询某编码当前启用的版本（含问卷定义）
     * 
     * @param code 问卷编码
     * @return 启用的问卷版本，没有返回 null
     */
    public AbrQuestionnaire selectActiveAbrQuestionnaire(String code);

    /**
     * 按编码和版本号查询（不含问卷定义）
     * 
     * @param code 问卷编码
     * @param version 版本号
     * @return 问卷版本，不存在返回 null
     */
    public AbrQuestionnaire selectAbrQuestionnaireByCodeVersion(@Param("code") String code, @Param("version") Integer version);

    /**
     * 新增问卷版本（草稿）
     * 
     * @param questionnaire 问卷版本
     * @return 结果
     */
    public int insertAbrQuestionnaire(AbrQuestionnaire questionnaire);

    /**
     * 把某编码当前启用的版本改为已停用
     * 
     * @param code 问卷编码
     * @param updateBy 操作人
     * @return 结果
     */
    public int retireActiveAbrQuestionnaire(@Param("code") String code, @Param("updateBy") String updateBy);

    /**
     * 启用问卷版本（草稿或已停用 → 启用）
     * 
     * @param questionnaireId 问卷版本ID
     * @param updateBy 操作人
     * @return 结果
     */
    public int activateAbrQuestionnaire(@Param("questionnaireId") Long questionnaireId, @Param("updateBy") String updateBy);

    /**
     * 批量删除草稿（逻辑删除，非草稿不受影响）
     * 
     * @param questionnaireIds 问卷版本ID
     * @param updateBy 操作人
     * @return 结果
     */
    public int deleteDraftAbrQuestionnaireByIds(@Param("ids") Long[] questionnaireIds, @Param("updateBy") String updateBy);
}
