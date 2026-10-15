package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.vo.AppIeltsVo;
import com.ygb.abroad.domain.vo.AppOpenProgramsVo;
import com.ygb.abroad.domain.vo.AppRecognitionVo;
import com.ygb.abroad.engine.BackgroundProfile;
import com.ygb.abroad.engine.NormalizationRules;
import com.ygb.abroad.engine.ProfileNormalizer;
import com.ygb.abroad.engine.ProgramFacts;
import com.ygb.abroad.service.IAbrSelectionService;
import com.ygb.common.exception.ServiceException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 查询类入口工具纯函数测试：本校认可度、雅思够不够、现在还能申（含 90 天未核实不展示数值、截止日解析）
 *
 * @author ygb
 */
class AbrLookupToolsTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    private static AbrAssessmentAnswers.Questionnaire q;

    private static JsonNode uk;

    @BeforeAll
    static void load() throws IOException
    {
        q = AbrAssessmentAnswers.parse(Files.readString(Path.of("..", "seed", "questionnaire", "questionnaire_v1.json"), StandardCharsets.UTF_8));
        uk = MAPPER.readTree(Files.readString(Path.of("..", "seed", "engine-config", "uk_master.json"), StandardCharsets.UTF_8));
    }

    private static JsonNode json(String text)
    {
        return MAPPER.readTree(text);
    }

    private static BackgroundProfile profile(ObjectNode answers, List<String> ids)
    {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        ids.forEach(id -> {
            if (answers.get(id) != null)
            {
                map.put(id, answers.get(id));
            }
        });
        return ProfileNormalizer.normalize(map, NormalizationRules.parse(q.root()), id -> id == 1L ? "211" : null, TODAY);
    }

    private static AbrProgram program(long id, Date verified)
    {
        AbrProgram p = new AbrProgram();
        p.setProgramId(id);
        p.setSchoolName("学校" + id);
        p.setProgramName("项目" + id);
        p.setVerifiedTime(verified);
        p.setWebsite("https://example.edu/" + id);
        return p;
    }

    private static Date daysAgo(int days)
    {
        return Date.from(TODAY.minusDays(days).atStartOfDay(AbrLookupTools.BEIJING).toInstant());
    }

    /**
     * 英国 4 个项目：11 写明 211 ≥ 80、雅思 6.5（6.0）；12 不接收 211、雅思 7.0（6.5）；13 未写明（按 D4 兜底）；14 信息超过 90 天未核实
     */
    private static IAbrSelectionService.CatalogSlice slice()
    {
        ProgramFacts.Requirement r11 = new ProgramFacts.Requirement(Map.of("211", new BigDecimal("80")), Set.of(), new BigDecimal("6.5"),
                new BigDecimal("6.0"), null, null, null, false, null, null, null);
        ProgramFacts.Requirement r12 = new ProgramFacts.Requirement(Map.of(), Set.of("211"), new BigDecimal("7.0"), new BigDecimal("6.5"), null, null,
                null, false, null, null, "0");
        List<ProgramFacts> facts = List.of(new ProgramFacts(11L, "UK", "D2", "en", false, r11), new ProgramFacts(12L, "UK", "D1", "en", false, r12),
                new ProgramFacts(13L, "UK", "D4", "en", false, null), new ProgramFacts(14L, "UK", "D2", "en", false, r11));
        Map<Long, AbrProgram> programs = Map.of(11L, program(11, daysAgo(10)), 12L, program(12, daysAgo(10)), 13L, program(13, daysAgo(90)),
                14L, program(14, daysAgo(91)));
        return new IAbrSelectionService.CatalogSlice(Map.of("UK", uk), List.of("UK"), List.of("US"), facts, programs, Map.of());
    }

    @Test
    void recognitionListsEveryProgramWithoutTier()
    {
        ObjectNode a = AbrLookupTools.validateRecognition(json("{\"undergrad_school\": {\"kind\": \"cn\", \"university_id\": 1, \"name\": \"某校\"}}"));
        AppRecognitionVo vo = AbrLookupTools.recognition(slice(), profile(a, List.of("undergrad_school")), Map.of("UK", "英国", "US", "美国"), TODAY, 5L);
        assertEquals(5L, vo.toolRunId());
        assertEquals(List.of(), vo.notices());
        assertEquals("美国", vo.closedCountries().get(0).label());
        AppRecognitionVo.Country c = vo.countries().get(0);
        assertEquals(3, c.acceptedCount());
        assertEquals(1, c.notAcceptedCount());
        // 接收的在前，不接收的在最后
        assertEquals(List.of(11L, 13L, 14L, 12L), c.programs().stream().map(AppRecognitionVo.Program::programId).toList());
        assertEquals(new AppRecognitionVo.Program(11L, "学校11", "项目11", "accepted", "≥ 80", false, false), c.programs().get(0));
        assertTrue(c.programs().get(1).estimated());
        assertTrue(c.programs().get(1).gpaRequired().startsWith("≥ "));
        // 超过 90 天未核实：不展示数值
        assertNull(c.programs().get(2).gpaRequired());
        assertTrue(c.programs().get(2).unverified());
        assertEquals("not_accepted", c.programs().get(3).status());
        assertNull(c.programs().get(3).gpaRequired());
        assertFalse(MAPPER.writeValueAsString(vo).contains("211"));
        assertEquals(5L, vo.withToolRunId(5L).toolRunId());
    }

    @Test
    void recognitionSchoolNotFound()
    {
        ObjectNode a = AbrLookupTools.validateRecognition(json("{\"undergrad_school\": {\"kind\": \"not_found\", \"name\": \"某学院\"}}"));
        AppRecognitionVo vo = AbrLookupTools.recognition(slice(), profile(a, List.of("undergrad_school")), Map.of(), TODAY, 1L);
        assertEquals(List.of(AbrQuickTool.NOTICE_SCHOOL_NOT_FOUND), vo.notices());
        assertThrows(ServiceException.class, () -> AbrLookupTools.validateRecognition(json("{}")));
        assertThrows(ServiceException.class, () -> AbrLookupTools.validateRecognition(json("[]")));
    }

    @Test
    void ieltsOutcomesAndGaps()
    {
        ObjectNode a = AbrLookupTools.validateIelts(q,
                json("{\"english_score\": {\"type\": \"ielts\", \"total\": 6.5, \"min_sub\": 6}, \"target_countries\": [\"UK\"]}"));
        assertEquals("taken", a.path("english_status").asString());
        assertFalse(a.has("target_field"));
        BackgroundProfile p = profile(a, AbrLookupTools.QUESTIONS.get(AbrLookupTools.IELTS));
        AppIeltsVo vo = AbrLookupTools.ielts(slice(), p, Map.of("UK", "英国"), TODAY, 2L);
        assertEquals("雅思 6.5（小分 6.0）", vo.score());
        AppIeltsVo.Country c = vo.countries().get(0);
        assertEquals(List.of(11L, 13L, 12L, 14L), c.programs().stream().map(AppIeltsVo.Program::programId).toList());
        assertEquals("met", c.programs().get(0).outcome());
        assertEquals("雅思 6.5（小分 6.0）", c.programs().get(0).required());
        // 12：要求 7.0（6.5），项目明确不接受语言班 → 未达标，总分差 0.5、小分差 0.5
        AppIeltsVo.Program p12 = c.programs().get(2);
        assertEquals("not_met", p12.outcome());
        assertEquals(new BigDecimal("0.5"), p12.totalGap());
        assertEquals(new BigDecimal("0.5"), p12.subGap());
        // 14：超过 90 天未核实 → 无法判断，不展示要求
        AppIeltsVo.Program p14 = c.programs().get(3);
        assertEquals("unchecked", p14.outcome());
        assertNull(p14.required());
        assertEquals(1, c.uncheckedCount());
        assertEquals(c.programs().size(), c.metCount() + c.preSessionalCount() + c.notMetCount() + c.uncheckedCount());
    }

    @Test
    void ieltsPreSessionalWhenGapWithinCountryRule()
    {
        // 英国规则：差距在可读语言班范围内、项目写明接受语言班 → 读语言班可达标
        ProgramFacts.Requirement accepts = new ProgramFacts.Requirement(Map.of(), Set.of(), new BigDecimal("7.0"), null, null, null, null, false,
                null, null, "1");
        IAbrSelectionService.CatalogSlice s = new IAbrSelectionService.CatalogSlice(Map.of("UK", uk), List.of("UK"), List.of(),
                List.of(new ProgramFacts(31L, "UK", "D2", "en", false, accepts)), Map.of(31L, program(31, daysAgo(1))), Map.of());
        ObjectNode a = AbrLookupTools.validateIelts(q, json("{\"english_score\": {\"type\": \"ielts\", \"total\": 6.5}, \"target_countries\": [\"UK\"]}"));
        AppIeltsVo.Program p = AbrLookupTools.ielts(s, profile(a, AbrLookupTools.QUESTIONS.get(AbrLookupTools.IELTS)), Map.of(), TODAY, 1L)
                .countries().get(0).programs().get(0);
        assertEquals("pre_sessional", p.outcome());
        assertFalse(p.preSessionalUnverified());
        assertNull(p.totalGap());
    }

    @Test
    void ieltsValidation()
    {
        assertRejects("{\"english_score\": {\"type\": \"toefl_ibt\", \"total\": 100}, \"target_countries\": [\"UK\"]}", "请填写雅思成绩");
        assertRejects("{\"english_score\": {\"type\": \"ielts\"}, \"target_countries\": [\"UK\"]}", "请填写雅思总分");
        assertRejects("{\"english_score\": {\"type\": \"ielts\", \"total\": 6.3}, \"target_countries\": [\"UK\"]}", "0.5 为一档");
        assertRejects("{\"english_score\": {\"type\": \"ielts\", \"total\": 9.5}, \"target_countries\": [\"UK\"]}", "0.5 为一档");
        assertRejects("{\"english_score\": {\"type\": \"ielts\", \"total\": 6.5, \"min_sub\": 7}, \"target_countries\": [\"UK\"]}", "不能高于总分");
        assertRejects("{\"english_score\": {\"type\": \"ielts\", \"total\": \"6.5\"}, \"target_countries\": [\"UK\"]}", "应为数字");
        assertRejects("{\"english_score\": {\"type\": \"ielts\", \"total\": 6.5}}", "请选择目标国家 / 地区");
        assertRejects("{\"english_score\": {\"type\": \"ielts\", \"total\": 6.5}, \"target_countries\": [\"UK\"], \"target_field\": \"x\"}", "请选择专业方向");
        ObjectNode ok = AbrLookupTools.validateIelts(q,
                json("{\"english_score\": {\"type\": \"ielts\", \"total\": 7}, \"target_countries\": [\"UK\"], \"target_field\": \"finance\"}"));
        assertTrue(ok.path("english_score").path("min_sub").isNull());
        assertEquals("finance", ok.path("target_field").asString());
    }

    private static void assertRejects(String answers, String message)
    {
        ServiceException e = assertThrows(ServiceException.class, () -> AbrLookupTools.validateIelts(q, json(answers)));
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    @Test
    void parseIeltsText()
    {
        assertArrayEquals(new BigDecimal[] { new BigDecimal("7.0"), new BigDecimal("6.5") }, AbrLookupTools.parseIelts("雅思 7.0（小分 6.5）"));
        assertArrayEquals(new BigDecimal[] { new BigDecimal("6.5"), null }, AbrLookupTools.parseIelts("雅思 6.5"));
        assertNull(AbrLookupTools.parseIelts("托福 90"));
        assertNull(AbrLookupTools.parseIelts(null));
    }

    @Test
    void deadlineParsing()
    {
        AbrLookupTools.Deadline d = AbrLookupTools.deadline(
                "{\"rolling\":false,\"rounds\":[{\"name\":\"R1\",\"date\":\"2026-10-01\"},{\"name\":\"R3\",\"date\":\"2027-03-01\"},{\"name\":\"R2\",\"date\":\"2026-12-01\"}]}",
                TODAY);
        assertEquals(new AbrLookupTools.Deadline(true, "R2", LocalDate.of(2026, 12, 1), 2, false), d);
        // 当天截止仍算开放
        assertTrue(AbrLookupTools.deadline("{\"rounds\":[{\"name\":\"\",\"date\":\"2026-10-16\"}]}", TODAY).open());
        assertNull(AbrLookupTools.deadline("{\"rounds\":[{\"name\":\"\",\"date\":\"2026-10-16\"}]}", TODAY).nextRound());
        assertFalse(AbrLookupTools.deadline("{\"rounds\":[{\"name\":\"R1\",\"date\":\"2026-10-15\"}]}", TODAY).open());
        // 只写滚动录取：开放；滚动录取但轮次都已过：已截止
        assertTrue(AbrLookupTools.deadline("{\"rolling\":true}", TODAY).open());
        assertFalse(AbrLookupTools.deadline("{\"rolling\":true,\"rounds\":[{\"name\":\"R1\",\"date\":\"2026-01-01\"}]}", TODAY).open());
        assertNull(AbrLookupTools.deadline(null, TODAY));
        assertNull(AbrLookupTools.deadline("{\"rounds\":[{\"name\":\"R1\",\"date\":\"明年\"}]}", TODAY));
    }

    @Test
    void openProgramsSortedByDeadline()
    {
        List<ProgramFacts> facts = List.of(new ProgramFacts(21L, "UK", "D2", "en", false, null), new ProgramFacts(22L, "HK", "D2", "en", false, null),
                new ProgramFacts(23L, "UK", "D2", "en", false, null), new ProgramFacts(24L, "UK", "D2", "en", false, null),
                new ProgramFacts(25L, "UK", "D2", "en", false, null), new ProgramFacts(26L, "UK", "D2", "en", false, null));
        Map<Long, AbrProgram> programs = Map.of(21L, program(21, daysAgo(1)), 22L, program(22, daysAgo(1)), 23L, program(23, daysAgo(1)),
                24L, program(24, null), 25L, program(25, daysAgo(1)), 26L, program(26, daysAgo(1)));
        Map<Long, AbrRequirement> reqs = Map.of(21L, req("{\"rounds\":[{\"name\":\"R2\",\"date\":\"2027-01-10\"}]}"),
                22L, req("{\"rounds\":[{\"name\":\"R1\",\"date\":\"2026-11-30\"}]}"), 23L, req("{\"rolling\":true}"),
                24L, req("{\"rounds\":[{\"name\":\"R1\",\"date\":\"2026-11-01\"}]}"), 25L, req("{\"rounds\":[{\"name\":\"R1\",\"date\":\"2026-09-01\"}]}"));
        IAbrSelectionService.CatalogSlice slice = new IAbrSelectionService.CatalogSlice(Map.of(), List.of("UK", "HK"), List.of(), facts, programs, reqs);
        AppOpenProgramsVo vo = AbrLookupTools.open(slice, Map.of("UK", "英国", "HK", "中国香港"), TODAY, 9L);
        assertEquals("2026-10-16", vo.today());
        assertEquals(List.of(22L, 21L, 23L, 24L), vo.programs().stream().map(AppOpenProgramsVo.Program::programId).toList());
        AppOpenProgramsVo.Program hk = vo.programs().get(0);
        assertEquals(new AppOpenProgramsVo.Program(22L, "学校22", "项目22", "HK", "中国香港", "2027-fall", "R1", "2026-11-30", 45, 1, false, false,
                "https://example.edu/22"), hk);
        assertTrue(vo.programs().get(2).rolling());
        assertNull(vo.programs().get(2).nextDeadline());
        // 从未核实：不展示日期
        assertTrue(vo.programs().get(3).unverified());
        assertNull(vo.programs().get(3).nextDeadline());
        assertNull(vo.programs().get(3).daysLeft());
        assertEquals(1, vo.closedCount());
        assertEquals(1, vo.missingCount());
    }

    private static AbrRequirement req(String deadlines)
    {
        AbrRequirement r = new AbrRequirement();
        r.setSeason("2027-fall");
        r.setDeadlines(deadlines);
        return r;
    }

    @Test
    void staleAndTrace()
    {
        assertTrue(AbrLookupTools.stale(null, TODAY));
        assertTrue(AbrLookupTools.stale(program(1, null), TODAY));
        assertFalse(AbrLookupTools.stale(program(1, daysAgo(90)), TODAY));
        assertTrue(AbrLookupTools.stale(program(1, daysAgo(91)), TODAY));
        Map<Long, String> outcomes = new LinkedHashMap<>();
        outcomes.put(11L, "met");
        String trace = AbrLookupTools.trace(outcomes, slice()).toString();
        assertEquals("{\"programs\":[{\"programId\":11,\"outcome\":\"met\"}],\"skippedCountries\":[\"US\"]}", trace);
        // 其他工具的留痕没有分组，加入选校单时不带档位
        assertNull(AbrQuickTool.groupOf(trace, 11L));
    }
}
