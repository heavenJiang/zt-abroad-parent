package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.vo.AppReportVo;
import com.ygb.abroad.growth.card.Card;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 定位海报图卡数据测试：默认不含个人信息、勾选后显示、未成年人勾选无效、数字取自报告
 *
 * @author ygb
 */
class AbrReportPosterTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final String URL = "https://m.example.com/?ch=share&ct=abc";

    private static AppReportVo report;

    private static ObjectNode answers;

    @BeforeAll
    static void load() throws IOException
    {
        AbrAssessmentReportTest.load();
        answers = AbrAssessmentReportTest.answers();
        report = AbrAssessmentReportTest.report(answers, MAPPER.createObjectNode());
    }

    private static Card card(boolean minor, boolean school, boolean gpa)
    {
        return AbrReportPoster.card(report, answers, minor, school, gpa, List.of("英国"), "管理", "硕士", "某留学工作室", URL);
    }

    @Test
    void defaultHasOnlyTiersAndSchoolNames()
    {
        Card c = card(false, false, false);
        assertEquals("某留学工作室", c.brand());
        assertEquals("英国 · 管理 · 硕士", c.kicker());
        assertEquals("主攻档 " + report.tiers().get(1).count() + " 个项目", c.title());
        assertEquals(List.of("冲刺", "主攻", "保底"), c.sections().stream().map(Card.Section::label).toList());
        for (int i = 0; i < 3; i++)
        {
            AppReportVo.Tier t = report.tiers().get(i);
            assertEquals("共 " + t.count() + " 个项目", c.sections().get(i).summary());
            assertTrue(c.sections().get(i).lines().size() <= AbrReportPoster.SCHOOLS);
        }
        assertTrue(c.extras().isEmpty());
        assertEquals(URL, c.qrContent());
        assertTrue(c.footnote().contains("不代表录取承诺"));
        String text = MAPPER.writeValueAsString(c);
        for (String forbidden : List.of("某大学", "85", "211", "雅思", "项目2"))
        {
            assertFalse(text.contains(forbidden), forbidden + " 出现在默认海报中：" + text);
        }
    }

    @Test
    void optInShowsSchoolAndGpaButNotForMinors()
    {
        assertEquals(List.of("本科：某大学", "均分：85"), card(false, true, true).extras());
        assertEquals(List.of("均分：85"), card(false, false, true).extras());
        assertTrue(card(true, true, true).extras().isEmpty());
    }

    @Test
    void gpaText()
    {
        assertEquals("3.6 / 4", AbrReportPoster.gpaText(MAPPER.readTree("{\"type\": \"gpa4\", \"total\": 3.60}")));
        assertEquals("8.5 / 10", AbrReportPoster.gpaText(MAPPER.readTree("{\"type\": \"other\", \"total\": 8.5, \"max\": 10}")));
        assertEquals("88", AbrReportPoster.gpaText(MAPPER.readTree("{\"type\": \"percent\", \"total\": 88.0}")));
        assertNull(AbrReportPoster.gpaText(MAPPER.readTree("{}")));
    }
}
