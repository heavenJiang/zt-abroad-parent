package com.ygb.abroad.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.service.IAbrOrchestratorService;

/**
 * 定时任务：营销编排器（sys_job：abrOrchestratorTask.dispatch()，每分钟一轮，禁止并发）
 *
 * @author ygb
 */
@Component("abrOrchestratorTask")
public class AbrOrchestratorTask
{
    @Autowired
    private IAbrOrchestratorService orchestratorService;

    /**
     * 执行一轮：按营销日历切换剧本、事件与定时触发、执行到期的编排任务
     */
    public void dispatch()
    {
        orchestratorService.dispatch();
    }
}
