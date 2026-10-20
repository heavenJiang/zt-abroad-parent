package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrReward;

/**
 * 奖励 数据层
 *
 * @author ygb
 */
public interface AbrRewardMapper
{
    /**
     * 写奖励（去重键已存在时不写）
     *
     * @param reward 奖励
     * @return 行数
     */
    public int insertReward(AbrReward reward);

    /**
     * 查询奖励（appUserId、rewardType、status）
     *
     * @param reward 条件
     * @return 列表
     */
    public List<AbrReward> selectRewardList(AbrReward reward);

    /**
     * 查询奖励
     *
     * @param rewardId 奖励ID
     * @return 奖励
     */
    public AbrReward selectRewardById(Long rewardId);

    /**
     * 改状态（只改当前为 fromStatus 的；确认发放时记确认人与时间，使用时记使用时间）
     *
     * @param rewardId 奖励ID
     * @param fromStatus 原状态
     * @param toStatus 新状态
     * @param operator 操作人
     * @return 行数
     */
    public int updateStatus(@Param("rewardId") Long rewardId, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
            @Param("operator") String operator);
}
