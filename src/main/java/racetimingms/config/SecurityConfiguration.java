package racetimingms.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import racetimingms.auth.ApplicationUserService;
import racetimingms.auth.CustomLogoutSuccessHandler;
import racetimingms.auth.FilterChainExceptionHandler;
import racetimingms.constants.Constants;
import racetimingms.jwt.JWTTokenVerifier;
import racetimingms.jwt.JWTUsernameAndPasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    private final PasswordEncoder passwordEncoder;
    private final ApplicationUserService applicationUserService;
    private final FilterChainExceptionHandler filterChainExceptionHandler;
    private final ApplicationContext applicationContext;

    @Value("${jwt.secret-key}")
    private String jwtSecretKey;

    public SecurityConfiguration(
            PasswordEncoder passwordEncoder,
            ApplicationUserService applicationUserService,
            FilterChainExceptionHandler filterChainExceptionHandler,
            ApplicationContext applicationContext) {
        this.passwordEncoder = passwordEncoder;
        this.applicationUserService = applicationUserService;
        this.filterChainExceptionHandler = filterChainExceptionHandler;
        this.applicationContext = applicationContext;
    }

    private static final String[] PUBLIC_URLS = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/v2/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**"
    };

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        JWTUsernameAndPasswordAuthenticationFilter authFilter = new JWTUsernameAndPasswordAuthenticationFilter(
                authenticationManager(), jwtSecretKey, applicationContext);
        authFilter.setFilterProcessesUrl("/login");

        JWTTokenVerifier tokenVerifier = new JWTTokenVerifier(jwtSecretKey);

    http
        .csrf().disable()
        .cors().and()
        .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and()
        .addFilter(authFilter)
        .addFilterAfter(tokenVerifier, JWTUsernameAndPasswordAuthenticationFilter.class)
        .addFilterBefore(filterChainExceptionHandler, JWTUsernameAndPasswordAuthenticationFilter.class)
        .authorizeRequests()
        .antMatchers(HttpMethod.GET, Constants.ENDPOINT + "/public-api/**").permitAll()
        .antMatchers(HttpMethod.POST, Constants.ENDPOINT + "/public-api/**").permitAll()
        .antMatchers(HttpMethod.DELETE, Constants.ENDPOINT + "/help/clearReports").permitAll()
        .antMatchers(HttpMethod.GET, "/").permitAll()
        .antMatchers(Constants.ENDPOINT + "/login").permitAll()
        .antMatchers(Constants.ENDPOINT + "/**").hasRole("USER")
        .antMatchers(PUBLIC_URLS).permitAll()
        .anyRequest()
        .authenticated()
        .and()
        .logout()
        .logoutUrl(Constants.ENDPOINT + "/logout")
        .clearAuthentication(true)
        .invalidateHttpSession(true)
        .logoutSuccessHandler(new CustomLogoutSuccessHandler());
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(daoAuthenticationProvider());
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(passwordEncoder);
        provider.setUserDetailsService(applicationUserService);
        return provider;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        String env = System.getenv("ENV");
        configuration.setAllowedMethods(Arrays.asList("HEAD", "GET", "POST", "PUT", "DELETE", "PATCH"));
        configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(
                Arrays.asList("Authorization", "Authorizationstation", "Cache-Control", "Content-Type"));
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (env == null) {
            env = System.getProperty("ENV");
        }
        if (env == null || !"PROD".equals(env)) {
            configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        } else if ("PROD".equals(env)) {
            configuration.setAllowedOriginPatterns(Arrays.asList("https://timing.action.in.th/"));
        }
        source.registerCorsConfiguration(Constants.ENDPOINT + "/**", configuration);
        return source;
    }

}
