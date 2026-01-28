package racetimingms.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Generated;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@Generated
@NoArgsConstructor
@AllArgsConstructor
public class UploadParticipantRequest {
    
    @JsonProperty("id")
    private String id;

    @JsonProperty("data")
    private List<RunnerRequest> data;
}
