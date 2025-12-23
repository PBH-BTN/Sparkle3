package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.entity.IPDBMeasure;
import com.ghostchu.btn.sparkle.entity.Torrent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface IIPDBMeasureService extends IService<IPDBMeasure> {

   boolean scheduleMeasure(@NotNull InetAddress address);

   @Nullable IPDBMeasure findClosestSuccessMeasure(@NotNull String address);

   @NotNull List<IPDBMeasure> findMeasures(@NotNull String address);

   IPDBMeasure findClosestMeasure(String hostAddress);
}
