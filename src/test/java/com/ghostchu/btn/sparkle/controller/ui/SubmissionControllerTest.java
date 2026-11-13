package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubmissionControllerTest {
    @Test
    void requiresAuthenticationAndUsesPrincipalOwner() {
        SubmissionService service = mock(SubmissionService.class);
        SubmissionController controller = new SubmissionController(service);
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> controller.query(null, "bans", 3, 1, 20)).getStatusCode().value());
        verifyNoInteractions(service);
        SparkleUserDetails principal = mock(SparkleUserDetails.class);
        when(principal.getUserId()).thenReturn(42L);
        controller.query(principal, "clients", 3, 1, 20);
        verify(service).query(42L, "clients", 3, 1, 20);
    }
}
