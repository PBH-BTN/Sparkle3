package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.swarmtracker.SwarmTrackerApiController;
import com.ghostchu.btn.sparkle.service.ISwarmTrackerService;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SwarmTrackerApiControllerTest {
    @Test
    void unknownInfoHashReturnsAnEmptyResultWithoutSearchingAllTorrents() throws Exception {
        var tracker = mock(ISwarmTrackerService.class);
        var controller = new SwarmTrackerApiController();
        ReflectionTestUtils.setField(controller, "swarmTrackerService", tracker);
        ReflectionTestUtils.setField(controller, "torrentService", mock(ITorrentService.class));

        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post("/api/swarmtracker/query").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"infoHash":"0123456789abcdef0123456789abcdef01234567","page":1,"size":100}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records").isEmpty());

        verifyNoInteractions(tracker);
    }
}
