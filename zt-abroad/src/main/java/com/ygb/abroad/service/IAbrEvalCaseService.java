package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrAiEvalCase;
import com.ygb.abroad.domain.AbrAiReview;

/**
 * 评测集 服务层：数据库中的评测样例（与提示词目录下的 eval/*.json 一起参与评测命令），不合格样本回流
 *
 * @author ygb
 */
public interface IAbrEvalCaseService
{
    /**
     * 查询样例列表
     *
     * @param evalCase 查询条件
     * @return 样例集合
     */
    public List<AbrAiEvalCase> selectAbrAiEvalCaseList(AbrAiEvalCase evalCase);

    /**
     * 按ID查询样例
     *
     * @param caseId 样例ID
     * @return 样例
     */
    public AbrAiEvalCase selectAbrAiEvalCaseById(Long caseId);

    /**
     * 新增样例（手工；校验 Agent 与定义写法，启用时须有模型输入和断言）
     *
     * @param evalCase 样例
     * @return 结果
     */
    public int insertAbrAiEvalCase(AbrAiEvalCase evalCase);

    /**
     * 修改样例（名称、定义、状态、备注）
     *
     * @param evalCase 样例
     * @return 结果
     */
    public int updateAbrAiEvalCase(AbrAiEvalCase evalCase);

    /**
     * 批量删除样例
     *
     * @param caseIds 样例ID
     * @return 结果
     */
    public int deleteAbrAiEvalCaseByIds(Long[] caseIds);

    /**
     * 由不合格的队列记录生成样例（命中的文字生成 notContains 断言；有模型输入时直接启用，否则待完善）；同一记录只生成一次
     *
     * @param review 队列记录
     * @param source 来源（sample / exception）
     * @param reason 不合格原因
     * @param operator 操作人
     * @return 样例ID
     */
    public Long createFromReview(AbrAiReview review, String source, String reason, String operator);
}
