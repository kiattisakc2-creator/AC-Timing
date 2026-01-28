package racetimingms.model;

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
public class CheckpointMappingData {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("eventId")
    private Integer eventId;

    @JsonProperty("distance")
    private Double distance;

    @JsonProperty("cutOffTime")
    private String cutOffTime;

    @JsonProperty("scanInOut")
    private Boolean scanInOut;

    @JsonProperty("stationId")
    private Integer stationId;

    @JsonProperty("stationUuid")
    private String stationUuid;

    @JsonProperty("eventUuid")
    private String eventUuid;

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;
}
