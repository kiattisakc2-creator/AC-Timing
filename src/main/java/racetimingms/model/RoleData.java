
package racetimingms.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Generated;
import lombok.NoArgsConstructor;

@Data
@Generated
@NoArgsConstructor
@AllArgsConstructor
public class RoleData extends StandardField {
    // @JsonProperty("id")
    // private Integer id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("name")
    private String name;

    @JsonProperty("activeText")
    private String activeText;

    @JsonProperty("menuPermission")
    private List<PermissionData> menuPermission;
}
