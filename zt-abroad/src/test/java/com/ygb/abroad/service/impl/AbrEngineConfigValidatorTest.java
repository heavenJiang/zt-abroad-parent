package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.vo.AbrEngineConfigCheck;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 引擎配置校验测试：8 份种子草案都能通过，逐条触发 README「导入校验规则」与试算失败
 *
 * @author ygb
 */
class AbrEngineConfigValidatorTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static String seed(String file)
    {
        try
        {
            return Files.readString(Path.of("..", "seed", "engine-config", file), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static AbrEngineConfigCheck checkModified(String file, Consumer<ObjectNode> change)
    {
        ObjectNode root = (ObjectNode) MAPPER.readTree(seed(file));
        change.accept(root);
        return AbrEngineConfigValidator.check(root.toString());
    }

    @Test
    void allSeedDraftsPass()
    {
        for (String c : List.of("uk", "au", "sg", "hk", "us", "jp", "kr", "de"))
        {
            AbrEngineConfigCheck check = AbrEngineConfigValidator.check(seed(c + "_master.json"));
            assertEquals(List.of(), check.getErrors(), c);
            assertEquals(c.toUpperCase(), check.getCountry());
            assertEquals("master", check.getDegree());
            assertEquals("draft", check.getFileStatus());
            assertFalse(check.isActivatable());
            assertTrue(check.isImportable());
            assertEquals(1, check.getWarnings().size());
        }
    }

    @Test
    void confirmedWarnings()
    {
        AbrEngineConfigCheck check = checkModified("uk_master.json", r -> r.put("status", "confirmed"));
        assertTrue(check.getErrors().isEmpty());
        assertTrue(check.isActivatable());
        // 仍有“AI 草拟”、缺确认人、版本含 draft
        assertEquals(3, check.getWarnings().size());
        check = checkModified("uk_master.json", r -> {
            r.put("status", "confirmed");
            r.put("confirmed_by", "GW");
            r.put("confirmed_at", "2026-11-01");
            r.put("version", "UK_MASTER-2026.11.1");
        });
        assertEquals(1, check.getWarnings().size());
    }

    @Test
    void structuralErrors()
    {
        assertEquals(List.of("不是合法的 JSON"), AbrEngineConfigValidator.check("{").getErrors());
        assertEquals(List.of("配置应为 JSON 对象"), AbrEngineConfigValidator.check("[]").getErrors());
        AbrEngineConfigCheck check = checkModified("uk_master.json", r -> {
            r.put("schema", "x");
            r.put("country", "FR");
            r.put("status", "final");
            r.remove("config_code");
            r.put("version", "v".repeat(65));
            r.remove("degree");
        });
        assertEquals(6, check.getErrors().size(), check.getErrors().toString());
    }

    @Test
    void weightErrors()
    {
        AbrEngineConfigCheck check = checkModified("uk_master.json", r -> {
            ((ObjectNode) r.path("weights").path("default")).put("academic", 0.5);
            ((ObjectNode) r.path("weights").path("default")).put("course_match", 0.15);
        });
        assertTrue(check.getErrors().contains("weights.default.course_match 只在德国大于 0"), check.getErrors().toString());
        check = checkModified("us_master.json", r -> ((ObjectNode) r.path("weights").path("by_field").path("stem")).put("research", -1));
        assertTrue(check.getErrors().contains("weights.by_field.stem.research 应为 ≥ 0 的数值"), check.getErrors().toString());
        assertTrue(check.getErrors().stream().anyMatch(e -> e.startsWith("weights.by_field.stem 权重之和应为 1")));
        check = checkModified("uk_master.json", r -> ((ObjectNode) r.path("weights")).remove("default"));
        assertTrue(check.getErrors().contains("缺少 weights.default"));
        check = checkModified("uk_master.json", r -> ((ObjectNode) r.path("dimensions")).set("keys", MAPPER.readTree("[\"academic\"]")));
        assertTrue(check.getErrors().get(0).startsWith("dimensions.keys"));
    }

    @Test
    void probabilityAndBenchmarkErrors()
    {
        AbrEngineConfigCheck check = checkModified("uk_master.json", r -> {
            ((ObjectNode) r.path("tier_cutoffs")).put("reach_below", 0.8);
            ((ObjectNode) r.path("tier_cutoffs")).put("expose_probability_to_student", true);
            ((ObjectNode) r.path("probability")).put("a_default", 0);
            ((ObjectNode) r.path("benchmark_defaults").path("difficulty_levels").path("D3")).put("t", 90);
            ((ObjectNode) r.path("benchmark_defaults").path("difficulty_levels")).remove("D5");
            r.remove("hard_thresholds");
            r.remove("preference");
        });
        List<String> e = check.getErrors();
        assertTrue(e.contains("tier_cutoffs 应满足 0 < reach_below < safety_above < 1"), e.toString());
        assertTrue(e.contains("tier_cutoffs.expose_probability_to_student 必须为 false（学生端不展示录取概率）"));
        assertTrue(e.contains("probability.a_default 应大于 0"));
        assertTrue(e.contains("benchmark_defaults 中 D1–D5 的 T 应严格递减（D3）"));
        assertTrue(e.contains("缺少 benchmark_defaults.difficulty_levels.D5.t"));
        assertTrue(e.contains("缺少 hard_thresholds"));
        assertTrue(e.stream().anyMatch(x -> x.startsWith("缺少 preference")));
    }

    @Test
    void dryRunCatchesMissingParameters()
    {
        AbrEngineConfigCheck check = checkModified("uk_master.json",
                r -> ((ObjectNode) r.path("scoring").path("academic")).remove("gpa_slope"));
        assertEquals(1, check.getErrors().size());
        assertTrue(check.getErrors().get(0).startsWith("试算失败："), check.getErrors().toString());
        assertEquals("{\"a\":1}", AbrEngineConfigValidator.normalize("{ \"a\" : 1 }"));
    }
}
