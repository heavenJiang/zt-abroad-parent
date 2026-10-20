package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrExperiment;

/**
 * A/B 实验 服务层（M9-04：钩子文案）
 * 
 * @author ygb
 */
public interface IAbrExperimentService
{
    /**
     * 写稿时的钩子文案分组
     *
     * @param experimentId 正在运行的实验（使用已结束实验的胜出文案时为空，不记分组）
     * @param variant 分组编码
     * @param template 文末引导模板
     */
    public record HookCopy(Long experimentId, String variant, String template)
    {
    }

    public List<AbrExperiment> selectExperimentList(AbrExperiment experiment);

    /**
     * 实验详情，附当前各组统计与评估（不保存）
     *
     * @param experimentId 实验ID
     * @return experiment、evaluation
     */
    public Map<String, Object> selectExperimentDetail(Long experimentId);

    public AbrExperiment insertExperiment(AbrExperiment experiment);

    public int updateExperiment(AbrExperiment experiment);

    public int deleteExperiments(Long[] experimentIds);

    public int startExperiment(Long experimentId, String operator);

    public int stopExperiment(Long experimentId, String reason, String operator);

    /**
     * 结束实验并确定胜出组（之后该平台新内容一律用胜出文案）
     */
    public int concludeExperiment(Long experimentId, String winner, String reason, String operator);

    /**
     * 写稿时取钩子文案：该平台有运行中的实验时按选题确定性分组；否则用最近结束实验的胜出文案；都没有时为空（用默认配置）
     *
     * @param platform 平台
     * @param topicId 选题
     * @return 分组；没有实验时为 null
     */
    public HookCopy hookCopy(String platform, Long topicId);

    /**
     * 记录内容所在分组
     */
    public void recordUnit(Long experimentId, Long contentId, String variant);

    /**
     * 评估全部运行中的实验
     *
     * @param autoConclude 有结论时是否直接结束（否则标为待确认结论）
     * @param operator 操作人
     * @return 每个实验的结论
     */
    public List<Map<String, Object>> evaluateAll(boolean autoConclude, String operator);
}
