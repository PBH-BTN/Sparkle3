package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.controller.ping.dto.BtnConfig;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IUserappConfigService;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserappConfigServiceImpl implements IUserappConfigService {
    @Autowired
    private List<SparkleBtnAbility> btnAbilities;
    @Autowired
    private BtnRuntimeConfigService configService;

    @Value("${sparkle.captcha.pow.endpoint}")
    private String powCaptchaEndpoint;

    @Override
    public BtnConfig configAnonymousUserapp() throws UserApplicationNotFoundException {
        throw new UserApplicationNotFoundException();
    }

    @Override
    public BtnConfig configLoggedInUserapp(Userapp userapp) {
        BtnConfig btnConfig = new BtnConfig();
        btnConfig.setMinProtocolVersion(
                configService.intValue("protocol", "minProtocolVersion")
        );
        btnConfig.setMaxProtocolVersion(
                configService.intValue("protocol", "maxProtocolVersion")
        );
        Map<String, SparkleBtnAbility> ability = new LinkedHashMap<>();
        for (SparkleBtnAbility btnAbility : btnAbilities) {
            ability.put(btnAbility.getConfigKey(), btnAbility);
        }
        btnConfig.setAbility(ability);
        btnConfig.setProofOfWorkConfig(new BtnConfig.ProofOfWorkConfig(powCaptchaEndpoint));
        return btnConfig;
    }


}
