package com.ygb.abroad.service;

import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppQuickBody;
import com.ygb.abroad.domain.vo.AppQuickFormVo;
import com.ygb.abroad.domain.vo.AppQuickResultVo;

/**
 * 30 秒快测 服务层（入口工具：本科学校、均分、目标国家 / 地区、专业方向 → 各国家 / 地区的冲 / 稳 / 保、代表校、关键差距）
 * 
 * @author ygb
 */
public interface IAbrQuickToolService
{
    /**
     * 快测输入页：题目与国家 / 地区开放情况
     * 
     * @param degree 学段（为空按硕士）
     * @return 输入页定义
     */
    public AppQuickFormVo selectForm(String degree);

    /**
     * 快测计算：校验答案 → 归一化 → 引擎快测模式 → 留痕；答案写入访客渐进档案（完整测评据此预填）
     * 
     * @param visitor 访客
     * @param body 请求
     * @return 学生可见结果
     */
    public AppQuickResultVo quick(AbrVisitor visitor, AppQuickBody body);
}
