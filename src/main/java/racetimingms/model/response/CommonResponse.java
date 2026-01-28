package racetimingms.model.response;

import racetimingms.model.ResponseStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Generated;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@Generated
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CommonResponse {

    @JsonProperty("status")
    ResponseStatus status;

}
