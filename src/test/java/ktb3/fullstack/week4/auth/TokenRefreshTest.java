package ktb3.fullstack.week4.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import ktb3.fullstack.week4.repository.users.UserRepository;
import ktb3.fullstack.week4.service.users.UserService;
import ktb3.fullstack.week4.support.MySqlContainerTest;
import ktb3.fullstack.week4.support.UserFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

import static ktb3.fullstack.week4.support.UserFixture.unique;
import static org.assertj.core.api.Assertions.assertThat;

/** 액세스 토큰이 만료돼도 리프레시 쿠키로 재발급받고, 로그아웃할 수 있어야 한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenRefreshTest extends MySqlContainerTest {

    // MySqlContainerTest의 jwt.access-secret과 같은 값
    static final String ACCESS_SECRET = "dGVzdC1hY2Nlc3Mtc2VjcmV0LWtleS0zMi1ieXRlcy0hIQ==";
    static final String PASSWORD = "Password1!";

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;

    String email;
    long userId;

    @BeforeEach
    void setUp() {
        email = unique("t") + "@test.com";
        userId = new UserFixture(userService, userRepository).registerActive(email, unique("t"));
    }

    @Test
    void 만료된_액세스_토큰이면_401_access_token_expired() {
        ResponseEntity<String> res = get("/users/me", expiredAccessToken());

        assertThat(res.getStatusCode().value()).isEqualTo(401);
        assertThat(message(res)).isEqualTo("access_token_expired");
    }

    @Test
    void 위조된_액세스_토큰이면_401이지만_재발급_신호는_아니다() {
        SecretKey otherKey = Keys.hmacShaKeyFor("another-secret-key-for-forged-tokens!!".getBytes());
        String forged = Jwts.builder().subject(String.valueOf(userId))
                .expiration(Date.from(Instant.now().plusSeconds(3600))).signWith(otherKey).compact();

        ResponseEntity<String> res = get("/users/me", forged);

        assertThat(res.getStatusCode().value()).isEqualTo(401);
        assertThat(message(res)).isNotEqualTo("access_token_expired");
    }

    @Test
    void 액세스_토큰_없이_리프레시_쿠키만으로_재발급하고_새_토큰으로_요청한다() {
        String cookie = loginRefreshCookie();

        ResponseEntity<String> refreshed = post("/auth/refresh", null, cookie);

        assertThat(refreshed.getStatusCode().value()).isEqualTo(200);
        String newAccess = data(refreshed).get("access_token").asText();
        assertThat(get("/users/me", newAccess).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void 만료된_액세스_토큰이_붙어_있어도_재발급된다() {
        String cookie = loginRefreshCookie();

        ResponseEntity<String> refreshed = post("/auth/refresh", expiredAccessToken(), cookie);

        assertThat(refreshed.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void 액세스_토큰이_만료된_뒤에도_로그아웃하면_저장된_리프레시_토큰이_지워진다() {
        String cookie = loginRefreshCookie();

        ResponseEntity<String> res = post("/auth/logout", expiredAccessToken(), cookie);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE user_id = ?", Integer.class, userId);
        assertThat(rows).isZero();
    }

    @Test
    void 로그아웃한_뒤_옛_리프레시_쿠키로_재발급하면_401_refresh_token_expired() {
        ResponseEntity<String> loggedIn = login();
        String access = data(loggedIn).get("access_token").asText();
        String cookie = cookieOf(loggedIn);
        post("/auth/logout", access, cookie);

        ResponseEntity<String> res = post("/auth/refresh", null, cookie);

        assertThat(res.getStatusCode().value()).isEqualTo(401);
        assertThat(message(res)).isEqualTo("refresh_token_expired");
    }

    @Test
    void 이미_로그아웃한_쿠키로_다시_로그아웃해도_성공한다() {
        String cookie = loginRefreshCookie();
        post("/auth/logout", null, cookie);

        ResponseEntity<String> res = post("/auth/logout", null, cookie);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(message(res)).isEqualTo("logout_success");
    }

    @Test
    void 무효한_리프레시_쿠키로_로그아웃해도_성공하고_쿠키를_지운다() {
        ResponseEntity<String> res = post("/auth/logout", null, "refresh_token=not-a-jwt");

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(message(res)).isEqualTo("logout_success");
        assertThat(res.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(c -> c.startsWith("refresh_token=;") && c.contains("Max-Age=0"));
    }

    String expiredAccessToken() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(ACCESS_SECRET));
        Instant past = Instant.now().minusSeconds(60);
        return Jwts.builder().subject(String.valueOf(userId))
                .issuedAt(Date.from(past.minusSeconds(3600))).expiration(Date.from(past))
                .signWith(key).compact();
    }

    ResponseEntity<String> login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = """
                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD);
        return rest.postForEntity("/auth/login", new HttpEntity<>(json, headers), String.class);
    }

    String loginRefreshCookie() {
        return cookieOf(login());
    }

    String cookieOf(ResponseEntity<String> res) {
        return res.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("refresh_token="))
                .map(c -> c.split(";")[0])
                .findFirst().orElseThrow();
    }

    ResponseEntity<String> get(String path, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    ResponseEntity<String> post(String path, String accessToken, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        if (accessToken != null) headers.setBearerAuth(accessToken);
        headers.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }

    JsonNode body(ResponseEntity<String> res) {
        try {
            return res.getBody() == null ? objectMapper.createObjectNode() : objectMapper.readTree(res.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("응답 파싱 실패: " + res, e);
        }
    }

    String message(ResponseEntity<String> res) {
        JsonNode m = body(res).get("message");
        return m == null ? null : m.asText();
    }

    JsonNode data(ResponseEntity<String> res) {
        return body(res).get("data");
    }
}
