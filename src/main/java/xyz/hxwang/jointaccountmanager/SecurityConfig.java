package xyz.hxwang.jointaccountmanager;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP Basic authentication over the whole API. Two people share a single login;
 * the username is fixed and not secret (it is also baked into the UI), while the
 * password is supplied at runtime via the AUTH_PASSWORD environment variable.
 *
 * <p>CORS is left to {@link WebConfig}: production is same-origin so CORS is never
 * exercised there, and preflight OPTIONS requests are permitted here so a local
 * cross-origin dev server still works.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Not a secret: security rests entirely on AUTH_PASSWORD. Kept in sync with the
    // username hardcoded in the frontend.
    private static final String USERNAME = "AdminUser";

    /**
     * The API docs stay behind the same login, but keep the standard
     * {@code WWW-Authenticate} challenge so a browser offers its own prompt.
     *
     * <p>Without this the docs are unusable interactively: the main chain
     * deliberately suppresses that header, so hitting Swagger in a browser
     * returns a bare 401 with no way to authenticate and the page renders
     * blank. Suppression is there for the SPA's benefit, and the SPA never
     * requests these paths.
     */
    @Bean
    @Order(1)
    SecurityFilterChain docsFilterChain(HttpSecurity http,
                                        @Value("${DOCS_REQUIRE_AUTH:true}") boolean requireAuth) throws Exception {
        http
                .securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    if (requireAuth) {
                        auth.anyRequest().authenticated();
                    } else {
                        // Local sandbox only. Set by docker/docker-compose.yml so the
                        // docs can be browsed without fighting a login dialog; the
                        // default everywhere else, including production, is authenticated.
                        auth.anyRequest().permitAll();
                    }
                })
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain filterChain(HttpSecurity http, AuthenticationEntryPoint entryPoint) throws Exception {
        http
                // No cookies or sessions, so the usual CSRF vector does not apply; the
                // credential travels in the Authorization header on every request.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // CORS preflight carries no credentials; it must not require auth.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // The container healthcheck hits this unauthenticated.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    /**
     * Answers unauthenticated requests with a bare 401 and no {@code WWW-Authenticate}
     * header, so the browser does not raise its native login dialog and the SPA can
     * present its own password prompt.
     */
    @Bean
    AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder,
                                          @Value("${AUTH_PASSWORD:}") String password) {
        if (password.isBlank()) {
            // Fail closed: refuse to start unauthenticated rather than fall back to a
            // default or absent password.
            throw new IllegalStateException(
                    "AUTH_PASSWORD environment variable must be set — it is the login password for user '"
                            + USERNAME + "'.");
        }
        UserDetails user = User.withUsername(USERNAME)
                .password(encoder.encode(password))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}
