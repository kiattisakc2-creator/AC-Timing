package racetimingms.auth;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import racetimingms.model.ResponseStatus;
import racetimingms.model.response.LogoutResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CustomLogoutSuccessHandler implements LogoutSuccessHandler {

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        ObjectMapper mapper = new ObjectMapper();
        LogoutResponse res = LogoutResponse.builder().status(
                ResponseStatus.builder().code("200").description("success").build())
                .build();
        response.setStatus(200);
        PrintWriter out = response.getWriter();
        out.write(mapper.writeValueAsString(res));
        out.flush();
    }
}
