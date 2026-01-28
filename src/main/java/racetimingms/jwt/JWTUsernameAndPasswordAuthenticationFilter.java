package racetimingms.jwt;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import racetimingms.model.MenuInfoData;
import racetimingms.model.ResponseStatus;
import racetimingms.model.UserInfoData;
import racetimingms.model.response.LoginResponse;
import racetimingms.model.response.LoginResponse.LoginData;
import racetimingms.service.DatabaseService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configurable
public class JWTUsernameAndPasswordAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final String key;
    private final AuthenticationManager authenticationManager;
    private DatabaseService databaseService;

    public JWTUsernameAndPasswordAuthenticationFilter(AuthenticationManager authenticationManager, String key) {
        this.authenticationManager = authenticationManager;
        this.key = key;
    }

    public JWTUsernameAndPasswordAuthenticationFilter(AuthenticationManager authenticationManager, String key,
            ApplicationContext ctx) {
        this.authenticationManager = authenticationManager;
        this.key = key;
        this.databaseService = ctx.getBean(DatabaseService.class);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {

        try {
            UsernameAndPasswordAuthenticationRequest authenticationRequest = new ObjectMapper()
                    .readValue(request.getInputStream(), UsernameAndPasswordAuthenticationRequest.class);
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    authenticationRequest.getEmail(),
                    authenticationRequest.getPassword());
            return authenticationManager.authenticate(authentication);
        } catch (IOException e) {
            if (!"Invalid Token".equals(e.getMessage())) {
                log.error("Spring Security Filter Chain Exception:", e);
            }
            throw new RuntimeException(e);
        }

    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
            Authentication authResult) throws IOException, ServletException {

        String token = Jwts.builder()
                .setSubject(authResult.getName())
                .claim("authorities", authResult
                        .getAuthorities())
                .setIssuedAt(new Date())
                .setExpiration(java.sql.Date.valueOf(LocalDate.now().plusWeeks(2)))
                .signWith(Keys.hmacShaKeyFor(key.getBytes()))
                .compact();
        ObjectMapper mapper = new ObjectMapper();
        UserInfoData userInfo;
        List<MenuInfoData> menuInfo;
        try {
            userInfo = databaseService.getUserInfo(authResult.getName());
            menuInfo = databaseService.getMenuInfo(authResult.getName());
        } catch (SQLException e) {
            throw new RuntimeException("There is error in SQL service");
        }
        LoginResponse res = LoginResponse.builder().status(
                ResponseStatus.builder().code("200").description("success").build())
                .data(LoginData.builder().loginStatus(true).loginMsg("success").token(token).userInfo(userInfo).menuInfo(menuInfo).build())
                .build();
        response.setStatus(200);
        response.addHeader("content-type", "application/json; charset=utf-8");
        response.addHeader("Authorization", "Bearer " + token);
        PrintWriter out = response.getWriter();
        out.write(mapper.writeValueAsString(res));
        out.flush();
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException failed) throws IOException, ServletException {
        ObjectMapper mapper = new ObjectMapper();
        LoginResponse res = LoginResponse.builder().status(
                ResponseStatus.builder().code("200").description("success").build())
                .data(LoginData.builder().loginStatus(false).loginMsg("User not found.").token(null).userInfo(null)
                        .build())
                .build();
        response.addHeader("content-type", "application/json");
        PrintWriter out = response.getWriter();
        out.write(mapper.writeValueAsString(res));
        out.flush();
    }

}
