package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.ping.dto.BtnConfig;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;

public interface IUserappConfigService {
    BtnConfig configAnonymousUserapp() throws UserApplicationNotFoundException;

    BtnConfig configLoggedInUserapp(Userapp userapp);
}
