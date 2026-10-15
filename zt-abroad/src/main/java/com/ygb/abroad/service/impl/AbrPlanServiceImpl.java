package com.ygb.abroad.service.impl;

import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrAssessment;
import com.ygb.abroad.domain.AbrAssessmentResult;
import com.ygb.abroad.domain.AbrPlanItem;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrQuestionnaire;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.AbrToolRun;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppCalendarVo;
import com.ygb.abroad.domain.vo.AppPlanItemBody;
import com.ygb.abroad.domain.vo.AppPlanItemVo;
import com.ygb.abroad.domain.vo.AppPlanVo;
import com.ygb.abroad.engine.AnswerVisibility;
import com.ygb.abroad.engine.BackgroundProfile;
import com.ygb.abroad.engine.SelectionEngine;
import com.ygb.abroad.mapper.AbrAssessmentMapper;
import com.ygb.abroad.mapper.AbrPlanItemMapper;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.mapper.AbrQuestionnaireMapper;
import com.ygb.abroad.mapper.AbrRequirementMapper;
import com.ygb.abroad.mapper.AbrToolRunMapper;
import com.ygb.abroad.service.IAbrPlanService;
import com.ygb.abroad.service.IAbrProfileService;
import com.ygb.abroad.service.IAbrSelectionService;
import com.ygb.common.core.redis.RedisCache;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DictUtils;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 我的选校单 服务层实现
 * 
 * 档位不信任前端：从快测加入时由后端按运行记录取该项目的分组，从报告推荐加入时取该测评选校结果中的分组（其他工具、项目页不带档位）。
 * 查看选校单时按访客最新的渐进档案逐个项目重新计算分组与逐项达标（M4-10）。
 * 
 * @author ygb
 */
@Service
public class AbrPlanServiceImpl implements IAbrPlanService
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 来源：快测 */
    static final String SOURCE_QUICK = "quick";

    /** 来源：其他入口工具（本校认可度、雅思够不够、现在还能申） */
    static final String SOURCE_TOOL = "tool";

    /** 来源：报告推荐 */
    static final String SOURCE_ASSESSMENT = "assessment";

    /** 日历下载链接的缓存键前缀（值为访客ID） */
    static final String CALENDAR_KEY = "abroad:plan:ics:";

    /** 日历下载链接有效分钟数 */
    static final int CALENDAR_LINK_MINUTES = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 目标学位题 */
    private static final String TARGET_DEGREE = "target_degree";

    /** 快测口径必需的题目（院校与均分） */
    static final List<String> QUICK_REQUIRED = List.of("undergrad_school", "gpa");

    /** 来源：项目页等直接加入 */
    static final String SOURCE_PROGRAM = "program";

    @Autowired
    private AbrPlanItemMapper planItemMapper;

    @Autowired
    private AbrProgramMapper programMapper;

    @Autowired
    private AbrToolRunMapper toolRunMapper;

    @Autowired
    private AbrAssessmentMapper assessmentMapper;

    @Autowired
    private AbrQuestionnaireMapper questionnaireMapper;

    @Autowired
    private IAbrProfileService profileService;

    @Autowired
    private IAbrSelectionService selectionService;

    @Autowired
    private AbrRequirementMapper requirementMapper;

    @Autowired
    private RedisCache redisCache;

    /** C 端地址（日历事件 UID 的域名部分取其主机名） */
    @Value("${abroad.app.url:http://localhost:5173/}")
    private String appUrl;

    @Override
    public List<AppPlanItemVo> selectPlanItems(AbrVisitor visitor)
    {
        return planItemMapper.selectAbrPlanItemsByVisitor(visitor.getVisitorId()).stream().filter(i -> Boolean.TRUE.equals(i.getActive()))
                .map(AbrPlanServiceImpl::toVo).toList();
    }

    @Override
    public List<AppPlanItemVo> addPlanItem(AbrVisitor visitor, AppPlanItemBody body)
    {
        AbrProgram program = programMapper.selectAbrProgramById(body.getProgramId());
        if (program == null || !"0".equals(program.getStatus()))
        {
            throw new ServiceException("项目不存在或已下线");
        }
        AbrPlanItem item = new AbrPlanItem();
        item.setVisitorId(visitor.getVisitorId());
        item.setAppUserId(visitor.getAppUserId());
        item.setProgramId(program.getProgramId());
        item.setSource(SOURCE_PROGRAM);
        if (body.getToolRunId() != null)
        {
            AbrToolRun run = toolRunMapper.selectAbrToolRunOwned(body.getToolRunId(), visitor.getVisitorId());
            if (run == null)
            {
                throw new ServiceException("工具记录不存在");
            }
            boolean quick = AbrQuickTool.TOOL.equals(run.getTool());
            item.setSource(quick ? SOURCE_QUICK : SOURCE_TOOL);
            item.setSourceRef(run.getToolRunId());
            // 只有快测给出档位；其他工具不分档
            item.setTierSnapshot(quick ? AbrQuickTool.groupOf(run.getResult(), program.getProgramId()) : null);
        }
        else if (body.getAssessmentId() != null)
        {
            AbrAssessment assessment = assessmentMapper.selectAbrAssessmentById(body.getAssessmentId());
            if (assessment == null || !visitor.getVisitorId().equals(assessment.getVisitorId()))
            {
                throw new ServiceException("测评不存在");
            }
            item.setSource(SOURCE_ASSESSMENT);
            item.setSourceRef(assessment.getAssessmentId());
            item.setTierSnapshot(groupInResult(selectionService.selectAbrAssessmentResult(assessment.getAssessmentId()), program.getProgramId()));
        }
        boolean exists = planItemMapper.selectAbrPlanItemsByVisitor(visitor.getVisitorId()).stream()
                .anyMatch(i -> i.getProgramId().equals(program.getProgramId()));
        if (!exists && planItemMapper.countAbrPlanItemsByVisitor(visitor.getVisitorId()) >= MAX_ITEMS)
        {
            throw new ServiceException("选校单最多 " + MAX_ITEMS + " 个项目，请先移除一些");
        }
        planItemMapper.upsertAbrPlanItem(item);
        return selectPlanItems(visitor);
    }

    @Override
    public AppPlanVo selectPlan(AbrVisitor visitor)
    {
        List<AbrPlanItem> items = planItemMapper.selectAbrPlanItemsByVisitor(visitor.getVisitorId());
        List<Long> ids = items.stream().map(AbrPlanItem::getProgramId).toList();
        Map<Long, AbrProgram> programs = new HashMap<>();
        if (!ids.isEmpty())
        {
            programMapper.selectAbrProgramByIds(ids).forEach(x -> programs.put(x.getProgramId(), x));
        }
        ObjectNode profile = AbrAssessmentAnswers.object(visitor.getProfile());
        String degree = StringUtils.defaultIfBlank(profile.path("target_degree").asString(""), AbrSelectionServiceImpl.DEFAULT_DEGREE);
        AbrQuestionnaire questionnaire = questionnaireMapper.selectActiveAbrQuestionnaireByDegree(degree);
        String basis = AbrPlanView.BASIS_NONE;
        List<String> missing = new ArrayList<>(QUICK_REQUIRED);
        IAbrSelectionService.PlanRun run = null;
        if (questionnaire != null && !ids.isEmpty())
        {
            AbrAssessmentAnswers.Questionnaire q = AbrAssessmentAnswers.parse(questionnaire.getDefinition());
            Map<String, JsonNode> visible = visibleAnswers(q, profile);
            if (q.questions().containsKey(TARGET_DEGREE) && !visible.containsKey(TARGET_DEGREE))
            {
                // 目标学位在创建测评时按首屏学段预填，未经分步保存，不在渐进档案中：用本次计算的学段
                visible.put(TARGET_DEGREE, MAPPER.getNodeFactory().stringNode(degree));
            }
            missing.removeIf(visible::containsKey);
            if (missing.isEmpty())
            {
                basis = fullProfile(q, visible) ? AbrPlanView.BASIS_FULL : AbrPlanView.BASIS_QUICK;
                BackgroundProfile p = profileService.normalizeToolAnswers(questionnaire.getQuestionnaireId(), visible,
                        List.copyOf(q.questions().keySet()));
                run = selectionService.evaluatePrograms(p, degree, ids, AbrPlanView.BASIS_QUICK.equals(basis));
            }
        }
        Map<String, String> labels = new LinkedHashMap<>();
        return AbrPlanView.build(basis, items, programs, run,
                c -> labels.computeIfAbsent(c, k -> StringUtils.defaultIfBlank(DictUtils.getDictLabel("abr_country", k), k)),
                AbrPlanView.BASIS_NONE.equals(basis) ? missing : List.of(), LocalDate.now(AbrLookupTools.BEIJING));
    }

    @Override
    public AppCalendarVo selectCalendar(AbrVisitor visitor)
    {
        return calendar(visitor.getVisitorId());
    }

    @Override
    public Map<String, Object> createCalendarLink(AbrVisitor visitor)
    {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisCache.setCacheObject(CALENDAR_KEY + key, String.valueOf(visitor.getVisitorId()), CALENDAR_LINK_MINUTES, TimeUnit.MINUTES);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", "/app/plan/calendar.ics?key=" + key);
        data.put("expiresInSeconds", CALENDAR_LINK_MINUTES * 60);
        return data;
    }

    @Override
    public String renderCalendarIcs(String key)
    {
        String visitorId = key == null || !key.matches("^[A-Za-z0-9_-]{32}$") ? null : redisCache.getCacheObject(CALENDAR_KEY + key);
        if (visitorId == null)
        {
            throw new ServiceException("下载链接已失效，请回到申请日历重新导出");
        }
        String host = URI.create(appUrl).getHost();
        return AbrPlanCalendar.ics(calendar(Long.valueOf(visitorId)), ZonedDateTime.now(AbrLookupTools.BEIJING),
                StringUtils.defaultIfBlank(host, "localhost"));
    }

    /**
     * 汇总某访客选校单的申请日历
     */
    private AppCalendarVo calendar(Long visitorId)
    {
        List<AbrPlanItem> items = planItemMapper.selectAbrPlanItemsByVisitor(visitorId).stream().filter(i -> Boolean.TRUE.equals(i.getActive()))
                .toList();
        List<Long> ids = items.stream().map(AbrPlanItem::getProgramId).toList();
        Map<Long, AbrProgram> programs = new HashMap<>();
        Map<Long, AbrRequirement> requirements = new HashMap<>();
        if (!ids.isEmpty())
        {
            programMapper.selectAbrProgramByIds(ids).forEach(x -> programs.put(x.getProgramId(), x));
            requirementMapper.selectLatestAbrRequirementByProgramIds(ids).forEach(r -> requirements.put(r.getProgramId(), r));
        }
        Map<String, String> labels = new LinkedHashMap<>();
        return AbrPlanCalendar.build(items, programs, requirements,
                c -> labels.computeIfAbsent(c, k -> StringUtils.defaultIfBlank(DictUtils.getDictLabel("abr_country", k), k)),
                LocalDate.now(AbrLookupTools.BEIJING));
    }

    /**
     * 档案中可见题目的答案（隐藏题的答案不参与计算）
     */
    static Map<String, JsonNode> visibleAnswers(AbrAssessmentAnswers.Questionnaire q, ObjectNode profile)
    {
        ArrayNode questions = MAPPER.createArrayNode();
        q.questions().values().forEach(questions::add);
        Map<String, JsonNode> all = new LinkedHashMap<>();
        profile.properties().forEach(e -> {
            if (q.questions().containsKey(e.getKey()) && !e.getValue().isNull())
            {
                all.put(e.getKey(), e.getValue());
            }
        });
        return AnswerVisibility.visibleAnswers(questions, all);
    }

    /**
     * 档案是否达到完整测评出报告的最少信息（资格类题目不进档案，不检查）
     */
    static boolean fullProfile(AbrAssessmentAnswers.Questionnaire q, Map<String, JsonNode> visible)
    {
        for (JsonNode id : q.root().path("report_requirements").path("min_answers_to_generate_report"))
        {
            JsonNode question = q.questions().get(id.asString(""));
            boolean eligibility = question != null && "eligibility".equals(question.path("maps_to").path("role").asString(""));
            if (!eligibility && !visible.containsKey(id.asString("")))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * 测评选校结果中某项目的分组（不在结果中或不在考虑范围为 null）
     */
    static String groupInResult(AbrAssessmentResult result, Long programId)
    {
        if (result == null)
        {
            return null;
        }
        JsonNode rows;
        try
        {
            rows = MAPPER.readTree(StringUtils.defaultIfBlank(result.getPrograms(), "[]"));
        }
        catch (JacksonException e)
        {
            return null;
        }
        for (JsonNode row : rows)
        {
            if (programId.equals(row.path("programId").asLong()))
            {
                String group = row.path("group").asString("");
                return StringUtils.isBlank(group) || SelectionEngine.EXCLUDED.equals(group) ? null : group;
            }
        }
        return null;
    }

    @Override
    public List<AppPlanItemVo> removePlanItem(AbrVisitor visitor, Long programId)
    {
        planItemMapper.deleteAbrPlanItem(visitor.getVisitorId(), programId);
        return selectPlanItems(visitor);
    }

    private static AppPlanItemVo toVo(AbrPlanItem item)
    {
        AppPlanItemVo vo = new AppPlanItemVo();
        vo.setProgramId(item.getProgramId());
        vo.setSchoolName(item.getSchoolName());
        vo.setProgramName(item.getProgramName());
        vo.setCountry(item.getCountry());
        vo.setCountryLabel(StringUtils.defaultIfBlank(DictUtils.getDictLabel("abr_country", item.getCountry()), item.getCountry()));
        vo.setTier(item.getTierSnapshot());
        vo.setSource(item.getSource());
        vo.setAddedTime(item.getCreateTime());
        return vo;
    }
}
