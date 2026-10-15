package com.ygb.abroad.service.impl;

import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrAssessment;
import com.ygb.abroad.domain.AbrAssessmentResult;
import com.ygb.abroad.domain.AbrMaterialTemplate;
import com.ygb.abroad.domain.AbrPlanItem;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrQuestionnaire;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.AbrShare;
import com.ygb.abroad.domain.AbrToolRun;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppCalendarVo;
import com.ygb.abroad.domain.vo.AppMaterialBody;
import com.ygb.abroad.domain.vo.AppMaterialsVo;
import com.ygb.abroad.domain.vo.AppPlanItemBody;
import com.ygb.abroad.domain.vo.AppPlanItemVo;
import com.ygb.abroad.domain.vo.AppPlanVo;
import com.ygb.abroad.domain.vo.AppSharedPlanVo;
import com.ygb.abroad.engine.AnswerVisibility;
import com.ygb.abroad.engine.BackgroundProfile;
import com.ygb.abroad.engine.SelectionEngine;
import com.ygb.abroad.mapper.AbrAssessmentMapper;
import com.ygb.abroad.mapper.AbrEventMapper;
import com.ygb.abroad.mapper.AbrMaterialTemplateMapper;
import com.ygb.abroad.mapper.AbrPlanItemMapper;
import com.ygb.abroad.mapper.AbrPlanMaterialMapper;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.mapper.AbrQuestionnaireMapper;
import com.ygb.abroad.mapper.AbrRequirementMapper;
import com.ygb.abroad.mapper.AbrShareMapper;
import com.ygb.abroad.mapper.AbrVisitorMapper;
import com.ygb.abroad.mapper.AbrToolRunMapper;
import com.ygb.abroad.service.IAbrEventService;
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

    private static final Logger log = LoggerFactory.getLogger(AbrPlanServiceImpl.class);

    /** 事件：加入项目 */
    static final String EVENT_ITEM_ADDED = "plan_item_added";

    /** 事件：移除项目 */
    static final String EVENT_ITEM_REMOVED = "plan_item_removed";

    /** 事件：勾选材料 */
    static final String EVENT_MATERIAL_UPDATED = "plan_material_updated";

    /** 事件：开启家长只读视图 */
    static final String EVENT_SHARE_CREATED = "plan_share_created";

    /** 事件：撤销家长只读视图 */
    static final String EVENT_SHARE_REVOKED = "plan_share_revoked";

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

    @Autowired
    private AbrMaterialTemplateMapper templateMapper;

    @Autowired
    private AbrPlanMaterialMapper planMaterialMapper;

    @Autowired
    private AbrShareMapper shareMapper;

    @Autowired
    private AbrVisitorMapper visitorMapper;

    @Autowired
    private AbrShareSupport shareSupport;

    @Autowired
    private IAbrEventService eventService;

    @Autowired
    private AbrEventMapper eventMapper;

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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", item.getSource());
        payload.put("tier", item.getTierSnapshot());
        edited(visitor, EVENT_ITEM_ADDED, program.getProgramId(), payload);
        return selectPlanItems(visitor);
    }

    @Override
    public AppPlanVo selectPlan(AbrVisitor visitor)
    {
        AppPlanVo plan = plan(visitor);
        try
        {
            detectOnPlanViewed(visitor, plan);
        }
        catch (RuntimeException e)
        {
            // 转化时刻只用于推荐与线索评分，判断失败不影响选校单
            log.warn("转化时刻判断失败 visitorId={}：{}", visitor.getVisitorId(), e.getMessage());
        }
        return plan.withRecommendation(eventService.takeRecommendation(visitor));
    }

    /**
     * 打开选校单时判断：冲保失衡、要求变化后不达标、截止前材料不齐（已记录过的时刻不再判断）
     */
    private void detectOnPlanViewed(AbrVisitor visitor, AppPlanVo plan)
    {
        Map<String, JsonNode> rules = eventService.activeMomentParams();
        Set<String> done = eventService.recordedMoments(visitor.getVisitorId());
        if (rules.containsKey(AbrPlanMoments.IMBALANCE) && !done.contains(AbrPlanMoments.IMBALANCE)
                && AbrPlanMoments.imbalance(plan, rules.get(AbrPlanMoments.IMBALANCE)))
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tierCounts", plan.tierCounts());
            eventService.recordMoment(visitor, AbrPlanMoments.IMBALANCE, payload);
        }
        List<Long> ids = plan.items().stream().filter(i -> AbrPlanView.STATUS_OK.equals(i.status())).map(AppPlanVo.Item::programId).toList();
        if (rules.containsKey(AbrPlanMoments.REQUIREMENT_UNMET) && !done.contains(AbrPlanMoments.REQUIREMENT_UNMET) && !ids.isEmpty())
        {
            Map<Long, Date> changed = new HashMap<>();
            for (AbrRequirement r : requirementMapper.selectLatestAbrRequirementByProgramIds(ids))
            {
                changed.put(r.getProgramId(), r.getUpdateTime() != null ? r.getUpdateTime() : r.getCreateTime());
            }
            List<Long> affected = AbrPlanMoments.requirementUnmet(plan, changed);
            if (!affected.isEmpty())
            {
                eventService.recordMoment(visitor, AbrPlanMoments.REQUIREMENT_UNMET, Map.of("programIds", affected));
            }
        }
        if (rules.containsKey(AbrPlanMoments.MATERIALS_BEHIND) && !done.contains(AbrPlanMoments.MATERIALS_BEHIND) && !ids.isEmpty())
        {
            detectMaterialsBehind(visitor, materials(visitor.getVisitorId()), rules.get(AbrPlanMoments.MATERIALS_BEHIND));
        }
    }

    /**
     * 截止前材料不齐
     */
    private void detectMaterialsBehind(AbrVisitor visitor, AppMaterialsVo materials, JsonNode params)
    {
        List<Long> affected = AbrPlanMoments.materialsBehind(materials, LocalDate.now(AbrLookupTools.BEIJING), params);
        if (!affected.isEmpty())
        {
            eventService.recordMoment(visitor, AbrPlanMoments.MATERIALS_BEHIND, Map.of("programIds", affected));
        }
    }

    /**
     * 加入或移除项目：记事件，并判断反复修改
     */
    private void edited(AbrVisitor visitor, String eventType, Long programId, Map<String, Object> payload)
    {
        eventService.recordAction(visitor, eventType, "program", programId, payload);
        try
        {
            JsonNode params = eventService.activeMomentParams().get(AbrPlanMoments.REPEATED_EDITS);
            if (params == null || eventService.recordedMoments(visitor.getVisitorId()).contains(AbrPlanMoments.REPEATED_EDITS))
            {
                return;
            }
            Date since = new Date(System.currentTimeMillis() - AbrPlanMoments.windowDays(params) * 86_400_000L);
            List<Date> times = eventMapper.selectEventTimes(visitor.getVisitorId(), List.of(EVENT_ITEM_ADDED, EVENT_ITEM_REMOVED), since);
            if (AbrPlanMoments.repeatedEdits(times, params))
            {
                eventService.recordMoment(visitor, AbrPlanMoments.REPEATED_EDITS, Map.of("edits", times.size()));
            }
        }
        catch (RuntimeException e)
        {
            log.warn("反复修改判断失败 visitorId={}：{}", visitor.getVisitorId(), e.getMessage());
        }
    }

    /**
     * 选校单计算（不判断转化时刻、不取推荐；家长只读视图与分享卡也用它）
     */
    private AppPlanVo plan(AbrVisitor visitor)
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

    @Override
    public AppMaterialsVo selectMaterials(AbrVisitor visitor)
    {
        AppMaterialsVo vo = materials(visitor.getVisitorId());
        try
        {
            JsonNode params = eventService.activeMomentParams().get(AbrPlanMoments.MATERIALS_BEHIND);
            if (params != null && !eventService.recordedMoments(visitor.getVisitorId()).contains(AbrPlanMoments.MATERIALS_BEHIND))
            {
                detectMaterialsBehind(visitor, vo, params);
            }
        }
        catch (RuntimeException e)
        {
            log.warn("转化时刻判断失败 visitorId={}：{}", visitor.getVisitorId(), e.getMessage());
        }
        return vo;
    }

    @Override
    public AppMaterialsVo updateMaterial(AbrVisitor visitor, AppMaterialBody body)
    {
        AppMaterialsVo current = materials(visitor.getVisitorId());
        String key = body.getKey().trim();
        if (!AbrPlanMaterials.keys(current).contains(key))
        {
            throw new ServiceException("材料不在当前清单中，请刷新后重试");
        }
        planMaterialMapper.upsertPlanMaterial(visitor.getVisitorId(), visitor.getAppUserId(), key, Boolean.TRUE.equals(body.getDone()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", key);
        payload.put("done", Boolean.TRUE.equals(body.getDone()));
        eventService.recordAction(visitor, EVENT_MATERIAL_UPDATED, null, null, payload);
        return materials(visitor.getVisitorId());
    }

    @Override
    public Map<String, Object> createPlanShare(AbrVisitor visitor)
    {
        shareSupport.shareCode(AbrPlanShare.KIND_VIEW, visitor.getVisitorId(), visitor.getVisitorId(), "{}");
        eventService.recordAction(visitor, EVENT_SHARE_CREATED, "share", null, null);
        return planShareStatus(visitor);
    }

    @Override
    public Map<String, Object> selectPlanShare(AbrVisitor visitor)
    {
        return planShareStatus(visitor);
    }

    @Override
    public Map<String, Object> revokePlanShare(AbrVisitor visitor)
    {
        shareMapper.revokeAbrShare(AbrPlanShare.KIND_VIEW, visitor.getVisitorId(), visitor.getVisitorId());
        eventService.recordAction(visitor, EVENT_SHARE_REVOKED, "share", null, null);
        return planShareStatus(visitor);
    }

    @Override
    public AppSharedPlanVo selectSharedPlan(String code)
    {
        AbrShare share = code == null || !code.matches("^[A-Za-z0-9]{10}$") ? null : shareMapper.selectAbrShareByCode(code);
        if (share == null || Boolean.TRUE.equals(share.getRevoked()) || !AbrPlanShare.KIND_VIEW.equals(share.getKind()))
        {
            throw new ServiceException("分享链接已失效，请让孩子重新分享");
        }
        AbrVisitor owner = visitorMapper.selectAbrVisitorById(share.getVisitorId());
        if (owner == null)
        {
            throw new ServiceException("分享链接已失效，请让孩子重新分享");
        }
        shareMapper.touchAbrShareView(share.getShareId());
        try
        {
            eventService.recordMoment(owner, AbrPlanMoments.PARENT_VIEW, Map.of("shareId", share.getShareId()));
        }
        catch (RuntimeException e)
        {
            log.warn("家长查看事件记录失败 visitorId={}：{}", owner.getVisitorId(), e.getMessage());
        }
        return AbrPlanShare.view(plan(owner), calendar(owner.getVisitorId()), materials(owner.getVisitorId()));
    }

    @Override
    public byte[] renderPlanCard(AbrVisitor visitor)
    {
        AppPlanVo plan = plan(visitor);
        if (plan.items().stream().allMatch(i -> AbrSelectionServiceImpl.PLAN_OFFLINE.equals(i.status())))
        {
            throw new ServiceException("选校单还是空的，先加几个项目再分享吧");
        }
        String code = shareSupport.shareCode(AbrPlanShare.KIND_CARD, visitor.getVisitorId(), visitor.getVisitorId(), "{}");
        return shareSupport.png(AbrPlanShare.card(plan, shareSupport.brandName(), shareSupport.landingLink(code)));
    }

    /**
     * 家长只读视图的分享状态（未分享或已撤销时 shared 为 false，不返回链接）
     */
    private Map<String, Object> planShareStatus(AbrVisitor visitor)
    {
        AbrShare share = shareMapper.selectAbrShareByRef(AbrPlanShare.KIND_VIEW, visitor.getVisitorId(), visitor.getVisitorId());
        boolean shared = share != null && !Boolean.TRUE.equals(share.getRevoked());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("shared", shared);
        if (shared)
        {
            String code = share.getShareCode();
            data.put("code", code);
            data.put("url", shareSupport.appLink("pages/share/plan?code=" + code + "&ch=share&ct=" + code));
            data.put("viewCount", share.getViewCount());
            data.put("lastViewTime", share.getLastViewTime());
        }
        return data;
    }

    /**
     * 汇总某访客选校单的材料清单
     */
    private AppMaterialsVo materials(Long visitorId)
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
        Map<String, String> templates = new HashMap<>();
        for (AbrMaterialTemplate t : templateMapper.selectActiveAbrMaterialTemplates())
        {
            templates.put(t.getCountry() + ":" + t.getDegree(), t.getItems());
        }
        Map<String, String> labels = new LinkedHashMap<>();
        return AbrPlanMaterials.build(items, programs, requirements, templates, AbrMaterials.types(),
                new HashSet<>(planMaterialMapper.selectDoneMaterialKeys(visitorId)),
                c -> labels.computeIfAbsent(c, k -> StringUtils.defaultIfBlank(DictUtils.getDictLabel("abr_country", k), k)),
                LocalDate.now(AbrLookupTools.BEIJING));
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
        if (planItemMapper.deleteAbrPlanItem(visitor.getVisitorId(), programId) > 0)
        {
            edited(visitor, EVENT_ITEM_REMOVED, programId, null);
        }
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
