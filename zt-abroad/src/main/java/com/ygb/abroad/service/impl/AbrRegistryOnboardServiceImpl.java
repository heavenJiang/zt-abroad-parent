package com.ygb.abroad.service.impl;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrFieldSource;
import com.ygb.abroad.domain.AbrSourceSnapshot;
import com.ygb.abroad.integration.AbrFetchException;
import com.ygb.abroad.integration.AbrHtmlText;
import com.ygb.abroad.integration.AbrRobotsPolicy;
import com.ygb.abroad.integration.AbrWebPage;
import com.ygb.abroad.integration.AbrWebPageFetcher;
import com.ygb.abroad.service.IAbrRegistryOnboardService;
import com.ygb.abroad.service.IAbrSourceArchiveService;
import com.ygb.common.core.redis.RedisCache;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 第 1 批学校按官方名录建档 服务层实现（M2-13）
 * <ol>
 * <li>名单 classpath:catalog/schools_batch1.tsv（国家、名录、中英文名、城市、官网），名录定义 classpath:catalog/registries.json；</li>
 * <li>逐个名录：robots 允许时下载（失败或空页重试 3 次，仍失败复用 30 天内的存档），原文存档；</li>
 * <li>逐校：英文名须在对应名录原文中出现（原样截取作为引句）；OfS 名录还取 UKPRN、法定名称与官网；</li>
 * <li>写入走 {@link AbrSchoolOnboardWriter}（字段历史、来源、官网首页存档）。</li>
 * </ol>
 *
 * @author ygb
 */
@Service
public class AbrRegistryOnboardServiceImpl implements IAbrRegistryOnboardService
{
    private static final Logger log = LoggerFactory.getLogger(AbrRegistryOnboardServiceImpl.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    static final String SEED = "catalog/schools_batch1.tsv";

    static final String REGISTRIES = "catalog/registries.json";

    static final String STATUS_KEY = "abroad:onboard:batch1";

    /** 名录下载失败时可复用的存档天数 */
    static final int REUSE_DAYS = 30;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    private AbrSchoolOnboardWriter writer;

    @Autowired
    private IAbrSourceArchiveService archiveService;

    @Autowired
    private AbrRobotsPolicy robots;

    @Autowired
    private AbrWebPageFetcher fetcher;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private Executor executor;

    /** 名单一行 */
    record SeedRow(String country, String registry, String nameZh, String nameEn, String city, String website)
    {
    }

    /** 下载好的名录 */
    record Registry(String key, String title, String url, Long snapshotId, String content, List<AbrRegistryRules.OfsRow> ofsRows)
    {
    }

    @Override
    public boolean start(String operator)
    {
        if (!running.compareAndSet(false, true))
        {
            return false;
        }
        Map<String, Object> st = new LinkedHashMap<>();
        st.put("status", "running");
        st.put("operator", operator);
        st.put("startTime", System.currentTimeMillis());
        redisCache.setCacheObject(STATUS_KEY, MAPPER.writeValueAsString(st));
        executor.execute(() -> {
            try
            {
                Map<String, Object> result = run(operator);
                result.put("status", "done");
                redisCache.setCacheObject(STATUS_KEY, MAPPER.writeValueAsString(result));
            }
            catch (RuntimeException e)
            {
                log.warn("第 1 批院校建档失败：{}", e.getMessage(), e);
                st.put("status", "failed");
                st.put("message", StringUtils.substring(e.getMessage(), 0, 300));
                redisCache.setCacheObject(STATUS_KEY, MAPPER.writeValueAsString(st));
            }
            finally
            {
                running.set(false);
            }
        });
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> status()
    {
        Object v = redisCache.getCacheObject(STATUS_KEY);
        return v == null ? Map.of("status", "none") : MAPPER.readValue(String.valueOf(v), LinkedHashMap.class);
    }

    Map<String, Object> run(String operator)
    {
        List<String> issues = new ArrayList<>();
        Map<String, Registry> registries = new LinkedHashMap<>();
        JsonNode defs = readJson(REGISTRIES);
        for (Map.Entry<String, JsonNode> e : defs.properties())
        {
            if (e.getKey().startsWith("_"))
            {
                continue;
            }
            for (JsonNode d : e.getValue())
            {
                Registry r = load(d, issues);
                if (r != null)
                {
                    registries.put(r.key(), r);
                }
            }
        }
        List<SeedRow> rows = seed();
        Map<String, Integer> count = new LinkedHashMap<>();
        for (String k : List.of("created", "updated", "unchanged", "skipped", "homepagesArchived"))
        {
            count.put(k, 0);
        }
        for (SeedRow row : rows)
        {
            Registry reg = registries.get(row.registry());
            if (reg == null)
            {
                issues.add(row.nameEn() + "：官方名录 " + row.registry() + " 不可用，跳过");
                count.merge("skipped", 1, Integer::sum);
                continue;
            }
            String website = row.website();
            String quote;
            List<String> aliases = new ArrayList<>();
            String extKey = null;
            String extValue = null;
            if (reg.ofsRows() != null)
            {
                AbrRegistryRules.OfsRow hit = AbrRegistryRules.findOfs(reg.ofsRows(), row.nameEn());
                if (hit == null)
                {
                    issues.add(row.nameEn() + "：在 " + reg.title() + " 中找不到（或不唯一），跳过");
                    count.merge("skipped", 1, Integer::sum);
                    continue;
                }
                quote = hit.line();
                if (StringUtils.isNotBlank(hit.website()))
                {
                    website = AbrSchoolOnboardRules.website(hit.website(), row.website());
                    if (!AbrSchoolOnboardRules.sameHost(website, row.website()))
                    {
                        issues.add(row.nameEn() + "：官网按官方名录改为 " + website + "（名单中为 " + row.website() + "）");
                    }
                }
                if (!AbrRegistryRules.norm(hit.legalName()).equals(AbrRegistryRules.norm(row.nameEn())))
                {
                    aliases.add(hit.legalName());
                }
                hit.tradingNames().stream().filter(t -> !AbrRegistryRules.norm(t).equals(AbrRegistryRules.norm(row.nameEn())))
                        .limit(5).forEach(aliases::add);
                extKey = "ukprn";
                extValue = hit.ukprn();
            }
            else
            {
                quote = AbrRegistryRules.quoteIn(reg.content(), row.nameEn());
                if (quote == null)
                {
                    issues.add(row.nameEn() + "：在 " + reg.title() + " 中找不到，跳过");
                    count.merge("skipped", 1, Integer::sum);
                    continue;
                }
            }
            Map<String, AbrFieldSource> sources = new LinkedHashMap<>();
            for (String f : List.of("schoolNameEn", "website"))
            {
                sources.put(f, AbrSchoolOnboardWriter.source(f, reg.url(), quote, reg.snapshotId(), operator));
            }
            if (extKey != null)
            {
                sources.put("extIds", AbrSchoolOnboardWriter.source("extIds", reg.url(), quote, reg.snapshotId(), operator));
                sources.put("aliases", AbrSchoolOnboardWriter.source("aliases", reg.url(), quote, reg.snapshotId(), operator));
            }
            String result = writer.write(new AbrSchoolOnboardWriter.Candidate(row.country(), row.nameZh(), row.nameEn(), row.city(), website,
                    aliases, extKey, extValue, sources), issues, operator);
            if (result.endsWith("+home"))
            {
                count.merge("homepagesArchived", 1, Integer::sum);
            }
            count.merge(result.replace("+home", ""), 1, Integer::sum);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", rows.size());
        out.putAll(count);
        List<Map<String, Object>> regs = new ArrayList<>();
        for (Registry r : registries.values())
        {
            regs.add(Map.of("key", r.key(), "title", r.title(), "snapshotId", r.snapshotId()));
        }
        out.put("registries", regs);
        out.put("issues", issues);
        out.put("finishTime", System.currentTimeMillis());
        log.info("第 1 批院校建档：{}，问题 {}", count, issues.size());
        return out;
    }

    /**
     * 下载并存档一个名录；robots 不允许、下载失败且没有可复用的存档时返回 null（原因写进 issues）
     */
    private Registry load(JsonNode d, List<String> issues)
    {
        String key = d.path("key").asString("");
        String title = d.path("title").asString(key);
        String url = d.path("url").asString("");
        String format = d.path("format").asString("html");
        if (!robots.allowed(url))
        {
            issues.add(title + "：robots.txt 不允许抓取，未使用");
            return null;
        }
        String content = null;
        String contentType = null;
        String pageTitle = title;
        String finalUrl = url;
        String kind = "web";
        List<AbrRegistryRules.OfsRow> ofs = null;
        for (int attempt = 1; attempt <= 3 && content == null; attempt++)
        {
            try
            {
                if ("html".equals(format))
                {
                    AbrWebPage page = fetcher.fetch(url);
                    if (StringUtils.isNotBlank(page.getText()) && page.getText().length() > 200)
                    {
                        content = page.getText();
                        contentType = page.getContentType();
                        pageTitle = StringUtils.defaultIfBlank(page.getTitle(), title);
                        finalUrl = page.getFinalUrl();
                    }
                }
                else if ("gov_uk".equals(format))
                {
                    content = getText(url);
                    contentType = "application/json";
                    kind = "api";
                }
                else if ("ofs_xlsx".equals(format))
                {
                    ofs = ofsRows(getBytes(url));
                    StringBuilder sb = new StringBuilder("法定名称\t对外名称\tUKPRN\t官网\n");
                    ofs.forEach(r -> sb.append(r.line()).append('\n'));
                    content = sb.toString();
                    contentType = "text/tab-separated-values";
                    kind = "file";
                }
            }
            catch (AbrFetchException | IllegalStateException e)
            {
                log.info("名录下载失败 {} 第 {} 次：{}", key, attempt, e.getMessage());
            }
            if (content == null && attempt < 3)
            {
                sleep(15000);
            }
        }
        Long snapshotId;
        if (content != null)
        {
            snapshotId = archiveService.archive(url, finalUrl, kind, 200, contentType, pageTitle, content, AbrSchoolOnboardWriter.AGENT);
        }
        else
        {
            AbrSourceSnapshot recent = archiveService.selectRecent(url, REUSE_DAYS);
            if (recent == null)
            {
                issues.add(title + "：下载失败且没有 " + REUSE_DAYS + " 天内的存档，未使用");
                return null;
            }
            issues.add(title + "：下载失败，使用 " + recent.getLastSeenTime() + " 的存档");
            content = recent.getContent();
            snapshotId = recent.getSnapshotId();
            if ("ofs_xlsx".equals(format))
            {
                ofs = ofsRowsFromText(content);
            }
        }
        if ("gov_uk".equals(format))
        {
            // GOV.UK 内容接口：只在指定章节中找校名（存档为完整接口返回）
            String part = d.path("part").asString("");
            StringBuilder sb = new StringBuilder();
            for (JsonNode p : MAPPER.readTree(content).path("details").path("parts"))
            {
                if (part.isEmpty() || part.equals(p.path("slug").asString("")))
                {
                    sb.append(p.path("body").asString("")).append('\n');
                }
            }
            return new Registry(key, title, url, snapshotId, AbrHtmlText.toText(sb.toString()), null);
        }
        return new Registry(key, title, url, snapshotId, content, ofs);
    }

    /**
     * 解析 OfS 名录表格（第一张表 Register：法定名称、所含学院、对外名称、UKPRN、地址、邮箱、官网…；表头在前几行）
     */
    static List<AbrRegistryRules.OfsRow> ofsRows(byte[] xlsx)
    {
        List<AbrRegistryRules.OfsRow> out = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx)))
        {
            Sheet sh = wb.getSheet("Register") != null ? wb.getSheet("Register") : wb.getSheetAt(0);
            int legal = -1, trading = -1, ukprn = -1, site = -1;
            for (Row row : sh)
            {
                if (legal < 0)
                {
                    for (Cell c : row)
                    {
                        String h = fmt.formatCellValue(c).toLowerCase();
                        if (h.contains("legal name")) legal = c.getColumnIndex();
                        else if (h.contains("trading name")) trading = c.getColumnIndex();
                        else if (h.contains("ukprn")) ukprn = c.getColumnIndex();
                        else if (h.contains("website")) site = c.getColumnIndex();
                    }
                    continue;
                }
                String name = cell(fmt, row, legal);
                String prn = cell(fmt, row, ukprn);
                if (name.isEmpty() || !prn.matches("^\\d{8}$"))
                {
                    continue;
                }
                List<String> tn = AbrRegistryRules.splitNames(cell(fmt, row, trading));
                String web = cell(fmt, row, site);
                out.add(new AbrRegistryRules.OfsRow(name, tn, prn, web, name + "\t" + String.join(" / ", tn) + "\t" + prn + "\t" + web));
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("OfS 名录表格无法读取：" + e.getMessage());
        }
        if (out.isEmpty())
        {
            throw new IllegalStateException("OfS 名录表格中没有找到学校");
        }
        return out;
    }

    /**
     * 从存档的名录文本还原（下载失败复用存档时）
     */
    static List<AbrRegistryRules.OfsRow> ofsRowsFromText(String text)
    {
        List<AbrRegistryRules.OfsRow> out = new ArrayList<>();
        for (String line : StringUtils.defaultString(text).split("\n"))
        {
            String[] c = line.split("\t", -1);
            if (c.length == 4 && c[2].matches("^\\d{8}$"))
            {
                out.add(new AbrRegistryRules.OfsRow(c[0], c[1].isEmpty() ? List.of() : List.of(c[1].split(" / ")), c[2], c[3], line));
            }
        }
        return out;
    }

    private static String cell(DataFormatter fmt, Row row, int idx)
    {
        if (idx < 0 || row.getCell(idx) == null)
        {
            return "";
        }
        return fmt.formatCellValue(row.getCell(idx)).replace(' ', ' ').trim().replaceAll("[\\t]+", " ");
    }

    private byte[] getBytes(String url)
    {
        HttpResponse<byte[]> resp = send(url, HttpResponse.BodyHandlers.ofByteArray());
        return resp.body();
    }

    private String getText(String url)
    {
        return send(url, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    }

    private <T> HttpResponse<T> send(String url, HttpResponse.BodyHandler<T> handler)
    {
        try
        {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "Mozilla/5.0 (compatible; " + AbrRobotsPolicy.AGENT + "/1.0)").GET().build();
            HttpResponse<T> resp = client.send(req, handler);
            if (resp.statusCode() != 200)
            {
                throw new IllegalStateException("返回 " + resp.statusCode());
            }
            return resp;
        }
        catch (IOException e)
        {
            throw new IllegalStateException("打不开：" + e.getMessage());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("已中断");
        }
    }

    private static void sleep(long ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    private static JsonNode readJson(String path)
    {
        try
        {
            return MAPPER.readTree(new ClassPathResource(path).getInputStream());
        }
        catch (IOException e)
        {
            throw new IllegalStateException("读取 " + path + " 失败：" + e.getMessage());
        }
    }

    static List<SeedRow> seed()
    {
        List<SeedRow> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new ClassPathResource(SEED).getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = r.readLine()) != null)
            {
                if (line.isBlank() || line.startsWith("#"))
                {
                    continue;
                }
                String[] c = line.split("\t");
                if (c.length == 6)
                {
                    rows.add(new SeedRow(c[0].trim(), c[1].trim(), c[2].trim(), c[3].trim(), c[4].trim(), c[5].trim()));
                }
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("读取名单失败：" + e.getMessage());
        }
        return rows;
    }
}
