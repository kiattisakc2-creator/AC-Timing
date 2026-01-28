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
public class UserData extends StandardField {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("username")
    private String username;

    @JsonProperty("password")
    private String password;

    @JsonProperty("role")
    private String role;

    @JsonProperty("roleId")
    private Integer roleId;

    @JsonProperty("email")
    private String email;

    @JsonProperty("prefixPath")
    private String prefixPath;

    @JsonProperty("pictureUrl")
    private String pictureUrl;

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("opw")
    private String opw;

    @JsonProperty("npw")
    private String npw;

    @JsonProperty("activeText")
    private String activeText;

    @JsonProperty("totalEvent")
    private String totalEvent;

    @JsonProperty("thumbPictureUrl")
    private String thumbPictureUrl;

    @JsonProperty("roleText")
    private String roleText;
}
