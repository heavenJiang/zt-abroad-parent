package com.ygb.abroad.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import com.ygb.abroad.domain.AbrSchool;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 院校建档规则（纯计算）：名称核对、官网规范化、别名、外部编号
 *
 * @author ygb
 */
final class AbrSchoolOnboardRules
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 校名比较时忽略的词 */
    private static final Set<String> STOP = Set.of("the", "of", "at", "in", "and", "main", "campus", "city", "new", "york");

    /** 教育部认可状态待核对（入库为空） */
    static final String MOE_PENDING = "";

    private AbrSchoolOnboardRules()
    {
    }

    /**
     * 名称核对：去掉标点与常见虚词后，名单名称的词按顺序连续出现在官方名称中（官方名称常带校区后缀，如 -Main Campus）
     */
    static boolean sameSchool(String seedName, String officialName)
    {
        List<String> official = words(officialName);
        List<String> seed = words(seedName);
        return !seed.isEmpty() && Collections.indexOfSubList(official, seed) >= 0;
    }

    /**
     * 名称核对（含官方别名）：与官方名称或任一官方别名（逗号或竖线分隔）相符
     */
    static boolean sameSchool(String seedName, String officialName, String officialAliases)
    {
        if (sameSchool(seedName, officialName))
        {
            return true;
        }
        for (String a : StringUtils.defaultString(officialAliases).split("[,|]"))
        {
            if (StringUtils.isNotBlank(a) && words(a).equals(words(seedName)))
            {
                return true;
            }
        }
        return false;
    }

    private static List<String> words(String s)
    {
        List<String> out = new ArrayList<>();
        String t = StringUtils.defaultString(s).toLowerCase(Locale.ROOT).replace("&", " and ").replaceAll("[^a-z0-9]+", " ");
        for (String w : t.trim().split("\\s+"))
        {
            if (!w.isEmpty() && !STOP.contains(w))
            {
                out.add(w);
            }
        }
        return out;
    }

    /**
     * 官网规范化：以官方记录为准（补 https:// 与结尾斜杠，去掉路径以外的空白）；官方记录为空时用名单中的
     */
    static String website(String official, String fallback)
    {
        String u = StringUtils.isBlank(official) ? StringUtils.defaultString(fallback) : official.trim();
        if (u.isEmpty())
        {
            return "";
        }
        if (!u.toLowerCase(Locale.ROOT).matches("^https?://.*"))
        {
            u = "https://" + u;
        }
        u = u.replaceFirst("^http://", "https://");
        String rest = u.substring("https://".length());
        if (!rest.contains("/"))
        {
            u = u + "/";
        }
        return u;
    }

    /**
     * 两个网址是否同一站点（忽略 www. 等常见前缀与大小写）
     */
    static boolean sameHost(String a, String b)
    {
        String ha = host(a);
        String hb = host(b);
        return !ha.isEmpty() && (ha.equals(hb) || ha.endsWith("." + hb) || hb.endsWith("." + ha));
    }

    static String host(String url)
    {
        String u = StringUtils.defaultString(url).toLowerCase(Locale.ROOT).replaceFirst("^https?://", "");
        int slash = u.indexOf('/');
        u = slash >= 0 ? u.substring(0, slash) : u;
        return u.replaceFirst("^(www\\d?|home|welcome)\\.", "");
    }

    /**
     * 别名：官方名称（与名单名称不同时）+ 官方记录中的别名（逗号或竖线分隔），去重，最多 20 个
     */
    static List<String> aliases(String seedName, String officialName, String officialAliases)
    {
        Set<String> out = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(officialName) && !officialName.equalsIgnoreCase(seedName))
        {
            out.add(officialName.trim());
        }
        for (String a : StringUtils.defaultString(officialAliases).split("[,|]"))
        {
            String t = a.trim();
            if (t.length() >= 2 && t.length() <= 100 && !t.equalsIgnoreCase(seedName))
            {
                out.add(t);
            }
        }
        return new ArrayList<>(out).subList(0, Math.min(out.size(), 20));
    }

    /**
     * 官方接口原文中这所学校的记录（原样截取，便于在存档中高亮）；找不到时为 null
     *
     * @param body 接口返回原文
     * @param id IPEDS 编号
     * @return 记录原文
     */
    static String rawRecord(String body, String id)
    {
        String end = "\"id\":" + id + "}";
        int idx = StringUtils.defaultString(body).indexOf(end);
        if (idx < 0)
        {
            return null;
        }
        int start = body.lastIndexOf('{', idx);
        return start < 0 ? null : body.substring(start, idx + end.length());
    }

    /**
     * 在原有外部编号上设置一项
     */
    static String extIds(String existing, String key, String value)
    {
        ObjectNode n;
        try
        {
            n = StringUtils.isBlank(existing) ? MAPPER.createObjectNode() : (ObjectNode) MAPPER.readTree(existing);
        }
        catch (RuntimeException e)
        {
            n = MAPPER.createObjectNode();
        }
        n.put(key, value);
        return MAPPER.writeValueAsString(n);
    }

    /**
     * 建档涉及的字段是否有变化
     */
    static boolean changed(AbrSchool before, AbrSchool after)
    {
        return !Objects.equals(before.getSchoolName(), after.getSchoolName())
                || !Objects.equals(before.getCity(), after.getCity())
                || !Objects.equals(before.getWebsite(), after.getWebsite())
                || !AbrSourceArchiveServiceImpl.same(AbrSourceArchiveServiceImpl.text(before.getAliases()), AbrSourceArchiveServiceImpl.text(after.getAliases()))
                || !AbrSourceArchiveServiceImpl.same(AbrSourceArchiveServiceImpl.text(before.getExtIds()), AbrSourceArchiveServiceImpl.text(after.getExtIds()));
    }
}
