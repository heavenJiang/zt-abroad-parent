package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrAssessmentResult;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.engine.BackgroundProfile;
import com.ygb.abroad.engine.ProgramFacts;
import com.ygb.abroad.engine.RecommendationPlanner;
import com.ygb.abroad.engine.SelectionEngine;
import tools.jackson.databind.JsonNode;

/**
 * 测评选校计算 服务层（归一化 → 选校引擎 → 推荐方案 → 结果留痕）
 * 
 * @author ygb
 */
public interface IAbrSelectionService
{
    /**
     * 查询测评的选校结果
     * 
     * @param assessmentId 测评ID
     * @return 结果，没有返回 null
     */
    public AbrAssessmentResult selectAbrAssessmentResult(Long assessmentId);

    /**
     * 计算（或重新计算）测评的选校结果：重新归一化，按启用的引擎配置、院校库与参考线计算并覆盖保存
     * 
     * @param assessmentId 测评ID
     * @param operator 操作人
     * @return 结果
     */
    public AbrAssessmentResult computeAssessment(Long assessmentId, String operator);

    /**
     * 快测：按快测背景档案计算（只算不存，由调用方留痕）
     * 
     * @param p 快测背景档案（只含院校层次、均分、目标国家 / 地区、专业方向）
     * @param degree 学段（须为引擎已实现的学段）
     * @return 计算结果
     */
    public QuickRun computeQuick(BackgroundProfile p, String degree);

    /**
     * 入口工具用的院校库切片：目标国家 / 地区中学位一致、专业大类一致（为空或未定时不限）的正常状态项目，及已开放国家的引擎配置
     * 
     * @param countries 国家 / 地区（保持顺序）
     * @param degree 学段
     * @param fieldGroup 专业大类（为空或 undecided 时不限）
     * @param requireConfig 是否只取有启用引擎配置的国家（查询截止日等纯院校库事实时为 false）
     * @return 切片
     */
    public CatalogSlice selectCatalogSlice(List<String> countries, String degree, String fieldGroup, boolean requireConfig);

    /**
     * 院校库切片
     * 
     * @param configs 已开放国家 / 地区 → 启用的引擎配置
     * @param openCountries 已开放的国家 / 地区（requireConfig 为 false 时为全部传入国家）
     * @param skippedCountries 暂未开放的国家 / 地区
     * @param facts 引擎用的项目事实（按国家、项目ID 排序）
     * @param programs 项目ID → 项目
     * @param requirements 项目ID → 最新申请季的要求（没有的项目不放）
     */
    public record CatalogSlice(Map<String, JsonNode> configs, List<String> openCountries, List<String> skippedCountries, List<ProgramFacts> facts,
            Map<Long, AbrProgram> programs, Map<Long, AbrRequirement> requirements)
    {
    }

    /**
     * 快测计算结果
     * 
     * @param evaluation 引擎评估（快测模式）
     * @param plan 推荐方案（档内排序、差距、提示）
     * @param engineVersions 所用引擎配置版本（国家 / 地区 → 版本）
     * @param openCountries 已开放（有启用配置）的目标国家 / 地区，按学生选择顺序
     * @param skippedCountries 暂未开放的目标国家 / 地区
     * @param programs 项目ID → 项目（院校、项目名称）
     */
    public record QuickRun(SelectionEngine.Evaluation evaluation, RecommendationPlanner.Plan plan, Map<String, String> engineVersions,
            List<String> openCountries, List<String> skippedCountries, Map<Long, AbrProgram> programs)
    {
    }
}
