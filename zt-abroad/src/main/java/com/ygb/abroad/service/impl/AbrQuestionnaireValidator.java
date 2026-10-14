package com.ygb.abroad.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import com.ygb.abroad.domain.vo.AbrQuestionnaireCheck;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 问卷 JSON 的结构校验（格式见 seed/questionnaire/README.md）
 * 
 * 只校验会导致前端或归一化出错的结构问题：编码与版本、步骤与题目的引用关系、题型、显示条件引用；
 * 换算表、选项内容等业务口径由顾问审定，不在这里判断。
 * 
 * @author ygb
 */
final class AbrQuestionnaireValidator
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 最多返回的错误条数 */
    static final int MAX_ERRORS = 50;

    /** 问卷编码格式 */
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    /** 步骤、题目 id 格式（snake_case，发布后不得改名） */
    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    /** 支持的题型 */
    static final Set<String> TYPES = Set.of("single", "multi", "number", "text", "select-search", "score-with-type", "exp-summary", "file");

    /** 需要选项的题型 */
    private static final Set<String> CHOICE_TYPES = Set.of("single", "multi");

    /** 显示条件运算符 */
    static final Set<String> OPS = Set.of("eq", "ne", "in", "not_in", "contains", "contains_any", "answered", "not_answered",
            "gt", "gte", "lt", "lte");

    /** 文案中不允许出现的片段（问卷文字会展示在 C 端） */
    private static final List<String> UNSAFE = List.of("<script", "javascript:", "onerror=", "onload=");

    private AbrQuestionnaireValidator()
    {
    }

    /**
     * 解析并校验问卷 JSON
     * 
     * @param json 问卷 JSON 文本
     * @return 校验结果（基本信息 + 错误列表）
     */
    static AbrQuestionnaireCheck check(String json)
    {
        Checker c = new Checker();
        if (StringUtils.isBlank(json))
        {
            c.error("问卷 JSON 为空");
            return c.result;
        }
        JsonNode root;
        try
        {
            root = MAPPER.readTree(json);
        }
        catch (JacksonException e)
        {
            c.error("不是合法的 JSON：" + StringUtils.substring(e.getOriginalMessage(), 0, 200));
            return c.result;
        }
        if (root == null || !root.isObject())
        {
            c.error("问卷 JSON 顶层必须是对象");
            return c.result;
        }
        String lower = json.toLowerCase(Locale.ROOT);
        for (String unsafe : UNSAFE)
        {
            if (lower.contains(unsafe))
            {
                c.error("文案中不能包含 “" + unsafe + "”");
            }
        }
        c.checkHeader(root);
        Set<String> stepIds = c.checkSteps(root.get("steps"));
        Set<String> questionIds = c.checkQuestions(root, stepIds);
        c.checkScreens(root.get("steps"), questionIds);
        c.checkConditions(root.get("questions"), questionIds);
        return c.result;
    }

    /**
     * 规范化 JSON 文本（去掉多余空白，用于入库前统一格式）
     * 
     * @param json JSON 文本（须已通过校验）
     * @return 规范化后的文本
     */
    static String normalize(String json)
    {
        return MAPPER.readTree(json).toString();
    }

    private static class Checker
    {
        private final AbrQuestionnaireCheck result = new AbrQuestionnaireCheck();

        private void error(String message)
        {
            if (result.getErrors().size() < MAX_ERRORS)
            {
                result.getErrors().add(message);
            }
        }

        private void checkHeader(JsonNode root)
        {
            JsonNode code = root.get("code");
            if (code == null || !code.isString() || !CODE.matcher(code.asString()).matches())
            {
                error("code 必填，只能是小写字母开头的小写字母、数字、下划线（2–64 位）");
            }
            else
            {
                result.setCode(code.asString());
            }
            JsonNode version = root.get("version");
            if (version == null || !version.isIntegralNumber() || version.asLong() < 1 || version.asLong() > Integer.MAX_VALUE)
            {
                error("version 必填，必须是大于 0 的整数");
            }
            else
            {
                result.setVersion(version.asInt());
            }
            String title = text(root.get("title"));
            if (StringUtils.isBlank(title))
            {
                error("title 必填");
            }
            else if (title.length() > 200)
            {
                error("title 不能超过 200 个字符");
            }
            result.setTitle(title);
            String label = text(root.get("version_label"));
            if (label != null && label.length() > 100)
            {
                error("version_label 不能超过 100 个字符");
            }
            result.setVersionLabel(label);
        }

        private Set<String> checkSteps(JsonNode steps)
        {
            Set<String> ids = new HashSet<>();
            if (steps == null || !steps.isArray() || steps.isEmpty())
            {
                error("steps 必须是非空数组");
                return ids;
            }
            result.setStepCount(steps.size());
            for (int i = 0; i < steps.size(); i++)
            {
                String id = text(steps.get(i).get("id"));
                if (id == null || !ID.matcher(id).matches())
                {
                    error("steps[" + i + "].id 必填，格式为 snake_case");
                }
                else if (!ids.add(id))
                {
                    error("步骤 id 重复：" + id);
                }
            }
            return ids;
        }

        private Set<String> checkQuestions(JsonNode root, Set<String> stepIds)
        {
            Set<String> ids = new HashSet<>();
            JsonNode questions = root.get("questions");
            if (questions == null || !questions.isArray() || questions.isEmpty())
            {
                error("questions 必须是非空数组");
                return ids;
            }
            result.setQuestionCount(questions.size());
            for (int i = 0; i < questions.size(); i++)
            {
                JsonNode q = questions.get(i);
                String id = text(q.get("id"));
                String at = id == null ? "questions[" + i + "]" : "题目 " + id;
                if (id == null || !ID.matcher(id).matches())
                {
                    error("questions[" + i + "].id 必填，格式为 snake_case");
                }
                else if (!ids.add(id))
                {
                    error("题目 id 重复：" + id);
                }
                String step = text(q.get("step"));
                if (step == null || !stepIds.contains(step))
                {
                    error(at + "：step “" + step + "” 不在 steps 中");
                }
                if (StringUtils.isBlank(text(q.get("title"))))
                {
                    error(at + "：title 必填");
                }
                String type = text(q.get("type"));
                if (type == null || !TYPES.contains(type))
                {
                    error(at + "：不支持的题型 “" + type + "”");
                }
                String ref = text(q.get("options_ref"));
                if (ref != null)
                {
                    JsonNode list = root.get(ref);
                    if (list == null || !list.isArray() || list.isEmpty())
                    {
                        error(at + "：options_ref “" + ref + "” 不是顶层的非空数组");
                    }
                }
                if (CHOICE_TYPES.contains(type) && ref == null && !nonEmptyArray(q.get("options")) && q.get("options_source") == null)
                {
                    error(at + "：单选 / 多选题需要 options、options_ref 或 options_source");
                }
                JsonNode required = q.get("required");
                if (required != null && !required.isBoolean())
                {
                    error(at + "：required 必须是 true / false");
                }
            }
            return ids;
        }

        private void checkScreens(JsonNode steps, Set<String> questionIds)
        {
            if (steps == null || !steps.isArray() || questionIds.isEmpty())
            {
                return;
            }
            Map<String, String> placed = new HashMap<>();
            for (JsonNode step : steps)
            {
                JsonNode screens = step.get("screens");
                if (screens == null)
                {
                    continue;
                }
                if (!screens.isArray())
                {
                    error("步骤 " + text(step.get("id")) + "：screens 必须是数组");
                    continue;
                }
                for (JsonNode screen : screens)
                {
                    String screenId = text(screen.get("id"));
                    JsonNode list = screen.get("questions");
                    if (list == null || !list.isArray())
                    {
                        error("屏 " + screenId + "：questions 必须是数组");
                        continue;
                    }
                    for (JsonNode item : list)
                    {
                        String qid = item.isString() ? item.asString() : null;
                        if (qid == null || !questionIds.contains(qid))
                        {
                            error("屏 " + screenId + "：引用了不存在的题目 “" + item + "”");
                        }
                        else if (placed.putIfAbsent(qid, screenId) != null)
                        {
                            error("题目 " + qid + " 同时出现在屏 " + placed.get(qid) + " 和 " + screenId);
                        }
                    }
                }
            }
            if (!placed.isEmpty())
            {
                for (String qid : questionIds)
                {
                    if (!placed.containsKey(qid))
                    {
                        error("题目 " + qid + " 不在任何屏中，学生看不到");
                    }
                }
            }
        }

        private void checkConditions(JsonNode node, Set<String> questionIds)
        {
            if (node == null)
            {
                return;
            }
            if (node.isArray())
            {
                for (JsonNode item : node)
                {
                    checkConditions(item, questionIds);
                }
                return;
            }
            if (!node.isObject())
            {
                return;
            }
            for (Map.Entry<String, JsonNode> e : node.properties())
            {
                if ("visible_if".equals(e.getKey()))
                {
                    checkCondition(e.getValue(), questionIds);
                }
                else
                {
                    checkConditions(e.getValue(), questionIds);
                }
            }
        }

        private void checkCondition(JsonNode cond, Set<String> questionIds)
        {
            if (cond == null || !cond.isObject())
            {
                error("显示条件必须是对象：" + cond);
                return;
            }
            for (String group : List.of("all", "any"))
            {
                JsonNode list = cond.get(group);
                if (list != null)
                {
                    if (!list.isArray() || list.isEmpty())
                    {
                        error("显示条件的 " + group + " 必须是非空数组");
                        return;
                    }
                    for (JsonNode item : list)
                    {
                        checkCondition(item, questionIds);
                    }
                    return;
                }
            }
            if (cond.has("not"))
            {
                checkCondition(cond.get("not"), questionIds);
                return;
            }
            // exp-summary 子字段之间的条件用 field 引用子字段，不引用题目
            if (!cond.has("field"))
            {
                String q = text(cond.get("q"));
                if (q == null || !questionIds.contains(q))
                {
                    error("显示条件引用了不存在的题目 “" + q + "”");
                }
            }
            String op = text(cond.get("op"));
            if (op == null || !OPS.contains(op))
            {
                error("显示条件的运算符 “" + op + "” 不支持");
            }
        }

        private static boolean nonEmptyArray(JsonNode node)
        {
            return node != null && node.isArray() && !node.isEmpty();
        }

        private static String text(JsonNode node)
        {
            return node == null || !node.isString() ? null : node.asString();
        }
    }
}
