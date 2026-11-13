package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.service.MailUnsubscribeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MailUnsubscribeControllerTest {
    private final MailUnsubscribeService service = mock(MailUnsubscribeService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new MailUnsubscribeController(service)).build();

    @Test
    void readsAndUpdatesUsingLinkCredentialWithoutPrincipal() throws Exception {
        when(service.options("credential")).thenReturn(List.of(new com.ghostchu.btn.sparkle.controller.ui.dto.MailUnsubscribeModels.Option("DIRECT_MESSAGE", true)));
        mvc.perform(get("/api/ui/mail-unsubscribe").header("X-Unsubscribe-Token", "credential"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].category").value("DIRECT_MESSAGE"));
        mvc.perform(post("/api/ui/mail-unsubscribe").header("X-Unsubscribe-Token", "credential")
                        .contentType("application/json").content("{\"categories\":[\"DIRECT_MESSAGE\"]}"))
                .andExpect(status().isOk());
        verify(service).unsubscribe("credential", List.of("DIRECT_MESSAGE"));
    }

    @Test
    void rejectsMissingAndInvalidCredentials() throws Exception {
        mvc.perform(get("/api/ui/mail-unsubscribe")).andExpect(status().isBadRequest());
        when(service.options("invalid")).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST));
        mvc.perform(get("/api/ui/mail-unsubscribe").header("X-Unsubscribe-Token", "invalid"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ui/mail-unsubscribe").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }
}
