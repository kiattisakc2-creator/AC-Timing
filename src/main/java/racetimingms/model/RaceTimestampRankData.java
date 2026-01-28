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
public class RaceTimestampRankData extends StandardField {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("pos")
    private Integer pos;

    @JsonProperty("stationId")
    private Integer stationId;

    @JsonProperty("bibNo")
    private String bibNo;

    @JsonProperty("name")
    private String name;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("age")
    private Integer age;

    @JsonProperty("raceTimingIn")
    private String raceTimingIn;

    @JsonProperty("raceTimingOut")
    private String raceTimingOut;

    @JsonProperty("elapsed")
    private String elapsed;

    @JsonProperty("status")
    private String status;
}
