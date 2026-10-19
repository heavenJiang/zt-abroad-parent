package com.ygb.abroad.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.service.IAbrLeadService;

/**
 * 定时任务：线索收口（sys_job：abrLeadTask.ingest()，每分钟，禁止并发）
 *
 * @author ygb
 */
@Component("abrLeadTask")
public class AbrLeadTask
{
    @Autowired
    private IAbrLeadService leadService;

    /**
     * 读取新的业务事件，有学生账号的归到对应线索（没有时新建）
     */
    public void ingest()
    {
        leadService.ingestEvents();
    }
}
