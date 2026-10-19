package com.ygb.abroad.service.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrContent;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.mapper.AbrRequirementMapper;
import com.ygb.abroad.service.IAbrContentDesignService;
import com.ygb.abroad.service.IAbrContentService;
import com.ygb.common.config.ZtConfig;
import com.ygb.common.constant.Constants;
import com.ygb.common.core.domain.entity.SysDictData;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.DictUtils;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 内容图卡 服务层实现
 *
 * 图片保存在 zt.profile 下的 content/cards（/profile/** 公开访问，文件名含内容编号与图卡数据摘要，内容一致时复用同一文件）。
 * 
 * @author ygb
 */
@Service
public class AbrContentDesignServiceImpl implements IAbrContentDesignService
{
    private static final Logger log = LoggerFactory.getLogger(AbrContentDesignServiceImpl.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 每条内容最多图卡数 */
    static final int MAX_CARDS = 9;

    /** 图卡标题最多字数 */
    private static final int MAX_TITLE = 40;

    /** 保存目录（zt.profile 下） */
    private static final String DIR = "content/cards";

    /** 钩子 → 二维码说明 */
    private static final Map<String, String> QR_CAPTIONS = Map.of(
            "quick", "扫码 30 秒测一下能申哪些",
            "ielts", "扫码测一下雅思够不够",
            "recognition", "扫码查本校认可度",
            "open", "扫码看现在还能申的项目",
            "plan", "扫码加入我的选校单");

    @Autowired
    private IAbrContentService contentService;

    @Autowired
    private AbrProgramMapper programMapper;

    @Autowired
    private AbrRequirementMapper requirementMapper;

    @Autowired
    private AbrShareSupport shareSupport;

    @Override
    public Map<String, Object> templates()
    {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("templates", AbrContentCards.TEMPLATES);
        Map<String, String> fields = new LinkedHashMap<>();
        AbrContentCards.FIELDS.forEach(f -> fields.put(f, AbrContentCards.fieldLabel(f)));
        map.put("fields", fields);
        map.put("maxCards", MAX_CARDS);
        return map;
    }

    @Override
    public byte[] preview(Long contentId, String template, String paramsJson, String title)
    {
        AbrContent c = contentId == null ? null : require(contentId);
        String t = title(title, c);
        AbrContentCards.Params p = AbrContentCards.params(template, parse(paramsJson, "{}"));
        AbrContentCards.Built built = build(template, p, t, c, catalog(List.of(p)));
        return shareSupport.png(built.card());
    }

    @Override
    public AbrContent renderCards(Long contentId, String specsJson, boolean fallback, String operator)
    {
        AbrContent c = require(contentId);
        JsonNode specs = StringUtils.isBlank(specsJson) ? parse(c.getCards(), "[]") : parse(specsJson, "[]");
        if (!specs.isArray() || specs.size() > MAX_CARDS)
        {
            throw new ServiceException("图卡清单须为数组，最多 " + MAX_CARDS + " 张");
        }
        List<String> templates = new ArrayList<>();
        List<AbrContentCards.Params> params = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        for (JsonNode spec : specs)
        {
            String template = spec.path("template").asString("");
            templates.add(template);
            titles.add(title(spec.path("title").asString(null), c));
            try
            {
                params.add(AbrContentCards.params(template, spec.path("params")));
            }
            catch (ServiceException e)
            {
                if (!fallback)
                {
                    throw new ServiceException("第 " + (templates.size()) + " 张图卡：" + e.getMessage());
                }
                params.add(null);
            }
        }
        Catalog catalog = catalog(params.stream().filter(p -> p != null).toList());
        ArrayNode cards = MAPPER.createArrayNode();
        List<Map<String, Object>> cardCitations = new ArrayList<>();
        for (int i = 0; i < templates.size(); i++)
        {
            String template = templates.get(i);
            AbrContentCards.Params p = params.get(i);
            AbrContentCards.Built built;
            String error = null;
            try
            {
                if (p == null)
                {
                    throw new ServiceException("模板参数不合法");
                }
                built = build(template, p, titles.get(i), c, catalog);
            }
            catch (ServiceException e)
            {
                if (!fallback)
                {
                    throw new ServiceException("第 " + (i + 1) + " 张图卡：" + e.getMessage());
                }
                // 默认模板：只用标题，不带任何数字
                error = e.getMessage();
                log.warn("内容 {} 第 {} 张图卡模板 {} 失败，改用封面：{}", c.getContentCode(), i + 1, template, error);
                template = AbrContentCards.COVER;
                p = new AbrContentCards.Params(null, List.of(), List.of());
                built = build(template, p, titles.get(i), c, catalog);
            }
            byte[] png = shareSupport.png(built.card());
            ObjectNode item = MAPPER.createObjectNode();
            item.put("template", template);
            ObjectNode pj = item.putObject("params");
            if (p.kicker() != null)
            {
                pj.put("kicker", p.kicker());
            }
            ArrayNode ids = pj.putArray("programIds");
            p.programIds().forEach(ids::add);
            if (!AbrContentCards.COVER.equals(template))
            {
                ArrayNode fs = pj.putArray("fields");
                p.fields().forEach(fs::add);
            }
            item.put("title", titles.get(i));
            item.put("imageUrl", save(c.getContentCode(), i + 1, png));
            item.put("text", built.text());
            item.set("citations", MAPPER.valueToTree(built.citations()));
            item.put("dataDate", built.dataDate() == null ? null : built.dataDate().toString());
            item.put("renderedTime", DateUtils.getTime());
            if (error != null)
            {
                item.put("fallback", true);
                item.put("error", StringUtils.substring(error, 0, 200));
            }
            cards.add(item);
            cardCitations.addAll(built.citations());
        }
        c.setCards(MAPPER.writeValueAsString(cards));
        c.setCitations(mergeCitations(c.getCitations(), cardCitations));
        c.setUpdateBy(operator);
        contentService.updateAbrContent(c);
        return contentService.selectAbrContentById(contentId);
    }

    @Override
    public List<Map<String, Object>> cards(Long contentId)
    {
        AbrContent c = require(contentId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode n : parse(c.getCards(), "[]"))
        {
            list.add(MAPPER.convertValue(n, Map.class));
        }
        return list;
    }

    /**
     * 引用依据合并：去掉上次图卡带来的，保留人工 / Agent 填写的，再加上本次图卡的（按 type + id 去重）
     */
    static String mergeCitations(String existing, List<Map<String, Object>> cardCitations)
    {
        ArrayNode out = MAPPER.createArrayNode();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode n : parse(existing, "[]"))
        {
            if ("card".equals(n.path("source").asString("")))
            {
                continue;
            }
            seen.add(n.path("type").asString("") + ":" + n.path("id").asString(n.path("url").asString("")));
            out.add(n);
        }
        for (Map<String, Object> m : cardCitations)
        {
            if (seen.add(m.get("type") + ":" + m.get("id")))
            {
                out.add(MAPPER.valueToTree(m));
            }
        }
        return MAPPER.writeValueAsString(out);
    }

    private AbrContentCards.Built build(String template, AbrContentCards.Params p, String title, AbrContent c, Catalog catalog)
    {
        String link = c == null ? null : StringUtils.trimToNull(c.getToolLink());
        String caption = c == null ? null : QR_CAPTIONS.getOrDefault(StringUtils.defaultString(c.getHookTool()), "扫码查看");
        return AbrContentCards.build(template, p, title, catalog.programs(), catalog.requirements(), countryLabels(), MarketingCalendar.today(),
                shareSupport.brandName(), link, caption);
    }

    private record Catalog(Map<Long, AbrProgram> programs, Map<Long, AbrRequirement> requirements)
    {
    }

    /**
     * 读取图卡用到的项目（启用的）与最新申请要求
     */
    private Catalog catalog(List<AbrContentCards.Params> params)
    {
        Set<Long> ids = new LinkedHashSet<>();
        params.forEach(p -> ids.addAll(p.programIds()));
        Map<Long, AbrProgram> programs = new HashMap<>();
        Map<Long, AbrRequirement> reqs = new HashMap<>();
        if (!ids.isEmpty())
        {
            List<Long> list = new ArrayList<>(ids);
            programMapper.selectAbrProgramByIds(list).stream().filter(p -> "0".equals(p.getStatus())).forEach(p -> programs.put(p.getProgramId(), p));
            requirementMapper.selectLatestAbrRequirementByProgramIds(list).forEach(r -> reqs.put(r.getProgramId(), r));
        }
        return new Catalog(programs, reqs);
    }

    private static Map<String, String> countryLabels()
    {
        Map<String, String> map = new HashMap<>();
        List<SysDictData> datas = DictUtils.getDictCache("abr_country");
        if (datas != null)
        {
            datas.forEach(d -> map.put(d.getDictValue(), d.getDictLabel()));
        }
        return map;
    }

    /**
     * 保存图片：文件名 = 内容编号-序号-数据摘要，内容相同时复用
     */
    private static String save(String contentCode, int index, byte[] png)
    {
        String name = contentCode + "-" + index + "-" + sha(png) + ".png";
        File dir = new File(ZtConfig.getProfile(), DIR);
        File file = new File(dir, name);
        try
        {
            if (!file.exists())
            {
                Files.createDirectories(dir.toPath());
                Files.write(file.toPath(), png);
            }
        }
        catch (IOException e)
        {
            throw new ServiceException("图卡保存失败：" + e.getMessage());
        }
        return Constants.RESOURCE_PREFIX + "/" + DIR + "/" + name;
    }

    private static String sha(byte[] data)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data), 0, 6);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static String title(String title, AbrContent c)
    {
        String t = StringUtils.trimToNull(title);
        if (t == null && c != null && c.getTitle() != null)
        {
            // 未单独填写时用内容标题，过长截断
            t = c.getTitle().length() > MAX_TITLE ? c.getTitle().substring(0, MAX_TITLE - 1) + "…" : c.getTitle();
        }
        if (StringUtils.isBlank(t))
        {
            throw new ServiceException("图卡标题不能为空");
        }
        if (t.length() > MAX_TITLE)
        {
            throw new ServiceException("图卡标题最多 " + MAX_TITLE + " 个字符");
        }
        if (AbrAssessmentAnswers.containsTag(MAPPER.getNodeFactory().stringNode(t)))
        {
            throw new ServiceException("图卡标题不能包含 < 加字母的写法");
        }
        return t;
    }

    private AbrContent require(Long contentId)
    {
        AbrContent c = contentService.selectAbrContentById(contentId);
        if (c == null)
        {
            throw new ServiceException("内容不存在");
        }
        return c;
    }

    private static JsonNode parse(String json, String empty)
    {
        try
        {
            return MAPPER.readTree(StringUtils.isBlank(json) ? empty : json);
        }
        catch (JacksonException e)
        {
            throw new ServiceException("图卡参数不是合法的 JSON");
        }
    }
}
