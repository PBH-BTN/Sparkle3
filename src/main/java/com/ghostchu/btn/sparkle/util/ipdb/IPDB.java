package com.ghostchu.btn.sparkle.util.ipdb;

import com.ghostchu.btn.sparkle.SparkleApplication;
import com.ghostchu.btn.sparkle.util.ipdb.geocn.GeoCN1;
import com.ghostchu.btn.sparkle.util.ipdb.geocn.GeoCN2;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.maxmind.db.*;
import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import io.sentry.Sentry;
import lombok.Cleanup;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Okio;
import org.jetbrains.annotations.NotNull;
import org.tukaani.xz.XZInputStream;

import java.io.*;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Slf4j
public final class IPDB implements AutoCloseable {
    private final File mmdbCityFile;
    private final File mmdbASNFile;
    private final boolean autoUpdate;
    private final File mmdbGeoCNFile;
    private final OkHttpClient httpClient;
    @Getter
    private DatabaseReader mmdbCity;
    @Getter
    private DatabaseReader mmdbASN;
    private List<String> languageTag;
    private GeoCN2 geoCN2;
    private GeoCN1 geoCN1;

    public IPDB(File dataFolder, String accountId, String licenseKey, String databaseCity, String databaseASN, String databaseGeoCN, boolean autoUpdate, String userAgent) throws IllegalArgumentException, IOException {
//        this.dataFolder = dataFolder;
//        this.accountId = accountId;
//        this.licenseKey = licenseKey;
        File directory = new File(dataFolder, "geoip");
        directory.mkdirs();
        this.mmdbCityFile = new File(directory, "GeoIP-City.mmdb");
        this.mmdbASNFile = new File(directory, "GeoIP-ASN.mmdb");
        this.mmdbGeoCNFile = new File(directory, "GeoCN.mmdb");
        this.autoUpdate = autoUpdate;
//        this.userAgent = userAgent;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofMinutes(3))
                .callTimeout(Duration.ofMinutes(3))
                .followRedirects(true)
                .authenticator((route, response) -> {
                    if (response.request().header("Authorization") != null) {
                        return null; // 已经尝试过认证，不再重试
                    }
                    String credential = Credentials.basic(accountId, licenseKey);
                    return response.request().newBuilder().header("Authorization", credential).build();
                })
                .build();
        if (needUpdateMMDB(mmdbCityFile)) {
            updateMMDB(databaseCity, mmdbCityFile);
        }
        if (needUpdateMMDB(mmdbASNFile)) {
            updateMMDB(databaseASN, mmdbASNFile);
        }
        if (needUpdateMMDB(mmdbGeoCNFile)) {
            updateMMDB(databaseGeoCN, mmdbGeoCNFile);
        }
        loadMMDB();
    }

    public IPGeoData query(InetAddress address) {
        IPGeoData geoData = new IPGeoData();
        try {
            geoData.setAs(queryAS(address));
        } catch (Exception e) {
            log.debug("Unable to query AS", e);
        }
        try {
            geoData.setCountry(queryCountry(address));
        } catch (Exception e) {
            log.debug("Unable to query Country", e);
        }
        try {
            geoData.setCity(queryCity(address));
        } catch (Exception e) {
            log.debug("Unable to query City", e);
        }
        try {
            geoData.setNetwork(queryNetwork(address));
        } catch (Exception e) {
            log.debug("Unable to query Network", e);
        }
        if (geoData.getCountry() != null && geoData.getCountry().getIso() != null) {
            String iso = geoData.getCountry().getIso();
            if ("CN".equalsIgnoreCase(iso) || "TW".equalsIgnoreCase(iso)
                    || "HK".equalsIgnoreCase(iso) || "MO".equalsIgnoreCase(iso)) {
                queryGeoCN(address, geoData);
            }
        }
        return geoData;
    }

    private void queryGeoCN(InetAddress address, IPGeoData geoData) {
        try {
            var data = geoCN2.query(address);
            if (data != null) {
                geoData.mergeFrom(data, true);
            }
        } catch (IllegalStateException e) {
            try {
                var data = geoCN1.query(address);
                if (data != null) {
                    geoData.mergeFrom(data, true);
                }
            } catch (IOException ioe1) {
                log.error("Unable to query GeoCN", ioe1);
            }
        } catch (IOException ioe) {
            log.error("Unable to query GeoCN", ioe);
        }
    }

    private IPGeoData.NetworkData queryNetwork(InetAddress address) {
        if (mmdbASN == null) {
            return null;
        }
        try {
            IPGeoData.NetworkData networkData = new IPGeoData.NetworkData();
            AsnResponse asnResponse = mmdbASN.asn(address);
            networkData.setIsp(asnResponse.getAutonomousSystemOrganization());
            networkData.setNetType(null);
            return networkData;
        } catch (Exception e) {
            log.error("Unable to query Network", e);
            return null;
        }
    }


    private IPGeoData.CityData queryCity(InetAddress address) {
        if (mmdbCity == null) {
            return null;
        }
        try {
            IPGeoData.CityData cityData = new IPGeoData.CityData();
            //IPGeoData.CityData.LocationData locationData = new IPGeoData.CityData.LocationData();
            CityResponse cityResponse = mmdbCity.city(address);
            City city = cityResponse.getCity();
//            Location location = cityResponse.location();
            cityData.setName(city.getName());
            cityData.setIso(city.getGeoNameId());
//            locationData.setTimeZone(location.timeZone());
//            locationData.setLongitude(location.longitude());
//            locationData.setLatitude(location.latitude());
//            locationData.setAccuracyRadius(location.accuracyRadius());
//            cityData.setLocation(locationData);
            return cityData;
        } catch (Exception e) {
            log.error("Unable to query City", e);
            return null;
        }
    }

    private IPGeoData.CountryData queryCountry(InetAddress address) {
        if (mmdbCity == null) {
            return null;
        }
        try {
            IPGeoData.CountryData countryData = new IPGeoData.CountryData();
            CountryResponse countryResponse = mmdbCity.country(address);
            Country country = countryResponse.getCountry();
            countryData.setIso(country.getIsoCode());
            String countryRegionName = country.getName();
            // 对 TW,HK,MO 后处理，偷个懒
            var code = languageTag.getFirst();
            code = code.toLowerCase(Locale.ROOT).replace("-", "_");
            // 台湾、香港、澳门地区有一个独立 ISO 代码，需要手动处理一下保证符合所在地法律法规
            // 这坨代码已经改成一坨了，有时间得写个好点的 :(
            if (("zh_cn".equals(code) || "zh_hk".equals(code) || "zh_mo".equals(code)) && ("TW".equals(country.getIsoCode()) || "HK".equals(country.getIsoCode()) || "MO".equalsIgnoreCase(country.getIsoCode()))) {
                countryRegionName = "中国" + countryRegionName;
            }
            countryData.setName(countryRegionName);
            return countryData;
        } catch (Exception e) {
            log.error("Unable to query Country", e);
            return null;
        }
    }


    private IPGeoData.ASData queryAS(InetAddress address) {
        if (mmdbASN == null) {
            return null;
        }
        try {
            IPGeoData.ASData asData = new IPGeoData.ASData();
            AsnResponse asnResponse = mmdbASN.asn(address);
            IPGeoData.ASData.ASNetwork network = new IPGeoData.ASData.ASNetwork();
            network.setPrefixLength(asnResponse.getNetwork().getPrefixLength());
            network.setIpAddress(asnResponse.getNetwork().getNetworkAddress().getHostAddress());
            asData.setNumber(asnResponse.getAutonomousSystemNumber());
            asData.setOrganization(asnResponse.getAutonomousSystemOrganization());
            asData.setIpAddress(asnResponse.getIpAddress());
            asData.setNetwork(network);
            return asData;
        } catch (Exception e) {
            log.error("Unable to query AS", e);
            return null;
        }
    }

    private void loadMMDB() throws IOException {
        this.languageTag = List.of("en");
        try {
            this.mmdbCity = new DatabaseReader.Builder(mmdbCityFile)
                    .locales(List.of("zh-CN", "en"))
                    .fileMode(Reader.FileMode.MEMORY_MAPPED)
                    .withCache(new MaxMindNodeCache())
                    .build();
        } catch (InvalidDatabaseException exception) {
            mmdbCityFile.delete();
            mmdbCityFile.deleteOnExit();
            log.error("Unable to load GeoIP City database, the file may be corrupted. It has been deleted and will be re-downloaded on next startup.", exception);
        }
        try {
            this.mmdbASN = new DatabaseReader.Builder(mmdbASNFile)
                    .locales(List.of("zh-CN", "en"))
                    .fileMode(Reader.FileMode.MEMORY_MAPPED)
                    .withCache(new MaxMindNodeCache())
                    .build();
        } catch (InvalidDatabaseException exception) {
            mmdbASNFile.delete();
            mmdbASNFile.deleteOnExit();
            log.error("Unable to load GeoIP ASN database, the file may be corrupted. It has been deleted and will be re-downloaded on next startup.", exception);
        }
        try {
            @Cleanup
            var divisionReader = new InputStreamReader(SparkleApplication.class.getResourceAsStream("/ok_data_level3.csv"));
            this.geoCN2 = new GeoCN2(mmdbGeoCNFile, divisionReader, new MaxMindNodeCache());
        } catch (InvalidDatabaseException exception) {
            mmdbGeoCNFile.delete();
            mmdbGeoCNFile.deleteOnExit();
            log.error("Unable to load GeoCN database (version 2), the file may be corrupted. It has been deleted and will be re-downloaded on next startup.", exception);
        }
        try {
            this.geoCN1 = new GeoCN1(mmdbGeoCNFile, new MaxMindNodeCache());
        } catch (InvalidDatabaseException exception) {
            mmdbGeoCNFile.delete();
            mmdbGeoCNFile.deleteOnExit();
            log.error("Unable to load GeoCN database (version 1), the file may be corrupted. It has been deleted and will be re-downloaded on next startup.", exception);
        }
    }

    private void updateMMDB(String databaseName, File target) throws IOException {
        log.info("UPDATING IPDB DATA FOR DATABASE " + databaseName);
        IPDBDownloadSource mirror1 = new IPDBDownloadSource("https://github.com/PBH-BTN/GeoLite.mmdb/releases/latest/download/", databaseName, true);
        IPDBDownloadSource mirror3 = new IPDBDownloadSource("https://pbh-static.paulzzh.com/ipdb/", databaseName, true);
        IPDBDownloadSource mirror4 = new IPDBDownloadSource("https://pbh-static.ghostchu.com/ipdb/", databaseName, true);
        Path tmp = Files.createTempFile(databaseName, ".mmdb");
        downloadFile(tmp, databaseName, mirror1, mirror3, mirror4).join();
        if (!tmp.toFile().exists()) {
            if (isMmdbNeverDownloaded(target)) {
                throw new IllegalStateException("Download mmdb database failed!");
            } else {
                log.warn("MMDB database exists but update failed: {}", databaseName);
            }
        }
        Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }


    private boolean isMmdbNeverDownloaded(File target) {
        return !target.exists();
    }

    private boolean needUpdateMMDB(File target) {
        if (!target.exists()) {
            return true;
        }
        if (!autoUpdate) {
            return false;
        }
        // 45天
        long updateInterval = 3888000000L;
        return System.currentTimeMillis() - target.lastModified() > updateInterval;
    }

    private CompletableFuture<Void> downloadFile(Path path, String databaseName, IPDBDownloadSource... mirrorList) {
        return downloadFile(Arrays.stream(mirrorList).collect(Collectors.toList()), path, databaseName);
    }

    private CompletableFuture<Void> downloadFile(List<IPDBDownloadSource> mirrorList, Path path, String databaseName) {
        return CompletableFuture.runAsync(() -> {
            IPDBDownloadSource mirror = mirrorList.removeFirst();
            // 创建带有进度追踪器的 HTTP 客户端
            Request request = new Request.Builder()
                    .url(mirror.getIPDBUrl())
                    .get()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                var body = response.body();
                long totalSize = body.contentLength();
                long totalRead = 0;
                if (response.code() == 200) {
                    if (mirror.supportXzip()) {
                        try {
                            File tmp = File.createTempFile(databaseName, ".tmp");
                            try (XZInputStream gzipInputStream = new XZInputStream(body.byteStream());
                                 FileOutputStream fileOutputStream = new FileOutputStream(tmp)) {
                                byte[] buffer = new byte[1024];
                                int len;
                                while ((len = gzipInputStream.read(buffer)) > 0) {
                                    totalRead += len;
                                    fileOutputStream.write(buffer, 0, len);
                                }
                            }
                            // validate mmdb
                            validateMMDB(tmp);
                            Files.move(tmp.toPath(), path, StandardCopyOption.REPLACE_EXISTING);
                            log.info("IPDB update {} success", databaseName);
                            return;
                        } catch (IOException e) {
                            log.warn("IPDB update {} failed: unable to unzip the archive", databaseName, e);
                        }
                    } else {
                        // 直接保存文件
                        try (var source = body.source();
                             var sink = Okio.buffer(Okio.sink(path))) {
                            sink.writeAll(source);
                            log.info("IPDB update {} success", databaseName);
                            return;
                        }
                    }
                }

                if (!mirrorList.isEmpty()) {
                    log.warn("Retry IPDB update from backup source....");
                    downloadFile(mirrorList, path, databaseName).join();
                    return;
                }
                log.error("IPDB update {} failed: {} - {}", databaseName, response.code(), response.body().string());
            } catch (Exception e) {
                if (!mirrorList.isEmpty()) {
                    log.warn("Retry IPDB update from backup source....");
                    downloadFile(mirrorList, path, databaseName).join();
                    return;
                }
                log.error("IPDB update {} failed: {}", databaseName, e.getMessage(), e);
            }
        });
    }

    private void validateMMDB(File tmp) throws IOException {
        try (InputStream is = new FileInputStream(tmp);
             var reader = new Reader(is, NoCache.getInstance())) {
            log.debug("Validate mmdb {} success: {}", tmp.getName(), reader.getMetadata());
        }
    }

    @Override
    public void close() {
        if (this.mmdbCity != null) {
            try {
                this.mmdbCity.close();
            } catch (IOException ignored) {

            }
        }
        if (this.mmdbASN != null) {
            try {
                this.mmdbASN.close();
            } catch (IOException ignored) {

            }
        }
        if (this.geoCN2 != null) {
            try {
                this.geoCN2.close();
            } catch (Exception ignored) {

            }
        }
        if (this.geoCN1 != null) {
            try {
                this.geoCN1.close();
            } catch (Exception ignored) {

            }
        }
    }


    public static final class MaxMindNodeCache implements NodeCache {
        private final static Cache<@NotNull CacheKey, @NotNull DecodedValue> cache = CacheBuilder.newBuilder()
                .maximumSize(2000)
                .expireAfterAccess(Duration.ofHours(1))
                .build();

        @SneakyThrows
        @Override
        public DecodedValue get(CacheKey cacheKey, Loader loader) {
            return cache.get(cacheKey, () -> loader.load(cacheKey));
        }
    }

}
