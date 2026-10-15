package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.vo.AppCalendarVo;
import com.ygb.abroad.domain.vo.AppMaterialsVo;
import com.ygb.abroad.domain.vo.AppPlanVo;
import com.ygb.abroad.domain.vo.AppSharedPlanVo;
import com.ygb.abroad.growth.card.Card;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 选校单分享测试：家长只读视图不含学生分数、只列未过的截止；分享卡只含档位与校名；分享码格式
 *
 * @author ygb
 */
class AbrPlanShareTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static AppPlanVo.Item item(long id, String school, String status, String tier)
    {
        List<AppPlanVo.Check> checks = List.of(new AppPlanVo.Check("gpa", "met", "≥ 80", "85"),
                new AppPlanVo.Check("language_en", "not_met", "雅思 7.0", "雅思 6.0"));
        return new AppPlanVo.Item(id, school, "项目" + id, "UK", "英国", status, tier, null, false, checks, List.of(), "2026-10-01", false, "program",
                null);
    }

    private static AppPlanVo plan()
    {
        List<AppPlanVo.Item> items = new ArrayList<>();
        items.add(item(1, "学校A", "ok", "reach"));
        items.add(item(2, "学校B", "ok", "match"));
        items.add(item(3, "学校B", "ok", "match"));
        for (int i = 4; i <= 9; i++)
        {
            items.add(item(i, "学校" + i, "ok", "safety"));
        }
        items.add(item(10, "学校X", "consultant", null));
        items.add(item(11, "学校Y", "offline", null));
        return new AppPlanVo("full", items, Map.of("reach", 1, "match", 2, "safety", 6), List.of(new AppPlanVo.Hint("no_safety", 2)), List.of(), null);
    }

    @Test
    void viewDropsStudentScoresAndPassedDeadlines()
    {
        List<AppCalendarVo.Event> events = new ArrayList<>();
        events.add(new AppCalendarVo.Event(1L, "学校A", "项目1", "英国", "Round 1", "2026-10-01", -15, true, null, null));
        for (int i = 0; i < 12; i++)
        {
            events.add(new AppCalendarVo.Event(2L, "学校B", "项目2", "英国", "R" + i, "2026-11-" + (10 + i), 25 + i, false, null, null));
        }
        AppCalendarVo calendar = new AppCalendarVo(events, List.of(), List.of(), List.of(), null, "");
        AppMaterialsVo materials = new AppMaterialsVo(3, 10, List.of(), List.of(), "");
        AppSharedPlanVo vo = AbrPlanShare.view(plan(), calendar, materials);
        assertEquals(11, vo.items().size());
        assertTrue(vo.items().stream().flatMap(i -> i.checks().stream()).allMatch(c -> c.actual() == null));
        assertEquals("≥ 80", vo.items().get(0).checks().get(0).required());
        assertEquals(AbrPlanShare.DEADLINES, vo.deadlines().size());
        assertTrue(vo.deadlines().stream().noneMatch(AppCalendarVo.Event::passed));
        assertEquals(3, vo.materialsDone());
        assertEquals(10, vo.materialsTotal());
        String text = MAPPER.writeValueAsString(vo);
        assertFalse(text.contains("雅思 6.0") || text.contains("\"85\""), text);
    }

    @Test
    void cardHasOnlyGroupsAndSchoolNames()
    {
        Card c = AbrPlanShare.card(plan(), "某留学工作室", "https://m.example.com/?ch=share&ct=abc");
        assertEquals("我的选校单 · 英国", c.kicker());
        assertEquals("共 10 个项目", c.title());
        assertEquals(List.of("冲刺", "主攻", "保底", "待评估"), c.sections().stream().map(Card.Section::label).toList());
        assertEquals(List.of("学校B"), c.sections().get(1).lines());
        assertEquals("2 个项目", c.sections().get(1).summary());
        // 保底 6 所：最多显示 4 所，其余合并
        assertEquals(List.of("学校4", "学校5", "学校6", "学校7", "等 6 所院校"), c.sections().get(2).lines());
        assertTrue(c.extras().isEmpty());
        assertFalse(MAPPER.writeValueAsString(c).contains("学校Y"));
        assertFalse(MAPPER.writeValueAsString(c).contains("85"));
    }

    @Test
    void cardWithoutTiers()
    {
        AppPlanVo none = new AppPlanVo("none", List.of(item(1, "学校A", "need_profile", null)), Map.of(), List.of(), List.of("gpa"), null);
        Card c = AbrPlanShare.card(none, "b", "u");
        assertEquals(List.of("选校单"), c.sections().stream().map(Card.Section::label).toList());
        assertTrue(c.extras().isEmpty());
    }

    @Test
    void shareCodeFormat()
    {
        String code = AbrShareSupport.newShareCode();
        assertTrue(code.matches("^[23456789a-km-zA-HJ-NP-Z]{10}$"), code);
        assertFalse(code.equals(AbrShareSupport.newShareCode()));
    }
}
