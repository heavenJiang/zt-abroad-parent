package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppPlanItemBody;
import com.ygb.abroad.domain.vo.AppPlanItemVo;
import com.ygb.abroad.domain.vo.AppPlanVo;

/**
 * 我的选校单 服务层（M4-02：加入、查看、移除；排序、逐项达标、失衡提示在 M4-10）
 * 
 * @author ygb
 */
public interface IAbrPlanService
{
    /** 每位访客选校单最多条目数 */
    public static final int MAX_ITEMS = 100;

    /**
     * 查询本人的选校单
     * 
     * @param visitor 访客
     * @return 条目（按加入先后）
     */
    public List<AppPlanItemVo> selectPlanItems(AbrVisitor visitor);

    /**
     * 加入选校单（已在单中时更新来源与档位）
     * 
     * @param visitor 访客
     * @param body 请求
     * @return 加入后的选校单
     */
    public List<AppPlanItemVo> addPlanItem(AbrVisitor visitor, AppPlanItemBody body);

    /**
     * 从选校单移除
     * 
     * @param visitor 访客
     * @param programId 项目ID
     * @return 移除后的选校单
     */
    public List<AppPlanItemVo> removePlanItem(AbrVisitor visitor, Long programId);

    /**
     * 本人的选校单：按最新背景档案逐个项目计算当前分组与逐项达标，并给出档位分布提示
     * 
     * @param visitor 访客
     * @return 选校单
     */
    public AppPlanVo selectPlan(AbrVisitor visitor);
}
