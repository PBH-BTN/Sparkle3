package com.ghostchu.btn.sparkle.util.ipdb.geocn;

import com.ghostchu.btn.sparkle.util.ipdb.GeoNetworkRecord;
import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
import com.maxmind.db.InvalidNetworkException;
import com.maxmind.db.NodeCache;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.NamedCsvRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;

@Slf4j
public class GeoCN2 implements AutoCloseable {
    private final com.maxmind.db.Reader reader;
    private final DivisionParser division;

    public GeoCN2(File geoCNMmdb, Reader divisionCsv, NodeCache nodeCache) throws IOException {
        this.reader = new com.maxmind.db.Reader(geoCNMmdb, nodeCache);
        this.division = new DivisionParser(divisionCsv);
    }

    @Nullable
    public IPGeoData query(InetAddress address) throws IOException {
        var record = this.reader.getRecord(address, Map.class);
        var network = record.getNetwork();
        var dataMap = record.getData();
        if (dataMap == null) return null;
        if (!dataMap.containsKey("division_code") && dataMap.containsKey("province")) {
            throw new IllegalStateException("This is a GeoCN (revision 1) but parsing with GeoCN Reader (revision 2)");
        }
        String divisionCode = String.valueOf(dataMap.get("division_code"));
        long divisionCodeNum = 0L;
        try {
            divisionCodeNum = Long.parseLong(divisionCode);
        } catch (Exception _) {
        }
        var divisionData = division.lookup(divisionCode);
        if (divisionData.isEmpty()) return null;
        IPGeoData geoData = new IPGeoData();
        //noinspection SizeReplaceableByIsEmpty
        String province = divisionData.size() >= 1 ? divisionData.get(0) : null;
        String city = divisionData.size() >= 2 ? divisionData.get(1) : null;
        String county = divisionData.size() >= 3 ? divisionData.get(2) : null;
        String town = divisionData.size() >= 4 ? divisionData.get(3) : null;
        StringJoiner fullName = new StringJoiner(" ");
        divisionData.forEach(fullName::add);
        var cityData = new IPGeoData.CityData(
                fullName.toString(),
                divisionCodeNum,
                province,
                city,
                county == null ? null : (town == null ? county : (county + " " + town))
        );
        geoData.setCity(cityData);
        var networkData = new IPGeoData.NetworkData(
                (String) dataMap.get("isp"),
                (String) dataMap.get("type")
        );
        var asNetworkData = new IPGeoData.ASData.ASNetwork(
                network.getNetworkAddress().getHostAddress(),
                network.getPrefixLength()
        );
        geoData.setNetwork(networkData);
        geoData.setAs(new IPGeoData.ASData(null, null, null, asNetworkData));
        return geoData;
    }

    public void forEachNetwork(Consumer<GeoNetworkRecord> consumer) throws IOException {
        try {
            var networks = reader.networks(Map.class);
            while (networks.hasNext()) {
                var record = networks.next();
                Map<?, ?> dataMap = record.getData();
                if (dataMap == null) {
                    continue;
                }
                if (!dataMap.containsKey("division_code") && dataMap.containsKey("province")) {
                    throw new IllegalStateException(
                            "This is a GeoCN (revision 1) but parsing with GeoCN Reader (revision 2)"
                    );
                }
                String divisionCode = String.valueOf(dataMap.get("division_code"));
                var divisionData = division.lookup(divisionCode);
                if (divisionData.isEmpty()) {
                    continue;
                }
                String province = divisionData.size() >= 1 ? divisionData.get(0) : null;
                String city = divisionData.size() >= 2 ? divisionData.get(1) : null;
                consumer.accept(new GeoNetworkRecord(
                        record.getNetwork(),
                        "CN",
                        names(province),
                        names(city)
                ));
            }
        } catch (InvalidNetworkException exception) {
            throw new IOException("Unable to enumerate GeoCN networks", exception);
        }
    }

    private Set<String> names(String value) {
        return value == null || value.isBlank() ? Set.of() : Set.of(value);
    }

    @Override
    public void close() throws Exception {
        reader.close();
    }

    public static class DivisionParser implements AutoCloseable {
        private final PatriciaTrie<String> divisionTrie = new PatriciaTrie<>();

        public DivisionParser(Reader divisionCsv) {
            log.info("Loading division map...");
            long beginMs = System.currentTimeMillis();
            int loaded = 0;
            int skipped = 0;
            CsvReader<NamedCsvRecord> csv = CsvReader.builder().ofNamedCsvRecord(divisionCsv);
            for (NamedCsvRecord record : csv) {
                var id = record.getField("id");
                var extName = record.getField("ext_name");
                if (id == null || extName == null) {
                    log.debug("Invalid division record: {}", record);
                    skipped++;
                    continue;
                }
                loaded++;
                divisionTrie.put(id, extName);
            }
            long cost = System.currentTimeMillis() - beginMs;
            log.info("Division map loaded: {} records, {} skipped, {} ms", loaded, skipped, cost);
        }

        @NotNull
        public List<String> lookup(String divisionCode) {
            // 获取从根到叶子的所有匹配节点
            List<String> path = new ArrayList<>();
            for (int i = 1; i <= divisionCode.length(); i++) {
                String subCode = divisionCode.substring(0, i);
                if (divisionTrie.containsKey(subCode)) {
                    path.add(divisionTrie.get(subCode));
                }
            }
            return path;
        }

        @Override
        public void close() throws Exception {

        }

    }
}
