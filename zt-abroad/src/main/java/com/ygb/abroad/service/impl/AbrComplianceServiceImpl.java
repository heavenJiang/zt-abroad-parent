package com.ygb.abroad.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrComplianceRule;
import com.ygb.abroad.domain.AbrContent;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.AbrSchool;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.abroad.growth.compliance.ComplianceChecker;
import com.ygb.abroad.growth.compliance.ComplianceFacts;
import com.ygb.abroad.growth.compliance.ComplianceRuleSet;
import com.ygb.abroad.mapper.AbrComplianceRuleMapper;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.mapper.AbrRequirementMapper;
import com.ygb.abroad.mapper.AbrSchoolMapper;
import com.ygb.abroad.service.IAbrComplianceService;
import com.ygb.abroad.service.IAbrContentService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 合规审查 compliance_checker 服务层实现：读取启用的词表与引用的院校库记录，交给确定性的 ComplianceChecker
 *
 * @author ygb
 */
@Service
public class AbrComplianceServiceImpl implements IAbrComplianceService
{
    private static final Logger log = LoggerFactory.getLogger(AbrComplianceServiceImpl.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final Set<String> MATCH_TYPES = Set.of(ComplianceRuleSet.MATCH_WORD, ComplianceRuleSet.MATCH_EXCEPTION, ComplianceRuleSet.MATCH_REGEX);

    private static final Set<String> ACTIONS = Set.of(ComplianceRuleSet.ACTION_BLOCK, ComplianceRuleSet.ACTION_CITE);

    /** 单条消息最多检查的字数 */
    private static final int MAX_MESSAGE_CHARS = 5000;

    @Autowired
    private AbrComplianceRuleMapper ruleMapper;

    @Autowired
    private IAbrContentService contentService;

    @Autowired
    private AbrProgramMapper programMapper;

    @Autowired
    private AbrRequirementMapper requirementMapper;

    @Autowired
    private AbrSchoolMapper schoolMapper;

    /** 内容的 AI 生成标识（与内容服务一致） */
    @Value("${abroad.content.ai-label:本文由 AI 辅助生成}")
    private String aiLabel;

    /** 消息的 AI 生成标识 */
    @Value("${abroad.compliance.message-ai-label:（AI 回复）}")
    private String messageAiLabel;

    @Override
    public List<AbrComplianceRule> selectAbrComplianceRuleList(AbrComplianceRule rule)
    {
        return ruleMapper.selectAbrComplianceRuleList(rule);
    }

    @Override
    public AbrComplianceRule selectAbrComplianceRuleById(Long ruleId)
    {
        return ruleMapper.selectAbrComplianceRuleById(ruleId);
    }

    @Override
    public boolean checkRuleUnique(AbrComplianceRule rule)
    {
        AbrComplianceRule probe = new AbrComplianceRule();
        probe.setCategory(rule.getCategory());
        probe.setMatchType(rule.getMatchType());
        probe.setPattern(StringUtils.trim(rule.getPattern()));
        AbrComplianceRule found = ruleMapper.checkRuleUnique(probe);
        return found == null || found.getRuleId().equals(rule.getRuleId());
    }

    @Override
    public int insertAbrComplianceRule(AbrComplianceRule rule)
    {
        normalize(rule);
        return ruleMapper.insertAbrComplianceRule(rule);
    }

    @Override
    public int updateAbrComplianceRule(AbrComplianceRule rule)
    {
        if (rule.getRuleId() == null || ruleMapper.selectAbrComplianceRuleById(rule.getRuleId()) == null)
        {
            throw new ServiceException("规则不存在");
        }
        normalize(rule);
        return ruleMapper.updateAbrComplianceRule(rule);
    }

    @Override
    public int deleteAbrComplianceRuleByIds(Long[] ruleIds)
    {
        return ruleMapper.deleteAbrComplianceRuleByIds(ruleIds);
    }

    @Override
    public AbrContent checkContent(Long contentId, String checker)
    {
        AbrContent c = contentId == null ? null : contentService.selectAbrContentById(contentId);
        if (c == null)
        {
            throw new ServiceException("内容不存在");
        }
        if (AbrContent.STATUS_PUBLISHED.equals(c.getStatus()) || AbrContent.STATUS_ARCHIVED.equals(c.getStatus()))
        {
            throw new ServiceException("已发布或已归档的内容不能再过闸门");
        }
        ComplianceChecker.Result result = checkDraft(c);
        Map<String, Object> detail = result.toMap();
        detail.put("checkedBy", checker);
        return contentService.recordGateResult(contentId, result.passed(), MAPPER.writeValueAsString(detail), checker);
    }

    @Override
    public ComplianceChecker.Result checkDraft(AbrContent c)
    {
        Map<String, String> words = new LinkedHashMap<>();
        Map<String, String> facts = new LinkedHashMap<>();
        String hashtags = hashtags(c.getHashtags());
        put(words, facts, "title", c.getTitle(), true);
        put(words, facts, "body", c.getBody(), true);
        put(words, facts, "hashtags", hashtags, true);
        // 图卡文字只查词表：数字由模板直接取自院校库，引用依据已并入内容
        put(words, facts, "cards", cardText(c.getCards()), false);
        ComplianceChecker.Input input = new ComplianceChecker.Input(StringUtils.trimToNull(c.getPlatform()), words, facts,
                "Y".equals(c.getAiGenerated()), aiLabel, "body", "case".equals(c.getContentKind()));
        return ComplianceChecker.check(input, rules(), facts(c.getCitations(), MarketingCalendar.today()));
    }

    @Override
    public ComplianceChecker.Result checkMessage(String platform, String text, String citationsJson, boolean aiGenerated)
    {
        String body = StringUtils.defaultString(text);
        if (body.length() > MAX_MESSAGE_CHARS)
        {
            throw new ServiceException("消息最多 " + MAX_MESSAGE_CHARS + " 个字");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("text", body);
        ComplianceChecker.Input input = new ComplianceChecker.Input(StringUtils.trimToNull(platform), fields, fields, aiGenerated, messageAiLabel,
                "text", false);
        return ComplianceChecker.check(input, rules(), facts(citationsJson, MarketingCalendar.today()));
    }

    private static void put(Map<String, String> words, Map<String, String> facts, String field, String text, boolean fact)
    {
        if (StringUtils.isNotBlank(text))
        {
            words.put(field, text);
            if (fact)
            {
                facts.put(field, text);
            }
        }
    }

    /**
     * 启用的规则；正则写法有误的（只可能来自直接改库）跳过并记日志
     */
    private ComplianceRuleSet rules()
    {
        List<ComplianceRuleSet.Rule> list = new ArrayList<>();
        for (AbrComplianceRule r : ruleMapper.selectActiveRules())
        {
            try
            {
                ComplianceRuleSet.compile(r.getMatchType(), r.getPattern());
            }
            catch (PatternSyntaxException e)
            {
                log.warn("合规规则 {} 的正则写法有误，已跳过：{}", r.getRuleId(), e.getDescription());
                continue;
            }
            list.add(new ComplianceRuleSet.Rule(r.getRuleId(), r.getCategory(), r.getMatchType(), r.getPattern(), r.getAction(), platforms(r.getPlatforms())));
        }
        return new ComplianceRuleSet(list);
    }

    /**
     * 按引用依据读取院校库记录：项目（最新申请要求）、指定的申请要求、院校；记录不存在或已下线、案例与 offer（案例库未上线）记为引用问题
     */
    private ComplianceFacts facts(String citationsJson, LocalDate today)
    {
        ComplianceFacts facts = new ComplianceFacts();
        JsonNode citations = read(citationsJson, "引用依据");
        if (!citations.isArray())
        {
            throw new ServiceException("引用依据必须是 JSON 数组");
        }
        Set<Long> programIds = new LinkedHashSet<>();
        Map<Long, AbrRequirement> pinned = new LinkedHashMap<>();
        for (JsonNode n : citations)
        {
            String type = n.path("type").asString("");
            facts.addCitationType(type);
            Long id = n.path("id").isIntegralNumber() ? n.path("id").asLong() : null;
            switch (type)
            {
                case "program":
                    if (id != null)
                    {
                        programIds.add(id);
                    }
                    break;
                case "requirement":
                    AbrRequirement req = id == null ? null : requirementMapper.selectAbrRequirementById(id);
                    if (req == null || "2".equals(req.getDelFlag()))
                    {
                        facts.addCitationProblem("引用的申请要求 " + id + " 不存在");
                    }
                    else
                    {
                        programIds.add(req.getProgramId());
                        pinned.put(req.getProgramId(), req);
                    }
                    break;
                case "school":
                    AbrSchool school = id == null ? null : schoolMapper.selectAbrSchoolById(id);
                    if (school == null || "2".equals(school.getDelFlag()))
                    {
                        facts.addCitationProblem("引用的院校 " + id + " 不存在");
                    }
                    else
                    {
                        facts.addCitedSchool(id);
                    }
                    break;
                case "case":
                case "offer":
                    facts.addCitationProblem("案例库尚未上线（M9-01），引用案例或 offer 的内容暂由员工审核");
                    break;
                default:
                    // field_source、policy、url：作为来源记录，不参与数字核对
                    break;
            }
        }
        if (!programIds.isEmpty())
        {
            List<Long> ids = new ArrayList<>(programIds);
            Map<Long, AbrProgram> programs = new LinkedHashMap<>();
            programMapper.selectAbrProgramByIds(ids).stream().filter(p -> "0".equals(p.getStatus()) && !"2".equals(p.getDelFlag()))
                    .forEach(p -> programs.put(p.getProgramId(), p));
            Map<Long, AbrRequirement> latest = new LinkedHashMap<>();
            requirementMapper.selectLatestAbrRequirementByProgramIds(ids).forEach(r -> latest.put(r.getProgramId(), r));
            for (Long id : ids)
            {
                AbrProgram p = programs.get(id);
                if (p == null)
                {
                    facts.addCitationProblem("引用的项目 " + id + " 不存在或已下线");
                    continue;
                }
                facts.addProgram(ComplianceFacts.of(p, pinned.containsKey(id) ? pinned.get(id) : latest.get(id), today));
            }
        }
        for (AbrSchool s : schoolMapper.selectAbrSchoolList(new AbrSchool()))
        {
            facts.addSchoolName(s.getSchoolName(), s.getSchoolId());
            facts.addSchoolName(s.getSchoolNameEn(), s.getSchoolId());
        }
        return facts;
    }

    /**
     * 校验并规范化规则：类别须为可配置类别、匹配方式与动作为可选值、正则可编译且不匹配空文字、平台为可选值
     */
    private void normalize(AbrComplianceRule r)
    {
        Set<String> categories = AbrPlaybookServiceImpl.dictValues("abr_compliance_category");
        if (!categories.contains(r.getCategory()) || ComplianceChecker.SYSTEM_CATEGORIES.contains(r.getCategory()))
        {
            throw new ServiceException("类别“" + r.getCategory() + "”不是可配置的类别");
        }
        if (!MATCH_TYPES.contains(r.getMatchType()))
        {
            throw new ServiceException("匹配方式只能是 word / exception / regex");
        }
        String action = StringUtils.defaultIfBlank(r.getAction(), ComplianceRuleSet.ACTION_BLOCK);
        if (!ACTIONS.contains(action))
        {
            throw new ServiceException("动作只能是 block / cite");
        }
        r.setAction(ComplianceRuleSet.MATCH_EXCEPTION.equals(r.getMatchType()) ? ComplianceRuleSet.ACTION_BLOCK : action);
        String pattern = StringUtils.trim(r.getPattern());
        if (StringUtils.isEmpty(pattern))
        {
            throw new ServiceException("内容不能为空");
        }
        try
        {
            Pattern p = ComplianceRuleSet.compile(r.getMatchType(), pattern);
            if (p.matcher("").find())
            {
                throw new ServiceException("正则不能匹配空文字");
            }
        }
        catch (PatternSyntaxException e)
        {
            throw new ServiceException("正则写法有误：" + e.getDescription());
        }
        r.setPattern(pattern);
        r.setPlatforms(platformsJson(r.getPlatforms()));
        r.setStatus("1".equals(r.getStatus()) ? "1" : "0");
        if (StringUtils.isNotBlank(r.getRemark()) && AbrAssessmentAnswers.containsTag(JsonNodeFactory.instance.stringNode(r.getRemark())))
        {
            throw new ServiceException("备注中不能包含 < 加字母的写法（类似 HTML 标签）");
        }
    }

    private static String platformsJson(String json)
    {
        JsonNode node = read(json, "生效平台");
        if (!node.isArray())
        {
            throw new ServiceException("生效平台必须是 JSON 数组");
        }
        Set<String> allowed = AbrPlaybookServiceImpl.dictValues("abr_platform");
        Set<String> out = new TreeSet<>();
        for (JsonNode n : node)
        {
            String v = n.isString() ? n.asString().trim() : "";
            if (!allowed.contains(v))
            {
                throw new ServiceException("生效平台中的“" + n.asString() + "”不是可选值");
            }
            out.add(v);
        }
        ArrayNode arr = MAPPER.createArrayNode();
        out.forEach(arr::add);
        return MAPPER.writeValueAsString(arr);
    }

    private static Set<String> platforms(String json)
    {
        Set<String> set = new LinkedHashSet<>();
        try
        {
            for (JsonNode n : MAPPER.readTree(StringUtils.defaultIfBlank(json, "[]")))
            {
                set.add(n.asString(""));
            }
        }
        catch (JacksonException e)
        {
            // 库中格式不对时按全部平台生效（宁可多拦）
        }
        return set;
    }

    private static String hashtags(String json)
    {
        List<String> tags = new ArrayList<>();
        for (JsonNode n : read(json, "话题标签"))
        {
            if (n.isString() && StringUtils.isNotBlank(n.asString()))
            {
                tags.add("#" + n.asString().trim());
            }
        }
        return String.join(" ", tags);
    }

    private static String cardText(String json)
    {
        List<String> parts = new ArrayList<>();
        for (JsonNode n : read(json, "图卡"))
        {
            String t = n.path("text").asString("");
            if (StringUtils.isNotBlank(t))
            {
                parts.add(t);
            }
        }
        return String.join("\n", parts);
    }

    private static JsonNode read(String json, String label)
    {
        try
        {
            return MAPPER.readTree(StringUtils.isBlank(json) ? "[]" : json);
        }
        catch (JacksonException e)
        {
            throw new ServiceException(label + "不是合法的 JSON");
        }
    }
}
