package com.ygb.abroad.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.domain.AbrSiteBuild;
import com.ygb.abroad.service.IAbrSiteService;

/**
 * 定时任务：公开项目页（sys_job：abrSiteTask.refresh()，每 5 分钟，禁止并发）
 *
 * @author ygb
 */
@Component("abrSiteTask")
public class AbrSiteTask
{
    @Autowired
    private IAbrSiteService siteService;

    /**
     * 院校库或日期变化时重新生成，只重写变化的页面；没有变化时跳过
     */
    public void refresh()
    {
        siteService.refresh(AbrSiteBuild.TRIGGER_SCHEDULE, false, "system");
    }
}
