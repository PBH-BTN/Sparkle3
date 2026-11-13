package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.time.OffsetDateTime;

public final class SubmissionModels {
    private SubmissionModels() {}

    public record SubmissionRecord(String id, String application, OffsetDateTime submittedAt,
                                   String peerId, String clientName, String moduleName,
                                   String clientType, String clientVersion, String torrentId,
                                   String uploaded, String downloaded) {}
}
