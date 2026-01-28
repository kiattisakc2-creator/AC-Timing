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
public class PagingData {

    @JsonProperty("field")
    String field;
    @JsonProperty("sort")
    String sort;
    @JsonProperty("start")
    Integer start;
    @JsonProperty("limit")
    Integer limit;
    @JsonProperty("searchField")
    String searchField;
    @JsonProperty("searchText")
    String searchText;
}
