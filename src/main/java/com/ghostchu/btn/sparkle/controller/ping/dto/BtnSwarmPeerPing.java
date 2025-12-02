package com.ghostchu.btn.sparkle.controller.ping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public final class BtnSwarmPeerPing {
    @JsonProperty("swarms")
    private List<BtnSwarm> swarms;

}
