package com.ghostchu.btn.sparkle.service.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnConfig;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IUserappConfigService;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserappConfigServiceImpl implements IUserappConfigService {
    @Value("${sparkle.ping.config.min-protocol-version}")
    private int minProtocolVersion;
    @Value("${sparkle.ping.config.max-protocol-version}")
    private int maxProtocolVersion;
    @Autowired
    private List<SparkleBtnAbility> btnAbilities;

    @Value("${sparkle.captcha.pow.endpoint}")
    private String powCaptchaEndpoint;

    @Override
    public BtnConfig configAnonymousUserapp() throws UserApplicationNotFoundException {
        throw new UserApplicationNotFoundException();
    }

    @Override
    public BtnConfig configLoggedInUserapp(Userapp userapp) {
        BtnConfig btnConfig = new BtnConfig();
        btnConfig.setMinProtocolVersion(minProtocolVersion);
        btnConfig.setMaxProtocolVersion(maxProtocolVersion);
        Map<String, SparkleBtnAbility> ability = new LinkedHashMap<>();
        for (SparkleBtnAbility btnAbility : btnAbilities) {
            ability.put(btnAbility.getConfigKey(), btnAbility);
        }
        btnConfig.setAbility(ability);
        btnConfig.setProofOfWorkConfig(new BtnConfig.ProofOfWorkConfig(powCaptchaEndpoint));
        return btnConfig;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Component
    public static class ReconfigureAbility implements SparkleBtnAbility {
        @Value("${sparkle.ping.reconfigure.interval}")
        private long interval;
        @Value("${sparkle.ping.reconfigure.random-initial-delay}")
        @JsonProperty("random_initial_delay")
        private long randomInitialDelay;
        @JsonProperty("version")
        private String version = UUID.randomUUID().toString();

        @Override
        public String getConfigKey() {
            return "reconfigure";
        }
    }
}
