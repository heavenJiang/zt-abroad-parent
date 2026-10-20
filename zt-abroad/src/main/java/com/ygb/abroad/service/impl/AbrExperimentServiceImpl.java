package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrExperiment;
import com.ygb.abroad.mapper.AbrGrowthAnalystMapper;
import com.ygb.abroad.service.IAbrExperimentService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * A/B 实验 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrExperimentServiceImpl implements IAbrExperimentService
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private AbrGrowthAnalystMapper analystMapper;

    @Autowired
    private Environment env;

    @Override
    public List<AbrExperiment> selectExperimentList(AbrExperiment experiment)
    {
        return analystMapper.selectExperimentList(experiment);
    }

    @Override
    public Map<String, Object> selectExperimentDetail(Long experimentId)
    {
        AbrExperiment e = require(experimentId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("experiment", e);
        out.put("evaluation", AbrExperimentRules.STATUS_DRAFT.equals(e.getStatus()) ? null : evaluation(e));
        return out;
    }

    @Override
    public AbrExperiment insertExperiment(AbrExperiment experiment)
    {
        validate(experiment);
        analystMapper.insertExperiment(experiment);
        return analystMapper.selectExperimentById(experiment.getExperimentId());
    }

    @Override
    public int updateExperiment(AbrExperiment experiment)
    {
        AbrExperiment old = require(experiment.getExperimentId());
        if (!AbrExperimentRules.STATUS_DRAFT.equals(old.getStatus()))
        {
            throw new ServiceException("只有草稿可以修改");
        }
        validate(experiment);
        return analystMapper.updateDraftExperiment(experiment);
    }

    @Override
    public int deleteExperiments(Long[] experimentIds)
    {
        int rows = analystMapper.deleteDraftExperiments(experimentIds);
        if (rows < experimentIds.length)
        {
            throw new ServiceException("只能删除草稿；已运行过的实验请停止");
        }
        return rows;
    }

    @Override
    public int startExperiment(Long experimentId, String operator)
    {
        AbrExperiment e = require(experimentId);
        // 开始前再校验一次（平台的文末引导配置可能已变）
        validate(e);
        try
        {
            int rows = analystMapper.startExperiment(experimentId, operator);
            if (rows == 0)
            {
                throw new ServiceException("只有草稿可以开始");
            }
            return rows;
        }
        catch (DuplicateKeyException ex)
        {
            throw new ServiceException("该平台已有运行中的钩子文案实验，请先结束或停止");
        }
    }

    @Override
    public int stopExperiment(Long experimentId, String reason, String operator)
    {
        int rows = analystMapper.stopExperiment(experimentId, AbrPartnerRules.note(reason, true, "停止原因"), operator);
        if (rows == 0)
        {
            throw new ServiceException("只有运行中或待确认结论的实验可以停止");
        }
        return rows;
    }

    @Override
    public int concludeExperiment(Long experimentId, String winner, String reason, String operator)
    {
        AbrExperiment e = require(experimentId);
        List<AbrExperimentRules.Variant> variants = AbrExperimentRules.variants(MAPPER.readTree(e.getVariants()), false);
        if (variants.stream().noneMatch(v -> v.code().equals(winner)))
        {
            throw new ServiceException("胜出组须是实验中的分组");
        }
        int rows = analystMapper.concludeExperiment(experimentId, winner, AbrPartnerRules.note(reason, true, "结论说明"), operator);
        if (rows == 0)
        {
            throw new ServiceException("只有运行中或待确认结论的实验可以结束");
        }
        return rows;
    }

    @Override
    public HookCopy hookCopy(String platform, Long topicId)
    {
        AbrExperiment running = analystMapper.selectRunningByPlatform(AbrExperimentRules.TYPE_HOOK_COPY, platform);
        if (running != null)
        {
            List<AbrExperimentRules.Variant> variants = AbrExperimentRules.variants(MAPPER.readTree(running.getVariants()), false);
            AbrExperimentRules.Variant v = AbrExperimentRules.assign(running.getExperimentId(), "topic:" + topicId, variants);
            return new HookCopy(running.getExperimentId(), v.code(), v.template());
        }
        AbrExperiment done = analystMapper.selectLatestWinner(AbrExperimentRules.TYPE_HOOK_COPY, platform);
        if (done != null)
        {
            for (AbrExperimentRules.Variant v : AbrExperimentRules.variants(MAPPER.readTree(done.getVariants()), false))
            {
                if (v.code().equals(done.getWinnerCode()))
                {
                    return new HookCopy(null, v.code(), v.template());
                }
            }
        }
        return null;
    }

    @Override
    public void recordUnit(Long experimentId, Long contentId, String variant)
    {
        if (experimentId != null && contentId != null)
        {
            analystMapper.insertUnit(experimentId, "content", contentId, variant);
        }
    }

    @Override
    public List<Map<String, Object>> evaluateAll(boolean autoConclude, String operator)
    {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AbrExperiment e : analystMapper.selectRunningExperiments())
        {
            Map<String, Object> ev = evaluation(e);
            String decision = (String) ev.get("decision");
            String reason = (String) ev.get("reason");
            String result = MAPPER.writeValueAsString(ev);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("experimentId", e.getExperimentId());
            row.put("decision", decision);
            row.put("winner", ev.get("winner"));
            if (!"continue".equals(decision) && autoConclude)
            {
                analystMapper.saveEvaluation(e.getExperimentId(), result, null, null);
                analystMapper.concludeExperiment(e.getExperimentId(), (String) ev.get("winner"), StringUtils.substring(reason, 0, 200), operator);
                row.put("action", "concluded");
            }
            else if (!"continue".equals(decision) && AbrExperimentRules.STATUS_RUNNING.equals(e.getStatus()))
            {
                analystMapper.saveEvaluation(e.getExperimentId(), result, AbrExperimentRules.STATUS_READY, StringUtils.substring(reason, 0, 200));
                row.put("action", "ready");
            }
            else
            {
                analystMapper.saveEvaluation(e.getExperimentId(), result, null, null);
                row.put("action", "evaluated");
            }
            out.add(row);
        }
        return out;
    }

    /**
     * 当前评估：各组样本与转化（按指标取分母、分子），与对照组比较
     */
    private Map<String, Object> evaluation(AbrExperiment e)
    {
        List<AbrExperimentRules.Variant> variants = AbrExperimentRules.variants(MAPPER.readTree(e.getVariants()), false);
        Map<String, Map<String, Object>> byCode = new LinkedHashMap<>();
        for (Map<String, Object> s : analystMapper.selectVariantStats(e.getExperimentId()))
        {
            byCode.put(String.valueOf(s.get("variant_code")), s);
        }
        boolean click = "click_rate".equals(e.getMetric());
        List<AbrExperimentRules.Stat> stats = new ArrayList<>();
        String control = null;
        for (AbrExperimentRules.Variant v : variants)
        {
            Map<String, Object> s = byCode.getOrDefault(v.code(), Map.of());
            long n = AbrAttributionRules.num(s.get(click ? "views" : "visitors"));
            long x = AbrAttributionRules.num(s.get(click ? "visitors_with_views" : "tool_visitors"));
            stats.add(new AbrExperimentRules.Stat(v.code(), n, x));
            if (v.control())
            {
                control = v.code();
            }
        }
        Date start = e.getStartTime() == null ? new Date() : e.getStartTime();
        long days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - start.getTime());
        double alpha = e.getAlpha() == null ? 0.05 : e.getAlpha().doubleValue();
        AbrExperimentRules.Decision d = AbrExperimentRules.evaluate(stats, control, e.getMinSample(), alpha, days, e.getMaxDays());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("decision", d.decision());
        out.put("winner", d.winner());
        out.put("reason", d.reason());
        out.put("elapsedDays", days);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : d.rows())
        {
            Map<String, Object> row = new LinkedHashMap<>(r);
            Map<String, Object> s = byCode.getOrDefault(String.valueOf(r.get("code")), Map.of());
            row.put("contents", AbrAttributionRules.num(s.get("contents")));
            row.put("views", AbrAttributionRules.num(s.get("views")));
            row.put("visitors", AbrAttributionRules.num(s.get("visitors")));
            row.put("toolVisitors", AbrAttributionRules.num(s.get("tool_visitors")));
            rows.add(row);
        }
        out.put("rows", rows);
        return out;
    }

    private AbrExperiment require(Long id)
    {
        AbrExperiment e = id == null ? null : analystMapper.selectExperimentById(id);
        if (e == null)
        {
            throw new ServiceException("实验不存在");
        }
        return e;
    }

    private void validate(AbrExperiment e)
    {
        if (StringUtils.isBlank(e.getExperimentName()) || e.getExperimentName().trim().length() > 100)
        {
            throw new ServiceException("请填写名称（最多 100 字）");
        }
        e.setExperimentName(e.getExperimentName().trim());
        e.setExperimentType(AbrExperimentRules.TYPE_HOOK_COPY);
        if (!AbrPlaybookServiceImpl.dictValues("abr_platform").contains(e.getPlatform()))
        {
            throw new ServiceException("请选择平台");
        }
        if (!AbrExperimentRules.METRICS.contains(e.getMetric()))
        {
            throw new ServiceException("指标只能是 click_rate / tool_rate");
        }
        if (e.getMinSample() == null || e.getMinSample() < 30 || e.getMinSample() > 1000000)
        {
            throw new ServiceException("每组最少样本须为 30–1000000");
        }
        if (e.getMaxDays() == null || e.getMaxDays() < 7 || e.getMaxDays() > 180)
        {
            throw new ServiceException("最长运行天数须为 7–180");
        }
        if (e.getAlpha() == null)
        {
            e.setAlpha(new BigDecimal("0.050"));
        }
        if (e.getAlpha().compareTo(new BigDecimal("0.001")) < 0 || e.getAlpha().compareTo(new BigDecimal("0.2")) > 0)
        {
            throw new ServiceException("显著性水平须为 0.001–0.2");
        }
        String base = env.getProperty("abroad.content.closing." + e.getPlatform(), env.getProperty("abroad.content.closing.default", ""));
        List<AbrExperimentRules.Variant> variants = AbrExperimentRules.variants(MAPPER.readTree(StringUtils.defaultIfBlank(e.getVariants(), "[]")),
                base.contains("{codeword}"));
        e.setVariants(MAPPER.writeValueAsString(variants.stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", v.code());
            m.put("name", v.name());
            m.put("template", v.template());
            m.put("weight", v.weight());
            m.put("control", v.control());
            return m;
        }).toList()));
    }
}
