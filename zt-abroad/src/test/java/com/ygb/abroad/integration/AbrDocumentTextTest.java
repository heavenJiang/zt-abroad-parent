package com.ygb.abroad.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/**
 * 文档类型识别与 PDF 文字提取测试
 *
 * @author ygb
 */
class AbrDocumentTextTest
{
    /**
     * 生成测试 PDF：每页一行英文；text 为 null 的页为空白页（模拟扫描件没有文字层）
     */
    static byte[] pdf(String... pages) throws IOException
    {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            for (String text : pages)
            {
                PDPage page = new PDPage();
                doc.addPage(page);
                if (text != null)
                {
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page))
                    {
                        cs.beginText();
                        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        cs.newLineAtOffset(72, 700);
                        cs.showText(text);
                        cs.endText();
                    }
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void detectsTypeByMagicBytes() throws IOException
    {
        assertEquals("pdf", AbrDocumentText.detectType(pdf("x")));
        assertEquals("png", AbrDocumentText.detectType(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A }));
        assertEquals("jpg", AbrDocumentText.detectType(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F' }));
        assertNull(AbrDocumentText.detectType("<html><script>".getBytes(StandardCharsets.UTF_8)));
        assertNull(AbrDocumentText.detectType(new byte[] { '%', 'P' }));
        assertNull(AbrDocumentText.detectType(null));
    }

    @Test
    void extractsPdfTextWithinPageLimit() throws IOException
    {
        byte[] bytes = pdf("Peking University Finance GPA 85.6", "IELTS 7.0", "Page three");
        String two = AbrDocumentText.pdfText(bytes, 2);
        assertTrue(two.contains("Peking University Finance GPA 85.6"), two);
        assertTrue(two.contains("IELTS 7.0"), two);
        assertTrue(!two.contains("Page three"), two);
    }

    @Test
    void scannedPdfHasNoTextAndRendersPages() throws IOException
    {
        byte[] bytes = pdf((String) null, null);
        assertEquals(0, AbrDocumentText.meaningfulChars(AbrDocumentText.pdfText(bytes, 10)));
        List<byte[]> images = AbrDocumentText.pdfPageImages(bytes, 1);
        assertEquals(1, images.size());
        assertEquals("png", AbrDocumentText.detectType(images.get(0)));
    }

    @Test
    void brokenPdfThrows()
    {
        assertThrows(IOException.class, () -> AbrDocumentText.pdfText("%PDF-1.7 broken".getBytes(StandardCharsets.UTF_8), 1));
        assertEquals(3, AbrDocumentText.meaningfulChars(" a b\n c "));
        assertEquals(0, AbrDocumentText.meaningfulChars(null));
    }
}
