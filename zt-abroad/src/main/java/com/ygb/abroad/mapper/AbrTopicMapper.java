package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrTopic;

/**
 * 选题排期 数据层
 *
 * @author ygb
 */
public interface AbrTopicMapper
{
    /**
     * 查询选题列表
     *
     * @param topic 查询条件（账号、状态、来源、类型精确；标题模糊；params.beginDate / endDate 计划日期区间）
     * @return 选题集合
     */
    public List<AbrTopic> selectAbrTopicList(AbrTopic topic);

    /**
     * 按ID查询选题
     *
     * @param topicId 选题ID
     * @return 选题
     */
    public AbrTopic selectAbrTopicById(Long topicId);

    /**
     * 新增选题
     *
     * @param topic 选题
     * @return 结果
     */
    public int insertAbrTopic(AbrTopic topic);

    /**
     * 修改选题（日期、条次、类型、标题、切入点、工具、剧本、项目、状态、备注）
     *
     * @param topic 选题
     * @return 结果
     */
    public int updateAbrTopic(AbrTopic topic);

    /**
     * 删除还没写稿的选题
     *
     * @param topicIds 选题ID
     * @return 结果
     */
    public int deleteUnwrittenTopicByIds(Long[] topicIds);

    /**
     * 账号在日期区间内每天的选题数（不含已取消）
     *
     * @param accountId 账号ID
     * @param from 开始日期（含）
     * @param to 结束日期（含）
     * @return [{planDate, count}]
     */
    public List<Map<String, Object>> countPerDay(@Param("accountId") Long accountId, @Param("from") Date from, @Param("to") Date to);

    /**
     * 账号在日期区间内各类型的选题数（不含已取消）
     *
     * @param accountId 账号ID
     * @param from 开始日期（含）
     * @param to 结束日期（含）
     * @return [{contentKind, count}]
     */
    public List<Map<String, Object>> countPerKind(@Param("accountId") Long accountId, @Param("from") Date from, @Param("to") Date to);

    /**
     * 当天已用的最大条次（不含已取消）
     *
     * @param accountId 账号ID
     * @param planDate 日期
     * @return 最大条次，没有为 0
     */
    public int maxSlotNo(@Param("accountId") Long accountId, @Param("planDate") Date planDate);

    /**
     * 账号近期的选题标题与内容标题（避免重复）
     *
     * @param accountId 账号ID
     * @param since 起始日期
     * @return 标题
     */
    public List<String> selectRecentTitles(@Param("accountId") Long accountId, @Param("since") Date since);

    /**
     * 取消剧本已不再生效的待写稿选题（AI 与备用选题；员工插入的保留）
     *
     * @param accountId 账号ID
     * @param from 开始日期（含）
     * @param activeCodes 生效的剧本编码（为空时取消全部带剧本的）
     * @param operator 操作人
     * @return 取消条数
     */
    public int cancelStalePlanned(@Param("accountId") Long accountId, @Param("from") Date from, @Param("activeCodes") List<String> activeCodes,
            @Param("operator") String operator);

    /**
     * 选题用的院校库：上架、在线、近期核对过的项目及最新申请要求的截止日与更新时间
     *
     * @param verifiedSince 核对日期下限（北京时间，含）
     * @return [{programId, schoolName, programName, country, deadlines, changedTime}]
     */
    public List<Map<String, Object>> selectCatalogForTopics(@Param("verifiedSince") Date verifiedSince);

    /**
     * 到期待写稿的选题（计划日期在区间内、还没写稿、账号在用且未暂停），按日期、条次排序
     *
     * @param from 开始日期（含）
     * @param to 结束日期（含）
     * @param limit 最多条数
     * @return 选题
     */
    public List<AbrTopic> selectDueTopics(@Param("from") Date from, @Param("to") Date to, @Param("limit") int limit);

    /**
     * 记为已写稿（只更新还没写稿的）
     *
     * @param topicId 选题ID
     * @param contentId 内容ID
     * @param operator 操作人
     * @return 影响行数
     */
    public int markDrafted(@Param("topicId") Long topicId, @Param("contentId") Long contentId, @Param("operator") String operator);
}
