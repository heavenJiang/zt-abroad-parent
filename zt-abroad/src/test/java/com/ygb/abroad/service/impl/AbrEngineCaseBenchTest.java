package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.engine.CaseBench;
import com.ygb.abroad.engine.NormalizationRules;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 测试台：解析 seed/engine-cases 的案例并用种子配置运行（验收：全部案例可解析、可计算），同时打印命中率报告
 *
 * @author ygb
 */
class AbrEngineCaseBenchTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static String read(String... path)
    {
        try
        {
            return Files.readString(Path.of("..", path), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, JsonNode> configs()
    {
        Map<String, JsonNode> configs = new LinkedHashMap<>();
        for (String c : List.of("uk", "au", "sg", "hk", "us", "jp", "kr", "de"))
        {
            JsonNode cfg = MAPPER.readTree(read("seed", "engine-config", c + "_master.json"));
            configs.put(cfg.path("country").asString(), cfg);
        }
        return configs;
    }

    private static NormalizationRules rules()
    {
        return NormalizationRules.parse(MAPPER.readTree(read("seed", "questionnaire", "questionnaire_v1.json")));
    }

    @Test
    void templateExamplesAreSkipped()
    {
        AbrEngineCaseCsv.Parsed parsed = AbrEngineCaseCsv.parse(read("seed", "engine-cases", "cases_template.csv"), rules());
        assertEquals(3, parsed.skipped());
        assertTrue(parsed.cases().isEmpty());
        assertTrue(parsed.errors().isEmpty());
    }

    @Test
    void sampleCasesAllRun()
    {
        AbrEngineCaseCsv.Parsed parsed = AbrEngineCaseCsv.parse(read("seed", "engine-cases", "cases_sample.csv"), rules());
        assertEquals(List.of(), parsed.errors());
        assertEquals(27, parsed.cases().size());
        CaseBench.Report report = CaseBench.run(parsed.cases(), configs());
        assertTrue(report.results().stream().allMatch(r -> r.error() == null));
        assertEquals(report, CaseBench.run(parsed.cases(), configs()));
        print(report);
    }

    @Test
    void conversionAndValidation()
    {
        String header = String.join(",", AbrEngineCaseCsv.REQUIRED) + ",english_total,english_min_sub,gpa_percent,outcome,waitlist_final,expected_threshold";
        String csv = "﻿" + header + "\r\n"
                + "T-1,UK,master,business,D2,211,same,3.6,4,toefl,保,100,22,,waitlist,admit,pass\r\n"
                + "T-2,UK,master,business,D2,211,same,85,100,none,稳,,,,reject,,\r\n"
                + "T-3,UK,master,business,D2,abc,same,85,100,ielts,稳,6.5,,,,,\n"
                + "T-4,UK,master,business,D2,211,same,85,100,ielts,好,6.5,,,,,\n"
                + "\"T-5\",UK,master,business,D2,211,same,85,100,ielts,冲,x,,,,,\n"
                + "T-6,UK,master,business,D2,211,same,85,100,ielts,冲,6.5,,,,,maybe\n"
                + ",,,,,,,,,,,,,,,,\n";
        AbrEngineCaseCsv.Parsed parsed = AbrEngineCaseCsv.parse(csv, rules());
        assertEquals(2, parsed.cases().size());
        assertEquals(4, parsed.errors().size(), parsed.errors().toString());
        CaseBench.BenchCase t1 = parsed.cases().get(0);
        assertEquals("safety", t1.consultantTier());
        assertEquals(Boolean.TRUE, t1.admitted());
        assertEquals(new BigDecimal("87.8"), t1.profile().getGpaPercent());
        assertEquals(0, new BigDecimal("7.0").compareTo(t1.profile().getIeltsEquivTotal()));
        CaseBench.BenchCase t2 = parsed.cases().get(1);
        assertEquals("none", t2.profile().getEnglishStatus());
        assertEquals(Boolean.FALSE, t2.admitted());
        assertNull(t2.expectedThreshold());

        assertEquals(List.of("CSV 为空"), AbrEngineCaseCsv.parse("", rules()).errors());
        assertFalse(AbrEngineCaseCsv.parse("case_id\nX", rules()).errors().isEmpty());
        // 没有换算表时必须填换算值
        AbrEngineCaseCsv.Parsed noRules = AbrEngineCaseCsv.parse(header + "\nT-1,UK,master,business,D2,211,same,3.6,4,ielts,保,6.5,,,,,\n", null);
        assertEquals(1, noRules.errors().size());
        AbrEngineCaseCsv.Parsed filled = AbrEngineCaseCsv.parse(header.replace("gpa_percent", "gpa_percent,ielts_equiv_total")
                + "\nT-1,UK,master,business,D2,211,same,3.6,4,ielts,保,6.5,,86,6.5,,,\n", null);
        assertEquals(List.of(), filled.errors());
        assertEquals(List.of(List.of("a", "b,\"c\""), List.of("d", "")), AbrEngineCaseCsv.records("a,\"b,\"\"c\"\"\"\nd,\n"));
        // req_min_gpa = none：官网不设均分线
        AbrEngineCaseCsv.Parsed none = AbrEngineCaseCsv.parse(header + ",req_min_gpa\nT-1,UK,master,business,D2,211,same,85,100,ielts,保,6.5,,,,,,none\n", rules());
        assertTrue(none.cases().get(0).program().requirement().gpaNoCutoff());
    }

    /** 报告打印到测试输出，便于在 CI 日志中查看 */
    private static void print(CaseBench.Report report)
    {
        StringBuilder sb = new StringBuilder("\n===== 引擎测试台：cases_sample.csv =====\n");
        report.summaries().forEach((scope, s) -> sb.append(String.format("%-4s 行 %2d | 档位统计 %2d 命中 %2d 整体 %s | 冲 %s 稳 %s 保 %s | 严重错档 %d | 补足后可申 %d | 硬门槛一致 %d/%d%n",
                scope, s.rows(), s.counted(), s.hits(), s.overall(), s.perTier().get("reach").rate(), s.perTier().get("match").rate(),
                s.perTier().get("safety").rate(), s.severe(), s.blocked(), s.thresholdHits(), s.thresholdChecked())));
        sb.append("混淆矩阵（行：顾问，列：引擎 冲/稳/保）：").append(report.summaries().get(CaseBench.ALL).confusion()).append('\n');
        for (CaseBench.CaseResult r : report.results())
        {
            sb.append(String.format("%s %s %-6s 顾问 %-6s 引擎 %-17s C %6s P %6s T %5s 缺 %s 门槛预期 %s%n", r.bench().caseId(), r.bench().program().country(),
                    r.bench().program().difficulty(), r.bench().consultantTier(), r.engineGroup(), r.c(), r.p(), r.t(), r.missing(),
                    r.bench().expectedThreshold()));
        }
        System.out.println(sb);
    }
}
