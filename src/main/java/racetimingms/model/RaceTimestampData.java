package racetimingms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Generated;
import lombok.NoArgsConstructor;

@Data
@Generated
@NoArgsConstructor
@AllArgsConstructor
public class RaceTimestampData extends StandardField {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("stationId")
    private Integer stationId;

    @JsonProperty("participantId")
    private Integer participantId;

    @JsonProperty("raceTimingIn")
    private String raceTimingIn;

    @JsonProperty("raceTimingOut")
    private String raceTimingOut;

    @JsonProperty("bibNo")
    private String bibNo;

    @JsonProperty("name")
    private String name;
    
    @JsonProperty("stationUuid")
    private String stationUuid;

    @JsonProperty("scanInOut")
    private Boolean scanInOut;
}
