package com.ygb.abroad.growth;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.service.IAbrContentWriterService;
import com.ygb.common.exception.ServiceException;

/**
 * content_writer 的编排任务处理器：动作 write_topics（选题排入后、每天 08:00），为到期的选题写稿
 *
 * 只写还没写稿的选题，重复执行不会重复写；正在写稿时稍后重试。
 *
 * @author ygb
 */
@Component
public class ContentWriterHandler implements GrowthAgentHandler
{
    static final String AGENT = "content_writer";

    static final String ACTION_WRITE = "write_topics";

    @Autowired
    private IAbrContentWriterService writerService;

    @Override
    public String agentCode()
    {
        return AGENT;
    }

    @Override
    public GrowthTaskOutcome handle(AbrGrowthTask task)
    {
        if (!ACTION_WRITE.equals(task.getAction()))
        {
            return GrowthTaskOutcome.skipped("不支持的动作：" + task.getAction());
        }
        Map<String, Object> result;
        try
        {
            result = writerService.writeDue(AGENT);
        }
        catch (ServiceException e)
        {
            return GrowthTaskOutcome.retry(e.getMessage());
        }
        return GrowthTaskOutcome.done(result);
    }
}
