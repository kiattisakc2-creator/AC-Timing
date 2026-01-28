package racetimingms.model.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
public class UserDataResponse {

    @JsonIgnore
    private Integer id;
    @JsonProperty("uuid")
    private String uuid;
    @JsonProperty("username")
    private String username;
    @JsonProperty("role")
    private String role;
    @JsonProperty("email")
    private String email;
    @JsonIgnore
    private String password;
    @JsonProperty("pictureUrl")
    private String pictureUrl;
}