package xyz.hxwang.jointaccountmanager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS headers on responses the security chain rejects.
 *
 * <p>The failure this guards against is invisible in production and fatal in
 * development. CORS is configured at the MVC level, but a 401 is written by the
 * authentication entry point inside the security filter chain, which never
 * reaches the dispatcher — so without CORS configured on the chain itself, a
 * rejected request comes back with no {@code Access-Control-Allow-Origin} at
 * all.
 *
 * <p>A browser then refuses to hand the response to the page, and {@code fetch}
 * rejects with an opaque network error instead of yielding a 401. The SPA's
 * whole login flow hangs off reading that status, so it cannot tell a wrong
 * password from an unreachable server, and the password prompt never appears.
 *
 * <p>Production is same-origin and so never exercises any of this, which is
 * precisely why it needs a test: the only symptom is a local dev server that
 * cannot log in.
 */
@WebMvcTest(controllers = RecordController.class)
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = "AUTH_PASSWORD=test-password")
class CorsOnRejectedRequestTest {

    private static final String DEV_ORIGIN = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecordService recordService;

    @Test
    @DisplayName("A rejected request still says which origins may read it")
    void unauthenticatedRequestCarriesCorsHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/records").header("Origin", DEV_ORIGIN))
                .andExpect(status().isUnauthorized())
                // Without this the browser hides the 401 behind a CORS error and the
                // login prompt never opens.
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    @DisplayName("A wrong password is reported as a 401 the page can actually read")
    void badCredentialsCarryCorsHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/records")
                        .header("Origin", DEV_ORIGIN)
                        .header("Authorization", "Basic " + java.util.Base64.getEncoder()
                                .encodeToString("AdminUser:wrong".getBytes())))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    @DisplayName("Preflight is answered without a credential, since it cannot carry one")
    void preflightIsPermitted() throws Exception {
        mockMvc.perform(options("/api/v1/records")
                        .header("Origin", DEV_ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    @DisplayName("Adding CORS to the security chain did not open the API up")
    void corsDoesNotReplaceAuthentication() throws Exception {
        // The headers say who may *read* the response; they are not a grant of
        // access. An unauthenticated call must still be refused.
        mockMvc.perform(get("/api/v1/records").header("Origin", DEV_ORIGIN))
                .andExpect(status().isUnauthorized());

        org.mockito.Mockito.verifyNoInteractions(recordService);
    }
}
