package com.ygb.abroad.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ygb.abroad.ai.AiContext;
import com.ygb.abroad.ai.AiGateway;
import com.ygb.abroad.ai.AiResult;
import com.ygb.abroad.domain.AbrAiAgent;
import com.ygb.abroad.domain.AbrCrawlPage;
import com.ygb.abroad.domain.AbrCrawlSite;
import com.ygb.abroad.domain.AbrSchool;
import com.ygb.abroad.integration.AbrFetchException;
import com.ygb.abroad.integration.AbrFetchProperties;
import com.ygb.abroad.integration.AbrRobotsPolicy;
import com.ygb.abroad.integration.AbrWebPage;
import com.ygb.abroad.integration.AbrWebPageFetcher;
import com.ygb.abroad.mapper.AbrAiAgentMapper;
import com.ygb.abroad.mapper.AbrCrawlMapper;
import com.ygb.abroad.service.IAbrProgramCrawlService;
import com.ygb.abroad.service.IAbrSchoolService;
import com.ygb.abroad.service.IAbrSourceArchiveService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 项目自动采集 服务层实现（M2-08）
 * <ol>
 * <li>发现：robots.txt 声明的站点地图（没有时试 /sitemap.xml）中的项目页与目录页网址；官网首页作为目录页，从链接继续找；</li>
 * <li>抓取：只抓 robots 允许的本校网址，同一站点两次请求至少间隔 max(配置, robots 的 Crawl-delay) 秒；原文（首行为页面标题）存档；</li>
 * <li>目录页：展开链接（最多 {@link #MAX_DEPTH} 层），像项目的链接加入待抓取；</li>
 * <li>项目页：规则抽取（{@link AbrProgramRuleExtractor}），catalog_extractor 配置了真实模型时再由大模型抽取并合并（大模型优先），
 * 都经 {@link AbrAiPrefillChecker} 核对引句与取值，写入院校库（{@link AbrProgramCrawlWriter}）；页面内容没变时不重新抽取。</li>
 * </ol>
 *
 * @author ygb
 */
@Service
public class AbrProgramCrawlServiceImpl implements IAbrProgramCrawlService
{
    private static final Logger log = LoggerFactory.getLogger(AbrProgramCrawlServiceImpl.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 目录页向下最多几层（官网首页为第 0 层） */
    static final int MAX_DEPTH = 3;

    /** 每个站点从站点地图取的目录页上限 */
    static final int MAX_SITEMAP_DIRECTORIES = 40;

    private static final Pattern LOC = Pattern.compile("(?is)<loc>\\s*(.*?)\\s*</loc>");

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    /** 站点 → 下次允许请求的时间 */
    private final Map<String, Long> nextAllowed = new ConcurrentHashMap<>();

    private final AtomicBoolean fetching = new AtomicBoolean(false);

    @Autowired
    private AbrCrawlMapper crawlMapper;

    @Autowired
    private IAbrSchoolService schoolService;

    @Autowired
    private IAbrSourceArchiveService archiveService;

    @Autowired
    private AbrProgramCrawlWriter writer;

    @Autowired
    private AbrRobotsPolicy robots;

    @Autowired
    private AbrWebPageFetcher fetcher;

    @Autowired
    private AbrFetchProperties fetchProperties;

    @Autowired
    private AbrAiAgentMapper agentMapper;

    @Autowired
    private AiGateway gateway;

    @Autowired
    private AbrLanguageBandService languageBands;

    /** 采集的国家 / 地区 */
    @Value("${abroad.crawl.countries:UK,HK,SG,AU}")
    private List<String> countries;

    /** 同一站点两次请求的最短间隔（秒） */
    @Value("${abroad.crawl.min-delay-seconds:5}")
    private int minDelay;

    /** 每个站点最多保存的页面数（发现阶段） */
    @Value("${abroad.crawl.max-pages-per-site:3000}")
    private int maxPagesPerSite;

    /** 每轮抓取的最长时间（秒） */
    @Value("${abroad.crawl.round-seconds:100}")
    private int roundSeconds;

    /** 多少天后重新发现 */
    @Value("${abroad.crawl.rediscover-days:7}")
    private int rediscoverDays;

    /** 项目页多少天后重新抓取 */
    @Value("${abroad.crawl.refetch-days:30}")
    private int refetchDays;

    /** 页面纯文本最多字符数 */
    @Value("${abroad.crawl.max-chars:150000}")
    private int maxChars;

    @Override
    public int syncSites()
    {
        return crawlMapper.insertMissingSites(countries);
    }

    @Override
    public List<AbrCrawlSite> selectSiteList(AbrCrawlSite site)
    {
        return crawlMapper.selectSiteList(site);
    }

    @Override
    public List<AbrCrawlPage> selectPageList(AbrCrawlPage page)
    {
        return crawlMapper.selectPageList(page);
    }

    @Override
    public AbrCrawlPage selectPage(Long pageId)
    {
        return crawlMapper.selectPageById(pageId);
    }

    @Override
    public int discoverDue()
    {
        syncSites();
        LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusDays(Math.max(1, rediscoverDays));
        int n = 0;
        for (AbrCrawlSite s : crawlMapper.selectSitesToDiscover(Date.from(before.atZone(ZoneId.of("Asia/Shanghai")).toInstant()), countries))
        {
            discover(s);
            n++;
        }
        return n;
    }

    @Override
    public Map<String, Object> discoverSchool(Long schoolId)
    {
        syncSites();
        AbrCrawlSite s = crawlMapper.selectSiteBySchool(schoolId);
        if (s == null)
        {
            throw new ServiceException("这所学校没有官网或不在采集范围内");
        }
        return discover(s);
    }

    /**
     * 发现：站点地图中的项目页与目录页；官网首页作为目录页
     */
    Map<String, Object> discover(AbrCrawlSite site)
    {
        String base = site.getBaseUrl();
        List<String> sitemaps = new ArrayList<>(robots.sitemaps(base));
        if (sitemaps.isEmpty())
        {
            sitemaps.add(URI.create(base).resolve("/sitemap.xml").toString());
        }
        int delay = robots.crawlDelay(base);
        int existing = crawlMapper.countPagesOfSite(site.getSiteId());
        int budget = Math.max(0, maxPagesPerSite - existing);
        int programs = 0;
        int directories = 0;
        int[] dirCount = { 0 };
        int files = 0;
        Deque<String> queue = new ArrayDeque<>(sitemaps);
        List<String> directoryCandidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        while (!queue.isEmpty() && files < 60 && budget > 0)
        {
            String sm = queue.pollFirst();
            if (!seen.add(sm) || !robots.allowed(sm) || !AbrCrawlRules.sameSite(sm, base))
            {
                continue;
            }
            String xml;
            try
            {
                politeWait(sm, delay);
                xml = getXml(sm);
            }
            catch (IllegalStateException e)
            {
                continue;
            }
            files++;
            List<String> locs = new ArrayList<>();
            Matcher m = LOC.matcher(xml);
            while (m.find())
            {
                locs.add(m.group(1).replace("&amp;", "&").trim());
            }
            if (xml.contains("<sitemapindex"))
            {
                // 子站点地图：与课程、研究生相关的优先
                List<String> pri = locs.stream().filter(l -> l.matches("(?i).*(course|program|postgrad|graduate|study|degree|taught|pg).*")).toList();
                for (int i = pri.size() - 1; i >= 0; i--)
                {
                    queue.addFirst(pri.get(i));
                }
                locs.stream().filter(l -> !pri.contains(l)).forEach(queue::addLast);
                continue;
            }
            for (String u : locs)
            {
                if (budget <= 0)
                {
                    break;
                }
                String kind = AbrCrawlRules.classify(u);
                if (kind == null || !AbrCrawlRules.sameSite(u, base))
                {
                    continue;
                }
                if (AbrCrawlRules.KIND_DIRECTORY.equals(kind))
                {
                    directoryCandidates.add(u);
                }
                else if (insert(site, u, kind, 1, "sitemap", "") > 0)
                {
                    budget--;
                    programs++;
                }
            }
        }
        // 目录页只取层级最短的若干个（通常是总目录与学院目录），其余项目靠展开链接找到
        directoryCandidates.stream().distinct().sorted((a, b) -> Integer.compare(a.split("/").length * 1000 + a.length(), b.split("/").length * 1000 + b.length()))
                .limit(MAX_SITEMAP_DIRECTORIES).forEach(u -> {
                    if (insert(site, u, AbrCrawlRules.KIND_DIRECTORY, 1, "sitemap", "") > 0)
                    {
                        dirCount[0]++;
                    }
                });
        directories += dirCount[0];
        // 官网首页作为第 0 层目录页
        if (insert(site, base, AbrCrawlRules.KIND_DIRECTORY, 0, "home", "") > 0)
        {
            directories++;
        }
        String result = "站点地图 " + files + " 个，新增项目页候选 " + programs + "、目录页 " + directories;
        crawlMapper.updateSiteDiscovered(site.getSiteId(), MAPPER.writeValueAsString(sitemaps), delay, result);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sitemapFiles", files);
        out.put("programs", programs);
        out.put("directories", directories);
        return out;
    }

    private int insert(AbrCrawlSite site, String rawUrl, String kind, int depth, String from, String text)
    {
        String url = "home".equals(from) ? rawUrl : AbrCrawlRules.normalize(rawUrl);
        if (url.length() > 1000)
        {
            return 0;
        }
        AbrCrawlPage p = new AbrCrawlPage();
        p.setSiteId(site.getSiteId());
        p.setSchoolId(site.getSchoolId());
        p.setUrl(url);
        p.setKind(kind);
        p.setDepth(depth);
        p.setFoundFrom(from);
        p.setLinkText(StringUtils.substring(text, 0, 200));
        return crawlMapper.insertPage(p);
    }

    @Override
    public Map<String, Object> fetchDue()
    {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!fetching.compareAndSet(false, true))
        {
            out.put("skipped", "上一轮还在进行");
            return out;
        }
        try
        {
            long deadline = System.currentTimeMillis() + Math.max(10, roundSeconds) * 1000L;
            int done = 0;
            Map<String, Integer> status = new LinkedHashMap<>();
            Map<Long, AbrCrawlSite> sites = new LinkedHashMap<>();
            while (System.currentTimeMillis() < deadline)
            {
                List<AbrCrawlPage> due = crawlMapper.selectDuePages(200, countries);
                if (due.isEmpty())
                {
                    break;
                }
                boolean any = false;
                for (AbrCrawlPage p : due)
                {
                    if (System.currentTimeMillis() >= deadline)
                    {
                        break;
                    }
                    String host = host(p.getUrl());
                    if (nextAllowed.getOrDefault(host, 0L) > System.currentTimeMillis())
                    {
                        continue;
                    }
                    AbrCrawlSite site = sites.computeIfAbsent(p.getSiteId(), crawlMapper::selectSiteById);
                    String st = process(p, site);
                    status.merge(st, 1, Integer::sum);
                    done++;
                    any = true;
                }
                if (!any)
                {
                    sleep(1000);
                }
            }
            out.put("processed", done);
            out.put("status", status);
            return out;
        }
        finally
        {
            fetching.set(false);
        }
    }

    @Override
    public String processPage(Long pageId)
    {
        AbrCrawlPage p = crawlMapper.selectPageById(pageId);
        if (p == null)
        {
            throw new ServiceException("页面不存在");
        }
        crawlMapper.requeuePage(pageId);
        p.setContentHash("");
        return process(p, crawlMapper.selectSiteById(p.getSiteId()));
    }

    /**
     * 处理一个页面，返回新状态
     */
    String process(AbrCrawlPage p, AbrCrawlSite site)
    {
        AbrCrawlPage r = new AbrCrawlPage();
        r.setPageId(p.getPageId());
        r.setFetchedTime(new Date());
        int delay = Math.max(minDelay, site == null || site.getCrawlDelay() == null ? 0 : site.getCrawlDelay());
        if (!robots.allowed(p.getUrl()))
        {
            return finish(r, "blocked", "robots.txt 不允许本系统抓取", 30);
        }
        AbrWebPage page;
        try
        {
            politeWait(p.getUrl(), delay);
            page = fetcher.fetch(p.getUrl(), maxChars);
        }
        catch (AbrFetchException e)
        {
            int fails = (p.getFailCount() == null ? 0 : p.getFailCount()) + 1;
            r.setFailCount(fails);
            return finish(r, "failed", StringUtils.substringBefore(e.getMessage(), "，请"), Math.min(30, fails * 2));
        }
        r.setFailCount(0);
        r.setTitle(StringUtils.substring(page.getTitle(), 0, 300));
        String text = "Title: " + StringUtils.defaultString(page.getTitle()) + "\n" + StringUtils.defaultString(page.getText());
        // 内容哈希带上规则版本：规则升级后已采集的页面会重新抽取
        String hash = AbrSourceArchiveServiceImpl.sha256(text + "\n#rules-v" + AbrProgramRuleExtractor.VERSION);
        Long snapshotId = archiveService.archive(p.getUrl(), page.getFinalUrl(), "web", 200, page.getContentType(), page.getTitle(), text,
                AbrProgramCrawlWriter.OPERATOR);
        r.setSnapshotId(snapshotId);
        r.setContentHash(hash);
        boolean unchanged = hash.equals(p.getContentHash());

        // 页面上的链接：目录页展开，项目页也收集同站的其他项目链接（兄弟项目）
        int added = 0;
        int depth = p.getDepth() == null ? 0 : p.getDepth();
        if (site != null && depth < MAX_DEPTH)
        {
            for (AbrCrawlRules.Link l : AbrCrawlRules.links(page.getHtml(), page.getFinalUrl()))
            {
                if (!AbrCrawlRules.sameSite(l.url(), site.getBaseUrl()))
                {
                    continue;
                }
                if (AbrCrawlRules.programLink(l))
                {
                    added += insert(site, l.url(), AbrCrawlRules.KIND_PROGRAM, depth + 1, "link", l.text());
                }
                else if (AbrCrawlRules.KIND_DIRECTORY.equals(p.getKind()) && AbrCrawlRules.KIND_DIRECTORY.equals(AbrCrawlRules.classify(l.url())))
                {
                    added += insert(site, l.url(), AbrCrawlRules.KIND_DIRECTORY, depth + 1, "link", l.text());
                }
                if (added >= 500)
                {
                    break;
                }
            }
        }
        if (!AbrProgramRuleExtractor.isProgramPage(page.getTitle(), text))
        {
            String st = AbrCrawlRules.KIND_DIRECTORY.equals(p.getKind()) ? "listed" : "not_program";
            String note = writer.retire(p.getProgramId()) ? "；已停用原项目" : "";
            return finish(r, st, "新链接 " + added + note, "listed".equals(st) ? 14 : 90);
        }
        String skip = AbrProgramRuleExtractor.skipReason(page.getTitle(), LocalDateTime.now(ZoneId.of("Asia/Shanghai")).getYear());
        if (skip != null)
        {
            return finish(r, "not_program", skip + (writer.retire(p.getProgramId()) ? "；已停用原项目" : ""), 90);
        }
        if (unchanged && p.getProgramId() != null)
        {
            r.setProgramId(p.getProgramId());
            return finish(r, "done", "页面没有变化", refetchDays);
        }
        AbrSchool school = schoolService.selectAbrSchoolById(p.getSchoolId());
        ObjectNode output = AbrProgramRuleExtractor.extract(page.getTitle(), text, school == null ? "" : school.getCountry());
        String llm = mergeLlm(output, p, page, text);
        List<Map<String, Object>> fields = new AbrAiPrefillChecker(AbrProgramAiPrefillServiceImpl::dictValuesFor).check(output, text);
        Map<String, Map<String, Object>> found = new LinkedHashMap<>();
        for (Map<String, Object> f : fields)
        {
            if (AbrAiPrefillChecker.STATUS_FOUND.equals(f.get("status")))
            {
                found.put(String.valueOf(f.get("fieldName")), f);
            }
        }
        bandIelts(school, text, found, fields);
        r.setFieldsFound(found.size());
        r.setResult(MAPPER.writeValueAsString(fields));
        if (school == null || !found.containsKey("programNameEn"))
        {
            return finish(r, "not_program", "没有识别出项目名称", 90);
        }
        try
        {
            AbrProgramCrawlWriter.Result w = writer.write(school, p.getUrl(), snapshotId, found);
            r.setProgramId(w.programId());
            r.setRequirementId(w.requirementId());
            return finish(r, "done", (w.created() ? "新建项目；" : "更新项目；") + w.reason() + llm, refetchDays);
        }
        catch (RuntimeException e)
        {
            log.warn("采集写入失败 page={}：{}", p.getPageId(), e.getMessage());
            r.setFailCount(1);
            return finish(r, "failed", "写入失败：" + StringUtils.substring(e.getMessage(), 0, 200), 7);
        }
    }

    /**
     * 项目页只标注了学校英语等级（如 UCL 的 Level 1）：按学校官方等级说明页的存档换算雅思要求，来源指向说明页
     */
    private void bandIelts(AbrSchool school, String text, Map<String, Map<String, Object>> found, List<Map<String, Object>> fields)
    {
        if (school == null || found.containsKey("ieltsOverall"))
        {
            return;
        }
        AbrLanguageBandService.Loaded bands = languageBands.get(school.getSchoolNameEn());
        if (bands == null)
        {
            return;
        }
        Matcher m = bands.marker().matcher(text);
        if (!m.find())
        {
            return;
        }
        String label = AbrLanguageBandRules.normalizeLabel(m.group(1));
        AbrLanguageBandRules.Band b = bands.bands().get(label);
        if (b == null)
        {
            return;
        }
        String note = "项目页标注 " + label + "（" + m.group().trim() + "），按学校英语等级说明换算";
        found.put("ieltsOverall", bandField("ieltsOverall", "雅思总分", b.overall(), b.quote(), bands, note));
        fields.removeIf(f -> "ieltsOverall".equals(f.get("fieldName")) || "ieltsMinSub".equals(f.get("fieldName")));
        fields.add(found.get("ieltsOverall"));
        if (b.minSub() != null)
        {
            found.put("ieltsMinSub", bandField("ieltsMinSub", "雅思小分", b.minSub(), b.quote(), bands, note));
            fields.add(found.get("ieltsMinSub"));
        }
    }

    private static Map<String, Object> bandField(String name, String label, Object value, String quote, AbrLanguageBandService.Loaded bands, String note)
    {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("fieldName", name);
        f.put("label", label);
        f.put("target", "requirement");
        f.put("value", value);
        f.put("quote", quote);
        f.put("status", AbrAiPrefillChecker.STATUS_FOUND);
        f.put("reason", note);
        f.put("sourceUrl", bands.url());
        f.put("snapshotId", bands.snapshotId());
        return f;
    }

    /**
     * catalog_extractor 配置了真实模型时调用大模型，找到的字段覆盖规则抽取的结果（之后统一校验）
     */
    private String mergeLlm(ObjectNode output, AbrCrawlPage p, AbrWebPage page, String text)
    {
        AbrAiAgent agent = agentMapper.selectAbrAiAgentByCode(AbrProgramAiPrefillServiceImpl.AGENT_CODE);
        if (agent == null || !"0".equals(agent.getStatus()) || StringUtils.defaultString(agent.getPrimaryModel()).startsWith("mock:"))
        {
            return "（规则抽取）";
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("url", page.getFinalUrl());
        input.put("programHint", "（未指定，按页面主体项目抽取）");
        input.put("pageTitle", page.getTitle());
        input.put("pageText", StringUtils.substring(text, 0, 30000));
        AiResult ai = gateway.invoke(AbrProgramAiPrefillServiceImpl.AGENT_CODE, input, AiContext.of("program_crawl", String.valueOf(p.getPageId()), AbrProgramCrawlWriter.OPERATOR));
        if (!ai.isSuccess() || ai.getOutput() == null)
        {
            return "（大模型失败，用规则抽取）";
        }
        for (Map.Entry<String, JsonNode> e : ai.getOutput().properties())
        {
            JsonNode v = e.getValue().get("value");
            if (v != null && !v.isNull() && !(v.isString() && v.asString().isBlank()))
            {
                output.set(e.getKey(), e.getValue());
            }
        }
        return "（规则 + 大模型抽取）";
    }

    private String finish(AbrCrawlPage r, String status, String reason, int days)
    {
        r.setStatus(status);
        r.setReason(StringUtils.substring(reason, 0, 300));
        LocalDateTime next = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(Math.max(1, days));
        r.setNextFetchTime(Date.from(next.atZone(ZoneId.of("Asia/Shanghai")).toInstant()));
        crawlMapper.updatePageResult(r);
        return status;
    }

    @Override
    public int pauseSite(Long siteId, boolean pause)
    {
        return crawlMapper.updateSiteStatus(siteId, pause ? "1" : "0");
    }

    @Override
    public int requeue(Long pageId)
    {
        return crawlMapper.requeuePage(pageId);
    }

    /**
     * 同一站点两次请求的间隔
     */
    private void politeWait(String url, int delaySeconds)
    {
        String host = host(url);
        long wait = nextAllowed.getOrDefault(host, 0L) - System.currentTimeMillis();
        if (wait > 0)
        {
            sleep(wait);
        }
        nextAllowed.put(host, System.currentTimeMillis() + Math.max(minDelay, delaySeconds) * 1000L);
    }

    private static String host(String url)
    {
        try
        {
            return StringUtils.defaultString(URI.create(url).getHost()).toLowerCase();
        }
        catch (IllegalArgumentException e)
        {
            return "";
        }
    }

    private String getXml(String url)
    {
        try
        {
            // 与页面抓取相同的内网地址限制（防 SSRF）
            for (InetAddress a : InetAddress.getAllByName(URI.create(url).getHost()))
            {
                if (!fetchProperties.isAllowPrivateHosts() && AbrWebPageFetcher.isBlockedAddress(a))
                {
                    throw new IllegalStateException("不允许访问内网地址");
                }
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "Mozilla/5.0 (compatible; " + AbrRobotsPolicy.AGENT + "/1.0)").GET().build();
            HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200)
            {
                resp.body().close();
                throw new IllegalStateException("返回 " + resp.statusCode());
            }
            byte[] body;
            try (InputStream in = resp.body())
            {
                body = in.readNBytes(30_000_000);
            }
            if (body.length > 2 && body[0] == (byte) 0x1f && body[1] == (byte) 0x8b)
            {
                try (InputStream gz = new GZIPInputStream(new ByteArrayInputStream(body)))
                {
                    body = gz.readNBytes(60_000_000);
                }
            }
            return new String(body, StandardCharsets.UTF_8);
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
}
