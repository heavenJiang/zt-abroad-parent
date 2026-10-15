package com.ygb.abroad.growth.card;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import com.google.zxing.common.BitMatrix;

/**
 * 图卡渲染器：Java2D 确定性排版，输出 PNG（宽 750 像素，高度随内容）
 *
 * 字体优先用配置的字体族，其次自动选第一个能显示中文的字体；服务器需安装中文字体（Dockerfile 已安装文泉驿微米黑）。
 * 同一输入同一输出，不调用大模型。
 *
 * @author ygb
 */
public final class CardRenderer
{
    /** 图宽 */
    public static final int WIDTH = 750;

    private static final int PAD = 48;

    private static final int QR_SIZE = 180;

    private static final Color BG = new Color(0xF6F4EF);

    private static final Color INK = new Color(0x1F2A37);

    private static final Color MUTED = new Color(0x6B7280);

    private static final Color LINE = new Color(0xE2DED5);

    private static final Color ACCENT = new Color(0x1D4ED8);

    /** 用于检测字体能否显示中文的样例 */
    private static final String CJK_SAMPLE = "测评冲刺主攻保底";

    private final String fontFamily;

    /**
     * 创建渲染器
     *
     * @param preferredFamily 首选字体族（为空或不能显示中文时自动选择）
     */
    public CardRenderer(String preferredFamily)
    {
        this.fontFamily = resolveFamily(preferredFamily);
    }

    /**
     * 实际使用的字体族
     *
     * @return 字体族
     */
    public String fontFamily()
    {
        return fontFamily;
    }

    /**
     * 渲染为 PNG
     *
     * @param card 图卡
     * @return PNG 字节
     */
    public byte[] png(Card card)
    {
        BufferedImage image = render(card);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 渲染为图像（先量高度再绘制）
     *
     * @param card 图卡
     * @return 图像
     */
    public BufferedImage render(Card card)
    {
        int height = draw(null, card);
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(BG);
            g.fillRect(0, 0, WIDTH, height);
            draw(g, card);
        }
        finally
        {
            g.dispose();
        }
        return image;
    }

    /**
     * 排版；g 为 null 时只计算高度
     */
    private int draw(Graphics2D g, Card card)
    {
        Graphics2D m = g != null ? g : new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        int y = PAD;
        y = text(g, m, card.brand(), font(Font.BOLD, 26), MUTED, PAD, y, WIDTH - 2 * PAD) + 28;
        if (card.kicker() != null && !card.kicker().isBlank())
        {
            y = text(g, m, card.kicker(), font(Font.PLAIN, 30), INK, PAD, y, WIDTH - 2 * PAD) + 10;
        }
        y = text(g, m, card.title(), font(Font.BOLD, 52), INK, PAD, y, WIDTH - 2 * PAD) + 32;
        for (Card.Section s : card.sections())
        {
            if (g != null)
            {
                g.setColor(LINE);
                g.fillRect(PAD, y, WIDTH - 2 * PAD, 2);
            }
            y += 22;
            Font label = font(Font.BOLD, 30);
            int labelBottom = text(g, m, s.label(), label, ACCENT, PAD, y, 200);
            if (s.summary() != null && !s.summary().isBlank())
            {
                Font f = font(Font.PLAIN, 24);
                int w = m.getFontMetrics(f).stringWidth(s.summary());
                text(g, m, s.summary(), f, MUTED, WIDTH - PAD - w, y + 4, w + 1);
            }
            y = labelBottom + 12;
            for (String line : s.lines())
            {
                y = text(g, m, line, font(Font.PLAIN, 30), INK, PAD + 12, y, WIDTH - 2 * PAD - 12) + 8;
            }
            y += 14;
        }
        if (card.extras() != null && !card.extras().isEmpty())
        {
            if (g != null)
            {
                g.setColor(LINE);
                g.fillRect(PAD, y, WIDTH - 2 * PAD, 2);
            }
            y += 22;
            for (String line : card.extras())
            {
                y = text(g, m, line, font(Font.PLAIN, 26), MUTED, PAD, y, WIDTH - 2 * PAD) + 6;
            }
            y += 14;
        }
        if (card.qrContent() != null && !card.qrContent().isBlank())
        {
            y += 16;
            if (g != null)
            {
                g.setColor(Color.WHITE);
                g.fillRect(PAD, y, QR_SIZE, QR_SIZE);
                drawQr(g, QrCodes.matrix(card.qrContent(), QR_SIZE), PAD, y);
            }
            int captionX = PAD + QR_SIZE + 28;
            text(g, m, card.qrCaption(), font(Font.BOLD, 30), INK, captionX, y + 50, WIDTH - PAD - captionX);
            y += QR_SIZE + 24;
        }
        if (card.footnote() != null && !card.footnote().isBlank())
        {
            y = text(g, m, card.footnote(), font(Font.PLAIN, 22), MUTED, PAD, y + 8, WIDTH - 2 * PAD);
        }
        if (g == null)
        {
            m.dispose();
        }
        return y + PAD;
    }

    /**
     * 按宽度自动换行绘制文字，返回最后一行底部的 y
     */
    private static int text(Graphics2D g, Graphics2D measure, String s, Font font, Color color, int x, int y, int maxWidth)
    {
        if (s == null || s.isEmpty())
        {
            return y;
        }
        FontMetrics fm = measure.getFontMetrics(font);
        int lineHeight = (int) Math.ceil(fm.getHeight() * 1.15);
        int cy = y;
        for (String line : wrap(s, fm, maxWidth))
        {
            if (g != null)
            {
                g.setFont(font);
                g.setColor(color);
                g.drawString(line, x, cy + fm.getAscent());
            }
            cy += lineHeight;
        }
        return cy;
    }

    /**
     * 按字符换行（中文无空格，按显示宽度断行）
     */
    static List<String> wrap(String s, FontMetrics fm, int maxWidth)
    {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); )
        {
            int cp = s.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            if (cur.length() > 0 && fm.stringWidth(cur + ch) > maxWidth)
            {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(ch);
            i += Character.charCount(cp);
        }
        if (cur.length() > 0)
        {
            lines.add(cur.toString());
        }
        return lines;
    }

    private static void drawQr(Graphics2D g, BitMatrix matrix, int x, int y)
    {
        g.setColor(INK);
        for (int i = 0; i < matrix.getWidth(); i++)
        {
            for (int j = 0; j < matrix.getHeight(); j++)
            {
                if (matrix.get(i, j))
                {
                    g.fillRect(x + i, y + j, 1, 1);
                }
            }
        }
    }

    private Font font(int style, int size)
    {
        return new Font(fontFamily, style, size);
    }

    /**
     * 选择字体族：首选能显示中文就用首选，否则用第一个能显示中文的已安装字体，都没有用 SansSerif
     */
    static String resolveFamily(String preferred)
    {
        if (preferred != null && !preferred.isBlank() && new Font(preferred, Font.PLAIN, 12).canDisplayUpTo(CJK_SAMPLE) == -1)
        {
            return preferred;
        }
        for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
        {
            if (new Font(family, Font.PLAIN, 12).canDisplayUpTo(CJK_SAMPLE) == -1)
            {
                return family;
            }
        }
        return Font.SANS_SERIF;
    }
}
