package racetimingms.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Generated;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@Generated
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StandardField {
    @JsonProperty(access = Access.WRITE_ONLY)
    private long hits;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("active")
    private Boolean active;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime date;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("createdTime")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("createdBy")
    private Integer createdBy;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("updatedTime")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("updatedBy")
    private Integer updatedBy;
}
