package com.ygb.abroad.service.impl;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import com.ygb.abroad.ai.AiConstants;
import com.ygb.abroad.ai.AiContext;
import com.ygb.abroad.ai.AiGateway;
import com.ygb.abroad.ai.AiResult;
import com.ygb.abroad.domain.AbrChannelAccount;
import com.ygb.abroad.domain.AbrContent;
import com.ygb.abroad.domain.AbrPlaybook;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.AbrTopic;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.abroad.growth.compliance.ComplianceChecker;
import com.ygb.abroad.mapper.AbrChannelAccountMapper;
import com.ygb.abroad.mapper.AbrContentMapper;
import com.ygb.abroad.mapper.AbrPlaybookMapper;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.mapper.AbrRequirementMapper;
import com.ygb.abroad.mapper.AbrTopicMapper;
import com.ygb.abroad.service.IAbrComplianceService;
import com.ygb.abroad.service.IAbrContentService;
import com.ygb.abroad.service.IAbrContentWriterService;
import com.ygb.abroad.service.IAbrExceptionService;
import com.ygb.abroad.service.IAbrExperimentService;
import com.ygb.abroad.service.IAbrGrowthGuardService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DictUtils;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * 内容创作 content_writer 服务层实现
 * <ol>
 * <li>取选题引用的院校库项目：90 天内核对过的给数值，过期的只给名称与“以官网为准”；</li>
 * <li>经 AI 网关写标题、正文、话题；系统追加平台的文末引导（私信口令或工具）与 AI 生成标识；</li>
 * <li>按目标平台过合规检查，不通过带着拦截项重写 1 次；仍不通过照常入库，由合规闸门拦截进例外队列；</li>
 * <li>经内容服务入库（执行模式、抽检、内容编号、闸门事件），选题记为已写稿。</li>
 * </ol>
 *
 * @author ygb
 */
@Service
public class AbrContentWriterServiceImpl implements IAbrContentWriterService
{
    private static final Logger log = LoggerFactory.getLogger(AbrContentWriterServiceImpl.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** Agent 编码 */
    static final String AGENT = "content_writer";

    /** 最多写几次（第一次 + 重写 1 次） */
    static final int MAX_ATTEMPTS = 2;

    private final ReentrantLock lock = new ReentrantLock();

    @Autowired
    private IAbrExperimentService experimentService;

    @Autowired
    private AbrTopicMapper topicMapper;

    @Autowired
    private AbrChannelAccountMapper accountMapper;

    @Autowired
    private AbrPlaybookMapper playbookMapper;

    @Autowired
    private AbrProgramMapper programMapper;

    @Autowired
    private AbrRequirementMapper requirementMapper;

    @Autowired
    private AbrContentMapper contentMapper;

    @Autowired
    private IAbrContentService contentService;

    @Autowired
    private IAbrComplianceService complianceService;

    @Autowired
    private IAbrGrowthGuardService guardService;

    @Autowired
    private IAbrExceptionService exceptionService;

    @Autowired
    private AiGateway gateway;

    @Autowired
    private Environment env;

    /** AI 生成标识（与内容服务一致） */
    @Value("${abroad.content.ai-label:本文由 AI 辅助生成}")
    private String aiLabel;

    /** 提前几天写稿（计划日期从今天起） */
    @Value("${abroad.content.write-ahead-days:2}")
    private int aheadDays;

    /** 每轮最多写几条 */
    @Value("${abroad.content.write-batch:10}")
    private int batch;

    @Override
    public Map<String, Object> writeDue(String operator)
    {
        if (!lock.tryLock())
        {
            throw new ServiceException("正在写稿，请稍后刷新查看");
        }
        try
        {
            LocalDate today = MarketingCalendar.today();
            List<Map<String, Object>> written = new ArrayList<>();
            List<Map<String, Object>> failed = new ArrayList<>();
            Map<String, Object> result = new LinkedHashMap<>();
            for (AbrTopic t : topicMapper.selectDueTopics(Date.valueOf(today), Date.valueOf(today.plusDays(Math.max(0, aheadDays))), Math.max(1, batch)))
            {
                String blocked = guardService.agentBlockReason(AGENT);
                if (blocked != null)
                {
                    result.put("stopped", "content_writer 现在不能运行：" + blocked);
                    break;
                }
                try
                {
                    AbrContent c = write(t, operator);
                    written.add(Map.of("topicId", t.getTopicId(), "contentId", c.getContentId(), "gateHint", c.getRemark() == null ? "" : c.getRemark()));
                }
                catch (ServiceException e)
                {
                    failed.add(Map.of("topicId", t.getTopicId(), "reason", StringUtils.defaultString(e.getMessage())));
                }
            }
            result.put("written", written);
            result.put("failed", failed);
            return result;
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public AbrContent writeTopic(Long topicId, String operator)
    {
        AbrTopic t = topicId == null ? null : topicMapper.selectAbrTopicById(topicId);
        if (t == null)
        {
            throw new ServiceException("选题不存在");
        }
        String blocked = guardService.agentBlockReason(AGENT);
        if (blocked != null)
        {
            throw new ServiceException("content_writer 现在不能运行：" + blocked);
        }
        if (!lock.tryLock())
        {
            throw new ServiceException("正在写稿，请稍后刷新查看");
        }
        try
        {
            return write(t, operator);
        }
        finally
        {
            lock.unlock();
        }
    }

    private AbrContent write(AbrTopic t, String operator)
    {
        AbrTopic topic = topicMapper.selectAbrTopicById(t.getTopicId());
        if (topic == null || topic.getContentId() != null || !AbrTopic.STATUS_PLANNED.equals(topic.getStatus()))
        {
            throw new ServiceException("选题已写稿、已跳过或已取消");
        }
        AbrChannelAccount account = accountMapper.selectAbrChannelAccountById(topic.getAccountId());
        if (account == null || !"0".equals(account.getStatus()) || "Y".equals(account.getPaused()))
        {
            throw new ServiceException("发布账号已停用或已暂停");
        }
        LocalDate today = MarketingCalendar.today();
        List<Long> ids = longs(topic.getProgramIds());
        List<Map<String, Object>> programs = new ArrayList<>();
        Set<String> countries = new LinkedHashSet<>();
        ArrayNode citations = MAPPER.createArrayNode();
        if (!ids.isEmpty())
        {
            Map<Long, AbrRequirement> reqs = new LinkedHashMap<>();
            requirementMapper.selectLatestAbrRequirementByProgramIds(ids).forEach(r -> reqs.put(r.getProgramId(), r));
            for (AbrProgram p : programMapper.selectAbrProgramByIds(ids))
            {
                if (!"0".equals(p.getStatus()) || "2".equals(p.getDelFlag()))
                {
                    continue;
                }
                programs.add(AbrContentWriterRules.programFacts(p, reqs.get(p.getProgramId()), label("abr_country", p.getCountry()), today));
                countries.add(p.getCountry());
                citations.addObject().put("type", "program").put("id", p.getProgramId());
            }
        }
        if ("flash".equals(topic.getContentKind()) && programs.isEmpty())
        {
            topic.setStatus(AbrTopic.STATUS_SKIPPED);
            topic.setRemark("引用的项目已下线，快讯不写");
            topic.setUpdateBy(operator);
            topicMapper.updateAbrTopic(topic);
            throw new ServiceException("引用的项目已下线，快讯已跳过");
        }
        Map<String, Object> input = input(topic, account, programs);
        // 评测回流用第一稿的输入（重写时输入多了拦截说明）
        String firstInput = MAPPER.writeValueAsString(input);
        String hook = label("abr_hook_tool", topic.getHookTool());
        String codeword = unusedCodeword();
        // 钩子文案 A/B 实验（M9-04）：运行中的实验按选题分组，已结束的用胜出文案，没有实验用配置
        IAbrExperimentService.HookCopy copy = experimentService.hookCopy(account.getPlatform(), topic.getTopicId());
        String closing = AbrContentWriterRules.closing(copy != null ? copy.template() : closingTemplate(account.getPlatform()), codeword, hook);
        AbrContent draft = null;
        Long callId = null;
        ComplianceChecker.Result check = null;
        AiResult filtered = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)
        {
            AiResult ai = gateway.invoke(AGENT, input, AiContext.of("topic", String.valueOf(topic.getTopicId()), operator));
            if (!ai.isSuccess())
            {
                if (AiConstants.STATUS_FILTERED.equals(ai.getStatus()))
                {
                    // 网关输出过滤拦下（录取承诺、违禁词等）：带着命中项重写
                    filtered = ai;
                    input.put("feedback", AbrContentWriterRules.filterFeedback(ai.getFlags()));
                    continue;
                }
                if (draft != null)
                {
                    // 重写时模型不可用：用第一稿入库，由合规闸门拦截
                    break;
                }
                throw new ServiceException("AI 写稿失败（" + ai.getStatus() + "）：" + StringUtils.defaultString(ai.getMessage()));
            }
            filtered = null;
            callId = ai.getCallId();
            draft = draft(topic, account, ai.getOutput(), closing, codeword, citations.toString(), countries);
            check = complianceService.checkDraft(draft);
            if (check.passed())
            {
                break;
            }
            input.put("feedback", AbrContentWriterRules.feedback(check));
        }
        if (draft == null)
        {
            handoff(topic, account, filtered, firstInput, operator);
            throw new ServiceException("两次写稿都被输出过滤拦下，选题已转例外队列");
        }
        draft.setAiInput(firstInput);
        draft.setRemark(check != null && !check.passed() ? "重写后仍未通过合规检查，待闸门处理" : null);
        AbrContent saved = contentService.insertAgentContent(draft, AGENT, callId);
        if (copy != null)
        {
            experimentService.recordUnit(copy.experimentId(), saved.getContentId(), copy.variant());
        }
        if (topicMapper.markDrafted(topic.getTopicId(), saved.getContentId(), operator) == 0)
        {
            log.warn("选题 {} 已被其他写稿记为已写稿，本次内容 {} 保留为草稿", topic.getTopicId(), saved.getContentId());
        }
        return saved;
    }

    /**
     * 两次都没写出可用稿件：选题记为已跳过，转例外队列（原因 Agent 转人工；带模型输入与命中项，确认拦截时回流评测集）
     */
    private void handoff(AbrTopic topic, AbrChannelAccount account, AiResult ai, String firstInput, String operator)
    {
        topic.setStatus(AbrTopic.STATUS_SKIPPED);
        topic.setRemark("AI 写稿两次被输出过滤拦下，已转例外队列");
        topic.setUpdateBy(operator);
        topicMapper.updateAbrTopic(topic);
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("topicId", topic.getTopicId());
        snap.put("title", topic.getTitle());
        snap.put("angle", topic.getAngle());
        snap.put("platform", account.getPlatform());
        snap.put("accountCode", account.getAccountCode());
        snap.put("aiGenerated", "Y");
        snap.put("message", ai == null ? "" : StringUtils.substring(ai.getMessage(), 0, 500));
        exceptionService.enqueue(AiConstants.QUEUE_EXCEPTION, IAbrExceptionService.REASON_AGENT_HANDOFF, AGENT, "topic", String.valueOf(topic.getTopicId()),
                ai == null ? null : ai.getCallId(), MAPPER.writeValueAsString(snap), AbrContentWriterRules.filterFlags(ai == null ? List.of() : ai.getFlags()),
                firstInput);
    }

    /**
     * 由模型输出拼成内容：标题、正文（+ 文末引导 + AI 标识）、话题、引用依据、平台与口令
     */
    private AbrContent draft(AbrTopic topic, AbrChannelAccount account, JsonNode out, String closing, String codeword, String citations,
            Set<String> countries)
    {
        AbrContent c = new AbrContent();
        c.setAccountId(account.getAccountId());
        c.setPlatform(account.getPlatform());
        c.setTitle(StringUtils.substring(out.path("title").asString("").trim(), 0, 100));
        c.setBody(AbrContentWriterRules.assemble(out.path("body").asString(""), closing, aiLabel));
        ArrayNode tags = MAPPER.createArrayNode();
        AbrContentWriterRules.hashtags(out.path("hashtags")).forEach(tags::add);
        c.setHashtags(tags.toString());
        c.setCitations(citations);
        c.setCards("[]");
        c.setContentKind(topic.getContentKind());
        c.setTopic(topic.getTitle());
        c.setTopicRef(String.valueOf(topic.getTopicId()));
        c.setPlaybookCode(topic.getPlaybookCode());
        c.setHookTool(topic.getHookTool());
        Set<String> allowed = AbrPlaybookServiceImpl.dictValues("abr_country");
        ArrayNode cs = MAPPER.createArrayNode();
        countries.stream().filter(allowed::contains).forEach(cs::add);
        c.setCountries(cs.toString());
        c.setCodeword(codeword);
        c.setAiGenerated("Y");
        return c;
    }

    /**
     * 模型输入：平台、人设、内容类型、选题、剧本、文末工具、院校库事实（不含个人信息）
     */
    private Map<String, Object> input(AbrTopic topic, AbrChannelAccount account, List<Map<String, Object>> programs)
    {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("platform", label("abr_platform", account.getPlatform()));
        in.put("persona", label("abr_persona", account.getPersona()));
        in.put("kind", topic.getContentKind());
        in.put("kindLabel", label("abr_content_kind", topic.getContentKind()));
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("title", topic.getTitle());
        t.put("angle", StringUtils.defaultString(topic.getAngle()));
        in.put("topic", t);
        Map<String, Object> pb = new LinkedHashMap<>();
        AbrPlaybook p = topic.getPlaybookCode() == null ? null : playbook(topic.getPlaybookCode());
        pb.put("name", p == null ? "" : p.getPlaybookName());
        pb.put("audience", p == null ? "" : StringUtils.defaultString(p.getAudience()));
        in.put("playbook", pb);
        in.put("hook", label("abr_hook_tool", topic.getHookTool()));
        in.put("programs", programs);
        in.put("feedback", new ArrayList<String>());
        return in;
    }

    private AbrPlaybook playbook(String code)
    {
        AbrPlaybook q = new AbrPlaybook();
        q.setPlaybookCode(code);
        return playbookMapper.selectAbrPlaybookList(q).stream().filter(x -> code.equals(x.getPlaybookCode())).findFirst().orElse(null);
    }

    private String closingTemplate(String platform)
    {
        String t = env.getProperty("abroad.content.closing." + platform);
        return t != null ? t : env.getProperty("abroad.content.closing.default", "");
    }

    private String unusedCodeword()
    {
        for (int i = 0; i < 20; i++)
        {
            String w = AbrContentRules.newCodeword();
            if (!contentMapper.existsCodeword(w, null))
            {
                return w;
            }
        }
        throw new ServiceException("可用的 4 位数字口令不足");
    }

    private static List<Long> longs(String json)
    {
        List<Long> out = new ArrayList<>();
        try
        {
            for (JsonNode n : MAPPER.readTree(StringUtils.defaultIfBlank(json, "[]")))
            {
                if (n.isIntegralNumber() && !out.contains(n.asLong()))
                {
                    out.add(n.asLong());
                }
            }
        }
        catch (JacksonException e)
        {
            // 格式不对时当作没有引用
        }
        return out;
    }

    private static String label(String dictType, String value)
    {
        String l = value == null ? null : DictUtils.getDictLabel(dictType, value);
        return StringUtils.isBlank(l) ? StringUtils.defaultString(value) : l;
    }
}
