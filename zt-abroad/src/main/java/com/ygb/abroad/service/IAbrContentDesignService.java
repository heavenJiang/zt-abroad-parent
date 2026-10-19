package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrContent;

/**
 * 内容图卡 服务层（content_designer）：按确定性模板渲染图卡，数字只取院校库
 * 
 * @author ygb
 */
public interface IAbrContentDesignService
{
    /**
     * 可选模板与展示字段
     * 
     * @return 模板编码 → 说明；fields 展示字段
     */
    public Map<String, Object> templates();

    /**
     * 预览一张图卡（不保存）
     * 
     * @param contentId 内容ID（取标题与工具链接；可为 null，此时用 title 且不画二维码）
     * @param template 模板
     * @param paramsJson 参数（JSON 对象：kicker、programIds、fields）
     * @param title 标题（为空用内容标题）
     * @return PNG
     */
    public byte[] preview(Long contentId, String template, String paramsJson, String title);

    /**
     * 渲染内容的全部图卡并保存：图片存公开目录，图卡的引用依据并入内容的引用依据；图卡变化后内容须重新过闸门
     * 
     * @param contentId 内容ID
     * @param specsJson 图卡清单（JSON 数组：template、params、title；为空时按内容现有图卡重新渲染）
     * @param fallback 模板失败时是否改用封面（Agent 调用为 true；员工调用为 false，直接提示错误）
     * @param operator 操作人
     * @return 渲染后的内容
     */
    public AbrContent renderCards(Long contentId, String specsJson, boolean fallback, String operator);

    /**
     * 图卡清单（管理端展示）
     * 
     * @param contentId 内容ID
     * @return 图卡
     */
    public List<Map<String, Object>> cards(Long contentId);
}
