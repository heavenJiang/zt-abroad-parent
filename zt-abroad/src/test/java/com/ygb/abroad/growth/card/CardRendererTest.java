package com.ygb.abroad.growth.card;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

/**
 * 图卡渲染测试：PNG 尺寸、内容越多图越高、同一输入同一输出、二维码可识别
 *
 * @author ygb
 */
class CardRendererTest
{
    private static final CardRenderer RENDERER = new CardRenderer(null);

    private static final String URL = "https://m.example.com/?ch=share&ct=Ab3dE5fG7h";

    private static Card card(int schools)
    {
        List<String> lines = IntStream.range(0, schools).mapToObj(i -> "学校" + i).toList();
        return new Card("某留学工作室", "英国 · 管理 · 硕士", "主攻档 6 个项目",
                List.of(new Card.Section("冲刺", "共 2 个项目", lines), new Card.Section("主攻", "共 6 个项目", lines)), List.of(), URL,
                "扫码测测你能申哪些学校", "结果仅供参考，不代表录取承诺");
    }

    @Test
    void rendersPngDeterministically() throws IOException
    {
        byte[] png = RENDERER.png(card(3));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(CardRenderer.WIDTH, image.getWidth());
        assertTrue(RENDERER.render(card(1)).getHeight() < image.getHeight());
        assertArrayEquals(png, RENDERER.png(card(3)));
    }

    @Test
    void qrCodeDecodesToShareUrl() throws Exception
    {
        BufferedImage image = RENDERER.render(card(2));
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));
        assertEquals(URL, new QRCodeReader().decode(bitmap).getText());
    }

    @Test
    void wrapsLongTextAndPicksFont()
    {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        FontMetrics fm = img.createGraphics().getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        List<String> lines = CardRenderer.wrap("abcdefghijklmnopqrstuvwxyz".repeat(4), fm, 200);
        assertTrue(lines.size() > 1);
        assertEquals("abcdefghijklmnopqrstuvwxyz".repeat(4), String.join("", lines));
        assertFalse(RENDERER.fontFamily().isBlank());
        assertThrows(IllegalArgumentException.class, () -> QrCodes.matrix("x".repeat(5000), 100));
    }
}
