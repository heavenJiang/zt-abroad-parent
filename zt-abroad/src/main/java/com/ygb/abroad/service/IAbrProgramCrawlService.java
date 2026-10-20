package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrCrawlPage;
import com.ygb.abroad.domain.AbrCrawlSite;

/**
 * 项目自动采集 服务层（M2-08）
 * 
 * @author ygb
 */
public interface IAbrProgramCrawlService
{
    /**
     * 为采集范围内有官网的学校建立采集站点
     *
     * @return 新建的站点数
     */
    public int syncSites();

    public List<AbrCrawlSite> selectSiteList(AbrCrawlSite site);

    public List<AbrCrawlPage> selectPageList(AbrCrawlPage page);

    public AbrCrawlPage selectPage(Long pageId);

    /**
     * 为到期的站点发现项目页（定时任务）
     *
     * @return 处理的站点数
     */
    public int discoverDue();

    /**
     * 立即为一所学校发现项目页
     *
     * @param schoolId 学校
     * @return sitemapFiles、programs、directories
     */
    public Map<String, Object> discoverSchool(Long schoolId);

    /**
     * 抓取到期页面一轮（定时任务；同一时间只运行一轮）
     *
     * @return processed、status
     */
    public Map<String, Object> fetchDue();

    /**
     * 立即重新抓取并抽取一个页面
     *
     * @param pageId 页面
     * @return 新状态
     */
    public String processPage(Long pageId);

    public int pauseSite(Long siteId, boolean pause);

    public int requeue(Long pageId);
}
