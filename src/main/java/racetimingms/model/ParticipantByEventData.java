package racetimingms.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Generated;
import lombok.NoArgsConstructor;

@Data
@Generated
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantByEventData extends StandardField {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("name")
    private String name;

    @JsonProperty("bibNo")
    private String bibNo;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("nationality")
    private String nationality;

    @JsonProperty("lastCP")
    private String lastCP;

    @JsonProperty("distance")
    private String distance;

    @JsonProperty("gunTime")
    private String gunTime;

    @JsonProperty("chipTime")
    private String chipTime;

    @JsonProperty("pictureUrl")
    private String pictureUrl;

    @JsonProperty("pos")
    private Integer pos;

    @JsonProperty("ageGroup")
    private String ageGroup;

    @JsonProperty("raceTimeDiff")
    private String raceTimeDiff;

    @JsonProperty("status")
    private String status;

    @JsonProperty("genderPos")
    private Integer genderPos;

    @JsonProperty("ageGroupPos")
    private Integer ageGroupPos;
    
    private Map<String, String> checkpoints;
}
