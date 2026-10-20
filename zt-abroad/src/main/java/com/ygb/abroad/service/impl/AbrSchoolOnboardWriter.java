package com.ygb.abroad.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ygb.abroad.domain.AbrFieldSource;
import com.ygb.abroad.domain.AbrSchool;
import com.ygb.abroad.integration.AbrFetchException;
import com.ygb.abroad.integration.AbrRobotsPolicy;
import com.ygb.abroad.integration.AbrWebPage;
import com.ygb.abroad.integration.AbrWebPageFetcher;
import com.ygb.abroad.mapper.AbrFieldSourceMapper;
import com.ygb.abroad.mapper.AbrSchoolMapper;
import com.ygb.abroad.service.IAbrSchoolService;
import com.ygb.abroad.service.IAbrSourceArchiveService;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 院校建档的写入（M2-12 / M2-13 共用）：新建或只更新有变化的字段（记字段历史，生效方式“导入”）、写字段来源、按 robots 存档官网首页
 *
 * @author ygb
 */
@Component
public class AbrSchoolOnboardWriter
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    static final String AGENT = "school_onboard";

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

    /** 抓取官网首页的间隔（毫秒） */
    @Value("${abroad.catalog.homepage-interval-ms:1000}")
    private long homepageIntervalMs;

    /**
     * 待写入的学校
     *
     * @param country 国家
     * @param nameZh 中文名
     * @param nameEn 英文名
     * @param city 城市
     * @param website 官网（以官方记录为准）
     * @param aliases 别名
     * @param extKey 外部编号名（ipeds / ukprn 等，可为空）
     * @param extValue 外部编号
     * @param sources 字段来源（字段名 → 来源，含存档）
     */
    record Candidate(String country, String nameZh, String nameEn, String city, String website, List<String> aliases, String extKey,
            String extValue, Map<String, AbrFieldSource> sources)
    {
    }

    /**
     * 写入一所学校
     *
     * @param c 学校
     * @param issues 问题清单（追加）
     * @param operator 操作人
     * @return created / updated / unchanged，以及首页是否存档（"+home" 后缀）
     */
    String write(Candidate c, List<String> issues, String operator)
    {
        Map<String, AbrFieldSource> sources = new LinkedHashMap<>(c.sources());
        Long home = homepage(c.website(), issues, c.nameEn());
        if (home != null)
        {
            sources.put("website", source("website", c.website(), archiveService.selectSnapshot(home).getTitle(), home, operator));
        }
        AbrSchool probe = new AbrSchool();
        probe.setCountry(c.country());
        probe.setSchoolNameEn(c.nameEn());
        AbrSchool old = schoolMapper.checkAbrSchoolUnique(probe);
        boolean isNew = old == null;
        AbrSchool s = isNew ? new AbrSchool() : schoolService.selectAbrSchoolById(old.getSchoolId());
        AbrSchool before = isNew ? null : copy(s);
        s.setSchoolName(c.nameZh());
        s.setSchoolNameEn(c.nameEn());
        s.setCountry(c.country());
        s.setCity(c.city());
        s.setWebsite(c.website());
        s.setAliases(MAPPER.writeValueAsString(c.aliases()));
        if (StringUtils.isNotBlank(c.extKey()))
        {
            s.setExtIds(AbrSchoolOnboardRules.extIds(s.getExtIds(), c.extKey(), c.extValue()));
        }
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
        String result;
        if (isNew)
        {
            schoolService.insertAbrSchool(s);
            result = "created";
        }
        else if (AbrSchoolOnboardRules.changed(before, s))
        {
            schoolService.updateAbrSchool(s);
            result = "updated";
        }
        else
        {
            result = "unchanged";
        }
        for (AbrFieldSource src : sources.values())
        {
            src.setTableName(AbrSchoolServiceImpl.SCHOOL_TABLE);
            src.setRecordId(s.getSchoolId());
            sourceMapper.upsertAbrFieldSource(src);
        }
        return home != null ? result + "+home" : result;
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

    static AbrFieldSource source(String field, String url, String quote, Long snapshotId, String operator)
    {
        AbrFieldSource f = new AbrFieldSource();
        f.setFieldName(field);
        f.setSourceUrl(StringUtils.substring(url, 0, 1000));
        f.setQuote(StringUtils.substring(quote, 0, 2000));
        f.setOrigin("2");
        f.setSnapshotId(snapshotId);
        f.setVerifiedBy(operator);
        return f;
    }

    /**
     * robots 允许时抓取官网首页并存档；返回存档 ID，不允许或失败为 null（原因写进 issues）
     */
    Long homepage(String website, List<String> issues, String name)
    {
        if (StringUtils.isBlank(website))
        {
            return null;
        }
        if (!robots.allowed(website))
        {
            issues.add(name + "：官网 robots.txt 不允许本系统抓取首页，官网依据为官方名录");
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
            issues.add(name + "：官网首页抓取失败（" + StringUtils.substringBefore(e.getMessage(), "，请") + "），官网依据为官方名录");
            return null;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
