package com.ygb.abroad.service.impl;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import com.ygb.abroad.integration.AbrFetchException;
import com.ygb.abroad.integration.AbrRobotsPolicy;
import com.ygb.abroad.integration.AbrWebPage;
import com.ygb.abroad.integration.AbrWebPageFetcher;
import com.ygb.abroad.service.IAbrSourceArchiveService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 学校英语等级对照（M2-08）：按 classpath:catalog/language_bands.json 抓取学校官方英语等级说明页（robots 允许才抓）并存档，
 * 解析出等级 → 雅思要求；按学校缓存 24 小时
 *
 * @author ygb
 */
@Component
public class AbrLanguageBandService
{
    private static final Logger log = LoggerFactory.getLogger(AbrLanguageBandService.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final long TTL_MS = 24L * 3600 * 1000;

    /**
     * 一所学校的对照
     *
     * @param url 说明页
     * @param snapshotId 说明页存档
     * @param marker 项目页上标注等级的正则（第 1 组为等级名）
     * @param bands 等级 → 要求
     * @param at 加载时间
     */
    record Loaded(String url, Long snapshotId, Pattern marker, Map<String, AbrLanguageBandRules.Band> bands, long at)
    {
    }

    private final Map<String, Loaded> cache = new ConcurrentHashMap<>();

    private volatile JsonNode config;

    @Autowired
    private AbrRobotsPolicy robots;

    @Autowired
    private AbrWebPageFetcher fetcher;

    @Autowired
    private IAbrSourceArchiveService archiveService;

    /**
     * 学校的等级对照；学校没有配置、说明页不可抓或解析不出时为 null
     *
     * @param schoolNameEn 学校英文名
     * @return 对照
     */
    Loaded get(String schoolNameEn)
    {
        Loaded c = cache.get(schoolNameEn);
        if (c != null && System.currentTimeMillis() - c.at() < TTL_MS)
        {
            return c.bands().isEmpty() ? null : c;
        }
        JsonNode def = null;
        for (JsonNode d : config().path("bands"))
        {
            if (schoolNameEn.equals(d.path("school").asString("")))
            {
                def = d;
            }
        }
        if (def == null)
        {
            return null;
        }
        String url = def.path("url").asString("");
        Loaded loaded = new Loaded(url, null, Pattern.compile(def.path("pageMarker").asString("(?!)")), Map.of(), System.currentTimeMillis());
        if (robots.allowed(url))
        {
            try
            {
                AbrWebPage page = fetcher.fetch(url, 200000);
                Long snap = archiveService.archive(url, page.getFinalUrl(), "web", 200, page.getContentType(), page.getTitle(), page.getText(),
                        AbrProgramCrawlWriter.OPERATOR);
                Map<String, AbrLanguageBandRules.Band> bands = AbrLanguageBandRules.parse(page.getText(), def.path("style").asString("inline"),
                        def.path("test").asString("IELTS"), def.path("label").asString(""));
                loaded = new Loaded(url, snap, loaded.marker(), bands, System.currentTimeMillis());
            }
            catch (AbrFetchException e)
            {
                log.info("英语等级说明页抓取失败 {}：{}", url, e.getMessage());
            }
        }
        cache.put(schoolNameEn, loaded);
        return loaded.bands().isEmpty() ? null : loaded;
    }

    private JsonNode config()
    {
        if (config == null)
        {
            try
            {
                config = MAPPER.readTree(new ClassPathResource("catalog/language_bands.json").getInputStream());
            }
            catch (IOException e)
            {
                config = MAPPER.createObjectNode();
            }
        }
        return config;
    }
}
