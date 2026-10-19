package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrLead;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 线索评分测试：各规则得分与理由、本项上限、入学时间档、来源取最高、活跃度减分、停用规则不计分、分级线、参数校验、特征换算
 *
 * @author ygb
 */
class AbrLeadScorerTest
{
    private static JsonNode json(String s) throws Exception
    {
        return JsonMapper.builder().build().readTree(s);
    }

    private static Map<String, JsonNode> rules() throws Exception
    {
        Map<String, JsonNode> m = new LinkedHashMap<>();
        m.put("contact", json("{\"phone\":15,\"wechat\":5}"));
        m.put("moment", json("{\"imbalance\":15,\"requirement_unmet\":20,\"materials_behind\":20,\"parent_view\":15,\"repeated_edits\":10,\"cap\":40}"));
        m.put("intake", json("{\"months_12\":20,\"months_18\":10}"));
        m.put("tools", json("{\"assessment\":15,\"runs_3\":15,\"runs_1\":5,\"days\":30}"));
        m.put("plan", json("{\"items_3\":10,\"items_1\":5}"));
        m.put("parent", json("{\"share\":10,\"viewed\":10}"));
        m.put("source", json("{\"referral\":15,\"partner\":10,\"lecture\":10,\"wecom\":5,\"dm\":5}"));
        m.put("recency", json("{\"days_7\":10,\"stale_30\":-10}"));
        m.put("grade", json("{\"a\":60,\"b\":30}"));
        return m;
    }

    private static AbrLeadScorer.Features hot()
    {
        return new AbrLeadScorer.Features(true, true, Set.of("imbalance", "materials_behind", "parent_view"), 10, true, 4, 5, true, true,
                Set.of("h5", "lecture", "referral"), 1);
    }

    @Test
    void hotLeadIsAWithReasons() throws Exception
    {
        AbrLeadScorer.Result r = AbrLeadScorer.score(hot(), rules());
        // 联系 20 + 时刻 40（上限）+ 入学 20 + 工具 30 + 选校单 10 + 家长 20 + 来源 15 + 活跃 10
        assertEquals(165, r.score());
        assertEquals("A", r.grade());
        Map<String, Object> moment = r.reasons().stream().filter(x -> "moment".equals(x.get("rule"))).findFirst().orElseThrow();
        assertEquals(40, moment.get("points"));
        assertTrue(((String) moment.get("detail")).contains("本项上限 40 分"));
        assertEquals("来自转介绍", r.reasons().stream().filter(x -> "source".equals(x.get("rule"))).findFirst().orElseThrow().get("detail"));
    }

    @Test
    void coldLeadAndBands() throws Exception
    {
        AbrLeadScorer.Features cold = new AbrLeadScorer.Features(false, false, Set.of(), 30, false, 0, 0, false, false, Set.of("h5"), 40);
        AbrLeadScorer.Result r = AbrLeadScorer.score(cold, rules());
        assertEquals(-10, r.score());
        assertEquals("C", r.grade());
        assertEquals(1, r.reasons().size(), "0 分的不列");
        AbrLeadScorer.Features mid = new AbrLeadScorer.Features(true, true, Set.of(), 15, false, 1, 1, false, false, Set.of("h5"), 10);
        r = AbrLeadScorer.score(mid, rules());
        assertEquals(15 + 5 + 10 + 5 + 5, r.score());
        assertEquals("B", r.grade());
        assertEquals("距入学约 15 个月", r.reasons().stream().filter(x -> "intake".equals(x.get("rule"))).findFirst().orElseThrow().get("detail"));
    }

    @Test
    void disabledRulesDoNotCount() throws Exception
    {
        Map<String, JsonNode> only = new HashMap<>();
        only.put("grade", json("{\"a\":20,\"b\":10}"));
        only.put("contact", json("{\"phone\":15,\"wechat\":5}"));
        AbrLeadScorer.Result r = AbrLeadScorer.score(hot(), only);
        assertEquals(20, r.score());
        assertEquals("A", r.grade());
        assertEquals("A", AbrLeadScorer.grade(60, null));
        assertEquals("B", AbrLeadScorer.grade(30, null));
        assertEquals("C", AbrLeadScorer.grade(29, null));
    }

    @Test
    void validateParams() throws Exception
    {
        assertNull(AbrLeadScorer.validate("contact", json("{\"phone\":20}")));
        assertTrue(AbrLeadScorer.validate("contact", json("{\"email\":5}")).contains("不是这条规则的参数"));
        assertTrue(AbrLeadScorer.validate("contact", json("{\"phone\":1.5}")).contains("整数"));
        assertTrue(AbrLeadScorer.validate("contact", json("{\"phone\":500}")).contains("超出范围"));
        assertTrue(AbrLeadScorer.validate("grade", json("{\"a\":30,\"b\":60}")).contains("a > b > 0"));
        assertNull(AbrLeadScorer.validate("grade", json("{\"a\":120,\"b\":40}")));
        assertTrue(AbrLeadScorer.validate("tools", json("{\"days\":0}")).contains("1–365"));
        assertTrue(AbrLeadScorer.validate("nope", json("{}")).contains("不存在"));
        assertTrue(AbrLeadScorer.validate("plan", json("[]")).contains("JSON 对象"));
    }

    @Test
    void featuresFromQuery()
    {
        LocalDate today = LocalDate.of(2026, 10, 19);
        AbrLead lead = new AbrLead();
        lead.setPhoneHash("x");
        lead.setAppUserId(1L);
        lead.setLastTouchTime(Date.from(today.minusDays(3).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));
        Map<String, Object> f = new HashMap<>();
        f.put("moments", "imbalance,parent_view");
        f.put("intakeYear", "2027");
        f.put("monthsToIntake", 99);
        f.put("submitted", 1L);
        f.put("toolRuns", 2L);
        f.put("planItems", 3L);
        f.put("shares", 0L);
        f.put("shareViews", 0L);
        f.put("sources", "h5,lecture");
        AbrLeadScorer.Features x = AbrLeadScoreServiceImpl.features(lead, f, today);
        assertEquals(11, x.monthsToIntake(), "2026-10 到 2027-09");
        assertEquals(Set.of("imbalance", "parent_view"), x.moments());
        assertEquals(3, x.daysSinceLastTouch());
        assertTrue(x.hasPhone() && x.hasWechat() && x.assessmentSubmitted());
        f.put("intakeYear", null);
        assertEquals(99, AbrLeadScoreServiceImpl.features(lead, f, today).monthsToIntake(), "没有入学年份时用测评档案的月数");
        lead.setLastTouchTime(null);
        assertEquals(Long.MAX_VALUE, AbrLeadScoreServiceImpl.features(lead, Map.of(), today).daysSinceLastTouch());
        assertEquals(List.of(), List.copyOf(AbrLeadScoreServiceImpl.features(lead, Map.of(), today).moments()));
    }
}
