package com.ghostchu.btn.sparkle.service.btnability;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface SparkleBtnAbility {
    @JsonProperty("config_key")
    String getConfigKey();
}
