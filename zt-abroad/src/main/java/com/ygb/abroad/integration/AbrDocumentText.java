package com.ygb.abroad.integration;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * 上传文档的类型识别与文字提取（纯函数，不访问网络）
 * <p>
 * 类型按文件头判断，不信任扩展名与 Content-Type；PDF 取文字层，扫描件渲染成图片交给文字识别。
 * </p>
 *
 * @author ygb
 */
public final class AbrDocumentText
{
    /** 扫描件渲染分辨率（DPI） */
    private static final float OCR_DPI = 150f;

    private AbrDocumentText()
    {
    }

    /**
     * 按文件头判断类型
     *
     * @param bytes 文件内容
     * @return pdf / png / jpg；无法识别返回 null
     */
    public static String detectType(byte[] bytes)
    {
        if (bytes == null || bytes.length < 8)
        {
            return null;
        }
        if (bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-')
        {
            return "pdf";
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G')
        {
            return "png";
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF)
        {
            return "jpg";
        }
        return null;
    }

    /**
     * 取 PDF 的文字层
     *
     * @param bytes PDF 内容
     * @param maxPages 最多读取的页数
     * @return 文字（扫描件可能为空字符串）
     * @throws IOException PDF 损坏或加密
     */
    public static String pdfText(byte[] bytes, int maxPages) throws IOException
    {
        try (PDDocument doc = Loader.loadPDF(bytes))
        {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(Math.max(1, Math.min(maxPages, doc.getNumberOfPages())));
            return stripper.getText(doc);
        }
    }

    /**
     * 把 PDF 前几页渲染为 PNG（扫描件交给文字识别用）
     *
     * @param bytes PDF 内容
     * @param maxPages 最多渲染的页数
     * @return 每页一张 PNG
     * @throws IOException PDF 损坏或加密
     */
    public static List<byte[]> pdfPageImages(byte[] bytes, int maxPages) throws IOException
    {
        List<byte[]> pages = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(bytes))
        {
            PDFRenderer renderer = new PDFRenderer(doc);
            int n = Math.min(maxPages, doc.getNumberOfPages());
            for (int i = 0; i < n; i++)
            {
                BufferedImage image = renderer.renderImageWithDPI(i, OCR_DPI, ImageType.GRAY);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                pages.add(out.toByteArray());
            }
        }
        return pages;
    }

    /**
     * 有效字数（去掉空白后的字符数）
     *
     * @param text 文字
     * @return 字数
     */
    public static int meaningfulChars(String text)
    {
        if (text == null)
        {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < text.length(); i++)
        {
            if (!Character.isWhitespace(text.charAt(i)))
            {
                n++;
            }
        }
        return n;
    }
}
