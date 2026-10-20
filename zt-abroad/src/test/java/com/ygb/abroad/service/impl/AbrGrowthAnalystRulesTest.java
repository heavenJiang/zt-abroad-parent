package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.ygb.common.exception.ServiceException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * growth_analyst 规则：A/B 实验、内容配比调参、周报摘要校验
 *
 * @author ygb
 */
class AbrGrowthAnalystRulesTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static JsonNode json(String s)
    {
        return MAPPER.readTree(s);
    }

    @Test
    void variants()
    {
        String ok = "[{\"code\":\"A\",\"template\":\"用「{hook}」测一下\",\"weight\":50,\"control\":true},"
                + "{\"code\":\"B\",\"name\":\"提问式\",\"template\":\"你能申哪些？用「{hook}」看看\",\"weight\":50}]";
        List<AbrExperimentRules.Variant> v = AbrExperimentRules.variants(json(ok), false);
        assertEquals(2, v.size());
        assertEquals("A", v.get(0).name());
        assertTrue(v.get(0).control());
        // 平台靠口令进入时须含 {codeword}
        assertThrows(ServiceException.class, () -> AbrExperimentRules.variants(json(ok), true));
        assertThrows(ServiceException.class, () -> AbrExperimentRules.variants(json("[{\"code\":\"A\",\"template\":\"{hook}\",\"weight\":1,\"control\":true}]"), false));
        assertThrows(ServiceException.class, () -> AbrExperimentRules.variants(json(
                "[{\"code\":\"A\",\"template\":\"{hook}\",\"weight\":1,\"control\":true},{\"code\":\"A\",\"template\":\"{hook}\",\"weight\":1}]"), false));
        assertThrows(ServiceException.class, () -> AbrExperimentRules.variants(json(
                "[{\"code\":\"A\",\"template\":\"{hook}\",\"weight\":1},{\"code\":\"B\",\"template\":\"{hook}\",\"weight\":1}]"), false));
        assertThrows(ServiceException.class, () -> AbrExperimentRules.variants(json(
                "[{\"code\":\"A\",\"template\":\"测一下\",\"weight\":1,\"control\":true},{\"code\":\"B\",\"template\":\"{hook}\",\"weight\":1}]"), false));
        assertThrows(ServiceException.class, () -> AbrExperimentRules.variants(json(
                "[{\"code\":\"A\",\"template\":\"{hook}\",\"weight\":0,\"control\":true},{\"code\":\"B\",\"template\":\"{hook}\",\"weight\":1}]"), false));
    }

    @Test
    void assignIsDeterministicAndWeighted()
    {
        List<AbrExperimentRules.Variant> v = List.of(new AbrExperimentRules.Variant("A", "A", "{hook}", 80, true),
                new AbrExperimentRules.Variant("B", "B", "{hook}", 20, false));
        assertEquals(AbrExperimentRules.assign(7L, "topic:1", v).code(), AbrExperimentRules.assign(7L, "topic:1", v).code());
        Map<String, Integer> count = new HashMap<>();
        for (int i = 0; i < 2000; i++)
        {
            count.merge(AbrExperimentRules.assign(7L, "topic:" + i, v).code(), 1, Integer::sum);
        }
        assertTrue(count.get("A") > 1450 && count.get("A") < 1750, "A=" + count.get("A"));
    }

    @Test
    void evaluate()
    {
        // 对照 5%，B 8%，各 2000：显著
        AbrExperimentRules.Decision d = AbrExperimentRules.evaluate(List.of(new AbrExperimentRules.Stat("A", 2000, 100),
                new AbrExperimentRules.Stat("B", 2000, 160)), "A", 1000, 0.05, 10, 28);
        assertEquals("winner", d.decision());
        assertEquals("B", d.winner());
        assertTrue((Double) d.rows().get(1).get("pValue") < 0.01);
        // 差异小：继续
        d = AbrExperimentRules.evaluate(List.of(new AbrExperimentRules.Stat("A", 2000, 100), new AbrExperimentRules.Stat("B", 2000, 105)), "A", 1000, 0.05, 10, 28);
        assertEquals("continue", d.decision());
        assertNull(d.winner());
        // 到期无差异：保留对照
        d = AbrExperimentRules.evaluate(List.of(new AbrExperimentRules.Stat("A", 2000, 100), new AbrExperimentRules.Stat("B", 2000, 105)), "A", 1000, 0.05, 28, 28);
        assertEquals("keep_control", d.decision());
        assertEquals("A", d.winner());
        // 样本不足：即使差异大也不判胜出
        d = AbrExperimentRules.evaluate(List.of(new AbrExperimentRules.Stat("A", 300, 10), new AbrExperimentRules.Stat("B", 300, 60)), "A", 1000, 0.05, 5, 28);
        assertEquals("continue", d.decision());
        assertTrue(d.reason().contains("样本不足"));
        // 实验组显著更差不算胜出
        d = AbrExperimentRules.evaluate(List.of(new AbrExperimentRules.Stat("A", 2000, 160), new AbrExperimentRules.Stat("B", 2000, 100)), "A", 1000, 0.05, 28, 28);
        assertEquals("keep_control", d.decision());
        assertEquals(1.0, AbrExperimentRules.pValue(new AbrExperimentRules.Stat("A", 0, 0), new AbrExperimentRules.Stat("B", 10, 1)));
        assertEquals(0.975, AbrExperimentRules.round(AbrExperimentRules.normalCdf(1.96), 3));
        assertEquals(0.025, AbrExperimentRules.round(AbrExperimentRules.normalCdf(-1.96), 3));
    }

    private static Map<String, Integer> mix(int c, int f, int l, int i)
    {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("case", c);
        m.put("flash", f);
        m.put("longtail", l);
        m.put("invite", i);
        return m;
    }

    @Test
    void tuneMix()
    {
        Map<String, int[]> bounds = AbrMixTuner.bounds("case:15-50,flash:15-50,longtail:10-40,invite:5-25,bad,x:9-1");
        assertEquals(4, bounds.size());
        Map<String, AbrMixTuner.Perf> perf = Map.of("case", new AbrMixTuner.Perf(5, 5), "flash", new AbrMixTuner.Perf(5, 20),
                "longtail", new AbrMixTuner.Perf(5, 10), "invite", new AbrMixTuner.Perf(1, 9));
        // 整体人均 35/15≈2.33：flash 4 加 5，case 1 减 5，longtail 2 不变；invite 样本不足不参与
        AbrMixTuner.Result r = AbrMixTuner.tune(mix(35, 30, 20, 15), perf, bounds, 5, 3, 0.2);
        assertTrue(r.changed());
        assertEquals(mix(30, 35, 20, 15), r.mix());
        // 边界：flash 已到上限 50，case 降到 30 后需要补 5 → 给没调整的类型
        r = AbrMixTuner.tune(mix(35, 50, 10, 5), perf, bounds, 5, 3, 0.2);
        assertEquals(100, r.mix().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(50, r.mix().get("flash"));
        assertEquals(30, r.mix().get("case"));
        // 样本不足
        r = AbrMixTuner.tune(mix(35, 30, 20, 15), Map.of("case", new AbrMixTuner.Perf(2, 5)), bounds, 5, 3, 0.2);
        assertFalse(r.changed());
        assertTrue(r.reason().contains("不足 2 类"));
        // 合计不是 100
        assertFalse(AbrMixTuner.tune(mix(35, 30, 20, 10), perf, bounds, 5, 3, 0.2).changed());
        // 表现接近
        Map<String, AbrMixTuner.Perf> flat = Map.of("case", new AbrMixTuner.Perf(5, 10), "flash", new AbrMixTuner.Perf(5, 11));
        assertFalse(AbrMixTuner.tune(mix(35, 30, 20, 15), flat, bounds, 5, 3, 0.2).changed());
        // 全为 0：不调
        Map<String, AbrMixTuner.Perf> zero = Map.of("case", new AbrMixTuner.Perf(5, 0), "flash", new AbrMixTuner.Perf(5, 0));
        assertFalse(AbrMixTuner.tune(mix(35, 30, 20, 15), zero, bounds, 5, 3, 0.2).changed());
        // 其他类型都在边界上补不齐：保持
        Map<String, int[]> tight = AbrMixTuner.bounds("case:35-40,flash:30-35,longtail:20-20,invite:15-15");
        Map<String, AbrMixTuner.Perf> up = Map.of("case", new AbrMixTuner.Perf(5, 20), "flash", new AbrMixTuner.Perf(5, 20),
                "longtail", new AbrMixTuner.Perf(5, 1));
        r = AbrMixTuner.tune(mix(35, 30, 20, 15), up, tight, 5, 3, 0.2);
        assertFalse(r.changed());
    }

    @Test
    void reportNarrative()
    {
        Set<String> allowed = AbrGrowthReportRules.numbers("{\"visitors\": 1234, \"rate\": 12.50, \"published\": 7}");
        assertEquals(Set.of("1234", "12.5", "7"), allowed);
        assertTrue(AbrGrowthReportRules.check(json("{\"summary\":\"本周新访客 1,234，发布 7 条\",\"highlights\":[\"工具使用率 12.5%\"],\"suggestions\":[]}"), allowed).isEmpty());
        assertFalse(AbrGrowthReportRules.check(json("{\"summary\":\"本周新访客 1300\",\"highlights\":[],\"suggestions\":[]}"), allowed).isEmpty());
        assertFalse(AbrGrowthReportRules.check(json("{\"summary\":\"\",\"highlights\":[],\"suggestions\":[]}"), allowed).isEmpty());
        assertFalse(AbrGrowthReportRules.check(json("{\"summary\":\"好\",\"highlights\":[\"" + "长".repeat(121) + "\"]}"), allowed).isEmpty());
        Map<String, Object> t = AbrGrowthReportRules.template(Map.of("visitors", 12L, "toolUsers", 5L), null, 3, 2, List.of("最好的内容：A", ""));
        assertTrue(((String) t.get("summary")).contains("本周新访客 12（上周 0）"));
        assertEquals(List.of("最好的内容：A"), t.get("highlights"));
        assertEquals("template", t.get("source"));
    }
}
