package racetimingms.model.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Generated;
import lombok.NoArgsConstructor;

@Data
@Builder
@Generated
@NoArgsConstructor
@AllArgsConstructor
public class RaceTimestampRequest {

    @JsonProperty("stationUuid")
    private String stationUuid;

    @JsonProperty("campaignUuid")
    private String campaignUuid;

    @JsonProperty("raceTimingItems")
    private List<RaceTimingItems> raceTimingItems;

    @Data
    @Builder
    @Generated
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RaceTimingItems {

        @JsonProperty("bibNo")
        private String bibNo;

        @JsonProperty("raceTimingIn")
        private String raceTimingIn;

        @JsonProperty("type")
        private String type;

        @JsonProperty("updateDupTime")
        private String updateDupTime;

    }

    @Data
    @Builder
    @Generated
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantItems {

        @JsonProperty("participantId")
        private Integer participantId;

        @JsonProperty("checkpointMappingId")
        private Integer checkpointMappingId;

        @JsonProperty("eventId")
        private Integer eventId;

        @JsonProperty("eventDate")
        private String eventDate;

        @JsonProperty("distance")
        private Integer distance;

        @JsonProperty("totalResult")
        private String totalResult;

        @JsonProperty("remainingStation")
        private String remainingStation;

        @JsonProperty("lastResultId")
        private Integer lastResultId;

        @JsonProperty("dupResult")
        private Integer dupResult;
    }
}
