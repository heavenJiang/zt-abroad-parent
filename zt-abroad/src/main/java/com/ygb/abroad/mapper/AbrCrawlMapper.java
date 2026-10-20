package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrCrawlPage;
import com.ygb.abroad.domain.AbrCrawlSite;

/**
 * 项目采集 数据层（M2-08）
 * 
 * @author ygb
 */
public interface AbrCrawlMapper
{
    /**
     * 为指定国家中有官网、状态正常、还没有站点的学校建立采集站点
     */
    public int insertMissingSites(@Param("countries") List<String> countries);

    public List<AbrCrawlSite> selectSiteList(AbrCrawlSite site);

    public AbrCrawlSite selectSiteBySchool(Long schoolId);

    public AbrCrawlSite selectSiteById(Long siteId);

    /**
     * 到期需要重新发现的站点（从未发现过，或最近一次发现早于 before）
     */
    public List<AbrCrawlSite> selectSitesToDiscover(@Param("before") Date before, @Param("countries") List<String> countries);

    public int updateSiteDiscovered(@Param("siteId") Long siteId, @Param("sitemaps") String sitemaps, @Param("crawlDelay") Integer crawlDelay,
            @Param("result") String result);

    public int updateSiteStatus(@Param("siteId") Long siteId, @Param("status") String status);

    /**
     * 新页面（网址已存在时不变）
     */
    public int insertPage(AbrCrawlPage page);

    public List<AbrCrawlPage> selectPageList(AbrCrawlPage page);

    public AbrCrawlPage selectPageById(Long pageId);

    /**
     * 到期的页面（站点正常），按下次抓取时间
     */
    public List<AbrCrawlPage> selectDuePages(@Param("limit") int limit, @Param("countries") List<String> countries);

    public int updatePageResult(AbrCrawlPage page);

    public int requeuePage(Long pageId);

    public int countPagesOfSite(Long siteId);
}
