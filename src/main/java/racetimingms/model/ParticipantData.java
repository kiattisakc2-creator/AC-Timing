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
public class ParticipantData extends StandardField {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("eventId")
    private Integer eventId;

    @JsonProperty("runnerId")
    private Integer runnerId;

    @JsonProperty("bibNo")
    private String bibNo;

    @JsonProperty("registerDate")
    private String registerDate;

    @JsonProperty("age")
    private String age;

    @JsonProperty("ageGroup")
    private String ageGroup;

    @JsonProperty("teamName")
    private String teamName;

    @JsonProperty("shirtSize")
    private String shirtSize;

    @JsonProperty("chipCode")
    private String chipCode;

    @JsonProperty("status")
    private String status;

    @JsonProperty("isStarted")
    private Boolean isStarted;

    @JsonProperty("name")
    private String name;

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("idNo")
    private String idNo;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("birthDate")
    private String birthDate;

    @JsonProperty("nationality")
    private String nationality;

    @JsonProperty("allowRFIDSync")
    private Boolean allowRFIDSync;
}
