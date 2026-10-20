package com.ygb.web.controller.abroad;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrCrawlPage;
import com.ygb.abroad.domain.AbrCrawlSite;
import com.ygb.abroad.service.IAbrProgramCrawlService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 项目自动采集（M2-08）：站点、页面与抽取结果，立即发现 / 抓取
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/crawl")
public class AbrCrawlController extends BaseController
{
    @Autowired
    private IAbrProgramCrawlService crawlService;

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private Executor executor;

    /**
     * 采集站点（每所学校一个）
     */
    @PreAuthorize("@ss.hasPermi('abroad:crawl:list')")
    @GetMapping("/site/list")
    public TableDataInfo siteList(AbrCrawlSite site)
    {
        crawlService.syncSites();
        startPage();
        List<AbrCrawlSite> list = crawlService.selectSiteList(site);
        return getDataTable(list);
    }

    /**
     * 采集页面
     */
    @PreAuthorize("@ss.hasPermi('abroad:crawl:list')")
    @GetMapping("/page/list")
    public TableDataInfo pageList(AbrCrawlPage page)
    {
        startPage();
        List<AbrCrawlPage> list = crawlService.selectPageList(page);
        return getDataTable(list);
    }

    /**
     * 页面详情（含逐字段结果）
     */
    @PreAuthorize("@ss.hasPermi('abroad:crawl:list')")
    @GetMapping("/page/{pageId}")
    public AjaxResult page(@PathVariable Long pageId)
    {
        return success(crawlService.selectPage(pageId));
    }

    /**
     * 立即为一所学校发现项目页（后台执行）
     */
    @PreAuthorize("@ss.hasPermi('abroad:crawl:run')")
    @Log(title = "项目采集发现", businessType = BusinessType.OTHER)
    @PostMapping("/discover/{schoolId}")
    public AjaxResult discover(@PathVariable Long schoolId)
    {
        executor.execute(() -> crawlService.discoverSchool(schoolId));
        return success("已开始发现，稍后刷新查看");
    }

    /**
     * 立即抓取一轮到期页面（后台执行）
     */
    @PreAuthorize("@ss.hasPermi('abroad:crawl:run')")
    @Log(title = "项目采集抓取", businessType = BusinessType.OTHER)
    @PostMapping("/fetch")
    public AjaxResult fetch()
    {
        executor.execute(crawlService::fetchDue);
        return success("已开始抓取，稍后刷新查看");
    }

    /**
     * 立即重新抓取并抽取一个页面
     */
    @PreAuthorize("@ss.hasPermi('abroad:crawl:run')")
    @Log(title = "项目采集重抓", businessType = BusinessType.OTHER)
    @PostMapping("/page/{pageId}/process")
    public AjaxResult process(@PathVariable Long pageId)
    {
        return success(Map.of("status", crawlService.processPage(pageId)));
    }

    /**
     * 暂停 / 恢复站点
     */
    @PreAuthorize("@ss.hasPermi('abroad:crawl:run')")
    @Log(title = "项目采集站点", businessType = BusinessType.UPDATE)
    @PutMapping("/site/{siteId}/status")
    public AjaxResult siteStatus(@PathVariable Long siteId, @RequestBody Map<String, Boolean> body)
    {
        return toAjax(crawlService.pauseSite(siteId, Boolean.TRUE.equals(body.get("pause"))));
    }
}
