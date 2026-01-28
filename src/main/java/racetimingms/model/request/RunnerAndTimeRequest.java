package racetimingms.model.request;

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
public class RunnerAndTimeRequest {
    
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("bibNo")
    private String bibNo;
    
    @JsonProperty("recordType")
    private String recordType;

    @JsonProperty("raceTimingIn")
    private String raceTimingIn;

    @JsonProperty("raceTimingOut")
    private String raceTimingOut;

    @JsonProperty("participantUuid")
    private String participantUuid;
}
