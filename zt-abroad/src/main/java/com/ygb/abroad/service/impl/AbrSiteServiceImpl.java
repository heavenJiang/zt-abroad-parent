package com.ygb.abroad.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.AbrSchool;
import com.ygb.abroad.domain.AbrSiteBuild;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.abroad.integration.AbrSearchSubmitter;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.mapper.AbrRequirementMapper;
import com.ygb.abroad.mapper.AbrSchoolMapper;
import com.ygb.abroad.mapper.AbrSiteBuildMapper;
import com.ygb.abroad.service.IAbrSiteService;
import com.ygb.common.config.ZtConfig;
import com.ygb.common.core.domain.entity.SysDictData;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DictUtils;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 公开项目页 服务层实现
 *
 * 定时任务每 5 分钟算一次指纹（院校库各表行数与最近变更时间、相关字典、今天的日期、站点设置、模板版本与样式），
 * 与上次成功生成的相同就跳过；不同时在内存中渲染全部页面，与站点目录下的清单（.manifest.json，记录每个文件的摘要与内容最后变化日期）
 * 比对，只重写变化的文件、删除不再存在的页面，再按变化的地址提交收录。文件先写临时文件再改名，Nginx 不会读到写了一半的页面。
 *
 * @author ygb
 */
@Service
public class AbrSiteServiceImpl implements IAbrSiteService
{
    private static final Logger log = LoggerFactory.getLogger(AbrSiteServiceImpl.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 站点目录下的文件清单 */
    static final String MANIFEST = ".manifest.json";

    /** 生成记录中每类变化最多保存的条数 */
    private static final int MAX_CHANGES = 200;

    /** 定时检查：同一指纹生成失败后，多少分钟内不再重试 */
    private static final long RETRY_AFTER_MINUTES = 60;

    /** 页面用到的字典 */
    private static final List<String> DICTS = List.of("abr_country", "abr_degree", "abr_major_direction", "abr_intake", "abr_teaching_language",
            "abr_test_requirement", "abr_cross_major", "abr_pre_sessional");

    /** 预览地址：小写字母、数字、短横线、下划线、斜杠 */
    private static final Pattern URL = Pattern.compile("^[a-z0-9_/-]{0,64}$");

    private final ReentrantLock lock = new ReentrantLock();

    private volatile String css;

    @Autowired
    private AbrSiteBuildMapper buildMapper;

    @Autowired
    private AbrProgramMapper programMapper;

    @Autowired
    private AbrSchoolMapper schoolMapper;

    @Autowired
    private AbrRequirementMapper requirementMapper;

    @Autowired
    private AbrSearchSubmitter submitter;

    /** 站点目录（Nginx www 域名的根目录）；为空时用 zt.profile 的同级目录 site */
    @Value("${abroad.site.dir:}")
    private String dir;

    /** 站点地址 */
    @Value("${abroad.site.url:http://localhost/}")
    private String siteUrl;

    /** C 端地址 */
    @Value("${abroad.app.url:http://localhost:5173/}")
    private String appUrl;

    /** 品牌名 */
    @Value("${abroad.app.brand-name:留学工作室}")
    private String brand;

    /** 入口链接的渠道码 */
    @Value("${abroad.site.channel:site}")
    private String channel;

    /** C 端“关注这个项目”页面 */
    @Value("${abroad.site.follow-page:pages/follow/index}")
    private String followPage;

    @Override
    public AbrSiteBuild refresh(String triggerType, boolean force, String operator)
    {
        boolean manual = AbrSiteBuild.TRIGGER_MANUAL.equals(triggerType);
        if (!lock.tryLock())
        {
            if (manual)
            {
                throw new ServiceException("项目页正在生成，请稍后刷新查看");
            }
            return null;
        }
        try
        {
            LocalDate today = MarketingCalendar.today();
            String fp = fingerprint(today);
            if (!force && skip(fp))
            {
                return null;
            }
            buildMapper.failRunning();
            AbrSiteBuild b = new AbrSiteBuild();
            b.setTriggerType(manual ? AbrSiteBuild.TRIGGER_MANUAL : AbrSiteBuild.TRIGGER_SCHEDULE);
            b.setFingerprint(fp);
            b.setCreateBy(StringUtils.defaultString(operator));
            buildMapper.insertAbrSiteBuild(b);
            try
            {
                Settings s = settings();
                Sync sync = sync(root(), render(today, s), s, today);
                Map<String, Object> submitted = submitter.submit(s.siteUrl(), absolute(s, sync.changed()), absolute(s, sync.removed()));
                b.setStatus(AbrSiteBuild.STATUS_SUCCESS);
                b.setPageTotal(sync.pageTotal());
                b.setWrittenCount(sync.written());
                b.setRemovedCount(sync.removedFiles());
                b.setSubmittedCount(submitted.get("count") instanceof Integer n ? n : 0);
                b.setChanges(MAPPER.writeValueAsString(sync.changes()));
                b.setSubmitResult(MAPPER.writeValueAsString(submitted));
                buildMapper.finishAbrSiteBuild(b);
                log.info("公开项目页生成完成：页面 {}，重写 {}，删除 {}", sync.pageTotal(), sync.written(), sync.removedFiles());
            }
            catch (RuntimeException | IOException e)
            {
                log.error("公开项目页生成失败", e);
                b.setStatus(AbrSiteBuild.STATUS_FAILED);
                b.setErrorMsg(StringUtils.substring(e.getClass().getSimpleName() + "：" + StringUtils.defaultString(e.getMessage()), 0, 2000));
                buildMapper.finishAbrSiteBuild(b);
                if (manual)
                {
                    throw new ServiceException("项目页生成失败：" + StringUtils.defaultString(e.getMessage()));
                }
            }
            return buildMapper.selectAbrSiteBuildById(b.getBuildId());
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * 定时检查是否跳过：指纹与上次成功的相同；或同一指纹刚失败过（避免目录不可写等问题每 5 分钟记一条失败）
     */
    private boolean skip(String fp)
    {
        AbrSiteBuild last = buildMapper.selectLastSuccess();
        if (last != null && fp.equals(last.getFingerprint()))
        {
            return true;
        }
        AbrSiteBuild latest = buildMapper.selectLatest();
        return latest != null && AbrSiteBuild.STATUS_FAILED.equals(latest.getStatus()) && fp.equals(latest.getFingerprint()) && latest.getStartTime() != null
                && System.currentTimeMillis() - latest.getStartTime().getTime() < RETRY_AFTER_MINUTES * 60_000L;
    }

    @Override
    public List<AbrSiteBuild> selectAbrSiteBuildList(AbrSiteBuild build)
    {
        return buildMapper.selectAbrSiteBuildList(build);
    }

    @Override
    public AbrSiteBuild selectAbrSiteBuildById(Long buildId)
    {
        return buildMapper.selectAbrSiteBuildById(buildId);
    }

    @Override
    public Map<String, Object> status()
    {
        Settings s = settings();
        Map<String, Object> m = new LinkedHashMap<>();
        Path root = root();
        m.put("dir", root.toString());
        m.put("siteUrl", s.siteUrl());
        Map<String, Integer> kinds = new TreeMap<>();
        readManifest(root).keySet().stream().filter(p -> p.endsWith(".html") && !"404.html".equals(p))
                .forEach(p -> kinds.merge(p.contains("/") ? p.substring(0, p.indexOf('/')) : AbrSitePages.KIND_HOME, 1, Integer::sum));
        m.put("pages", kinds);
        AbrSiteBuild last = buildMapper.selectLastSuccess();
        m.put("lastSuccess", last);
        m.put("latest", buildMapper.selectLatest());
        m.put("pendingChanges", last == null || !fingerprint(MarketingCalendar.today()).equals(last.getFingerprint()));
        Map<String, Object> submit = new LinkedHashMap<>();
        submit.put("baidu", submitter.baiduEnabled());
        submit.put("indexnow", submitter.indexNowKey() != null);
        m.put("submit", submit);
        return m;
    }

    @Override
    public String preview(String url)
    {
        String u = StringUtils.trimToEmpty(url);
        u = u.startsWith("/") ? u.substring(1) : u;
        u = u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
        if (!URL.matcher(u).matches() || u.contains(".."))
        {
            throw new ServiceException("页面地址格式不对，例如 program/12、country/uk、major/cs、deadline/2026-12");
        }
        String path = u.isEmpty() ? "index.html" : u + ".html";
        LocalDate today = MarketingCalendar.today();
        for (AbrSitePages.Page p : render(today, settings()))
        {
            if (p.path().equals(path))
            {
                // 管理端预览：样式内联（站点样式表在 www 域名下，管理端域名取不到）
                return p.content().replaceFirst("<link rel=\"stylesheet\" href=\"/assets/site.css\\?v=\\d+\">",
                        Matcher.quoteReplacement("<style>\n" + css() + "</style>"));
            }
        }
        throw new ServiceException("页面不存在：项目未上架，或聚合页下没有项目");
    }

    // ---------------------------------------------------------------- 渲染

    private List<AbrSitePages.Page> render(LocalDate today, Settings s)
    {
        AbrSchool schoolQuery = new AbrSchool();
        schoolQuery.setStatus("0");
        Map<Long, AbrSchool> schools = new HashMap<>();
        schoolMapper.selectAbrSchoolList(schoolQuery).forEach(x -> schools.put(x.getSchoolId(), x));
        AbrProgram query = new AbrProgram();
        query.setStatus("0");
        List<AbrProgram> programs = new ArrayList<>();
        for (AbrProgram p : programMapper.selectAbrProgramList(query))
        {
            if (schools.containsKey(p.getSchoolId()))
            {
                programs.add(p);
            }
        }
        Map<Long, AbrRequirement> reqs = new HashMap<>();
        Map<Long, List<String>> sources = new HashMap<>();
        if (!programs.isEmpty())
        {
            List<Long> ids = programs.stream().map(AbrProgram::getProgramId).toList();
            requirementMapper.selectLatestAbrRequirementByProgramIds(ids).forEach(r -> reqs.put(r.getProgramId(), r));
            List<Long> reqIds = reqs.values().stream().map(AbrRequirement::getRequirementId).toList();
            for (Map<String, Object> row : buildMapper.selectSourceUrls(ids, reqIds))
            {
                Object id = row.get("program_id");
                Object src = row.get("source_url");
                if (id instanceof Number n && src != null)
                {
                    sources.computeIfAbsent(n.longValue(), k -> new ArrayList<>()).add(src.toString());
                }
            }
        }
        List<AbrSitePages.Entry> entries = new ArrayList<>();
        for (AbrProgram p : programs)
        {
            entries.add(new AbrSitePages.Entry(p, schools.get(p.getSchoolId()), reqs.get(p.getProgramId()),
                    sources.getOrDefault(p.getProgramId(), List.of())));
        }
        return AbrSitePages.build(entries, dicts(), s.pages(), today);
    }

    private static Map<String, Map<String, String>> dicts()
    {
        Map<String, Map<String, String>> m = new HashMap<>();
        for (String type : DICTS)
        {
            Map<String, String> labels = new LinkedHashMap<>();
            List<SysDictData> datas = DictUtils.getDictCache(type);
            if (datas != null)
            {
                datas.forEach(d -> labels.put(d.getDictValue(), d.getDictLabel()));
            }
            m.put(type, labels);
        }
        return m;
    }

    // ---------------------------------------------------------------- 写入

    /**
     * 写入结果
     *
     * @param pageTotal HTML 页面数（不含 404）
     * @param written 重写的文件数
     * @param removedFiles 删除的文件数
     * @param changed 新增或内容变化的页面地址
     * @param removed 删除的页面地址
     * @param changes 变化明细（地址以 / 开头）
     */
    private record Sync(int pageTotal, int written, int removedFiles, List<String> changed, List<String> removed, Map<String, List<String>> changes)
    {
    }

    private Sync sync(Path root, List<AbrSitePages.Page> pages, Settings s, LocalDate today) throws IOException
    {
        Files.createDirectories(root);
        Map<String, String[]> old = readManifest(root);
        Map<String, byte[]> files = new TreeMap<>();
        Map<String, LocalDate> lastmod = new HashMap<>();
        Map<String, String> kinds = new HashMap<>();
        int pageTotal = 0;
        for (AbrSitePages.Page p : pages)
        {
            byte[] bytes = p.content().getBytes(StandardCharsets.UTF_8);
            files.put(p.path(), bytes);
            kinds.put(p.path(), p.kind());
            String[] before = old.get(p.path());
            lastmod.put(p.path(), before != null && before[0].equals(sha(bytes)) && parseDay(before[1]) != null ? parseDay(before[1]) : today);
            if (AbrSitePages.isListed(p))
            {
                pageTotal++;
            }
        }
        files.put("sitemap.xml", AbrSitePages.sitemap(pages, lastmod, s.siteUrl()).getBytes(StandardCharsets.UTF_8));
        files.put("robots.txt", AbrSitePages.robots(s.siteUrl()).getBytes(StandardCharsets.UTF_8));
        files.put("assets/site.css", css().getBytes(StandardCharsets.UTF_8));
        String key = submitter.indexNowKey();
        if (key != null)
        {
            files.put(key + ".txt", key.getBytes(StandardCharsets.UTF_8));
        }
        Map<String, List<String>> changes = new LinkedHashMap<>();
        changes.put("added", new ArrayList<>());
        changes.put("updated", new ArrayList<>());
        changes.put("removed", new ArrayList<>());
        List<String> changed = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        Map<String, String[]> manifest = new TreeMap<>();
        int written = 0;
        for (Map.Entry<String, byte[]> f : files.entrySet())
        {
            String path = f.getKey();
            String sha = sha(f.getValue());
            String[] before = old.get(path);
            Path file = resolve(root, path);
            boolean same = before != null && before[0].equals(sha) && Files.isRegularFile(file);
            LocalDate kept = same ? parseDay(before[1]) : null;
            LocalDate mod = lastmod.containsKey(path) ? lastmod.get(path) : kept != null ? kept : today;
            if (!same)
            {
                write(file, f.getValue());
                written++;
                if (path.endsWith(".html") && !AbrSitePages.KIND_OTHER.equals(kinds.get(path)))
                {
                    changed.add(AbrSitePages.urlOf(path));
                    note(changes.get(before == null ? "added" : "updated"), "/" + AbrSitePages.urlOf(path));
                }
            }
            manifest.put(path, new String[] { sha, mod.toString() });
        }
        int removedFiles = 0;
        for (String path : old.keySet())
        {
            if (files.containsKey(path))
            {
                continue;
            }
            Path file = resolve(root, path);
            if (Files.deleteIfExists(file))
            {
                removedFiles++;
            }
            if (path.endsWith(".html") && !"404.html".equals(path))
            {
                removed.add(AbrSitePages.urlOf(path));
                note(changes.get("removed"), "/" + AbrSitePages.urlOf(path));
            }
        }
        writeManifest(root, manifest);
        return new Sync(pageTotal, written, removedFiles, changed, removed, changes);
    }

    private static void note(List<String> list, String url)
    {
        if (list.size() < MAX_CHANGES)
        {
            list.add(url);
        }
    }

    private static List<String> absolute(Settings s, List<String> urls)
    {
        return urls.stream().map(u -> s.siteUrl() + u).toList();
    }

    /**
     * 站点目录内的文件（拒绝越出目录的路径）
     */
    private static Path resolve(Path root, String path)
    {
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root) || file.equals(root))
        {
            throw new ServiceException("文件路径越出站点目录：" + path);
        }
        return file;
    }

    private static void write(Path file, byte[] bytes) throws IOException
    {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        try
        {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException e)
        {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 清单：路径 → [摘要, 内容最后变化日期]；没有或损坏时为空（全部按新增处理）
     */
    private static Map<String, String[]> readManifest(Path root)
    {
        Map<String, String[]> m = new HashMap<>();
        Path file = root.resolve(MANIFEST);
        if (!Files.isRegularFile(file))
        {
            return m;
        }
        try
        {
            JsonNode files = MAPPER.readTree(Files.readAllBytes(file)).path("files");
            for (Map.Entry<String, JsonNode> e : files.properties())
            {
                String sha = e.getValue().path("sha").asString("");
                if (!sha.isEmpty())
                {
                    m.put(e.getKey(), new String[] { sha, e.getValue().path("lastmod").asString(null) });
                }
            }
        }
        catch (IOException | JacksonException e)
        {
            log.warn("站点清单损坏，按全部新增处理：{}", e.toString());
        }
        return m;
    }

    private static void writeManifest(Path root, Map<String, String[]> manifest) throws IOException
    {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("version", AbrSitePages.VERSION);
        ObjectNode files = n.putObject("files");
        manifest.forEach((path, v) -> {
            ObjectNode f = files.putObject(path);
            f.put("sha", v[0]);
            f.put("lastmod", v[1]);
        });
        write(root.resolve(MANIFEST), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(n));
    }

    // ---------------------------------------------------------------- 设置与指纹

    /**
     * 站点设置（地址统一以 / 结尾）
     */
    private record Settings(String siteUrl, AbrSitePages.Settings pages)
    {
    }

    private Settings settings()
    {
        String site = slash(StringUtils.defaultIfBlank(siteUrl, "http://localhost/"));
        String code = AbrVisitorServiceImpl.code(StringUtils.trimToNull(channel), "site");
        return new Settings(site, new AbrSitePages.Settings(site, slash(StringUtils.defaultIfBlank(appUrl, "http://localhost:5173/")),
                StringUtils.defaultIfBlank(brand, "留学工作室"), code, StringUtils.removeStart(StringUtils.trimToEmpty(followPage), "/")));
    }

    private String fingerprint(LocalDate today)
    {
        Settings s = settings();
        String raw = String.join("|", "v" + AbrSitePages.VERSION, StringUtils.defaultString(buildMapper.selectCatalogStamp()), today.toString(), s.siteUrl(),
                s.pages().appUrl(), s.pages().brand(), s.pages().channel(), s.pages().followPage(), sha(css().getBytes(StandardCharsets.UTF_8)),
                StringUtils.defaultString(submitter.indexNowKey()), root().toString());
        return sha(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Path root()
    {
        String d = StringUtils.trimToNull(dir);
        if (d == null)
        {
            return Paths.get(ZtConfig.getProfile()).toAbsolutePath().normalize().resolveSibling("site");
        }
        return Paths.get(d).toAbsolutePath().normalize();
    }

    private String css()
    {
        String c = css;
        if (c == null)
        {
            try (InputStream in = new ClassPathResource("site/site.css").getInputStream())
            {
                c = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            catch (IOException e)
            {
                throw new IllegalStateException("缺少站点样式 site/site.css", e);
            }
            css = c;
        }
        return c;
    }

    private static String slash(String url)
    {
        String u = url.trim();
        return u.endsWith("/") ? u : u + "/";
    }

    private static LocalDate parseDay(String text)
    {
        if (text == null)
        {
            return null;
        }
        try
        {
            return LocalDate.parse(text);
        }
        catch (DateTimeParseException e)
        {
            return null;
        }
    }

    static String sha(byte[] data)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }
}
