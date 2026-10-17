package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrAssessmentFile;
import com.ygb.abroad.domain.AbrCnUniversity;
import com.ygb.abroad.domain.vo.AppResumeParseVo;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 简历解析纯函数测试：预处理去掉无关个人信息并找出姓名、模型输出逐项校验、多份文件合并与冲突标记
 *
 * @author ygb
 */
class AbrResumeParserTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static AbrAssessmentAnswers.Questionnaire q;

    private static final String RESUME = String.join("\n",
            "张三  |  13812345678",
            "姓名：张三   性别：男   出生年月：2003.05   籍贯：江苏南京",
            "家庭住址：北京市 海淀区 某某路 1 号",
            "教育背景",
            "2022.09-2026.06 北京大学 金融学 本科 学号：2200012345",
            "加权平均分：85.6/100",
            "IELTS 7.0（L 7.5 R 8.0 W 6.5 S 6.5）",
            "科研：参与国家自然科学基金课题",
            "实习：某证券公司研究所 2025.07-2025.10");

    @BeforeAll
    static void load() throws IOException
    {
        q = AbrAssessmentAnswers.parse(Files.readString(Path.of("..", "seed", "questionnaire", "questionnaire_v1.json"), StandardCharsets.UTF_8));
    }

    private static JsonNode json(String s)
    {
        return MAPPER.readTree(s);
    }

    private static AbrCnUniversity university(long id, String name, String aliases)
    {
        AbrCnUniversity u = new AbrCnUniversity();
        u.setCnUniversityId(id);
        u.setUniversityName(name);
        u.setAliases(aliases);
        return u;
    }

    private static final Function<String, List<AbrCnUniversity>> SEARCH = name -> switch (name)
    {
        case "北京大学", "北大" -> List.of(university(1L, "北京大学", "北大|PKU"));
        case "北京某某大学" -> List.of(university(7L, "北京某某科技大学", ""), university(8L, "北京某某师范大学", ""));
        default -> List.of();
    };

    @Test
    void prepareRemovesUnrelatedPersonalInfoAndFindsNames()
    {
        AbrResumeParser.Prepared p = AbrResumeParser.prepare(RESUME, 12000);
        assertEquals(List.of("张三"), p.names());
        String t = p.text();
        assertFalse(t.contains("2003.05"), t);
        assertFalse(t.contains("江苏南京"), t);
        assertFalse(t.contains("海淀区"), t);
        assertFalse(t.contains("2200012345"), t);
        assertTrue(t.contains("出生年月：[已去除]"), t);
        assertTrue(t.contains("家庭住址：[已去除]"), t);
        // 测评需要的内容保留；手机号、姓名由网关替换
        assertTrue(t.contains("北京大学 金融学") && t.contains("85.6") && t.contains("IELTS 7.0"), t);
        assertTrue(t.contains("13812345678"));
    }

    @Test
    void prepareSkipsTitlesAndTruncates()
    {
        AbrResumeParser.Prepared p = AbrResumeParser.prepare("个人简历\nName: Li Hua\n北京大学", 5);
        assertEquals(List.of("Li Hua"), p.names());
        assertEquals(5, p.text().length());
        assertEquals(List.of(), AbrResumeParser.prepare("北京大学\n金融学", 100).names());
        assertEquals(List.of("Wang Fang"), AbrResumeParser.prepare("Wang Fang\nPeking University", 100).names());
        assertEquals(List.of(), AbrResumeParser.prepare("Curriculum Vitae\nxx", 100).names());
        assertEquals("", AbrResumeParser.prepare(null, 100).text());
        assertEquals(List.of("李四"), AbrResumeParser.prepare("个人简历\n姓名：李四性别：女", 100).names());
        assertEquals(List.of("欧阳娜娜"), AbrResumeParser.prepare("x\n姓名 欧阳娜娜 手机", 100).names());
        assertEquals(List.of(), AbrResumeParser.prepare("x\nSchool Name: Peking University", 100).names());
    }

    @Test
    void validateKeepsFactsFoundInSourceText()
    {
        String text = AbrResumeParser.prepare(RESUME, 12000).text();
        JsonNode ai = json("{\"school\":{\"name\":\"北京大学\",\"overseas\":false},\"major\":\"金融学\","
                + "\"gpa\":{\"type\":\"percent\",\"total\":85.6,\"max\":null},\"english\":{\"type\":\"ielts\",\"total\":7,\"min_sub\":6.5},"
                + "\"research\":{\"count\":1,\"level\":\"lab_project\"},\"work\":{\"count\":1,\"total_months\":4,\"fulltime_months\":0},\"awards\":null}");
        ObjectNode r = AbrResumeParser.validate(q, ai, text, SEARCH);
        JsonNode s = r.get("suggestions");
        assertEquals(json("{\"kind\":\"cn\",\"university_id\":1,\"name\":\"北京大学\"}").toString(), String.valueOf(s.get("undergrad_school")));
        assertEquals(json("{\"value\":\"finance\",\"text\":\"金融学\",\"matched\":true}").toString(), String.valueOf(s.get("undergrad_major")));
        assertEquals("percent", s.get("gpa").get("type").asString());
        assertEquals(0, s.get("gpa").get("total").decimalValue().compareTo(new java.math.BigDecimal("85.6")));
        assertEquals("taken", s.get("english_status").asString());
        assertEquals(0, s.get("english_score").get("min_sub").decimalValue().compareTo(new java.math.BigDecimal("6.5")));
        assertEquals(json("{\"has\":\"yes\",\"count\":1,\"level\":\"lab_project\"}").toString(), String.valueOf(s.get("exp_research")));
        assertEquals(json("{\"has\":\"yes\",\"count\":1,\"total_months\":4,\"fulltime_months\":0}").toString(), String.valueOf(s.get("exp_work")));
        assertNull(s.get("exp_awards"));
        assertEquals(0, r.get("dropped").size(), r.toString());
    }

    @Test
    void validateDropsHallucinatedOrOutOfRangeValues()
    {
        String text = "某某学院 会计学\nGPA 3.62/4\n托福 102\n获得全国大学生数学建模竞赛一等奖";
        JsonNode ai = json("{\"school\":{\"name\":\"清华大学\",\"overseas\":false},\"major\":\"金融学\","
                + "\"gpa\":{\"type\":\"percent\",\"total\":88,\"max\":null},\"english\":{\"type\":\"ielts\",\"total\":7.3,\"min_sub\":null},"
                + "\"research\":null,\"work\":{\"count\":30,\"total_months\":3,\"fulltime_months\":6},"
                + "\"awards\":{\"count\":1,\"level\":\"world\"}}");
        ObjectNode r = AbrResumeParser.validate(q, ai, text, SEARCH);
        JsonNode s = r.get("suggestions");
        // 校名、专业不在原文；均分 88 不在原文；雅思 7.3 不在原文且不符合 0.5 步长
        assertNull(s.get("undergrad_school"));
        assertNull(s.get("undergrad_major"));
        assertNull(s.get("gpa"));
        assertNull(s.get("english_score"));
        assertNull(s.get("english_status"));
        // 段数超出范围、全职月数大于累计月数的字段去掉，“有”保留；级别不是选项的去掉
        assertEquals(json("{\"has\":\"yes\",\"total_months\":3}").toString(), String.valueOf(s.get("exp_work")));
        assertEquals(json("{\"has\":\"yes\",\"count\":1}").toString(), String.valueOf(s.get("exp_awards")));
        assertTrue(r.get("dropped").size() >= 7, r.toString());
    }

    @Test
    void validateScoreTypesAndSubScores()
    {
        String text = "GPA 3.62/4.3\nTOEFL 102 (R 28 L 27 S 22 W 25)\n托福小分 31";
        JsonNode ai = json("{\"school\":null,\"major\":null,\"gpa\":{\"type\":\"other\",\"total\":3.62,\"max\":4.3},"
                + "\"english\":{\"type\":\"toefl_ibt\",\"total\":102,\"min_sub\":31},\"research\":null,\"work\":null,\"awards\":null}");
        JsonNode s = AbrResumeParser.validate(q, ai, text, SEARCH).get("suggestions");
        assertEquals(0, s.get("gpa").get("max").decimalValue().compareTo(new java.math.BigDecimal("4.3")));
        // 小分 31 超过托福单项满分 30：只去掉小分
        assertTrue(s.get("english_score").get("min_sub").isNull());
        assertEquals(102, s.get("english_score").get("total").asInt());

        JsonNode noMax = json("{\"school\":null,\"major\":null,\"gpa\":{\"type\":\"other\",\"total\":3.62,\"max\":null},"
                + "\"english\":{\"type\":\"cet6\",\"total\":550,\"min_sub\":null},\"research\":null,\"work\":null,\"awards\":null}");
        JsonNode s2 = AbrResumeParser.validate(q, noMax, text, SEARCH).get("suggestions");
        assertNull(s2.get("gpa"));
        assertNull(s2.get("english_score"));
    }

    @Test
    void schoolMatchingUsesAliasesOverseasAndCandidates()
    {
        JsonNode alias = json("{\"school\":{\"name\":\"北大\",\"overseas\":false},\"major\":\"国际经济与贸易\",\"gpa\":null,\"english\":null,"
                + "\"research\":null,\"work\":null,\"awards\":null}");
        JsonNode s = AbrResumeParser.validate(q, alias, "北大 国际经济与贸易", SEARCH).get("suggestions");
        assertEquals(1L, s.get("undergrad_school").get("university_id").asLong());
        assertEquals("北京大学", s.get("undergrad_school").get("name").asString());
        assertEquals("economics", s.get("undergrad_major").get("value").asString());

        JsonNode overseas = json("{\"school\":{\"name\":\"University of Toronto\",\"overseas\":true},\"major\":\"Statistics\",\"gpa\":null,"
                + "\"english\":null,\"research\":null,\"work\":null,\"awards\":null}");
        JsonNode s2 = AbrResumeParser.validate(q, overseas, "University  of Toronto\nStatistics", SEARCH).get("suggestions");
        assertEquals("overseas", s2.get("undergrad_school").get("kind").asString());
        assertTrue(s2.get("undergrad_school").get("university_id").isNull());
        assertEquals(json("{\"value\":\"other\",\"text\":\"Statistics\",\"matched\":false}").toString(), String.valueOf(s2.get("undergrad_major")));

        JsonNode fuzzy = json("{\"school\":{\"name\":\"北京某某大学\",\"overseas\":false},\"major\":null,\"gpa\":null,\"english\":null,"
                + "\"research\":null,\"work\":null,\"awards\":null}");
        ObjectNode r = AbrResumeParser.validate(q, fuzzy, "北京某某大学", SEARCH);
        assertNull(r.get("suggestions").get("undergrad_school"));
        assertEquals(2, r.get("schoolCandidates").size());
        assertEquals(7L, r.get("schoolCandidates").get(0).get("universityId").asLong());
    }

    @Test
    void inRangeChecksStep()
    {
        JsonNode ielts = json("{\"min\":0,\"max\":9,\"step\":0.5}");
        assertTrue(AbrResumeParser.inRange(new java.math.BigDecimal("6.5"), ielts));
        assertFalse(AbrResumeParser.inRange(new java.math.BigDecimal("6.3"), ielts));
        assertFalse(AbrResumeParser.inRange(new java.math.BigDecimal("9.5"), ielts));
        assertFalse(AbrResumeParser.inRange(new java.math.BigDecimal("-1"), ielts));
        assertTrue(AbrResumeParser.inRange(new java.math.BigDecimal("1000"), json("{}")));
    }

    private static AbrAssessmentFile file(long id, String kind, String status, String parsed, String failReason)
    {
        AbrAssessmentFile f = new AbrAssessmentFile();
        f.setFileId(id);
        f.setFileKind(kind);
        f.setParseStatus(status);
        f.setParsed(parsed);
        f.setFailReason(failReason);
        f.setOriginalName(kind + ".pdf");
        f.setFileSize(1024L);
        f.setCreateTime(new Date());
        return f;
    }

    @Test
    void mergePrefersTranscriptForGpaAndMarksConflicts()
    {
        String resume = "{\"suggestions\":{\"undergrad_school\":{\"kind\":\"cn\",\"university_id\":1,\"name\":\"北京大学\"},"
                + "\"gpa\":{\"type\":\"percent\",\"total\":85},\"exp_research\":{\"has\":\"yes\",\"count\":1}},\"schoolCandidates\":[]}";
        String transcript = "{\"suggestions\":{\"gpa\":{\"type\":\"percent\",\"total\":85.6}},\"schoolCandidates\":[]}";
        ObjectNode answers = (ObjectNode) json("{\"undergrad_school\":{\"kind\":\"cn\",\"university_id\":2,\"name\":\"清华大学\"},"
                + "\"exp_research\":{\"has\":\"yes\",\"count\":1,\"level\":\"conference\",\"relevant\":\"yes\"}}");
        AppResumeParseVo vo = AbrResumeParser.merge(List.of(file(1, "resume", "done", resume, null), file(2, "transcript", "done", transcript, null)),
                answers, AbrResumeServiceImpl.MESSAGES);
        assertEquals("done", vo.status());
        assertEquals(List.of("undergrad_school", "gpa", "exp_research"), vo.suggestions().stream().map(AppResumeParseVo.Suggestion::questionId).toList());
        AppResumeParseVo.Suggestion school = vo.suggestions().get(0);
        assertEquals(AppResumeParseVo.MATCH_CONFLICT, school.match());
        assertEquals("清华大学", school.current().get("name").asString());
        AppResumeParseVo.Suggestion gpa = vo.suggestions().get(1);
        assertEquals("transcript", gpa.source());
        assertEquals(AppResumeParseVo.MATCH_NEW, gpa.match());
        assertNull(gpa.current());
        // 经历：建议中的字段都与当前答案一致，学生补选的字段不算冲突
        assertEquals(AppResumeParseVo.MATCH_SAME, vo.suggestions().get(2).match());
        assertEquals("已识别 3 项，请逐项确认", vo.files().get(0).message());
        assertTrue(vo.schoolCandidates().isEmpty());
    }

    @Test
    void mergeStatusesAndCandidates()
    {
        ObjectNode empty = MAPPER.createObjectNode();
        assertEquals(AppResumeParseVo.STATUS_NONE, AbrResumeParser.merge(List.of(), empty, AbrResumeServiceImpl.MESSAGES).status());

        AppResumeParseVo running = AbrResumeParser.merge(List.of(file(1, "resume", "running", null, null),
                file(2, "transcript", "failed", null, "ocr_unavailable")), empty, AbrResumeServiceImpl.MESSAGES);
        assertEquals("running", running.status());
        assertNull(running.files().get(0).message());
        assertTrue(running.files().get(1).message().contains("暂不能自动识别"));

        AppResumeParseVo failed = AbrResumeParser.merge(List.of(file(1, "resume", "failed", null, "unknown_reason")), empty,
                AbrResumeServiceImpl.MESSAGES);
        assertEquals("failed", failed.status());
        assertEquals("没识别出来，手动填也只要 1 分钟", failed.files().get(0).message());

        String withCandidates = "{\"suggestions\":{},\"schoolCandidates\":[{\"universityId\":7,\"name\":\"北京某某科技大学\"}]}";
        AppResumeParseVo c = AbrResumeParser.merge(List.of(file(1, "resume", "done", withCandidates, null)), empty, AbrResumeServiceImpl.MESSAGES);
        assertEquals(1, c.schoolCandidates().size());
        assertEquals("北京某某科技大学", c.schoolCandidates().get(0).name());
    }

    @Test
    void coveredComparesNumbersByValue()
    {
        assertTrue(AbrResumeParser.covered(json("{\"type\":\"ielts\",\"total\":7,\"min_sub\":null}"), json("{\"type\":\"ielts\",\"total\":7.0}")));
        assertFalse(AbrResumeParser.covered(json("{\"type\":\"ielts\",\"total\":7}"), json("{\"type\":\"ielts\",\"total\":6.5}")));
        assertTrue(AbrResumeParser.covered(json("\"taken\""), json("\"taken\"")));
        assertFalse(AbrResumeParser.covered(json("\"taken\""), json("\"estimated\"")));
    }

    @Test
    void inputUsesKindLabel()
    {
        AbrResumeParser.Prepared p = new AbrResumeParser.Prepared("文字", List.of("张三"));
        assertEquals("成绩单", AbrResumeParser.input("transcript", p).get("kind"));
        assertEquals("简历", AbrResumeParser.input("resume", p).get("kind"));
        assertFalse(AbrResumeParser.input("resume", p).containsKey("names"));
    }
}
