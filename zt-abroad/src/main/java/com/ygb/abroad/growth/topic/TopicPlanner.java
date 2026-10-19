package com.ygb.abroad.growth.topic;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import tools.jackson.databind.JsonNode;

/**
 * 选题排期 topic_planner 的确定性部分（纯计算，不访问数据库、不调用大模型）
 * <ol>
 * <li>空位：每个账号未来若干天、每天若干条，扣除已有选题；</li>
 * <li>分配：空位按剧本优先级轮流分给当前生效的剧本，内容类型按该剧本的配比补齐缺口（已有选题计入）；</li>
 * <li>校验大模型的选题：空位须存在且未用、标题长度、文末工具在允许范围、引用的项目来自输入、快讯须引用项目、
 * 不与近期标题重复、通过合规词表（由调用方传入的检查）；</li>
 * <li>兜底：没填上的空位用剧本的备用选题（轮换，跳过近期用过的），类型记为长尾问答。</li>
 * </ol>
 *
 * @author ygb
 */
public final class TopicPlanner
{
    /** 内容类型：变化快讯与限时信息（须引用院校库项目） */
    public static final String KIND_FLASH = "flash";

    /** 内容类型：搜索型长尾问答（备用选题使用） */
    public static final String KIND_LONGTAIL = "longtail";

    /** 来源：大模型 */
    public static final String SOURCE_AI = "ai";

    /** 来源：剧本备用选题 */
    public static final String SOURCE_BACKUP = "backup";

    /** 标题最短、最长字数 */
    static final int TITLE_MIN = 4;

    static final int TITLE_MAX = 40;

    /** 切入点最长字数 */
    static final int ANGLE_MAX = 200;

    /** 每条选题最多引用的项目数 */
    static final int MAX_PROGRAMS = 8;

    /**
     * 剧本（按优先级从高到低传入）
     *
     * @param code 剧本编码
     * @param mix 内容配比（类型 → 百分比；空为沿用默认）
     * @param hooks 允许的文末工具（已上线的，按主次）
     * @param backupTopics 备用选题
     */
    public record Playbook(String code, Map<String, Integer> mix, List<String> hooks, List<String> backupTopics)
    {
    }

    /**
     * 一个空位
     *
     * @param id 空位编号（s1、s2…，给大模型引用）
     * @param date 日期
     * @param slotNo 当天第几条（从 1 开始，跳过已有的）
     * @param kind 内容类型
     * @param playbookCode 所属剧本（没有生效剧本时为 null）
     */
    public record Slot(String id, LocalDate date, int slotNo, String kind, String playbookCode)
    {
    }

    /**
     * 排好的选题
     *
     * @param slot 空位
     * @param kind 内容类型（备用选题为长尾问答）
     * @param title 标题
     * @param angle 切入点
     * @param hookTool 文末工具
     * @param programIds 引用的项目
     * @param source 来源（ai / backup）
     */
    public record Topic(Slot slot, String kind, String title, String angle, String hookTool, List<Long> programIds, String source)
    {
    }

    /**
     * 校验结果
     *
     * @param accepted 采用的选题
     * @param rejected 丢弃的选题与原因（写入运行结果，便于调提示词）
     */
    public record Checked(List<Topic> accepted, List<String> rejected)
    {
    }

    private TopicPlanner()
    {
    }

    /**
     * 计算空位并分配剧本与内容类型
     *
     * @param dates 要排的日期（按顺序）
     * @param perDay 每天条数
     * @param existingPerDay 各日期已有的选题数（不含已取消）
     * @param existingKinds 窗口内已有选题的类型计数
     * @param playbooks 生效剧本（按优先级从高到低；为空时只用默认配比）
     * @param defaultMix 默认配比
     * @param disabledKinds 暂不排的类型（如案例库上线前的案例）
     * @return 空位
     */
    public static List<Slot> slots(List<LocalDate> dates, int perDay, Map<LocalDate, Integer> existingPerDay, Map<String, Integer> existingKinds,
            List<Playbook> playbooks, Map<String, Integer> defaultMix, Set<String> disabledKinds)
    {
        List<Slot> out = new ArrayList<>();
        // 类型数量按账号整个窗口统计（含已有选题），各空位按所属剧本的配比补缺口
        Map<String, Integer> counts = new HashMap<>(existingKinds);
        int n = 0;
        for (LocalDate d : dates)
        {
            int have = existingPerDay.getOrDefault(d, 0);
            for (int i = have + 1; i <= perDay; i++)
            {
                Playbook pb = playbooks.isEmpty() ? null : playbooks.get(n % playbooks.size());
                Map<String, Integer> mix = pb == null || pb.mix() == null || pb.mix().isEmpty() ? defaultMix : pb.mix();
                String kind = nextKind(mix, counts, disabledKinds);
                counts.merge(kind, 1, Integer::sum);
                n++;
                out.add(new Slot("s" + n, d, i, kind, pb == null ? null : pb.code()));
            }
        }
        return out;
    }

    /**
     * 按配比选下一个类型：目标份额与已有数量之差最大的（并列取配比大的、再按类型名），跳过暂不排的类型
     *
     * @param mix 配比
     * @param counts 已有数量
     * @param disabled 暂不排的类型
     * @return 类型
     */
    static String nextKind(Map<String, Integer> mix, Map<String, Integer> counts, Set<String> disabled)
    {
        int total = counts.entrySet().stream().filter(e -> mix.containsKey(e.getKey()) && !disabled.contains(e.getKey()))
                .mapToInt(Map.Entry::getValue).sum() + 1;
        int weightSum = mix.entrySet().stream().filter(e -> !disabled.contains(e.getKey())).mapToInt(e -> Math.max(0, e.getValue())).sum();
        String best = null;
        double bestGap = -Double.MAX_VALUE;
        int bestWeight = -1;
        for (String kind : mix.keySet().stream().sorted().toList())
        {
            int w = Math.max(0, mix.get(kind));
            if (disabled.contains(kind) || w == 0 || weightSum == 0)
            {
                continue;
            }
            double gap = total * (double) w / weightSum - counts.getOrDefault(kind, 0);
            if (gap > bestGap + 1e-9 || (Math.abs(gap - bestGap) <= 1e-9 && w > bestWeight))
            {
                best = kind;
                bestGap = gap;
                bestWeight = w;
            }
        }
        return best == null ? KIND_LONGTAIL : best;
    }

    /**
     * 校验大模型输出：{"topics":[{"slot","title","angle","hookTool","programIds"}]}
     *
     * @param slots 空位
     * @param output 输出
     * @param hooksByPlaybook 剧本 → 允许的文末工具（键 "" 为没有剧本时）
     * @param knownPrograms 输入中提供的项目
     * @param recentTitles 近期标题（忽略大小写与空白比较）
     * @param compliant 合规检查（标题 + 切入点，引用的项目）→ 是否通过
     * @return 采用与丢弃的选题
     */
    public static Checked accept(List<Slot> slots, JsonNode output, Map<String, List<String>> hooksByPlaybook, Set<Long> knownPrograms,
            Set<String> recentTitles, BiPredicate<String, List<Long>> compliant)
    {
        Map<String, Slot> byId = new LinkedHashMap<>();
        slots.forEach(s -> byId.put(s.id(), s));
        Set<String> used = new HashSet<>();
        Set<String> titles = new HashSet<>();
        recentTitles.forEach(t -> titles.add(norm(t)));
        List<Topic> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        JsonNode topics = output == null ? null : output.path("topics");
        if (topics == null || !topics.isArray())
        {
            return new Checked(accepted, rejected);
        }
        for (JsonNode t : topics)
        {
            String id = t.path("slot").asString("");
            Slot slot = byId.get(id);
            String title = t.path("title").asString("").trim();
            String angle = t.path("angle").asString("").trim();
            String hook = t.path("hookTool").asString("").trim();
            List<Long> programs = new ArrayList<>();
            boolean unknownProgram = false;
            for (JsonNode p : t.path("programIds"))
            {
                if (p.isIntegralNumber() && knownPrograms.contains(p.asLong()))
                {
                    if (!programs.contains(p.asLong()))
                    {
                        programs.add(p.asLong());
                    }
                }
                else
                {
                    unknownProgram = true;
                }
            }
            String reason = null;
            List<String> hooks = slot == null ? List.of() : hooksByPlaybook.getOrDefault(slot.playbookCode() == null ? "" : slot.playbookCode(), List.of());
            if (slot == null || used.contains(id))
            {
                reason = "空位不存在或重复";
            }
            else if (title.length() < TITLE_MIN || title.length() > TITLE_MAX)
            {
                reason = "标题须为 " + TITLE_MIN + "–" + TITLE_MAX + " 个字";
            }
            else if (angle.length() > ANGLE_MAX)
            {
                reason = "切入点超过 " + ANGLE_MAX + " 个字";
            }
            else if (!hooks.contains(hook))
            {
                reason = "文末工具“" + hook + "”不在剧本允许的范围";
            }
            else if (unknownProgram || programs.size() > MAX_PROGRAMS)
            {
                reason = "引用了输入以外的项目或超过 " + MAX_PROGRAMS + " 个";
            }
            else if (KIND_FLASH.equals(slot.kind()) && programs.isEmpty())
            {
                reason = "快讯须引用院校库项目";
            }
            else if (titles.contains(norm(title)))
            {
                reason = "与近期标题重复";
            }
            else if (!compliant.test(title + "\n" + angle, programs))
            {
                reason = "未通过合规词表或事实核对";
            }
            if (reason != null)
            {
                rejected.add((id.isEmpty() ? "?" : id) + "「" + title + "」：" + reason);
                continue;
            }
            used.add(id);
            titles.add(norm(title));
            accepted.add(new Topic(slot, slot.kind(), title, angle, hook, programs, SOURCE_AI));
        }
        return new Checked(accepted, rejected);
    }

    /**
     * 用剧本备用选题填补剩下的空位：每个剧本内轮换，跳过近期用过和本次已用的；没有可用的空位留空
     *
     * @param slots 剩下的空位
     * @param playbooks 剧本编码 → 剧本
     * @param recentTitles 近期标题
     * @param compliant 合规检查
     * @return 备用选题
     */
    public static List<Topic> backup(List<Slot> slots, Map<String, Playbook> playbooks, Set<String> recentTitles,
            BiPredicate<String, List<Long>> compliant)
    {
        Set<String> titles = new LinkedHashSet<>();
        recentTitles.forEach(t -> titles.add(norm(t)));
        List<Topic> out = new ArrayList<>();
        for (Slot s : slots)
        {
            Playbook pb = s.playbookCode() == null ? null : playbooks.get(s.playbookCode());
            if (pb == null || pb.backupTopics() == null)
            {
                continue;
            }
            for (String candidate : pb.backupTopics())
            {
                String title = candidate == null ? "" : candidate.trim();
                if (title.length() < TITLE_MIN || title.length() > TITLE_MAX || titles.contains(norm(title)) || !compliant.test(title, List.of()))
                {
                    continue;
                }
                titles.add(norm(title));
                String hook = pb.hooks().isEmpty() ? null : pb.hooks().get(0);
                out.add(new Topic(s, KIND_LONGTAIL, title, "", hook, List.of(), SOURCE_BACKUP));
                break;
            }
        }
        return out;
    }

    /**
     * 标题比较用的规范化：去掉空白，转小写
     *
     * @param title 标题
     * @return 规范化后的标题
     */
    public static String norm(String title)
    {
        return title == null ? "" : title.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
