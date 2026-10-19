package com.ygb.abroad.service;

import java.util.Map;
import com.ygb.abroad.domain.AbrContent;

/**
 * 内容创作 content_writer 服务层：按选题为账号所在平台写稿并入库（经合规检查，不通过重写 1 次）
 *
 * @author ygb
 */
public interface IAbrContentWriterService
{
    /**
     * 写到期的选题：计划日期从今天起 abroad.content.write-ahead-days 天内、还没写稿的，每轮最多 abroad.content.write-batch 条
     *
     * @param operator 操作人（编排器为 content_writer）
     * @return 结果：写成的内容、跳过与失败的选题及原因
     */
    public Map<String, Object> writeDue(String operator);

    /**
     * 写一条选题
     *
     * @param topicId 选题ID
     * @param operator 操作人
     * @return 写成的内容
     */
    public AbrContent writeTopic(Long topicId, String operator);
}
