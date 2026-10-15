package com.ygb.abroad.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrAssessment;
import com.ygb.abroad.domain.AbrAssessmentResult;
import com.ygb.abroad.domain.AbrProfile;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrProgramBenchmark;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.AbrSchool;
import com.ygb.abroad.engine.AdmissionEstimator;
import com.ygb.abroad.engine.BackgroundProfile;
import com.ygb.abroad.engine.DegreeStrategy;
import com.ygb.abroad.engine.ProgramBenchmark;
import com.ygb.abroad.engine.ProgramFacts;
import com.ygb.abroad.engine.RecommendationPlanner;
import com.ygb.abroad.engine.SelectionEngine;
import com.ygb.abroad.mapper.AbrAssessmentMapper;
import com.ygb.abroad.mapper.AbrAssessmentResultMapper;
import com.ygb.abroad.mapper.AbrProgramBenchmarkMapper;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.mapper.AbrRequirementMapper;
import com.ygb.abroad.mapper.AbrSchoolMapper;
import com.ygb.abroad.service.IAbrEngineConfigService;
import com.ygb.abroad.service.IAbrProfileService;
import com.ygb.abroad.service.IAbrSelectionService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 测评选校计算 服务层实现
 *
 * 候选项目：学生目标国家 / 地区中已启用引擎配置的、学位一致、专业大类一致（未定方向时不限）的正常状态项目；
 * 申请要求取每个项目最新申请季的一条。没有启用配置的国家记入 skipped_countries，不计算。
 *
 * @author ygb
 */
@Service
public class AbrSelectionServiceImpl implements IAbrSelectionService
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 问卷未答学位时按硕士计算（1a 引擎只支持硕士，其余学段见 M3-09） */
    static final String DEFAULT_DEGREE = "master";

    /** 结果模式：该学段由顾问评估（引擎尚未实现） */
    static final String MODE_CONSULTANT = "consultant";

    @Autowired
    private IAbrProfileService profileService;

    @Autowired
    private IAbrEngineConfigService engineConfigService;

    @Autowired
    private AbrProgramMapper programMapper;

    @Autowired
    private AbrSchoolMapper schoolMapper;

    @Autowired
    private AbrRequirementMapper requirementMapper;

    @Autowired
    private AbrProgramBenchmarkMapper benchmarkMapper;

    @Autowired
    private AbrAssessmentResultMapper resultMapper;

    @Autowired
    private AbrAssessmentMapper assessmentMapper;

    @Override
    public AbrAssessmentResult selectAbrAssessmentResult(Long assessmentId)
    {
        return resultMapper.selectAbrAssessmentResultByAssessmentId(assessmentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbrAssessmentResult computeAssessment(Long assessmentId, String operator)
    {
        AbrProfile profile = profileService.normalizeAssessment(assessmentId, operator);
        BackgroundProfile p = MAPPER.readValue(profile.getProfile(), BackgroundProfile.class);
        String degree = p.getTargetDegree();
        if (StringUtils.isBlank(degree))
        {
            // 问卷没问目标学位时用首屏选择的学段
            AbrAssessment assessment = assessmentMapper.selectAbrAssessmentById(assessmentId);
            degree = assessment == null || StringUtils.isBlank(assessment.getDegree()) ? DEFAULT_DEGREE : assessment.getDegree();
        }
        DegreeStrategy strategy = DegreeStrategy.of(degree);
        if (strategy == null || !strategy.implemented())
        {
            // 引擎尚未实现该学段：不计算，结果标“由顾问评估”
            AbrAssessmentResult result = consultantResult(assessmentId, p.getTargetCountries());
            result.setCreateBy(StringUtils.nvl(operator, ""));
            resultMapper.upsertAbrAssessmentResult(result);
            return resultMapper.selectAbrAssessmentResultByAssessmentId(assessmentId);
        }
        Map<String, JsonNode> active = engineConfigService.selectActiveConfigs(degree);

        List<String> open = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String c : p.getTargetCountries())
        {
            (active.containsKey(c) ? open : skipped).add(c);
        }
        Map<String, JsonNode> configs = new LinkedHashMap<>();
        open.forEach(c -> configs.put(c, active.get(c)));

        String fieldGroup = StringUtils.isBlank(p.getTargetFieldGroup()) || "undecided".equals(p.getTargetFieldGroup()) ? null
                : p.getTargetFieldGroup();
        List<AbrProgram> programs = open.isEmpty() ? List.of() : programMapper.selectEngineCandidates(open, degree, fieldGroup);
        List<ProgramFacts> facts = new ArrayList<>();
        Map<Long, AbrProgram> byId = new HashMap<>();
        Map<Long, ProgramBenchmark> benchmarks = new HashMap<>();
        if (!programs.isEmpty())
        {
            List<Long> ids = programs.stream().map(AbrProgram::getProgramId).toList();
            Map<Long, AbrRequirement> requirements = new HashMap<>();
            requirementMapper.selectLatestAbrRequirementByProgramIds(ids).forEach(r -> requirements.put(r.getProgramId(), r));
            for (AbrProgramBenchmark b : benchmarkMapper.selectAbrProgramBenchmarkByProgramIds(ids))
            {
                benchmarks.put(b.getProgramId(), new ProgramBenchmark(b.getRefLine(), b.getStability(), b.getSource()));
            }
            Map<Long, AbrSchool> schools = new HashMap<>();
            for (AbrProgram program : programs)
            {
                AbrSchool school = schools.computeIfAbsent(program.getSchoolId(), schoolMapper::selectAbrSchoolById);
                facts.add(AbrEngineInputs.programFacts(program, school, requirements.get(program.getProgramId())));
                byId.put(program.getProgramId(), program);
            }
        }

        SelectionEngine.Evaluation evaluation;
        RecommendationPlanner.Plan plan;
        try
        {
            evaluation = SelectionEngine.evaluate(p, facts, configs, benchmarks, false);
            plan = RecommendationPlanner.plan(p, evaluation, configs);
        }
        catch (IllegalArgumentException e)
        {
            throw new ServiceException("选校计算失败（引擎配置有误）：" + e.getMessage());
        }
        AbrAssessmentResult result = toResult(assessmentId, evaluation, plan, configs, skipped, byId);
        result.setCreateBy(StringUtils.nvl(operator, ""));
        resultMapper.upsertAbrAssessmentResult(result);
        return resultMapper.selectAbrAssessmentResultByAssessmentId(assessmentId);
    }

    /**
     * 引擎尚未实现的学段：不计算，提示由顾问评估
     */
    private static AbrAssessmentResult consultantResult(Long assessmentId, List<String> countries)
    {
        AbrAssessmentResult result = new AbrAssessmentResult();
        result.setAssessmentId(assessmentId);
        result.setMode(MODE_CONSULTANT);
        result.setEngineVersions("{}");
        result.setSkippedCountries(String.join(",", countries));
        result.setProgramCount(0);
        result.setReachCount(0);
        result.setMatchCount(0);
        result.setSafetyCount(0);
        result.setNeedsImprovementCount(0);
        result.setPrograms("[]");
        result.setPortfolio("{}");
        result.setGaps("[]");
        result.setHints("[]");
        result.setNotices(MAPPER.writeValueAsString(List.of(DegreeStrategy.NOTICE_CONSULTANT_REVIEW)));
        result.setSubScores("{}");
        return result;
    }

    private static AbrAssessmentResult toResult(Long assessmentId, SelectionEngine.Evaluation evaluation, RecommendationPlanner.Plan plan,
            Map<String, JsonNode> configs, List<String> skipped, Map<Long, AbrProgram> programs)
    {
        // 按分组顺序输出：冲 → 稳 → 保 → 待定档 → 补足后可申 → 不在考虑范围；组内按推荐方案的排序
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String tier : RecommendationPlanner.TIERS)
        {
            plan.tiers().get(tier).forEach(r -> rows.add(row(r.evaluation(), r, programs)));
        }
        plan.unrated().forEach(r -> rows.add(row(r.evaluation(), r, programs)));
        plan.needsImprovement().forEach(r -> rows.add(row(r.evaluation(), r, programs)));
        evaluation.group(SelectionEngine.EXCLUDED).forEach(e -> rows.add(row(e, null, programs)));

        Map<String, List<Long>> portfolio = new LinkedHashMap<>();
        plan.portfolio().forEach((tier, list) -> portfolio.put(tier, list.stream().map(r -> r.evaluation().program().programId()).toList()));
        Map<String, String> versions = new LinkedHashMap<>();
        configs.forEach((c, cfg) -> versions.put(c, cfg.path("version").asString("")));

        AbrAssessmentResult result = new AbrAssessmentResult();
        result.setAssessmentId(assessmentId);
        result.setMode(evaluation.mode());
        result.setEngineVersions(MAPPER.writeValueAsString(versions));
        result.setSkippedCountries(String.join(",", skipped));
        result.setProgramCount(evaluation.results().size());
        result.setReachCount(plan.tiers().get(AdmissionEstimator.REACH).size());
        result.setMatchCount(plan.tiers().get(AdmissionEstimator.MATCH).size());
        result.setSafetyCount(plan.tiers().get(AdmissionEstimator.SAFETY).size());
        result.setNeedsImprovementCount(plan.needsImprovement().size());
        result.setPrograms(MAPPER.writeValueAsString(rows));
        result.setPortfolio(MAPPER.writeValueAsString(portfolio));
        result.setGaps(MAPPER.writeValueAsString(plan.gaps()));
        result.setHints(MAPPER.writeValueAsString(plan.hints()));
        result.setNotices(MAPPER.writeValueAsString(plan.notices()));
        result.setSubScores(MAPPER.writeValueAsString(evaluation.subScores()));
        return result;
    }

    /**
     * 单个项目的留痕：分组、档位、C、P、T 及来源、硬门槛明细、偏好分
     */
    private static Map<String, Object> row(SelectionEngine.ProgramEvaluation e, RecommendationPlanner.Ranked ranked,
            Map<Long, AbrProgram> programs)
    {
        AbrProgram program = programs.get(e.program().programId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("programId", e.program().programId());
        m.put("schoolName", program == null ? null : program.getSchoolName());
        m.put("programName", program == null ? null : program.getProgramName());
        m.put("country", e.program().country());
        m.put("difficulty", e.program().difficulty());
        m.put("group", e.group());
        m.put("tier", e.estimate().tier());
        m.put("c", e.composite().c());
        m.put("p", e.estimate().p());
        m.put("t", e.estimate().t());
        m.put("tEff", e.estimate().tEff());
        m.put("a", e.estimate().a());
        m.put("tSource", e.estimate().tSource());
        m.put("weights", e.composite().weights());
        m.put("removedDimensions", e.composite().removed());
        m.put("configVersion", e.configVersion());
        m.put("missing", e.threshold().missing());
        m.put("thresholdItems", e.threshold().items());
        m.put("thresholdFlags", e.threshold().flags());
        if (ranked != null)
        {
            m.put("preferenceScore", ranked.preference().score());
            m.put("preferenceParts", ranked.preference().parts());
            m.put("costWan", ranked.preference().costWan());
            m.put("overBudget", ranked.preference().overBudget());
            m.put("preferenceFlags", ranked.preference().flags());
        }
        return m;
    }
}
