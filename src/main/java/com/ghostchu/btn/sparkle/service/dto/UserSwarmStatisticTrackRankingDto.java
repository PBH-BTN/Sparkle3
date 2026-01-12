package com.ghostchu.btn.sparkle.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserSwarmStatisticTrackRankingDto {
    @JsonProperty("rank") // 排名
    private Long rank;
    @JsonProperty("user_id")
    private Long userId;
    @JsonProperty("sent_traffic_other_ack") // 确认上传
    private Long sentTrafficOtherAck;
    @JsonProperty("received_traffic_other_ack") // 确认下载
    private Long receivedTrafficOtherAck;
    @JsonProperty("sent_traffic_self_report") // 汇报上传
    private Long sentTrafficSelfReport;
    @JsonProperty("received_traffic_self_report") // 汇报下载
    private Long receivedTrafficSelfReport;
}
