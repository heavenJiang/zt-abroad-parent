package com.ygb.abroad.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrFieldSource;
import com.ygb.abroad.domain.AbrSchool;
import com.ygb.abroad.domain.AbrSourceSnapshot;
import com.ygb.abroad.integration.AbrFetchException;
import com.ygb.abroad.integration.AbrRobotsPolicy;
import com.ygb.abroad.integration.AbrWebPage;
import com.ygb.abroad.integration.AbrWebPageFetcher;
import com.ygb.abroad.mapper.AbrFieldSourceMapper;
import com.ygb.abroad.mapper.AbrSchoolMapper;
import com.ygb.abroad.service.IAbrSchoolOnboardService;
import com.ygb.abroad.service.IAbrSchoolService;
import com.ygb.abroad.service.IAbrSourceArchiveService;
import com.ygb.common.core.redis.RedisCache;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 院校自动建档 服务层实现
 * <ol>
 * <li>名单取 classpath:catalog/us_national_universities.tsv（中英文名、城市、州、官网、IPEDS 编号）；</li>
 * <li>按 IPEDS 编号批量调用 College Scorecard 官方接口，接口返回整页存档（网址不含密钥）；</li>
 * <li>逐校核对州与名称，官网以官方记录为准；新建或更新学校档案（记字段历史，生效方式“导入”），字段来源指向存档；</li>
 * <li>robots 允许时抓取官网首页存档（每校间隔 1 秒），作为官网字段的依据；不允许或抓取失败时官网依据仍为官方记录。</li>
 * </ol>
 * 名次不在这里写入，由榜单导入（M2-10）从存档的官方榜单填入。教育部认可状态留空（待核对），不做推断。
 *
 * @author ygb
 */
@Service
public class AbrSchoolOnboardServiceImpl implements IAbrSchoolOnboardService
{
    private static final Logger log = LoggerFactory.getLogger(AbrSchoolOnboardServiceImpl.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    static final String SEED = "catalog/us_national_universities.tsv";

    static final String STATUS_KEY = "abroad:onboard:us_national";

    static final String AGENT = "school_onboard";

    /** 每次调用接口的编号数 */
    static final int BATCH = 50;

    private static final String FIELDS = "id,school.name,school.alias,school.city,school.state,school.school_url";

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    private IAbrSchoolService schoolService;

    @Autowired
    private AbrSchoolMapper schoolMapper;

    @Autowired
    private AbrFieldSourceMapper sourceMapper;

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

    /** College Scorecard 接口地址 */
    @Value("${abroad.catalog.scorecard-url:https://api.data.gov/ed/collegescorecard/v1}")
    private String scorecardUrl;

    /** api.data.gov 密钥（免费申请；未配置时用 DEMO_KEY，每小时 10 次） */
    @Value("${abroad.catalog.scorecard-api-key:DEMO_KEY}")
    private String apiKey;

    /** 官方记录在多少天内已存档就不再请求接口 */
    @Value("${abroad.catalog.scorecard-refresh-days:7}")
    private int refreshDays;

    /** 抓取官网首页的间隔（毫秒） */
    @Value("${abroad.catalog.homepage-interval-ms:1000}")
    private long homepageIntervalMs;

    /** 名单一行 */
    record SeedRow(String nameZh, String nameEn, String city, String state, String website, String ipeds)
    {
    }

    @Override
    public boolean startUsNational(String operator)
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
                log.warn("美国院校建档失败：{}", e.getMessage(), e);
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
        List<SeedRow> rows = seed();
        Map<String, JsonNode> records = new LinkedHashMap<>();
        Map<String, Long> snapshotOf = new LinkedHashMap<>();
        Map<String, String> pageUrlOf = new LinkedHashMap<>();
        Map<String, String> rawOf = new LinkedHashMap<>();
        List<String> ids = rows.stream().map(SeedRow::ipeds).toList();
        // 每批 50 个编号，保证存档网址不超过 1000 字符（编号为纯数字，逗号不需编码）
        for (int from = 0; from < ids.size(); from += BATCH)
        {
            List<String> batch = ids.subList(from, Math.min(ids.size(), from + BATCH));
            String query = "?id=" + String.join(",", batch) + "&fields=" + FIELDS + "&per_page=100";
            String publicUrl = scorecardUrl + "/schools" + query;
            // 官方记录变化很少：最近若干天内已存档的直接复用，不重复请求接口
            AbrSourceSnapshot recent = archiveService.selectRecent(publicUrl, refreshDays);
            String body;
            Long snapshotId;
            if (recent != null)
            {
                body = recent.getContent();
                snapshotId = recent.getSnapshotId();
            }
            else
            {
                body = get(publicUrl + "&api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
                snapshotId = archiveService.archive(publicUrl, publicUrl, "api", 200, "application/json",
                        "College Scorecard（美国教育部）院校记录 " + (from + 1) + "–" + (from + batch.size()), body, AGENT);
            }
            for (JsonNode r : MAPPER.readTree(body).path("results"))
            {
                String id = r.path("id").asString("");
                records.put(id, r);
                rawOf.put(id, AbrSchoolOnboardRules.rawRecord(body, id));
                snapshotOf.put(id, snapshotId);
                pageUrlOf.put(id, publicUrl);
            }
        }
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int homepages = 0;
        List<String> issues = new ArrayList<>();
        for (SeedRow row : rows)
        {
            JsonNode rec = records.get(row.ipeds());
            if (rec == null)
            {
                issues.add(row.nameEn() + "：官方记录中没有 IPEDS " + row.ipeds());
                continue;
            }
            String official = rec.path("school.name").asString("");
            if (!row.state().equals(rec.path("school.state").asString("")) || !AbrSchoolOnboardRules.sameSchool(row.nameEn(), official, rec.path("school.alias").asString("")))
            {
                issues.add(row.nameEn() + "：与官方记录（" + official + "，" + rec.path("school.state").asString("") + "）不一致，跳过");
                continue;
            }
            String website = AbrSchoolOnboardRules.website(rec.path("school.school_url").asString(""), row.website());
            if (!AbrSchoolOnboardRules.sameHost(website, row.website()))
            {
                issues.add(row.nameEn() + "：官网按官方记录改为 " + website + "（名单中为 " + row.website() + "）");
            }
            String quote = StringUtils.substring(StringUtils.defaultIfBlank(rawOf.get(row.ipeds()), rec.toString()), 0, 2000);
            Map<String, AbrFieldSource> sources = new LinkedHashMap<>();
            for (String f : List.of("schoolNameEn", "city", "extIds", "aliases"))
            {
                sources.put(f, source(f, pageUrlOf.get(row.ipeds()), quote, snapshotOf.get(row.ipeds()), operator));
            }
            sources.put("website", source("website", pageUrlOf.get(row.ipeds()), quote, snapshotOf.get(row.ipeds()), operator));
            Long home = homepage(website, issues, row.nameEn());
            if (home != null)
            {
                homepages++;
                AbrFieldSource hs = source("website", website, archiveService.selectSnapshot(home).getTitle(), home, operator);
                sources.put("website", hs);
            }
            AbrSchool probe = new AbrSchool();
            probe.setCountry("US");
            probe.setSchoolNameEn(row.nameEn());
            AbrSchool old = schoolMapper.checkAbrSchoolUnique(probe);
            AbrSchool s = old == null ? new AbrSchool() : schoolService.selectAbrSchoolById(old.getSchoolId());
            boolean isNew = old == null;
            AbrSchool before = isNew ? null : copy(s);
            s.setSchoolName(row.nameZh());
            s.setSchoolNameEn(row.nameEn());
            s.setCountry("US");
            s.setCity(rec.path("school.city").asString(row.city()) + ", " + row.state());
            s.setWebsite(website);
            s.setAliases(MAPPER.writeValueAsString(AbrSchoolOnboardRules.aliases(row.nameEn(), official, rec.path("school.alias").asString(""))));
            s.setExtIds(AbrSchoolOnboardRules.extIds(s.getExtIds(), "ipeds", row.ipeds()));
            if (isNew)
            {
                s.setRankings("[]");
                // 教育部认可状态为空表示待核对（不做推断）
                s.setMoeRecognized(AbrSchoolOnboardRules.MOE_PENDING);
                s.setStatus("0");
                s.setCreateBy(operator);
            }
            s.setUpdateBy(operator);
            s.getParams().put(IAbrSourceArchiveService.PARAM_CHANGE_MODE, "import");
            s.getParams().put("sources", sources);
            if (isNew)
            {
                schoolService.insertAbrSchool(s);
                created++;
            }
            else if (AbrSchoolOnboardRules.changed(before, s))
            {
                schoolService.updateAbrSchool(s);
                updated++;
            }
            else
            {
                unchanged++;
            }
            for (AbrFieldSource src : sources.values())
            {
                src.setTableName(AbrSchoolServiceImpl.SCHOOL_TABLE);
                src.setRecordId(s.getSchoolId());
                sourceMapper.upsertAbrFieldSource(src);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", rows.size());
        out.put("created", created);
        out.put("updated", updated);
        out.put("unchanged", unchanged);
        out.put("homepagesArchived", homepages);
        out.put("issues", issues);
        out.put("finishTime", System.currentTimeMillis());
        log.info("美国院校建档：共 {}，新建 {}，更新 {}，未变 {}，首页存档 {}，问题 {}", rows.size(), created, updated, unchanged, homepages, issues.size());
        return out;
    }

    private static AbrSchool copy(AbrSchool s)
    {
        AbrSchool c = new AbrSchool();
        c.setSchoolName(s.getSchoolName());
        c.setSchoolNameEn(s.getSchoolNameEn());
        c.setCity(s.getCity());
        c.setWebsite(s.getWebsite());
        c.setAliases(s.getAliases());
        c.setExtIds(s.getExtIds());
        return c;
    }

    private static AbrFieldSource source(String field, String url, String quote, Long snapshotId, String operator)
    {
        AbrFieldSource f = new AbrFieldSource();
        f.setFieldName(field);
        f.setSourceUrl(StringUtils.substring(url, 0, 1000));
        f.setQuote(quote);
        f.setOrigin("2");
        f.setSnapshotId(snapshotId);
        f.setVerifiedBy(operator);
        return f;
    }

    /**
     * robots 允许时抓取官网首页并存档；返回存档 ID，不允许或失败为 null（原因写进 issues）
     */
    private Long homepage(String website, List<String> issues, String name)
    {
        if (!robots.allowed(website))
        {
            issues.add(name + "：官网 robots.txt 不允许本系统抓取首页，官网依据为官方记录");
            return null;
        }
        try
        {
            Thread.sleep(Math.max(0, homepageIntervalMs));
            AbrWebPage page = fetcher.fetch(website);
            return archiveService.archive(website, page.getFinalUrl(), "web", 200, page.getContentType(), page.getTitle(), page.getText(), AGENT);
        }
        catch (AbrFetchException e)
        {
            issues.add(name + "：官网首页抓取失败（" + e.getMessage() + "），官网依据为官方记录");
            return null;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String get(String url)
    {
        try
        {
            HttpResponse<String> resp = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200)
            {
                throw new IllegalStateException("College Scorecard 接口返回 " + resp.statusCode());
            }
            return resp.body();
        }
        catch (IOException e)
        {
            throw new IllegalStateException("College Scorecard 接口不可用：" + e.getMessage());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("已中断");
        }
    }

    static List<SeedRow> seed()
    {
        List<SeedRow> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new ClassPathResource(SEED).getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            Set<String> seen = new LinkedHashSet<>();
            while ((line = r.readLine()) != null)
            {
                if (line.isBlank() || line.startsWith("#"))
                {
                    continue;
                }
                String[] c = line.split("\t");
                if (c.length == 6 && c[5].trim().matches("^\\d{6}$") && seen.add(c[5].trim()))
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
