package com.ghostchu.btn.sparkle.controller.ping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public final class BtnRuleset {
    @JsonProperty("version")
    private String version;
    @JsonProperty("peer_id")
    private Map<String, List<String>> peerIdRules;
    @JsonProperty("client_name")
    private Map<String, List<String>> clientNameRules;
    @JsonProperty("port")
    private Map<String, List<Integer>> portRules;
    @JsonProperty("script")
    private Map<String, String> scriptRules;
}
