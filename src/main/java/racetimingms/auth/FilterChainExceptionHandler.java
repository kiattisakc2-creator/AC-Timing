package racetimingms.auth;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import racetimingms.model.ResponseStatus;
import racetimingms.model.response.CommonResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FilterChainExceptionHandler extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            ObjectMapper mapper = new ObjectMapper();
            ResponseStatus responseStatus = new ResponseStatus();
            if ("Invalid Token".equals(e.getMessage())) {
                response.setStatus(400);
                responseStatus.setCode("400");
            } else {
                log.error("Spring Security Filter Chain Exception:", e);
                response.setStatus(500);
                responseStatus.setCode("500");
            }
            responseStatus.setDescription(e.getMessage());
            CommonResponse res = CommonResponse.builder().status(responseStatus).build();
            response.addHeader("content-type", "application/json");
            PrintWriter out = response.getWriter();
            out.write(mapper.writeValueAsString(res));
            out.flush();
        }
    }

}
