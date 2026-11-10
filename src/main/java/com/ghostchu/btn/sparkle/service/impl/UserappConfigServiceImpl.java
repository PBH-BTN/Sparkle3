package com.ghostchu.btn.sparkle.service.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnConfig;
import com.ghostchu.btn.sparkle.controller.ping.PingQueryIpController;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.AdminConfigService;
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
    @Autowired
    private List<SparkleBtnAbility> btnAbilities;
    @Autowired
    private AdminConfigService adminConfigService;

    @Value("${sparkle.captcha.pow.endpoint}")
    private String powCaptchaEndpoint;

    @Override
    public BtnConfig configAnonymousUserapp() throws UserApplicationNotFoundException {
        throw new UserApplicationNotFoundException();
    }

    @Override
    public BtnConfig configLoggedInUserapp(Userapp userapp) {
        var runtimeConfig = adminConfigService.get();
        BtnConfig btnConfig = new BtnConfig();
        btnConfig.setMinProtocolVersion(runtimeConfig.protocolMinVersion());
        btnConfig.setMaxProtocolVersion(runtimeConfig.protocolMaxVersion());
        Map<String, SparkleBtnAbility> ability = new LinkedHashMap<>();
        for (SparkleBtnAbility btnAbility : btnAbilities) {
            if (btnAbility instanceof PingQueryIpController.IpQueryBtnModule ipQueryModule) {
                ipQueryModule.setPowCaptcha(runtimeConfig.queryIpPowCaptcha());
            }
            ability.put(btnAbility.getConfigKey(), btnAbility);
        }
        btnConfig.setAbility(ability);
        btnConfig.setProofOfWorkConfig(new BtnConfig.ProofOfWorkConfig(powCaptchaEndpoint));
        return btnConfig;
    }


}
