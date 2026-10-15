package com.ygb.abroad.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 测试台统计口径：档位命中、混淆矩阵、严重错档、硬门槛误判与一致率、结果一致性、按国家汇总
 *
 * @author ygb
 */
class CaseBenchTest
{
    private static final JsonNode UK = EngineSeeds.config("uk_master.json");

    /** M0-06 算例学生：英国 C = 81 */
    private static BackgroundProfile student()
    {
        BackgroundProfile p = new BackgroundProfile();
        p.setUndergradTier("211");
        p.setGpaPercent(new BigDecimal("85"));
        p.setClassRank("top30");
        p.setMajorRelevance("same");
        p.setEnglishStatus("taken");
        p.setEnglishTest("ielts");
        p.setIeltsEquivTotal(new BigDecimal("6.5"));
        p.setIeltsEquivMinSub(new BigDecimal("6.0"));
        p.setResearchLevel("none");
        p.setWorkMonths(3);
        p.setWorkCompanyTier("normal");
        p.setWorkRelevant("yes");
        p.setAwardsLevel("provincial");
        p.setAwardsCount(1);
        p.setTargetFieldGroup("business");
        return p;
    }

    private static CaseBench.BenchCase c(String id, String country, String difficulty, String consultant, Boolean admitted, String expected)
    {
        return new CaseBench.BenchCase(id, "A", student(), new ProgramFacts(1L, country, difficulty, "en", false, null), consultant, admitted,
                false, expected);
    }

    @Test
    void metrics()
    {
        // D2 → 稳（P 0.4551），D4 → 保（0.8787），D1 → 补足后可申（均分 85 < 87），无难度档 → 待定档
        List<CaseBench.BenchCase> cases = List.of(c("1", "UK", "D2", "match", true, "pass"), c("2", "UK", "D4", "safety", true, "pass"),
                c("3", "UK", "D4", "reach", false, null), c("4", "UK", "D1", "match", true, "pass"), c("5", "UK", null, "match", null, null),
                c("6", "US", "D2", "match", null, null), c("7", "UK", "D2", null, false, "needs_improvement"));
        CaseBench.Report r = CaseBench.run(cases, Map.of("UK", UK));
        CaseBench.Summary all = r.summaries().get(CaseBench.ALL);
        assertEquals(7, all.rows());
        assertEquals(3, all.counted());
        assertEquals(2, all.hits());
        assertEquals(new BigDecimal("0.6667"), all.overall());
        assertEquals(new CaseBench.Rate(1, 0, BigDecimal.ZERO.setScale(4)), all.perTier().get("reach"));
        assertEquals(new CaseBench.Rate(1, 1, BigDecimal.ONE.setScale(4)), all.perTier().get("match"));
        assertEquals(1, all.confusion().get("reach").get("safety"));
        // 顾问冲、引擎保：严重错档
        assertEquals(1, all.severe());
        assertTrue(r.results().get(2).severe());
        // 补足后可申却被录取：硬门槛误判；预期 pass 而引擎拦下：硬门槛不一致
        assertEquals(1, all.blocked());
        assertEquals(1, all.thresholdMisjudged());
        assertEquals(4, all.thresholdChecked());
        assertEquals(2, all.thresholdHits());
        assertEquals(1, all.other());
        assertEquals(1, all.errors());
        assertEquals("没有该国家 / 地区的引擎配置", r.results().get(5).error());
        assertNull(r.results().get(6).tierHit());
        // 结果一致性：保 2 行有结果（1 录取 1 拒绝），样本不足
        assertEquals(new CaseBench.Outcome(2, 1, new BigDecimal("0.5000"), true), all.outcomes().get("safety"));
        assertNull(all.outcomes().get("reach").rate());
        assertEquals(List.of(CaseBench.ALL, "UK", "US"), List.copyOf(r.summaries().keySet()));
        assertEquals(1, r.summaries().get("US").errors());
    }

    @Test
    void preSessionalThreshold()
    {
        // D1：雅思 6.5 差 0.5，英国可读语言班；均分要求放低到 80 让均分达标
        ProgramFacts.Requirement req = new ProgramFacts.Requirement(Map.of("211", new BigDecimal("80")), java.util.Set.of(), new BigDecimal("7.0"),
                null, null, null, null, false, null, "2", "1", false);
        CaseBench.BenchCase pre = new CaseBench.BenchCase("p", "X", student(), new ProgramFacts(1L, "UK", "D1", "en", false, req), null, null, false,
                "pre_sessional");
        CaseBench.BenchCase strict = new CaseBench.BenchCase("s", "X", student(), new ProgramFacts(1L, "UK", "D1", "en", false, req), null, null, false,
                "needs_improvement");
        CaseBench.Report r = CaseBench.run(List.of(pre, strict), Map.of("UK", UK));
        assertEquals(Boolean.TRUE, r.results().get(0).thresholdHit());
        assertEquals(Boolean.FALSE, r.results().get(1).thresholdHit());
    }
}
