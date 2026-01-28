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
public class MenuInfoData {
    
    @JsonProperty("id")
    private String id;

    @JsonProperty("role")
    private String role;

    @JsonProperty("title")
    private String title;

    @JsonProperty("main")
    private String main;

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
}
