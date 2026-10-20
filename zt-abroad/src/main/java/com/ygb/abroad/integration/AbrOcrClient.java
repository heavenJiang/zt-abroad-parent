package com.ygb.abroad.integration;

/**
 * 文字识别（OCR）接口：图片简历、扫描版 PDF 先识别成文字，去标识化后再交给大模型（图片原件不送大模型）
 * <p>
 * 实现为内嵌的开源识别服务 {@link AbrRapidOcrClient}（MA-15，同一部署内运行，不涉及第三方受托方）；
 * 未配置 abroad.ocr.url 时没有实现类，图片与扫描件提示学生手动填写。
 * </p>
 *
 * @author ygb
 */
public interface AbrOcrClient
{
    /**
     * 识别一张图片中的文字
     *
     * @param image 图片字节（PNG 或 JPEG）
     * @param ext 扩展名（png / jpg / jpeg）
     * @return 识别出的文字（按行），识别不出返回空字符串
     * @throws AbrFetchException 调用失败（运行时异常）
     */
    public String recognize(byte[] image, String ext);
}
