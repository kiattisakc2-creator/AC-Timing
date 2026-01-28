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
public class LatestParticipantByCheckpoint extends StandardField {

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("bibNo")
    private String bibNo;

    @JsonProperty("name")
    private String name;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("nationality")
    private String nationality;

    @JsonProperty("passTime")
    private String passTime;

    @JsonProperty("raceTimingIn")
    private String raceTimingIn;

    @JsonProperty("checkpointName")
    private String checkpointName;

    @JsonProperty("ageGroup")
    private String ageGroup;

}