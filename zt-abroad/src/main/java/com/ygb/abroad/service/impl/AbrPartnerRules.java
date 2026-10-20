package com.ygb.abroad.service.impl;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;

/**
 * 合作方规则（纯计算）：可嵌入的工具链接配置、结算月份、对外导出时的学生称呼
 *
 * @author ygb
 */
final class AbrPartnerRules
{
    /** 发放、退款处理说明最长字数 */
    static final int NOTE_MAX = 90;

    /**
     * 可嵌入的工具
     *
     * @param code 编码
     * @param label 名称
     * @param path C 端页面（空为首页）
     */
    record Tool(String code, String label, String path)
    {
    }

    private AbrPartnerRules()
    {
    }

    /**
     * 解析工具配置：逗号分隔的“编码:名称:页面”，页面可为空（首页）；格式不对的项跳过
     *
     * @param config 配置
     * @return 工具
     */
    static List<Tool> tools(String config)
    {
        List<Tool> out = new ArrayList<>();
        if (StringUtils.isBlank(config))
        {
            return out;
        }
        for (String item : config.split(","))
        {
            String[] p = item.trim().split(":", -1);
            if (p.length == 3 && p[0].trim().matches("^[a-z0-9_]{1,32}$") && StringUtils.isNotBlank(p[1]))
            {
                out.add(new Tool(p[0].trim(), p[1].trim(), p[2].trim().replaceFirst("^/", "")));
            }
        }
        return out;
    }

    /**
     * 工具链接的路径与参数（不含 C 端地址）
     *
     * @param tool 工具
     * @param channel 渠道码
     * @param refCode 推荐码
     * @return 如 pages/quick/index?ch=partner&ct=abcd2345
     */
    static String pathAndQuery(Tool tool, String channel, String refCode)
    {
        String path = tool.path();
        return path + (path.contains("?") ? "&" : "?") + "ch=" + channel + "&ct=" + refCode;
    }

    /**
     * 结算月份：不填为本月
     *
     * @param month yyyy-MM
     * @param today 今天（北京时间）
     * @return yyyy-MM
     */
    static String month(String month, LocalDate today)
    {
        if (StringUtils.isBlank(month))
        {
            return YearMonth.from(today).toString();
        }
        try
        {
            return YearMonth.parse(month.trim()).toString();
        }
        catch (DateTimeParseException e)
        {
            throw new ServiceException("月份格式应为 yyyy-MM");
        }
    }

    /**
     * 对外（发给合作方的对账单）只保留学生称呼的第一个字
     *
     * @param name 称呼
     * @return 如“张*”
     */
    static String maskName(String name)
    {
        if (StringUtils.isBlank(name))
        {
            return "";
        }
        String n = name.trim();
        int first = n.offsetByCodePoints(0, 1);
        return n.substring(0, first) + "*";
    }

    /**
     * 说明文字：去空白、限长
     *
     * @param note 说明
     * @param required 是否必填
     * @param what 提示用的名称
     * @return 说明
     */
    static String note(String note, boolean required, String what)
    {
        String n = StringUtils.trimToEmpty(note);
        if (required && n.isEmpty())
        {
            throw new ServiceException("请填写" + what);
        }
        if (n.length() > NOTE_MAX)
        {
            throw new ServiceException(what + "最多 " + NOTE_MAX + " 字");
        }
        return n;
    }
}
