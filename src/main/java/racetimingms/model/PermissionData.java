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
public class PermissionData {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("menuId")
    private Integer menuId;

    @JsonProperty("roleId")
    private Integer roleId;

    @JsonProperty("canAccess")
    private Boolean canAccess;

    @JsonProperty("active")
    private Boolean active;

    @JsonProperty("name")
    private String name;
}
