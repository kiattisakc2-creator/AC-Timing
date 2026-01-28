package racetimingms.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class TimeRecordData {
    
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("checkpointMappingId")
    private Integer checkpointMappingId;

    @JsonProperty("participantId")
    private Integer participantId;

    @JsonProperty("raceTimingIn")
    private String raceTimingIn;

    @JsonProperty("raceTimingOut")
    private String raceTimingOut;

    // @JsonProperty("raceTimingIn")
    // @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    // private LocalDateTime raceTimingIn;

    // @JsonProperty("raceTimingOut")
    // @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    // private LocalDateTime raceTimingOut;

    @JsonProperty("recordType")
    private String recordType;

    @JsonProperty("active")
    private Boolean active;

    @JsonProperty("checkpointMappingUuid")
    private String checkpointMappingUuid;

    @JsonProperty("participantUuid")
    private String participantUuid;

    @JsonProperty("eventUuid")
    private String eventUuid;
}
