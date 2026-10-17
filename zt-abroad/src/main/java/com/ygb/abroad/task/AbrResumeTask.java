package com.ygb.abroad.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.service.IAbrResumeService;

/**
 * 定时任务：简历 / 成绩单原件到期删除（sys_job：abrResumeTask.purgeExpired()，每天 03:20）
 *
 * @author ygb
 */
@Component("abrResumeTask")
public class AbrResumeTask
{
    @Autowired
    private IAbrResumeService resumeService;

    /**
     * 删除保存期满的原件（解析出的建议值保留）
     */
    public void purgeExpired()
    {
        resumeService.purgeExpiredResumes();
    }
}
