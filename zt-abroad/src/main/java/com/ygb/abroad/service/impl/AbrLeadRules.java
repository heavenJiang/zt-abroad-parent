package com.ygb.abroad.service.impl;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import com.ygb.abroad.domain.AbrLead;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;

/**
 * 线索规则（纯计算，不访问数据库）：手机号规范化、去重时保留哪一条、业务事件如何记入时间线
 *
 * @author ygb
 */
final class AbrLeadRules
{
    /** 阶段：流失 */
    static final String STAGE_LOST = "lost";

    /** 下次联系时间最多约到多久之后 */
    static final long MAX_FOLLOW_AHEAD_MS = 366L * 24 * 3600 * 1000;

    /** 来源：C 端（自动收口） */
    static final String SOURCE_H5 = "h5";

    /** 员工可录入、表格可导入的来源（C 端由系统自动收口；企业微信由 M5-07 接口接入） */
    static final Set<String> STAFF_SOURCES = Set.of("lecture", "partner", "referral", "dm", "manual");

    /** 接口可用的全部来源 */
    static final Set<String> ALL_SOURCES = Set.of("h5", "wecom", "lecture", "partner", "referral", "dm", "manual");

    /** 中国大陆手机号 */
    static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    /** 业务事件 → 触达记录类型与摘要（未列出的事件只更新最近触达时间，时间线里按业务事件展示） */
    static final Map<String, String[]> EVENT_TOUCHES = Map.of(
            "app_user_registered", new String[] { "registered", "C 端注册（服务号登录）" },
            "app_user_phone_bound", new String[] { "phone_bound", "绑定手机号（已单独同意）" },
            "plan_moment", new String[] { "plan_moment", "转化时刻" },
            "plan_share_created", new String[] { "plan_share", "开启了家长只读分享" });

    /** 转化时刻编码 → 说明 */
    static final Map<String, String> MOMENTS = Map.of(
            "imbalance", "冲保失衡",
            "requirement_unmet", "要求变化后不达标",
            "materials_behind", "截止前材料不齐",
            "parent_view", "家长查看了选校单",
            "repeated_edits", "反复修改选校单");

    /** 时间线中业务事件的说明 */
    static final Map<String, String> EVENT_LABELS = Map.ofEntries(
            Map.entry("app_user_login", "登录"),
            Map.entry("plan_item_added", "加入选校单"),
            Map.entry("plan_item_removed", "移出选校单"),
            Map.entry("plan_material_updated", "更新材料清单"),
            Map.entry("plan_share_revoked", "撤销家长分享"),
            Map.entry("assessment_resume_uploaded", "上传简历 / 成绩单"),
            Map.entry("assessment_resume_deleted", "删除简历 / 成绩单"),
            Map.entry("content_published", "内容发布"));

    /** 触达记录类型的说明（摘要为空时显示） */
    static final Map<String, String> TOUCH_LABELS = Map.of("registered", "C 端注册", "phone_bound", "绑定手机号", "plan_moment", "转化时刻",
            "plan_share", "分享选校单", "manual", "员工录入", "import", "表格导入", "api", "接口接入", "merged", "线索合并");

    /** 时间线中预约、订单状态的后缀 */
    static final Map<String, String> BOOKING_STATUS = Map.of("cancelled", "（已取消）", "done", "（已完成）", "no_show", "（未到）");

    static final Map<String, String> ORDER_STATUS = Map.of("pending", "（待确认收款）", "paid", "（已确认收款）", "cancelled", "（已取消）", "refunded", "（已退款）");

    /** 时间线中工具的说明 */
    static final Map<String, String> TOOL_LABELS = Map.of("quick", "使用 30 秒快测", "recognition", "查询本校认可度", "ielts", "查询雅思够不够",
            "open", "查询现在还能申");

    private AbrLeadRules()
    {
    }

    /**
     * 手机号规范化：去掉空格、短横线、括号与 +86 / 86 前缀；为空返回 null；格式不对抛出业务异常
     *
     * @param raw 原值
     * @return 11 位手机号
     */
    static String normalizePhone(String raw)
    {
        if (StringUtils.isBlank(raw))
        {
            return null;
        }
        String p = raw.replaceAll("[\\s\\-()（）]", "");
        if (p.startsWith("+86"))
        {
            p = p.substring(3);
        }
        else if (p.startsWith("86") && p.length() == 13)
        {
            p = p.substring(2);
        }
        if (!PHONE.matcher(p).matches())
        {
            throw new ServiceException("手机号格式不对：" + AbrAppAuthServiceImpl.maskPhone(p.length() >= 7 ? p : "") + "（须为 11 位中国大陆手机号）");
        }
        return p;
    }

    /**
     * 去重：几条线索指向同一人时保留首次触达最早的一条（同时按ID），其余合并进它
     *
     * @param leads 候选（都未合并）
     * @return 保留的一条
     */
    static AbrLead master(List<AbrLead> leads)
    {
        return leads.stream().min(Comparator.comparing(AbrLead::getFirstTouchTime).thenComparing(AbrLead::getLeadId)).orElse(null);
    }

    /**
     * 下次联系时间：可为空；不能早于现在（留 5 分钟误差），不能晚于一年后
     *
     * @param next 下次联系时间
     * @param now 现在
     */
    static void checkNextFollow(Date next, Date now)
    {
        if (next == null)
        {
            return;
        }
        if (next.getTime() < now.getTime() - 5 * 60 * 1000L)
        {
            throw new ServiceException("下次联系时间不能早于现在");
        }
        if (next.getTime() > now.getTime() + MAX_FOLLOW_AHEAD_MS)
        {
            throw new ServiceException("下次联系时间不能晚于一年后");
        }
    }

    /**
     * 时间线条目的说明
     *
     * @param kind 类别（touch / event / tool / assessment）
     * @param type 类型
     * @param summary 原摘要
     * @return 说明
     */
    static String label(String kind, String type, String summary)
    {
        return switch (kind)
        {
            case "event" -> EVENT_LABELS.getOrDefault(type, type);
            case "tool" -> TOOL_LABELS.getOrDefault(type, "使用工具 " + type);
            case "assessment" -> "submitted".equals(type) ? "提交完整测评" : "开始完整测评";
            case "booking" -> "预约：" + StringUtils.defaultString(summary) + BOOKING_STATUS.getOrDefault(type, "");
            case "order" -> "订单：" + StringUtils.defaultString(summary) + ORDER_STATUS.getOrDefault(type, "");
            case "follow" -> switch (type)
            {
                case "stage" -> "阶段变更" + (StringUtils.isBlank(summary) ? "" : "：" + summary);
                case "assign" -> "转给同事" + (StringUtils.isBlank(summary) ? "" : "：" + summary);
                default -> "跟进：" + StringUtils.defaultString(summary);
            };
            default -> StringUtils.isNotBlank(summary) ? summary : TOUCH_LABELS.getOrDefault(type, type);
        };
    }
}
