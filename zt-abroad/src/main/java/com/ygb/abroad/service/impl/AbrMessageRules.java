package com.ygb.abroad.service.impl;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ygb.abroad.domain.AbrMessageTemplate;
import com.ygb.abroad.domain.AbrTouchLog;
import com.ygb.abroad.integration.channel.ChannelAdapter;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 消息模板规则（纯计算）：变量渲染、模板校验、学生消息的渠道优先顺序
 *
 * @author ygb
 */
final class AbrMessageRules
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 学生消息按此顺序选第一个可达的渠道（服务号 → 小程序订阅消息 → 短信） */
    static final List<String> STUDENT_ORDER = List.of(ChannelAdapter.SERVICE_ACCOUNT, ChannelAdapter.MP_SUBSCRIBE, ChannelAdapter.SMS);

    static final Set<String> CHANNELS = Set.of(ChannelAdapter.SERVICE_ACCOUNT, ChannelAdapter.MP_SUBSCRIBE, ChannelAdapter.WECOM, ChannelAdapter.SMS);

    static final Set<String> CATEGORIES = Set.of(AbrTouchLog.CATEGORY_MARKETING, AbrTouchLog.CATEGORY_SERVICE);

    /** 业务编码 */
    static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    /** 变量 {name} */
    static final Pattern VAR = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]{0,40})\\}");

    private AbrMessageRules()
    {
    }

    /**
     * 渲染文字中的 {变量}
     *
     * @param text 文字
     * @param vars 变量
     * @return 渲染结果
     * @throws ServiceException 缺少变量
     */
    static String render(String text, Map<String, ?> vars)
    {
        if (StringUtils.isEmpty(text))
        {
            return "";
        }
        Matcher m = VAR.matcher(text);
        StringBuilder sb = new StringBuilder();
        Set<String> missing = new LinkedHashSet<>();
        while (m.find())
        {
            Object v = vars == null ? null : vars.get(m.group(1));
            if (v == null || StringUtils.isBlank(String.valueOf(v)))
            {
                missing.add(m.group(1));
                m.appendReplacement(sb, "");
            }
            else
            {
                m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(v).trim()));
            }
        }
        m.appendTail(sb);
        if (!missing.isEmpty())
        {
            throw new ServiceException("缺少变量：" + String.join("、", missing));
        }
        return sb.toString();
    }

    /**
     * 文字中用到的变量
     *
     * @param text 文字
     * @return 变量名
     */
    static Set<String> variables(String text)
    {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = VAR.matcher(StringUtils.defaultString(text));
        while (m.find())
        {
            out.add(m.group(1));
        }
        return out;
    }

    /**
     * 解析字段映射（平台字段 → 文字）
     *
     * @param json JSON 对象文本
     * @return 映射（按原顺序）
     * @throws ServiceException 不是字符串值的 JSON 对象
     */
    static Map<String, String> fieldMap(String json)
    {
        JsonNode n;
        try
        {
            n = MAPPER.readTree(StringUtils.isBlank(json) ? "{}" : json);
        }
        catch (JacksonException e)
        {
            throw new ServiceException("字段映射须为 JSON 对象");
        }
        if (!n.isObject())
        {
            throw new ServiceException("字段映射须为 JSON 对象");
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : n.properties())
        {
            if (!e.getValue().isString() || !e.getKey().matches("^[A-Za-z][A-Za-z0-9_]{0,40}$"))
            {
                throw new ServiceException("字段映射的键须为平台字段名、值须为文字：" + e.getKey());
            }
            out.put(e.getKey(), e.getValue().asString());
        }
        return out;
    }

    /**
     * 渲染全部字段
     *
     * @param fields 字段映射
     * @param vars 变量
     * @return 渲染结果
     */
    static Map<String, String> renderFields(Map<String, String> fields, Map<String, ?> vars)
    {
        Map<String, String> out = new LinkedHashMap<>();
        fields.forEach((k, v) -> out.put(k, render(v, vars)));
        return out;
    }

    /**
     * 校验并规范模板；启用的模板须能发出：企业微信须有文字，其余渠道须有平台模板 ID 与字段映射
     *
     * @param t 模板
     */
    static void validate(AbrMessageTemplate t)
    {
        if (StringUtils.isBlank(t.getTemplateCode()) || !CODE.matcher(t.getTemplateCode().trim()).matches())
        {
            throw new ServiceException("业务编码为小写字母开头的字母、数字、下划线（2–64 位）");
        }
        t.setTemplateCode(t.getTemplateCode().trim());
        if (!CHANNELS.contains(t.getChannel()))
        {
            throw new ServiceException("渠道只能是 service_account / mp_subscribe / wecom / sms");
        }
        if (!CATEGORIES.contains(t.getCategory()))
        {
            throw new ServiceException("类别只能是 marketing / service");
        }
        if (StringUtils.isBlank(t.getTemplateName()))
        {
            throw new ServiceException("请填写名称");
        }
        Map<String, String> fields = fieldMap(t.getFieldMap());
        t.setFieldMap(toJson(fields));
        t.setStatus("0".equals(t.getStatus()) ? "0" : "1");
        t.setPlatformTemplateId(StringUtils.trimToEmpty(t.getPlatformTemplateId()));
        if (StringUtils.length(t.getContent()) > 1000 || StringUtils.length(t.getLink()) > 500)
        {
            throw new ServiceException("消息文字最多 1000 字、跳转最多 500 字");
        }
        if (!"0".equals(t.getStatus()))
        {
            return;
        }
        if (ChannelAdapter.WECOM.equals(t.getChannel()))
        {
            if (StringUtils.isBlank(t.getContent()))
            {
                throw new ServiceException("企业微信模板须填写消息文字");
            }
        }
        else if (t.getPlatformTemplateId().isEmpty() || fields.isEmpty())
        {
            throw new ServiceException("启用前须填写平台模板 ID 与字段映射（在公众平台选用模板后对照字段名填写）");
        }
        if (AbrTouchLog.CATEGORY_MARKETING.equals(t.getCategory()) && StringUtils.isBlank(t.getContent()))
        {
            throw new ServiceException("营销类模板须填写消息文字（发出前用它过合规闸门）");
        }
    }

    /**
     * 字段映射转 JSON 文本
     *
     * @param fields 映射
     * @return JSON
     */
    static String toJson(Map<String, String> fields)
    {
        return MAPPER.writeValueAsString(fields);
    }
}
