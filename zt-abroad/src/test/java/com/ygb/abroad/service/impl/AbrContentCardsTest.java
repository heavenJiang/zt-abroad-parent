package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.growth.card.Card;
import com.ygb.common.exception.ServiceException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 内容图卡模板测试：参数校验、项目清单 / 截止日历 / 对比表取值、过期显示“以官网为准”、引用依据与图上文字、引用合并
 *
 * @author ygb
 */
class AbrContentCardsTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    private static final Map<String, String> LABELS = Map.of("UK", "英国", "HK", "中国香港");

    private static Date daysAgo(int days)
    {
        return Date.from(TODAY.minusDays(days).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
    }

    private static AbrProgram program(long id, String school, String name, String country, int verifiedDaysAgo)
    {
        AbrProgram p = new AbrProgram();
        p.setProgramId(id);
        p.setSchoolName(school);
        p.setProgramName(name);
        p.setCountry(country);
        p.setTuition(new BigDecimal("32500"));
        p.setCurrency("GBP");
        p.setDurationMonths(12);
        p.setVerifiedTime(daysAgo(verifiedDaysAgo));
        p.setStatus("0");
        return p;
    }

    private static AbrRequirement req(long id, long programId, String ielts, String sub, String deadlines)
    {
        AbrRequirement r = new AbrRequirement();
        r.setRequirementId(id);
        r.setProgramId(programId);
        r.setIeltsOverall(ielts == null ? null : new BigDecimal(ielts));
        r.setIeltsMinSub(sub == null ? null : new BigDecimal(sub));
        r.setDeadlines(deadlines);
        return r;
    }

    private static final Map<Long, AbrProgram> PROGRAMS = Map.of(
            1L, program(1, "甲大学", "管理学硕士", "UK", 10),
            2L, program(2, "乙大学", "金融硕士", "HK", 30),
            3L, program(3, "丙大学", "教育硕士", "UK", 200));

    private static final Map<Long, AbrRequirement> REQS = Map.of(
            1L, req(11, 1, "7.0", "6.5", "{\"rounds\":[{\"name\":\"R1\",\"date\":\"2026-11-30\"},{\"name\":\"R2\",\"date\":\"2027-01-31\"}]}"),
            2L, req(12, 2, "6.5", null, "{\"rounds\":[{\"name\":\"第一轮\",\"date\":\"2026-11-15\"}]}"),
            3L, req(13, 3, "6.5", "6.0", "{\"rounds\":[{\"name\":\"R1\",\"date\":\"2026-12-01\"}]}"));

    private static AbrContentCards.Params params(String template, String json)
    {
        return AbrContentCards.params(template, MAPPER.readTree(json));
    }

    private static AbrContentCards.Built build(String template, String json)
    {
        return AbrContentCards.build(template, params(template, json), "测试标题", PROGRAMS, REQS, LABELS, TODAY, "工作室",
                "https://h5.example.com/?ch=a&ct=c1", "扫码测一下");
    }

    @Test
    void paramsValidation()
    {
        assertThrows(ServiceException.class, () -> params("poster", "{}"));
        assertThrows(ServiceException.class, () -> params("cover", "{\"programIds\":[1]}"));
        assertThrows(ServiceException.class, () -> params("comparison", "{\"programIds\":[1]}"));
        assertThrows(ServiceException.class, () -> params("program_list", "{\"programIds\":[\"1\"]}"));
        assertThrows(ServiceException.class, () -> params("program_list", "{\"programIds\":[1],\"fields\":[\"rank\"]}"));
        assertEquals(List.of("ielts", "deadline"), params("program_list", "{\"programIds\":[1,1]}").fields());
        assertEquals(List.of(1L), params("program_list", "{\"programIds\":[1,1]}").programIds());
        assertEquals(AbrContentCards.FIELDS, params("comparison", "{\"programIds\":[1,2]}").fields());
    }

    @Test
    void programListUsesCatalogValuesAndHidesStale()
    {
        AbrContentCards.Built b = build("program_list", "{\"kicker\":\"英国 · 管理\",\"programIds\":[1,3],\"fields\":[\"ielts\",\"tuition\"]}");
        Card card = b.card();
        assertEquals("英国 · 管理", card.kicker());
        assertEquals("甲大学", card.sections().get(0).label());
        assertEquals("英国", card.sections().get(0).summary());
        assertEquals(List.of("管理学硕士", "雅思：7.0（单项 6.5）", "学费：GBP 32,500 / 年"), card.sections().get(0).lines());
        assertEquals(List.of("教育硕士", "雅思：以官网为准", "学费：以官网为准"), card.sections().get(1).lines());
        assertEquals(4, b.citations().size());
        assertEquals("program", b.citations().get(0).get("type"));
        assertEquals(13L, b.citations().get(3).get("id"));
        assertEquals(TODAY.minusDays(200), b.dataDate());
        assertTrue(b.text().contains("雅思：7.0（单项 6.5）"));
        assertTrue(b.text().contains("扫码测一下"));
        assertTrue(card.footnote().contains("以官网为准"));
    }

    @Test
    void deadlineCalendarSortsAndGroups()
    {
        Card card = build("deadline_calendar", "{\"programIds\":[1,2,3]}").card();
        assertEquals("11 月", card.sections().get(0).label());
        assertEquals("2026 年 · 2 个项目", card.sections().get(0).summary());
        assertEquals(List.of("11-15  乙大学 · 金融硕士（第一轮）", "11-30  甲大学 · 管理学硕士（R1）"), card.sections().get(0).lines());
        assertEquals("其他", card.sections().get(1).label());
        assertEquals(List.of("丙大学 · 教育硕士：截止日以官网为准"), card.sections().get(1).lines());
    }

    @Test
    void comparisonAndMissingProgram()
    {
        Card card = build("comparison", "{\"programIds\":[1,2],\"fields\":[\"ielts\",\"deadline\",\"duration\"]}").card();
        assertEquals(4, card.sections().size());
        assertEquals(List.of("甲大学：7.0（单项 6.5）", "乙大学：6.5"), card.sections().get(1).lines());
        assertEquals(List.of("甲大学：2026-11-30（R1）", "乙大学：2026-11-15（第一轮）"), card.sections().get(2).lines());
        assertEquals(List.of("甲大学：12 个月", "乙大学：12 个月"), card.sections().get(3).lines());
        assertThrows(ServiceException.class, () -> build("comparison", "{\"programIds\":[1,99]}"));
        Map<Long, AbrProgram> same = Map.of(1L, program(1, "甲大学", "管理", "UK", 1), 2L, program(2, "甲大学", "金融", "UK", 1));
        Card two = AbrContentCards.build("comparison", params("comparison", "{\"programIds\":[1,2],\"fields\":[\"duration\"]}"), "t", same, REQS, LABELS,
                TODAY, "b", null, null).card();
        assertEquals(List.of("甲大学 · 管理：12 个月", "甲大学 · 金融：12 个月"), two.sections().get(1).lines());
    }

    @Test
    void coverHasNoNumbers()
    {
        AbrContentCards.Built b = build("cover", "{\"kicker\":\"考研失利\"}");
        assertTrue(b.card().sections().isEmpty());
        assertTrue(b.citations().isEmpty());
        assertNull(b.card().footnote());
        assertNull(b.dataDate());
    }

    @Test
    void valueFallbacks()
    {
        AbrProgram p = program(9, "丁大学", "x", "UK", 1);
        p.setTuition(null);
        p.setDurationMonths(null);
        assertEquals(AbrContentCards.UNVERIFIED, AbrContentCards.value("tuition", p, null, TODAY));
        assertEquals(AbrContentCards.UNVERIFIED, AbrContentCards.value("duration", p, null, TODAY));
        assertEquals(AbrContentCards.UNVERIFIED, AbrContentCards.value("ielts", p, null, TODAY));
        assertEquals("本季已截止", AbrContentCards.value("deadline", p, req(1, 9, null, null, "{\"rounds\":[{\"date\":\"2026-01-01\"}]}"), TODAY));
        assertEquals("滚动录取", AbrContentCards.value("deadline", p, req(1, 9, null, null, "{\"rolling\":true}"), TODAY));
    }

    @Test
    void mergeCitationsReplacesCardOnes()
    {
        String existing = "[{\"type\":\"policy\",\"id\":5},{\"type\":\"program\",\"id\":1,\"source\":\"card\"},{\"type\":\"url\",\"url\":\"https://x.ac.uk\"}]";
        String merged = AbrContentDesignServiceImpl.mergeCitations(existing,
                List.of(Map.of("type", "program", "id", 2L, "source", "card"), Map.of("type", "policy", "id", 5L, "source", "card")));
        assertEquals(MAPPER.readTree("[{\"type\":\"policy\",\"id\":5},{\"type\":\"url\",\"url\":\"https://x.ac.uk\"},{\"type\":\"program\",\"id\":2,\"source\":\"card\"}]"),
                MAPPER.readTree(merged));
        assertFalse(merged.contains("\"id\":1"));
    }
}
