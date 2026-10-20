package com.ygb.abroad.growth.card;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import javax.imageio.ImageIO;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * 二维码矩阵生成
 *
 * @author ygb
 */
public final class QrCodes
{
    private QrCodes()
    {
    }

    /**
     * 生成二维码矩阵（纠错等级 M，留 1 格静区）
     *
     * @param content 内容
     * @param size 边长（像素）
     * @return 矩阵
     */
    public static BitMatrix matrix(String content, int size)
    {
        try
        {
            return new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1, EncodeHintType.CHARACTER_SET, "UTF-8"));
        }
        catch (WriterException e)
        {
            throw new IllegalArgumentException("二维码内容过长", e);
        }
    }

    /**
     * 生成二维码 PNG（黑白）
     *
     * @param content 内容
     * @param size 边长（像素）
     * @return PNG 字节
     */
    public static byte[] png(String content, int size)
    {
        BitMatrix m = matrix(content, size);
        BufferedImage img = new BufferedImage(m.getWidth(), m.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < m.getHeight(); y++)
        {
            for (int x = 0; x < m.getWidth(); x++)
            {
                img.setRGB(x, y, m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
