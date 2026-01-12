package com.ghostchu.btn.sparkle.util;

import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

public class ImageBlurUtil {

    /**
     * 对图像进行高斯模糊
     *
     * @param srcBuffer 原图像
     * @param radius    模糊半径
     * @return 模糊后的图像
     */
    @NotNull
    public static BufferedImage blur(@NotNull BufferedImage srcBuffer, int radius) {
        int size = radius * 2 + 1;
        float[] data = new float[size * size];

        // 1. 生成高斯核数据（这里简单演示，所有权重平摊，类似均值模糊）
        // 若需严格高斯分布，需使用高斯函数计算每个点的权重
        float sigma = radius / 3.0f;
        float sum = 0;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int x = i - radius;
                int y = j - radius;
                data[i * size + j] = (float) (Math.exp(-(x * x + y * y) / (2 * sigma * sigma)));
                sum += data[i * size + j];
            }
        }

        // 归一化，确保图像亮度不变
        for (int i = 0; i < data.length; i++) {
            data[i] /= sum;
        }

        // 2. 创建卷积核
        Kernel kernel = new Kernel(size, size, data);

        // 3. 应用模糊操作
        // EDGE_NO_OP 表示边缘像素不处理，防止黑边
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        return op.filter(srcBuffer, null);
    }
}
