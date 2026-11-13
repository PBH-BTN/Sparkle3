package com.ghostchu.btn.sparkle.service.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

@Getter
@Setter
@Accessors(chain = true)
public class TrafficRefreshStateRow {
    private OffsetDateTime backfillCursor;
    private OffsetDateTime watermark;
    private Boolean ready;
}
