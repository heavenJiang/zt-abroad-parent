package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrGrowthParamLog;
import com.ygb.abroad.domain.AbrGrowthReport;

/**
 * growth_analyst 服务层（M9-04）：周报、内容配比边界内调参、实验评估
 * 
 * @author ygb
 */
public interface IAbrGrowthAnalystService
{
    /**
     * 生成周报（同一周重复生成时覆盖数据与摘要；调参每周只做一次）
     *
     * @param weekStart 所报告的周的周一（yyyy-MM-dd；不填为上一周）
     * @param tune 是否调参
     * @param operator 操作人
     * @return 周报
     */
    public AbrGrowthReport weekly(String weekStart, boolean tune, String operator);

    /**
     * 评估 A/B 实验（执行模式为自动时有结论直接结束，否则标为待确认结论）
     *
     * @param operator 操作人
     * @return 每个实验的结论
     */
    public List<Map<String, Object>> evaluateExperiments(String operator);

    public List<AbrGrowthReport> selectReportList();

    public AbrGrowthReport selectReportById(Long reportId);

    public List<AbrGrowthParamLog> selectParamLogList(AbrGrowthParamLog log);

    /**
     * 撤销已生效的调整（配比须仍是调整后的值）
     */
    public int revertParam(Long logId, String operator);

    /**
     * 采纳建议（配比须仍是调整前的值）
     */
    public int applyParam(Long logId, String operator);

    /**
     * 忽略建议
     */
    public int dismissParam(Long logId, String operator);
}
