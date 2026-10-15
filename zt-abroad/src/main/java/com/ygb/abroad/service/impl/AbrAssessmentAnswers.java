package com.ygb.abroad.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.ygb.abroad.engine.AnswerVisibility;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 测评答案的合并规则（纯函数，不访问数据库）
 * 
 * 分步保存时前端只提交本屏改动（题目 id → 答案，null 表示清除）；这里校验题目 id、大小，
 * 并按问卷定义里的 on_answer 计算流程标志和是否终止（如 age_band = under14：终止且不保存任何答案）。
 * 
 * @author ygb
 */
final class AbrAssessmentAnswers
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 单个答案序列化后的最大字符数 */
    static final int MAX_VALUE_CHARS = 4000;

    /** 全部答案序列化后的最大字符数 */
    static final int MAX_TOTAL_CHARS = 65536;

    /** 类似 HTML 标签的片段（答案按纯文本保存和展示，接口不经 XSS 过滤，由这里拦截） */
    private static final Pattern TAG_LIKE = Pattern.compile("<\\s*[A-Za-z/!?]");

    /** 不进入访客档案、也不预填的题型（文件需重新上传并单独同意） */
    private static final String TYPE_FILE = "file";

    /** 不进入访客档案、也不预填的题目角色（资格类题目每次都要重新回答） */
    private static final String ROLE_ELIGIBILITY = "eligibility";

    private AbrAssessmentAnswers()
    {
    }

    /**
     * 解析问卷定义
     * 
     * @param definition 问卷 JSON
     * @return 题目索引
     */
    static Questionnaire parse(String definition)
    {
        JsonNode root;
        try
        {
            root = MAPPER.readTree(definition);
        }
        catch (JacksonException e)
        {
            throw new ServiceException("问卷定义损坏，请联系工作室");
        }
        Map<String, JsonNode> questions = new LinkedHashMap<>();
        for (JsonNode q : root.path("questions"))
        {
            questions.put(q.path("id").asString(), q);
        }
        return new Questionnaire(questions, root.path("consent").path("policy_code").asString(""), root);
    }

    /**
     * 解析 JSON 对象文本（空或非对象返回空对象）
     * 
     * @param json JSON 文本
     * @return 对象节点
     */
    static ObjectNode object(String json)
    {
        if (StringUtils.isNotBlank(json))
        {
            try
            {
                JsonNode node = MAPPER.readTree(json);
                if (node != null && node.isObject())
                {
                    return (ObjectNode) node;
                }
            }
            catch (JacksonException e)
            {
                // 损坏的数据按空对象处理
            }
        }
        return MAPPER.createObjectNode();
    }

    /**
     * 合并本次保存的答案
     * 
     * @param questionnaire 问卷
     * @param current 当前已保存的答案
     * @param patch 本次提交（题目 id → 答案，null 表示清除）
     * @return 合并结果
     */
    static Outcome merge(Questionnaire questionnaire, ObjectNode current, JsonNode patch)
    {
        if (patch == null || !patch.isObject() || patch.isEmpty())
        {
            throw new ServiceException("没有要保存的答案");
        }
        ObjectNode set = MAPPER.createObjectNode();
        List<String> removed = new ArrayList<>();
        ObjectNode flags = MAPPER.createObjectNode();
        boolean terminated = false;
        boolean saveAnswers = true;
        for (Map.Entry<String, JsonNode> e : patch.properties())
        {
            String id = e.getKey();
            JsonNode question = questionnaire.questions().get(id);
            if (question == null)
            {
                throw new ServiceException("题目不存在：" + StringUtils.substring(id, 0, 64));
            }
            JsonNode value = e.getValue();
            if (value == null || value.isNull())
            {
                removed.add(id);
                continue;
            }
            if (value.toString().length() > MAX_VALUE_CHARS)
            {
                throw new ServiceException("题目 " + id + " 的答案过长");
            }
            if (containsTag(value))
            {
                throw new ServiceException("题目 " + id + " 的答案不能包含 < 加字母的写法");
            }
            set.set(id, value);
            JsonNode rule = value.isString() ? question.path("on_answer").get(value.asString()) : null;
            if (rule != null && rule.isObject())
            {
                if (rule.path("flags").isObject())
                {
                    flags.setAll((ObjectNode) rule.get("flags"));
                }
                if ("stop".equals(rule.path("action").asString("")))
                {
                    terminated = true;
                    saveAnswers = saveAnswers && rule.path("save_answer").asBoolean(true);
                }
            }
        }
        if (terminated && !saveAnswers)
        {
            return new Outcome(MAPPER.createObjectNode(), Collections.emptyList(), flags, true, true);
        }
        ObjectNode merged = current.deepCopy();
        removed.forEach(merged::remove);
        merged.setAll(set);
        if (merged.toString().length() > MAX_TOTAL_CHARS)
        {
            throw new ServiceException("答案总量过大");
        }
        return new Outcome(set, removed, flags, terminated, false);
    }

    /**
     * 从访客档案中取出可以预填到新测评的答案
     * 
     * @param questionnaire 问卷
     * @param profile 访客档案
     * @return 预填答案
     */
    static ObjectNode prefill(Questionnaire questionnaire, ObjectNode profile)
    {
        ObjectNode result = MAPPER.createObjectNode();
        for (Map.Entry<String, JsonNode> e : profile.properties())
        {
            JsonNode question = questionnaire.questions().get(e.getKey());
            if (question != null && sharable(question) && !e.getValue().isNull())
            {
                result.set(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    /**
     * 本次保存中应写入访客档案的答案
     * 
     * @param questionnaire 问卷
     * @param set 本次新写入的答案
     * @return 档案增量
     */
    static ObjectNode profilePatch(Questionnaire questionnaire, ObjectNode set)
    {
        ObjectNode result = MAPPER.createObjectNode();
        for (Map.Entry<String, JsonNode> e : set.properties())
        {
            if (sharable(questionnaire.questions().get(e.getKey())))
            {
                result.set(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    /**
     * 题目 id 列表转为 JSON 数组文本
     * 
     * @param ids 题目 id
     * @return JSON 数组
     */
    static String toJsonArray(List<String> ids)
    {
        ArrayNode array = MAPPER.createArrayNode();
        ids.forEach(array::add);
        return array.toString();
    }

    /**
     * 答案中是否有类似 HTML 标签的文本（递归检查所有字符串）
     * 
     * @param value 答案
     * @return 有则为 true
     */
    static boolean containsTag(JsonNode value)
    {
        if (value.isString())
        {
            return TAG_LIKE.matcher(value.asString()).find();
        }
        if (value.isArray() || value.isObject())
        {
            for (JsonNode child : value)
            {
                if (containsTag(child))
                {
                    return true;
                }
            }
            if (value.isObject())
            {
                for (Map.Entry<String, JsonNode> e : value.properties())
                {
                    if (TAG_LIKE.matcher(e.getKey()).find())
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean sharable(JsonNode question)
    {
        return question != null
                && !TYPE_FILE.equals(question.path("type").asString(""))
                && !ROLE_ELIGIBILITY.equals(question.path("maps_to").path("role").asString(""));
    }

    /**
     * 已解析的问卷
     * 
     * @param questions 题目 id → 题目定义
     * @param policyCode 隐私政策编码
     * @param root 问卷定义根节点（解析 options_ref 等顶层引用）
     */
    record Questionnaire(Map<String, JsonNode> questions, String policyCode, JsonNode root)
    {
    }

    /**
     * 合并结果
     * 
     * @param set 要写入的答案（clearAll 为 true 时忽略）
     * @param removed 要清除的题目 id
     * @param flags 要合并的流程标志
     * @param terminated 是否终止测评
     * @param clearAll 是否清空全部答案（终止且不保存答案）
     */
    record Outcome(ObjectNode set, List<String> removed, ObjectNode flags, boolean terminated, boolean clearAll)
    {
    }

    /**
     * 首屏已选学段时，预填问卷的目标学位题（题目存在且有该选项、尚未预填时）
     * 
     * @param questionnaire 问卷
     * @param answers 预填答案（会被修改）
     * @param degree 学段
     */
    static void prefillDegree(Questionnaire questionnaire, ObjectNode answers, String degree)
    {
        JsonNode q = questionnaire.questions().get("target_degree");
        if (q == null || answers.has("target_degree"))
        {
            return;
        }
        // 选项可以直接写在题目里，也可以用 options_ref 引用问卷顶层的列表（如 degree_levels）
        JsonNode options = q.has("options") ? q.path("options") : questionnaire.root().path(q.path("options_ref").asString(""));
        for (JsonNode option : options)
        {
            if (degree.equals(option.path("value").asString("")))
            {
                answers.put("target_degree", degree);
                return;
            }
        }
    }

    /**
     * 提交前整理：剔除隐藏题的答案，列出可见但未回答的必答题
     * 
     * @param questionnaire 问卷
     * @param answers 已保存的答案
     * @return 整理结果
     */
    static Submission submission(Questionnaire questionnaire, ObjectNode answers)
    {
        ArrayNode questions = MAPPER.createArrayNode();
        questionnaire.questions().values().forEach(questions::add);
        Map<String, JsonNode> all = new LinkedHashMap<>();
        answers.properties().forEach(e -> all.put(e.getKey(), e.getValue()));
        Map<String, JsonNode> visible = AnswerVisibility.visibleAnswers(questions, all);
        List<String> missing = new ArrayList<>();
        for (JsonNode q : questions)
        {
            String id = q.path("id").asString("");
            if (q.path("required").asBoolean(false) && !visible.containsKey(id) && AnswerVisibility.isVisible(q.get("visible_if"), visible))
            {
                missing.add(q.path("title").asString(id));
            }
        }
        ObjectNode kept = MAPPER.createObjectNode();
        visible.forEach(kept::set);
        return new Submission(kept, Collections.unmodifiableList(missing));
    }

    /**
     * 提交整理结果
     * 
     * @param answers 只含可见题目的答案
     * @param missingRequired 可见但未回答的必答题标题
     */
    record Submission(ObjectNode answers, List<String> missingRequired)
    {
    }
}
