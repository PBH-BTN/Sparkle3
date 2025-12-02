package com.ghostchu.btn.sparkle.autoconfig;

import com.ghostchu.btn.sparkle.util.TimeConverter;
import com.ghostchu.btn.sparkle.util.UnitConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

@Configuration
public class SaplingThymeleafConfig {
    @Autowired
    private void configureThymeleafStaticVars(ThymeleafViewResolver viewResolver) {
        viewResolver.addStaticVariable("unitConverter", UnitConverter.INSTANCE);
        viewResolver.addStaticVariable("timeConverter", TimeConverter.INSTANCE);
    }
}
