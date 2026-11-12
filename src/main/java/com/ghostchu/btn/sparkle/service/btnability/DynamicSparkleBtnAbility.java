package com.ghostchu.btn.sparkle.service.btnability;

import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class DynamicSparkleBtnAbility implements SparkleBtnAbility {
    @Autowired
    protected BtnRuntimeConfigService configService;

    protected String text(String section, String field) {
        return configService.text(section, field);
    }

    protected long longValue(String section, String field) {
        return configService.longValue(section, field);
    }

    protected boolean booleanValue(String section, String field) {
        return configService.booleanValue(section, field);
    }
}
