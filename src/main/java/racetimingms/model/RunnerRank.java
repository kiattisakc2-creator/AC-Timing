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
public class RunnerRank extends StandardField {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("name")
    private String name;

    @JsonProperty("bibNo")
    private String bibNo;

    @JsonProperty("category")
    private String category;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("raceTime")
    private String raceTime;

    @JsonProperty("overallRank")
    private String overallRank;

    @JsonProperty("genderRank")
    private String genderRank;

    @JsonProperty("ageGroupRank")
    private String ageGroupRank;

    @JsonProperty("status")
    private String status;

    @JsonProperty("pictureUrl")
    private String pictureUrl;
}
