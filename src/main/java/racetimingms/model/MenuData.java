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
public class MenuData {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("role")
    private String role;

    @JsonProperty("title")
    private String title;

    @JsonProperty("main")
    private Integer main;

    @JsonProperty("name")
    private String name;

    @JsonProperty("desc")
    private String desc;

    @JsonProperty("icon")
    private String icon;

    @JsonProperty("path")
    private String path;

    @JsonProperty("isDisabled")
    private Boolean isDisabled;

    @JsonProperty("isDisplay")
    private Boolean isDisplay;

    @JsonProperty("active")
    private Boolean active;
}
