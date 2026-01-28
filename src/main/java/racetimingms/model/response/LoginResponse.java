package racetimingms.model.response;

import racetimingms.model.MenuInfoData;
import racetimingms.model.UserInfoData;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Generated;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@Generated
@EqualsAndHashCode(callSuper = true)
public class LoginResponse extends CommonResponse {

    @JsonProperty("data")
    private LoginData data;

    @Data
    @Builder
    public static class LoginData {

        @JsonProperty("loginStatus")
        private Boolean loginStatus;

        @JsonProperty("loginMsg")
        private String loginMsg;

        @JsonProperty("token")
        private String token;

        @JsonProperty("userInfo")
        private UserInfoData userInfo;

        @JsonProperty("menuInfo")
        private List<MenuInfoData> menuInfo;
    }

}
