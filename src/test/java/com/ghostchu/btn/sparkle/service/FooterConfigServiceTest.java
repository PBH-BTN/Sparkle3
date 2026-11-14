package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterConfigView;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterSupportButton;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FooterConfigServiceTest {
    private final FooterConfigService service = new FooterConfigService(
            mock(DatabaseConfigService.class), new ObjectMapper());

    private FooterConfigView config(List<FooterSupportButton> buttons) {
        return new FooterConfigView("", "", null, "", null, List.of(), null, null, null, List.of(), buttons);
    }

    @Test
    void preservesOrderAndAllowsRemovingAllButtons() throws Exception {
        var buttons = List.of(new FooterSupportButton("支持", "Support", "https://example.com/a", "/images/a.svg"),
                new FooterSupportButton("咖啡", "Coffee", "/support", "https://example.com/icon.svg"));
        assertEquals(buttons, service.update(config(buttons), 1L).supportButtons());
        assertTrue(service.update(config(List.of()), 1L).supportButtons().isEmpty());
    }

    @Test
    void oldConfigurationRetainsDefaultAfdianButton() throws Exception {
        var buttons = service.update(config(null), 1L).supportButtons();
        assertEquals("https://ifdian.net/a/Ghost_chu", buttons.getFirst().href());
        assertEquals("/img/afdian-support.svg", buttons.getFirst().iconUrl());
    }

    @Test
    void rejectsUnsafeLinksIconsAndExcessiveButtons() {
        for (String url : List.of("", "javascript:alert(1)", "//example.com", "/\\example.com", "https:no-host", "data:image/svg+xml,test")) {
            assertThrows(BusinessException.class, () -> service.update(config(List.of(
                    new FooterSupportButton("支持", "Support", url, "/images/a.svg"))), 1L));
            assertThrows(BusinessException.class, () -> service.update(config(List.of(
                    new FooterSupportButton("支持", "Support", "/support", url))), 1L));
        }
        assertThrows(BusinessException.class, () -> service.update(config(Collections.nCopies(13,
                new FooterSupportButton("支持", "Support", "/support", "/img/a.svg"))), 1L));
        assertThrows(BusinessException.class, () -> service.update(config(List.of(
                new FooterSupportButton("", "", "/support", "/img/a.svg"))), 1L));
    }
}
