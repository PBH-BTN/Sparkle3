package com.ghostchu.btn.sparkle.util.ipdb;

import com.maxmind.db.Network;

import java.util.Set;

public record GeoNetworkRecord(
        Network network,
        String countryIso,
        Set<String> provinceNames,
        Set<String> cityNames
) {
}
