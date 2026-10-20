package com.ygb.abroad.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrExperiment;
import com.ygb.abroad.domain.AbrGrowthParamLog;
import com.ygb.abroad.domain.AbrGrowthReport;

/**
 * growth_analyst 数据层（M9-04）：A/B 实验、周报、调参记录与所需统计
 * 
 * @author ygb
 */
public interface AbrGrowthAnalystMapper
{
    public List<AbrExperiment> selectExperimentList(AbrExperiment experiment);

    public AbrExperiment selectExperimentById(Long experimentId);

    /**
     * 正在运行（含待确认结论）的实验
     */
    public List<AbrExperiment> selectRunningExperiments();

    /**
     * 该平台正在运行的实验（只取 running，用于写稿分组）
     */
    public AbrExperiment selectRunningByPlatform(@Param("type") String type, @Param("platform") String platform);

    /**
     * 该平台最近一个已结束且有胜出组的实验
     */
    public AbrExperiment selectLatestWinner(@Param("type") String type, @Param("platform") String platform);

    public int insertExperiment(AbrExperiment experiment);

    /**
     * 修改草稿
     */
    public int updateDraftExperiment(AbrExperiment experiment);

    public int deleteDraftExperiments(Long[] experimentIds);

    public int startExperiment(@Param("id") Long id, @Param("operator") String operator);

    public int stopExperiment(@Param("id") Long id, @Param("reason") String reason, @Param("operator") String operator);

    public int concludeExperiment(@Param("id") Long id, @Param("winner") String winner, @Param("reason") String reason, @Param("operator") String operator);

    /**
     * 保存评估结果；status 不为空时同时改状态（running → ready）
     */
    public int saveEvaluation(@Param("id") Long id, @Param("result") String result, @Param("status") String status, @Param("reason") String reason);

    public int insertUnit(@Param("experimentId") Long experimentId, @Param("unitType") String unitType, @Param("unitId") Long unitId,
            @Param("variant") String variant);

    /**
     * 各组统计：contents、views（回填了阅读的内容的阅读合计）、visitors_with_views、visitors、tool_visitors
     */
    public List<Map<String, Object>> selectVariantStats(Long experimentId);

    public List<AbrGrowthReport> selectReportList();

    public AbrGrowthReport selectReportById(Long reportId);

    public AbrGrowthReport selectReportByWeek(String weekStart);

    public int insertReport(AbrGrowthReport report);

    public int updateReport(AbrGrowthReport report);

    public List<AbrGrowthParamLog> selectParamLogList(AbrGrowthParamLog log);

    public AbrGrowthParamLog selectParamLogById(Long logId);

    public int insertParamLog(AbrGrowthParamLog log);

    public int changeParamLogStatus(@Param("id") Long id, @Param("from") String from, @Param("to") String to, @Param("operator") String operator);

    public int countParamLogs(@Param("reportId") Long reportId, @Param("paramKey") String paramKey);

    /**
     * 启用剧本的内容配比
     * 
     * @return playbook_code、playbook_name、content_mix（JSON 文本）
     */
    public List<Map<String, Object>> selectPlaybookMixes();

    public String selectPlaybookMix(String playbookCode);

    public int updatePlaybookMix(@Param("code") String code, @Param("mix") String mix, @Param("operator") String operator);

    /**
     * 期间发布的内容按类型：contents 发布数、tool_users 这些内容带来的用过工具的访客
     */
    public List<Map<String, Object>> selectKindPerformance(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /**
     * 期间的熔断与预算闸记录
     */
    public List<Map<String, Object>> selectBreakerTrips(@Param("beginDate") String beginDate, @Param("endDate") String endDate);
}
