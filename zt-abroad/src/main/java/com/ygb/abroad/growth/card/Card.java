package com.ygb.abroad.growth.card;

import java.util.List;

/**
 * 图卡（确定性模板的数据）：定位海报、选校单分享卡、内容引擎图卡共用同一结构与渲染器
 *
 * 图卡上的每个数字、校名都由调用方从引擎结果或院校库直接取值，渲染器只负责排版，不生成任何内容。
 *
 * @param brand 品牌名（顶部）
 * @param kicker 小标题（如“英国 · 管理 · 硕士”）
 * @param title 大标题（如“主攻档 6 所”）
 * @param sections 分组（如冲刺 / 主攻 / 保底各一组）
 * @param extras 附加行（学生主动选择显示的信息；为空不显示）
 * @param qrContent 二维码内容（链接；为空不画二维码）
 * @param qrCaption 二维码旁说明
 * @param footnote 页脚说明（免责、数据日期）
 * @author ygb
 */
public record Card(String brand, String kicker, String title, List<Section> sections, List<String> extras, String qrContent, String qrCaption,
        String footnote)
{
    /**
     * 分组
     *
     * @param label 标签（如“主攻”）
     * @param summary 标签右侧说明（如“共 6 所”）
     * @param lines 行（如校名）
     */
    public record Section(String label, String summary, List<String> lines)
    {
    }
}
