package com.ygb.abroad.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 显示条件测试：运算符、路径、组合条件、被隐藏题目的连锁隐藏，以及种子问卷的实际条件
 *
 * @author ygb
 */
class AnswerVisibilityTest
{
    private static boolean visible(String cond, String answers)
    {
        return AnswerVisibility.isVisible(EngineSeeds.json(cond), EngineSeeds.answers(answers));
    }

    @Test
    void comparisonOperators()
    {
        String a = "{\"n\": 5, \"s\": \"x\", \"arr\": [\"UK\", \"HK\"], \"empty\": \"\", \"none\": [], \"obj\": {\"kind\": \"cn\"}}";
        assertTrue(visible("{\"q\": \"n\", \"op\": \"eq\", \"value\": 5}", a));
        assertTrue(visible("{\"q\": \"n\", \"op\": \"eq\", \"value\": \"5.0\"}", a));
        assertTrue(visible("{\"q\": \"s\", \"op\": \"ne\", \"value\": \"y\"}", a));
        assertTrue(visible("{\"q\": \"missing\", \"op\": \"ne\", \"value\": \"y\"}", a));
        assertTrue(visible("{\"q\": \"s\", \"op\": \"in\", \"value\": [\"x\", \"z\"]}", a));
        assertFalse(visible("{\"q\": \"s\", \"op\": \"not_in\", \"value\": [\"x\"]}", a));
        assertTrue(visible("{\"q\": \"missing\", \"op\": \"not_in\", \"value\": [\"x\"]}", a));
        assertTrue(visible("{\"q\": \"arr\", \"op\": \"contains\", \"value\": \"HK\"}", a));
        assertFalse(visible("{\"q\": \"s\", \"op\": \"contains\", \"value\": \"x\"}", a));
        assertTrue(visible("{\"q\": \"arr\", \"op\": \"contains_any\", \"value\": [\"US\", \"UK\"]}", a));
        assertFalse(visible("{\"q\": \"arr\", \"op\": \"contains_any\", \"value\": [\"US\"]}", a));
        assertFalse(visible("{\"q\": \"arr\", \"op\": \"contains_any\", \"value\": \"UK\"}", a));
        assertTrue(visible("{\"q\": \"n\", \"op\": \"gt\", \"value\": 4}", a));
        assertTrue(visible("{\"q\": \"n\", \"op\": \"gte\", \"value\": 5}", a));
        assertTrue(visible("{\"q\": \"n\", \"op\": \"lt\", \"value\": 6}", a));
        assertTrue(visible("{\"q\": \"n\", \"op\": \"lte\", \"value\": 5}", a));
        assertFalse(visible("{\"q\": \"s\", \"op\": \"gt\", \"value\": 1}", a));
        assertTrue(visible("{\"q\": \"s\", \"op\": \"answered\"}", a));
        assertTrue(visible("{\"q\": \"empty\", \"op\": \"not_answered\"}", a));
        assertTrue(visible("{\"q\": \"none\", \"op\": \"not_answered\"}", a));
        assertFalse(visible("{\"q\": \"s\", \"op\": \"unknown_op\"}", a));
        assertTrue(visible("{\"q\": \"obj\", \"path\": \"kind\", \"op\": \"eq\", \"value\": \"cn\"}", a));
        assertTrue(visible("{\"q\": \"obj\", \"op\": \"eq\", \"value\": {\"kind\": \"cn\"}}", a));
        assertTrue(AnswerVisibility.isVisible(null, Map.of()));
    }

    @Test
    void combinedConditions()
    {
        String a = "{\"x\": \"1\", \"y\": \"2\"}";
        assertTrue(visible("{\"all\": [{\"q\": \"x\", \"op\": \"eq\", \"value\": \"1\"}, {\"q\": \"y\", \"op\": \"eq\", \"value\": \"2\"}]}", a));
        assertFalse(visible("{\"all\": [{\"q\": \"x\", \"op\": \"eq\", \"value\": \"1\"}, {\"q\": \"y\", \"op\": \"eq\", \"value\": \"3\"}]}", a));
        assertTrue(visible("{\"any\": [{\"q\": \"x\", \"op\": \"eq\", \"value\": \"9\"}, {\"q\": \"y\", \"op\": \"eq\", \"value\": \"2\"}]}", a));
        assertFalse(visible("{\"any\": [{\"q\": \"x\", \"op\": \"eq\", \"value\": \"9\"}]}", a));
        assertTrue(visible("{\"not\": {\"q\": \"x\", \"op\": \"eq\", \"value\": \"9\"}}", a));
    }

    @Test
    void hiddenQuestionsAreDroppedInChains()
    {
        JsonNode questions = EngineSeeds.json("[{\"id\": \"a\"}, {\"id\": \"b\", \"visible_if\": {\"q\": \"a\", \"op\": \"eq\", \"value\": \"yes\"}},"
                + " {\"id\": \"c\", \"visible_if\": {\"q\": \"b\", \"op\": \"answered\"}}, {\"id\": \"d\"}]");
        Map<String, JsonNode> shown = AnswerVisibility.visibleAnswers(questions,
                EngineSeeds.answers("{\"a\": \"yes\", \"b\": \"x\", \"c\": \"y\", \"d\": null}"));
        assertEquals(List.of("a", "b", "c"), List.copyOf(shown.keySet()));
        Map<String, JsonNode> hidden = AnswerVisibility.visibleAnswers(questions,
                EngineSeeds.answers("{\"a\": \"no\", \"b\": \"x\", \"c\": \"y\", \"d\": \"z\"}"));
        assertEquals(List.of("a", "d"), List.copyOf(hidden.keySet()));
    }

    @Test
    void seedQuestionnaireConditions()
    {
        JsonNode questions = EngineSeeds.questionnaire().path("questions");
        Map<String, JsonNode> shown = AnswerVisibility.visibleAnswers(questions, EngineSeeds.answers(
                "{\"target_countries\": [\"UK\"], \"target_degree\": \"master\", \"jlpt_level\": \"n2\","
                        + " \"std_test\": {\"type\": \"gre\", \"total\": 320}, \"english_status\": \"none\","
                        + " \"english_score\": {\"type\": \"ielts\", \"total\": 6.5}, \"undergrad_school\": {\"kind\": \"cn\", \"university_id\": 1},"
                        + " \"undergrad_school_manual\": \"x\"}"));
        assertEquals(List.of("target_countries", "target_degree", "undergrad_school", "english_status"), List.copyOf(shown.keySet()));
        Map<String, JsonNode> hk = AnswerVisibility.visibleAnswers(questions, EngineSeeds.answers(
                "{\"target_countries\": [\"HK\", \"JP\"], \"target_degree\": \"master\", \"jlpt_level\": \"n2\", \"std_test\": {\"type\": \"none\"}}"));
        assertTrue(hk.containsKey("jlpt_level"));
        assertTrue(hk.containsKey("std_test"));
    }

    @Test
    void numberAndStringHelpers()
    {
        assertEquals(null, AnswerVisibility.number(EngineSeeds.json("\"abc\"")));
        assertEquals(null, AnswerVisibility.number(EngineSeeds.json("[1]")));
        assertEquals(null, AnswerVisibility.number(null));
        assertEquals(List.of(), AnswerVisibility.strings(EngineSeeds.json("\"x\"")));
    }
}
