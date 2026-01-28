package racetimingms.model.request;

import lombok.Data;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParticipantLiveRequest {

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer id;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer eventId;

    private Long hits;
    private String uuid;
    private String bibNo;
    private String name;
    private String gender;
    private String nationality;
    private String lastCP;
    private String lastCPTime;
    private Double distance;
    private String gunTime;
    private String chipTime;
    private String status;
    private String ageGroup;
    private Integer pos;
    private Integer genderPos;
    private Integer ageGroupPos;
    private Map<String, String> checkpoints;
}
