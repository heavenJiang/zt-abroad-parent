package com.ygb.abroad.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.service.IAbrProgramCrawlService;

/**
 * 定时任务：项目自动采集（sys_job：abrCrawlTask.discover() 每天 02:30；abrCrawlTask.fetch() 每 2 分钟）
 *
 * @author ygb
 */
@Component("abrCrawlTask")
public class AbrCrawlTask
{
    @Autowired
    private IAbrProgramCrawlService crawlService;

    /**
     * 为到期的站点从站点地图与课程目录发现项目页
     */
    public void discover()
    {
        crawlService.discoverDue();
    }

    /**
     * 按站点抓取间隔低频抓取到期页面，存档、抽取、写入院校库
     */
    public void fetch()
    {
        crawlService.fetchDue();
    }
}
