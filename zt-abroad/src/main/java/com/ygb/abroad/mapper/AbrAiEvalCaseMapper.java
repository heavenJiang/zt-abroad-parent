package com.ygb.abroad.mapper;

import java.util.List;
import com.ygb.abroad.domain.AbrAiEvalCase;

/**
 * 评测集 数据层
 *
 * @author ygb
 */
public interface AbrAiEvalCaseMapper
{
    /**
     * 查询样例列表
     *
     * @param evalCase 查询条件（Agent、来源、状态精确；名称模糊）
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
     * 查询某个 Agent 启用的样例（评测命令用）
     *
     * @param agentCode Agent 编码
     * @return 样例集合
     */
    public List<AbrAiEvalCase> selectActiveByAgent(String agentCode);

    /**
     * 按来源记录查询样例
     *
     * @param reviewId 队列记录ID
     * @return 样例，没有返回 null
     */
    public AbrAiEvalCase selectByReviewId(Long reviewId);

    /**
     * 新增样例
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
}
