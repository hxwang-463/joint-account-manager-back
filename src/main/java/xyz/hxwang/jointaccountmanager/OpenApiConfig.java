package xyz.hxwang.jointaccountmanager;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BASIC_AUTH = "basicAuth";

    /**
     * Read from the jar manifest rather than written here, so the documented
     * version is whatever is actually running. A hardcoded one goes stale the
     * first time the pom is bumped and then quietly misreports.
     */
    private static String runningVersion() {
        String version = OpenApiConfig.class.getPackage().getImplementationVersion();
        // Absent when running from exploded classes, e.g. in an IDE.
        return version == null ? "dev" : version;
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Joint Account Manager")
                        .version(runningVersion())
                        .description("""
                                Bill tracking and itemised spend analysis.

                                Every endpoint requires HTTP Basic authentication as `AdminUser`.
                                Use the **Authorize** button before trying anything out — the API
                                deliberately does not send `WWW-Authenticate`, so the browser will
                                not offer its own login box and requests would otherwise just 401.
                                """))
                // Declaring the scheme is what puts the Authorize button in Swagger UI.
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("Username is always AdminUser; the password is AUTH_PASSWORD.")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
