package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.vo.AppMaterialsVo;
import com.ygb.abroad.domain.vo.AppPlanVo;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 转化时刻判断测试：冲保失衡、要求变化后不达标（须在加入后更新）、截止前材料不齐、反复修改；阈值来自规则参数
 *
 * @author ygb
 */
class AbrPlanMomentsTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    private static JsonNode params(String json)
    {
        return MAPPER.readTree(json);
    }

    private static Date at(int day, int hour)
    {
        return Date.from(LocalDateTime.of(2026, 10, day, hour, 0).atZone(AbrLookupTools.BEIJING).toInstant());
    }

    private static AppPlanVo plan(int reach, int match, int safety)
    {
        return new AppPlanVo("full", List.of(), Map.of("reach", reach, "match", match, "safety", safety), List.of(), List.of(), null);
    }

    @Test
    void imbalance()
    {
        JsonNode p = params("{\"min_tiered\": 3}");
        assertTrue(AbrPlanMoments.imbalance(plan(1, 2, 0), p));
        assertTrue(AbrPlanMoments.imbalance(plan(4, 1, 1), p));
        assertFalse(AbrPlanMoments.imbalance(plan(2, 4, 2), p));
        assertFalse(AbrPlanMoments.imbalance(plan(1, 1, 0), p));
        assertTrue(AbrPlanMoments.imbalance(plan(1, 1, 0), params("{\"min_tiered\": 2}")));
        assertFalse(AbrPlanMoments.imbalance(plan(1, 1, 0), params("{}")));
    }

    private static AppPlanVo.Item item(long id, String atAdd, String now, Date added)
    {
        return new AppPlanVo.Item(id, "S", "P", "UK", "英国", "ok", now, atAdd, atAdd != null && !atAdd.equals(now), List.of(), List.of(), null, false,
                "quick", added);
    }

    @Test
    void requirementUnmetOnlyWhenRequirementChangedAfterAdding()
    {
        AppPlanVo plan = new AppPlanVo("full", List.of(item(1, "match", "needs_improvement", at(1, 9)), item(2, "safety", "excluded", at(10, 9)),
                item(3, "reach", "match", at(1, 9)), item(4, null, "needs_improvement", at(1, 9)), item(5, "match", "needs_improvement", at(1, 9))),
                Map.of(), List.of(), List.of(), null);
        // 1：要求 5 日更新，晚于加入 → 计入；2：要求更新早于加入 → 不计（是档案变化或加入时就已不满足）；3 仍可申；4 加入时无档位；5 没有要求记录
        List<Long> ids = AbrPlanMoments.requirementUnmet(plan, Map.of(1L, at(5, 9), 2L, at(5, 9), 3L, at(5, 9), 4L, at(5, 9)));
        assertEquals(List.of(1L), ids);
    }

    private static AppMaterialsVo.ProgramMaterials program(long id, String deadline, int done, int total)
    {
        return new AppMaterialsVo.ProgramMaterials(id, "S", "P", "英国", deadline, done, total, false, false, List.of());
    }

    @Test
    void materialsBehind()
    {
        AppMaterialsVo vo = new AppMaterialsVo(0, 0, List.of(), List.of(program(1, "2026-11-10", 1, 6), program(2, "2026-11-10", 3, 6),
                program(3, "2026-12-01", 0, 6), program(4, null, 0, 6), program(5, "2026-10-16", 0, 0), program(6, "2026-10-16", 2, 7)), "");
        // 1：25 天后截止、完成 1/6 → 计入；2：完成一半不算低；3：46 天后；4：无截止；5：没有材料；6：今天截止、2/7 → 计入
        assertEquals(List.of(1L, 6L), AbrPlanMoments.materialsBehind(vo, TODAY, params("{\"days_before\": 30, \"completion_below\": 0.5}")));
        assertEquals(List.of(1L, 3L, 6L), AbrPlanMoments.materialsBehind(vo, TODAY, params("{\"days_before\": 60}")));
    }

    @Test
    void repeatedEdits()
    {
        JsonNode p = params("{\"window_days\": 14, \"min_edits\": 4, \"min_days\": 3}");
        List<Date> times = new ArrayList<>(List.of(at(10, 9), at(10, 10), at(10, 23), at(11, 9)));
        assertFalse(AbrPlanMoments.repeatedEdits(times, p));
        times.add(at(12, 9));
        assertTrue(AbrPlanMoments.repeatedEdits(times, p));
        assertFalse(AbrPlanMoments.repeatedEdits(times.subList(0, 3), p));
        assertEquals(14, AbrPlanMoments.windowDays(p));
        assertEquals(14, AbrPlanMoments.windowDays(params("{}")));
        assertEquals(1, AbrPlanMoments.windowDays(params("{\"window_days\": 0}")));
    }
}
