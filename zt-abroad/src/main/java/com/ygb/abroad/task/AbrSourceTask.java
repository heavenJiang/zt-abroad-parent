package com.ygb.abroad.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.service.IAbrSourceArchiveService;

/**
 * 定时任务：源信息存档清理（sys_job：abrSourceTask.purgeExpired()，每天 04:10）
 *
 * @author ygb
 */
@Component("abrSourceTask")
public class AbrSourceTask
{
    @Autowired
    private IAbrSourceArchiveService archiveService;

    /**
     * 删除最近一次看到超过 2 年、且不再被当前字段来源引用的存档
     */
    public void purgeExpired()
    {
        archiveService.purgeExpired();
    }
}
