package com.ghostchu.btn.sparkle.machinelearn.smile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.entity.BanHistory;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import smile.classification.LogisticRegression;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
public class SmileML {
    @Value("${sparkle.machine-learning.enabled}")
    private boolean enabled;
    private double learningRate;
    private static final int VECTOR_SIZE = 2048;
    private LogisticRegression model;
    @Autowired
    private ObjectMapper objectMapper;
    private static final String MODEL_PATH = "data/ai_model.bin";
    private BlockingQueue<LearningTask> trainingQueue;

    public SmileML(@Value("${sparkle.machine-learning.queue-capacity}") int queueCapacity, @Value("${sparkle.machine-learning.learning-rate}") double learningRate) {
        this.learningRate =  learningRate;
        trainingQueue = new LinkedBlockingQueue<>(queueCapacity);
        if (Files.exists(Paths.get(MODEL_PATH))) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(MODEL_PATH))) {
                this.model = (LogisticRegression) ois.readObject();
                log.info("AI model loaded from disk.");
            } catch (Exception e) {
                log.error("Failed to load model, resetting...", e);
                createNewModel();
            }
        } else {
            createNewModel();
        }
        startTrainer();
    }

    private void createNewModel() {
        this.model = LogisticRegression.fit(new double[2][VECTOR_SIZE], new int[]{0, 1});
        this.model.setLearningRate(learningRate);
        log.info("AI model initialized from scratch.");
    }

    public double predict(@NotNull SwarmTracker swarmTracker) {
        double[] features = LearningData.fromSwarmTracker(objectMapper, swarmTracker).extractIdentity();
        double[] posteriors = new double[2];
        model.predict(features, posteriors);
        return posteriors[1];
    }

    public double predict(@NotNull BanHistory banHistory) {
        double[] features = LearningData.fromBanHistory(objectMapper, banHistory).extractIdentity();
        double[] posteriors = new double[2];
        model.predict(features, posteriors);
        return posteriors[1];
    }

    public boolean learnFromBanHistory(@NotNull BanHistory banHistory) { // It's bad!
        if (!enabled) return false;
        if (!banHistory.getModuleName().contains("ProgressCheatBlocker") &&
                !banHistory.getModuleName().contains("MultiDialingBlocker")) return false;
        double[] data = LearningData.fromBanHistory(objectMapper, banHistory).extractIdentity();
        return trainingQueue.offer(new LearningTask(data, 1));
    }

    public boolean learnFromSwarmTracker(@NotNull SwarmTracker swarmTracker) { // Unknown!
        if (!enabled) return false;
        double[] data = LearningData.fromSwarmTracker(objectMapper, swarmTracker).extractIdentity();
        return trainingQueue.offer(new LearningTask(data, 0));
    }

    // 2. 启动一个单线程消费逻辑
    public void startTrainer() {
        if (!enabled) return;
        Thread trainer = new Thread(() -> {
            while (true) {
                try {
                    LearningTask task = trainingQueue.take();
                    model.update(task.feature, task.label);
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        trainer.setDaemon(true);
        trainer.start();
    }

    @Scheduled(fixedRate = 3600000) // 1小时执行一次
    @PreDestroy
    public synchronized void saveModel() {
        try {
            Files.createDirectories(Paths.get("data"));
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(MODEL_PATH))) {
                oos.writeObject(this.model);
                log.info("AI model successfully save to disk.");
            }
        } catch (IOException e) {
            log.error("AI model unable to save to disk", e);
        }
    }


    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class LearningData {
        private String peerIp;
        private int peerPort;
        private String peerId;
        private String peerClientName;
        private String peerFlags;
        private double peerProgress;
        private double reporterProgress;
        private long toPeerTraffic;
        private long fromPeerTraffic;
        private String moduleName;
        private String rule;
        private String description;
        private String structuredData;

        private String cityName;
        private String cityCnProvince;
        private String cityCnCity;
        private String cityCnDistricts;
        private String countryIso;
        private Long asNumber;
        private String asNetworkIpAddress;
        private Integer asNetworkPrefixLength;
        private String netType;
        private String isp;

        public double[] extractIdentity() {
            int reserved = 50;
            double[] vector = new double[VECTOR_SIZE];
            vector[0] = peerPort;
            vector[1] = peerProgress;
            vector[2] = reporterProgress;
            vector[3] = Math.log1p(toPeerTraffic) / 20.0;
            vector[4] = Math.log1p(fromPeerTraffic) / 20.0;
            vector[5] = asNumber;
            vector[6] = asNetworkPrefixLength;


            hashTo(vector, "peerIp:" + (peerId != null && peerId.length() > 8 ? peerId.substring(0, 8) : peerId), reserved);
            hashTo(vector, "peerId:" + peerId, reserved);
            hashTo(vector, "peerClientName:" + (peerClientName != null ? peerClientName.replace(" (n/a)", "") : null), reserved);
            hashTo(vector, "peerFlags:" + peerFlags, reserved);
            //hashTo(vector, "moduleName:" + moduleName, reserved);
            //hashTo(vector, "rule:" + rule, reserved);
            //hashTo(vector, "description:" + description, reserved);
            //hashTo(vector, "structuredData:" + structuredData, reserved);
            hashTo(vector, "cityName:" + cityName, reserved);
            hashTo(vector, "cityCnProvince:" + cityCnProvince, reserved);
            hashTo(vector, "cityCnCity:" + cityCnCity, reserved);
            hashTo(vector, "cityCnDistricts:" + cityCnDistricts, reserved);
            hashTo(vector, "countryIso:" + countryIso, reserved);
            hashTo(vector, "asNetworkIpAddress:" + asNetworkIpAddress, reserved);
            hashTo(vector, "netType:" + netType, reserved);
            hashTo(vector, "isp:" + isp, reserved);
            return vector;
        }

        public static LearningData fromBanHistory(ObjectMapper objectMapper, BanHistory banHistory) {
            LearningData data = new LearningData();
            data.peerIp = banHistory.getPeerIp().getHostAddress();
            data.peerPort = banHistory.getPeerPort();
            data.peerId = banHistory.getPeerId();
            data.peerClientName = banHistory.getPeerClientName();
            data.peerFlags = banHistory.getPeerFlags();
            data.peerProgress = banHistory.getPeerProgress();
            data.reporterProgress = banHistory.getReporterProgress();
            data.toPeerTraffic = banHistory.getToPeerTraffic();
            data.fromPeerTraffic = banHistory.getFromPeerTraffic();
            data.moduleName = banHistory.getModuleName();
            data.rule = banHistory.getRule();
            data.description = banHistory.getDescription();
            try {
                data.structuredData = objectMapper.writeValueAsString(banHistory.getStructuredData());
            } catch (JsonProcessingException e) {
                log.error("Unable to serialize structured data", e);
            }
            if (banHistory.getPeerGeoip() != null) {
                data.cityName = banHistory.getPeerGeoip().getCityName();
                data.cityCnProvince = banHistory.getPeerGeoip().getCityCnProvince();
                data.cityCnCity = banHistory.getPeerGeoip().getCityCnCity();
                data.cityCnDistricts = banHistory.getPeerGeoip().getCityCnDistricts();
                data.countryIso = banHistory.getPeerGeoip().getCountryIso();
                data.asNumber = banHistory.getPeerGeoip().getAsNumber();
                data.asNetworkIpAddress = banHistory.getPeerGeoip().getAsNetworkIpAddress();
                data.asNetworkPrefixLength = banHistory.getPeerGeoip().getAsNetworkPrefixLength();
                data.netType = banHistory.getPeerGeoip().getNetType();
                data.isp = banHistory.getPeerGeoip().getIsp();
            }
            return data;
        }

        public static LearningData fromSwarmTracker(ObjectMapper objectMapper, SwarmTracker swarmTracker) {
            LearningData data = new LearningData();
            data.peerIp = swarmTracker.getPeerIp().getHostAddress();
            data.peerPort = swarmTracker.getPeerPort();
            data.peerId = swarmTracker.getPeerId();
            data.peerClientName = swarmTracker.getPeerClientName();
            data.peerFlags = swarmTracker.getFlags();
            data.peerProgress = swarmTracker.getPeerProgress();
            data.reporterProgress = swarmTracker.getUserProgress();
            data.toPeerTraffic = swarmTracker.getToPeerTraffic();
            data.fromPeerTraffic = swarmTracker.getFromPeerTraffic();
            data.moduleName = "null";
            data.rule = "null";
            data.description = "null";
            data.structuredData = "null";

            if (swarmTracker.getPeerGeoip() != null) {
                data.cityName = swarmTracker.getPeerGeoip().getCityName();
                data.cityCnProvince = swarmTracker.getPeerGeoip().getCityCnProvince();
                data.cityCnCity = swarmTracker.getPeerGeoip().getCityCnCity();
                data.cityCnDistricts = swarmTracker.getPeerGeoip().getCityCnDistricts();
                data.countryIso = swarmTracker.getPeerGeoip().getCountryIso();
                data.asNumber = swarmTracker.getPeerGeoip().getAsNumber();
                data.asNetworkIpAddress = swarmTracker.getPeerGeoip().getAsNetworkIpAddress();
                data.asNetworkPrefixLength = swarmTracker.getPeerGeoip().getAsNetworkPrefixLength();
                data.netType = swarmTracker.getPeerGeoip().getNetType();
                data.isp = swarmTracker.getPeerGeoip().getIsp();
            }
            return data;

        }

        private void hashTo(double[] vector, String feature, int offset) {
            if (feature == null) return;
            // 使用 MurmurHash 或简单的 hashCode
            int hash = Math.abs(feature.hashCode());
            int index = offset + (hash % (vector.length - offset));
            vector[index] += 1.0;
        }
    }


    @Data
    @AllArgsConstructor
    private static class LearningTask {
        private double[] feature;
        private int label;
    }
}
