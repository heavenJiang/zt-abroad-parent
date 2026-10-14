package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.ygb.common.exception.ServiceException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 测评答案合并规则测试：用种子问卷 v1 验证分步保存、清除、年龄段终止与未成年标志、访客档案的预填范围
 *
 * @author ygb
 */
class AbrAssessmentAnswersTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static AbrAssessmentAnswers.Questionnaire q;

    @BeforeAll
    static void load() throws IOException
    {
        q = AbrAssessmentAnswers.parse(Files.readString(Path.of("..", "seed", "questionnaire", "questionnaire_v1.json"), StandardCharsets.UTF_8));
    }

    private static ObjectNode json(String text)
    {
        return (ObjectNode) MAPPER.readTree(text);
    }

    @Test
    void parseReadsQuestionsAndPolicy()
    {
        assertEquals(25, q.questions().size());
        assertEquals("abr_privacy_policy", q.policyCode());
    }

    @Test
    void mergeSetsAndRemoves()
    {
        ObjectNode current = json("{\"target_degree\": \"master\", \"intake_year\": \"2027\"}");
        AbrAssessmentAnswers.Outcome outcome = AbrAssessmentAnswers.merge(q, current,
                json("{\"target_countries\": [\"UK\", \"HK\"], \"intake_year\": null}"));
        assertEquals(json("{\"target_countries\": [\"UK\", \"HK\"]}"), outcome.set());
        assertEquals(List.of("intake_year"), outcome.removed());
        assertFalse(outcome.terminated());
        assertFalse(outcome.clearAll());
        assertTrue(outcome.flags().isEmpty());
    }

    @Test
    void under14TerminatesWithoutSavingAnything()
    {
        AbrAssessmentAnswers.Outcome outcome = AbrAssessmentAnswers.merge(q, json("{\"target_degree\": \"master\"}"),
                json("{\"age_band\": \"under14\", \"target_field\": \"cs\"}"));
        assertTrue(outcome.terminated());
        assertTrue(outcome.clearAll());
        assertTrue(outcome.set().isEmpty());
    }

    @Test
    void minorFlagsFollowLatestAnswer()
    {
        ObjectNode current = json("{}");
        JsonNode minor = AbrAssessmentAnswers.merge(q, current, json("{\"age_band\": \"14_17\"}")).flags();
        assertTrue(minor.path("is_minor").asBoolean());
        assertTrue(minor.path("exclude_from_referral_unlock").asBoolean());
        JsonNode adult = AbrAssessmentAnswers.merge(q, current, json("{\"age_band\": \"18plus\"}")).flags();
        assertFalse(adult.path("is_minor").asBoolean(true));
    }

    @Test
    void rejectsUnknownQuestionEmptyPatchAndOversizedValues()
    {
        ObjectNode current = json("{}");
        assertThrows(ServiceException.class, () -> AbrAssessmentAnswers.merge(q, current, json("{\"nickname\": \"x\"}")));
        assertThrows(ServiceException.class, () -> AbrAssessmentAnswers.merge(q, current, json("{}")));
        assertThrows(ServiceException.class, () -> AbrAssessmentAnswers.merge(q, current, MAPPER.readTree("[1]")));
        ObjectNode big = MAPPER.createObjectNode().put("undergrad_school_manual", "长".repeat(AbrAssessmentAnswers.MAX_VALUE_CHARS + 1));
        assertThrows(ServiceException.class, () -> AbrAssessmentAnswers.merge(q, current, big));
    }

    @Test
    void rejectsTagLikeTextButKeepsComparisons()
    {
        ObjectNode current = json("{}");
        assertThrows(ServiceException.class, () -> AbrAssessmentAnswers.merge(q, current,
                json("{\"undergrad_school_manual\": \"某学院<b>x</b>\"}")));
        assertThrows(ServiceException.class, () -> AbrAssessmentAnswers.merge(q, current,
                json("{\"exp_work\": {\"has\": \"yes\", \"note\": [\"< script\"]}}")));
        AbrAssessmentAnswers.Outcome ok = AbrAssessmentAnswers.merge(q, current,
                json("{\"undergrad_school_manual\": \"均分>80，排名<10%，A&B 学院\"}"));
        assertEquals("均分>80，排名<10%，A&B 学院", ok.set().path("undergrad_school_manual").asString());
    }

    @Test
    void rejectsOversizedTotal()
    {
        ObjectNode current = MAPPER.createObjectNode();
        for (String id : List.of("target_field", "intake_year", "undergrad_major", "class_rank", "english_status", "std_test",
                "budget_total", "rank_vs_employment", "city_preference", "major_relevance_self", "gpa", "english_score",
                "exp_research", "exp_work", "exp_awards", "jlpt_level", "topik_level"))
        {
            current.put(id, "x".repeat(3900));
        }
        ObjectNode patch = MAPPER.createObjectNode().put("undergrad_school_manual", "x".repeat(3900));
        assertThrows(ServiceException.class, () -> AbrAssessmentAnswers.merge(q, current, patch));
    }

    @Test
    void prefillSkipsEligibilityFilesAndUnknown()
    {
        ObjectNode profile = json("{\"age_band\": \"18plus\", \"resume_upload\": [{\"file_id\": 1}], \"gpa\": {\"type\": \"percent\", \"total\": 85},"
                + " \"tool_only_field\": 1, \"target_degree\": null}");
        assertEquals(json("{\"gpa\": {\"type\": \"percent\", \"total\": 85}}"), AbrAssessmentAnswers.prefill(q, profile));
    }

    @Test
    void profilePatchUsesSameRules()
    {
        ObjectNode set = json("{\"age_band\": \"18plus\", \"target_countries\": [\"SG\"], \"resume_upload\": []}");
        assertEquals(json("{\"target_countries\": [\"SG\"]}"), AbrAssessmentAnswers.profilePatch(q, set));
    }

    @Test
    void objectAndArrayHelpers()
    {
        assertTrue(AbrAssessmentAnswers.object(null).isEmpty());
        assertTrue(AbrAssessmentAnswers.object("not json").isEmpty());
        assertTrue(AbrAssessmentAnswers.object("[1]").isEmpty());
        assertEquals("[\"a\",\"b\"]", AbrAssessmentAnswers.toJsonArray(List.of("a", "b")));
        assertEquals("[]", AbrAssessmentAnswers.toJsonArray(List.of()));
    }
}
