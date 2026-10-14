package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.vo.AbrQuestionnaireCheck;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 问卷 JSON 结构校验测试：种子问卷必须通过；常见结构错误必须被拦下
 *
 * @author ygb
 */
class AbrQuestionnaireValidatorTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final String MINIMAL = """
            {
              "code": "demo",
              "version": 2,
              "version_label": "v2",
              "title": "测试问卷",
              "levels": [{"value": "master", "label": "硕士"}],
              "steps": [{"id": "goal", "screens": [{"id": "goal_1", "questions": ["degree", "note"]}]}],
              "questions": [
                {"id": "degree", "step": "goal", "title": "学位", "type": "single", "required": true, "options_ref": "levels"},
                {"id": "note", "step": "goal", "title": "备注", "type": "text",
                 "visible_if": {"all": [{"q": "degree", "op": "eq", "value": "master"}]}}
              ]
            }
            """;

    private static ObjectNode minimal()
    {
        return (ObjectNode) MAPPER.readTree(MINIMAL);
    }

    private static List<String> errors(ObjectNode root)
    {
        return AbrQuestionnaireValidator.check(root.toString()).getErrors();
    }

    private static boolean hasError(List<String> errors, String fragment)
    {
        return errors.stream().anyMatch(e -> e.contains(fragment));
    }

    @Test
    void seedQuestionnairePasses() throws IOException
    {
        Path seed = Path.of("..", "seed", "questionnaire", "questionnaire_v1.json");
        AbrQuestionnaireCheck check = AbrQuestionnaireValidator.check(Files.readString(seed, StandardCharsets.UTF_8));
        assertEquals(List.of(), check.getErrors());
        assertEquals("abr_school_assessment", check.getCode());
        assertEquals(1, check.getVersion());
        assertEquals(5, check.getStepCount());
        assertEquals(25, check.getQuestionCount());
    }

    @Test
    void minimalPasses()
    {
        AbrQuestionnaireCheck check = AbrQuestionnaireValidator.check(MINIMAL);
        assertEquals(List.of(), check.getErrors());
        assertEquals("v2", check.getVersionLabel());
        assertTrue(check.isImportable());
    }

    @Test
    void rejectsInvalidJsonAndNonObject()
    {
        assertTrue(hasError(AbrQuestionnaireValidator.check("{oops").getErrors(), "不是合法的 JSON"));
        assertTrue(hasError(AbrQuestionnaireValidator.check("[1]").getErrors(), "顶层必须是对象"));
        assertTrue(hasError(AbrQuestionnaireValidator.check(" ").getErrors(), "为空"));
    }

    @Test
    void rejectsBadHeader()
    {
        ObjectNode root = minimal();
        root.put("code", "Bad Code");
        root.put("version", 0);
        root.remove("title");
        List<String> errors = errors(root);
        assertTrue(hasError(errors, "code 必填"));
        assertTrue(hasError(errors, "version 必填"));
        assertTrue(hasError(errors, "title 必填"));
    }

    @Test
    void rejectsDuplicateIdUnknownStepAndType()
    {
        ObjectNode root = minimal();
        ArrayNode questions = (ArrayNode) root.get("questions");
        ObjectNode dup = (ObjectNode) questions.get(0).deepCopy();
        dup.put("step", "nowhere");
        dup.put("type", "slider");
        questions.add(dup);
        List<String> errors = errors(root);
        assertTrue(hasError(errors, "题目 id 重复：degree"));
        assertTrue(hasError(errors, "step “nowhere” 不在 steps 中"));
        assertTrue(hasError(errors, "不支持的题型 “slider”"));
    }

    @Test
    void rejectsMissingOptions()
    {
        ObjectNode root = minimal();
        ObjectNode q = (ObjectNode) root.get("questions").get(0);
        q.put("options_ref", "missing_list");
        assertTrue(hasError(errors(root), "options_ref “missing_list” 不是顶层的非空数组"));
        q.remove("options_ref");
        assertTrue(hasError(errors(root), "需要 options、options_ref 或 options_source"));
    }

    @Test
    void rejectsScreenProblems()
    {
        ObjectNode root = minimal();
        ArrayNode list = (ArrayNode) root.get("steps").get(0).get("screens").get(0).get("questions");
        list.remove(1);
        list.add("ghost");
        List<String> errors = errors(root);
        assertTrue(hasError(errors, "引用了不存在的题目 “\"ghost\"”"));
        assertTrue(hasError(errors, "题目 note 不在任何屏中"));
        list.add("degree");
        assertTrue(hasError(errors(root), "题目 degree 同时出现在屏 goal_1 和 goal_1"));
    }

    @Test
    void rejectsBadConditions()
    {
        ObjectNode root = minimal();
        ObjectNode cond = (ObjectNode) root.get("questions").get(1).get("visible_if").get("all").get(0);
        cond.put("q", "unknown");
        cond.put("op", "like");
        List<String> errors = errors(root);
        assertTrue(hasError(errors, "显示条件引用了不存在的题目 “unknown”"));
        assertTrue(hasError(errors, "运算符 “like” 不支持"));
    }

    @Test
    void subFieldConditionNeedsNoQuestion()
    {
        ObjectNode root = minimal();
        ObjectNode q = (ObjectNode) root.get("questions").get(1);
        q.set("visible_if", MAPPER.readTree("{\"not\": {\"field\": \"has\", \"op\": \"eq\", \"value\": \"yes\"}}"));
        assertEquals(List.of(), errors(root));
    }

    @Test
    void rejectsUnsafeText()
    {
        ObjectNode root = minimal();
        ((ObjectNode) root.get("questions").get(1)).put("help", "<SCRIPT>alert(1)</script>");
        assertTrue(hasError(errors(root), "<script"));
    }

    @Test
    void errorsAreCapped()
    {
        ObjectNode root = minimal();
        ArrayNode questions = (ArrayNode) root.get("questions");
        for (int i = 0; i < 80; i++)
        {
            questions.add(MAPPER.readTree("{\"id\": \"x" + i + "\", \"step\": \"none\", \"title\": \"t\", \"type\": \"text\"}"));
        }
        List<String> errors = errors(root);
        assertEquals(AbrQuestionnaireValidator.MAX_ERRORS, errors.size());
        assertFalse(AbrQuestionnaireValidator.check(root.toString()).isImportable());
    }

    @Test
    void normalizeKeepsContent()
    {
        String normalized = AbrQuestionnaireValidator.normalize(MINIMAL);
        assertFalse(normalized.contains("\n"));
        assertEquals(MAPPER.readTree(MINIMAL), MAPPER.readTree(normalized));
    }
}
