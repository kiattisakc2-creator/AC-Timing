package racetimingms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Generated;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@Generated
public class UserInfoData {

    @JsonProperty("userData")
    String userData;

}
