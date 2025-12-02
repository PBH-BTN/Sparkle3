package com.ghostchu.btn.sparkle.controller.ping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BtnConfig {
    @JsonProperty("min_protocol_version")
    private int minProtocolVersion;
    @JsonProperty("max_protocol_version")
    private int maxProtocolVersion;
    @JsonProperty("ability")
    private Map<String, SparkleBtnAbility> ability;
}
