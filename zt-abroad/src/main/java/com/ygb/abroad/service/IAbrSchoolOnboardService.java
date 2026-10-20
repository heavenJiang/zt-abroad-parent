package com.ygb.abroad.service;

import java.util.Map;

/**
 * 院校自动建档 服务层（M2-12：美国全国性大学；M2-10 榜单建档复用）
 * 
 * @author ygb
 */
public interface IAbrSchoolOnboardService
{
    /**
     * 后台建立 / 更新美国全国性大学学校档案：官方记录取自美国教育部 College Scorecard（存档），官网首页在 robots 允许时抓取存档
     *
     * @param operator 操作人
     * @return 是否已开始（已有任务在运行时为 false）
     */
    public boolean startUsNational(String operator);

    /**
     * 最近一次建档的进度与结果
     *
     * @return status（running / done / failed）、计数与逐校问题
     */
    public Map<String, Object> status();
}
