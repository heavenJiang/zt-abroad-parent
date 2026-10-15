package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppCalendarVo;
import com.ygb.abroad.domain.vo.AppMaterialBody;
import com.ygb.abroad.domain.vo.AppMaterialsVo;
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

    /**
     * 本人的申请日历：选校单中正常项目的申请轮次与截止日
     * 
     * @param visitor 访客
     * @return 日历
     */
    public AppCalendarVo selectCalendar(AbrVisitor visitor);

    /**
     * 生成日历文件的一次性下载链接（10 分钟内有效；手机日历打开链接时无法带访客令牌请求头）
     * 
     * @param visitor 访客
     * @return path 下载路径、expiresInSeconds 有效秒数
     */
    public Map<String, Object> createCalendarLink(AbrVisitor visitor);

    /**
     * 按下载链接生成日历文件
     * 
     * @param key 下载链接中的 key
     * @return iCalendar 文本
     */
    public String renderCalendarIcs(String key);

    /**
     * 本人的材料清单：按选校单项目的申请要求汇总材料与完成情况
     * 
     * @param visitor 访客
     * @return 材料清单
     */
    public AppMaterialsVo selectMaterials(AbrVisitor visitor);

    /**
     * 勾选某项材料的完成状态（材料须在当前清单中）
     * 
     * @param visitor 访客
     * @param body 材料键与完成状态
     * @return 更新后的材料清单
     */
    public AppMaterialsVo updateMaterial(AbrVisitor visitor, AppMaterialBody body);
}
