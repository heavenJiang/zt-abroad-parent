package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrSiteBuild;

/**
 * 公开项目页（M7-08）服务层：院校库确定性渲染为静态 HTML、增量写入站点目录、站点地图与收录提交
 *
 * @author ygb
 */
public interface IAbrSiteService
{
    /**
     * 检查并生成：院校库与日期都没变时跳过（force 为 true 时总是生成）；只重写内容变化的文件，删除不再存在的页面
     *
     * @param triggerType 触发方式（schedule / manual）
     * @param force 是否强制生成
     * @param operator 操作人
     * @return 生成记录；跳过时为 null
     */
    public AbrSiteBuild refresh(String triggerType, boolean force, String operator);

    /**
     * 查询生成记录
     *
     * @param build 查询条件
     * @return 记录集合
     */
    public List<AbrSiteBuild> selectAbrSiteBuildList(AbrSiteBuild build);

    /**
     * 按ID查询生成记录
     *
     * @param buildId 记录ID
     * @return 记录
     */
    public AbrSiteBuild selectAbrSiteBuildById(Long buildId);

    /**
     * 站点概况：目录、地址、各类页面数、最近一次生成、收录提交是否配置、院校库是否有未生成的变化
     *
     * @return 概况
     */
    public Map<String, Object> status();

    /**
     * 预览一个页面（按当前院校库渲染，不写文件）
     *
     * @param url 页面地址（如 program/12、country/uk，首页为空）
     * @return HTML
     */
    public String preview(String url);
}
