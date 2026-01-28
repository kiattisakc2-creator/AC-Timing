package racetimingms.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Generated;

@Data
@Generated
@EqualsAndHashCode(callSuper = true)
public class NormalizedResponse extends CommonResponse {

    @JsonProperty("data")
    private Object data;

}
