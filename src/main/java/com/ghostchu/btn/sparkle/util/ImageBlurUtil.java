package com.ghostchu.btn.sparkle.util;

import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class ImageBlurUtil {

    /**
     * 对图像进行马赛克处理
     *
     * @param srcBuffer 原图像
     * @param blockSize 马赛克块大小
     * @return 马赛克后的图像
     */
    @NotNull
    public static BufferedImage blur(@NotNull BufferedImage srcBuffer, int blockSize) {
        if (blockSize < 1) {
            blockSize = 1;
        }

        int width = srcBuffer.getWidth();
        int height = srcBuffer.getHeight();
        int type = srcBuffer.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : srcBuffer.getType();

        BufferedImage mosaicImage = new BufferedImage(width, height, type);
        Graphics2D g = mosaicImage.createGraphics();

        for (int y = 0; y < height; y += blockSize) {
            for (int x = 0; x < width; x += blockSize) {
                // 计算当前块的实际大小
                int w = Math.min(blockSize, width - x);
                int h = Math.min(blockSize, height - y);

                // 取块中心点的颜色
                int centerX = x + (w / 2);
                int centerY = y + (h / 2);
                int rgb = srcBuffer.getRGB(centerX, centerY);

                g.setColor(new Color(rgb, true));
                g.fillRect(x, y, w, h);
            }
        }

        g.dispose();
        return mosaicImage;
    }
}
