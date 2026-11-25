package ktb3.fullstack.week4.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ktb3.fullstack.week4.dto.posts.PostUploadRequeset;
import ktb3.fullstack.week4.repository.users.UserRepository;
import ktb3.fullstack.week4.service.posts.PostService;
import ktb3.fullstack.week4.service.users.UserService;
import ktb3.fullstack.week4.support.MySqlContainerTest;
import ktb3.fullstack.week4.support.UserFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static ktb3.fullstack.week4.support.UserFixture.unique;
import static org.assertj.core.api.Assertions.assertThat;

/** 대표 프로필 이미지가 0장·2장이 되거나 엉뚱한 이미지를 읽으면 다른 회원의 화면까지 실패한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrimaryProfileImageTest extends MySqlContainerTest {

    static final String PASSWORD = "Password1!";

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired PostService postService;

    UserFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new UserFixture(userService, userRepository);
    }

    @Test
    void DELETE_후_PATCH가_오지_않아도_본인_로그인과_다른_회원의_목록은_200() {
        String emailA = unique("a") + "@test.com";
        long userA = register(emailA, unique("a"));
        createPost(userA);
        String emailB = unique("b") + "@test.com";
        register(emailB, unique("b"));
        String tokenA = token(emailA);
        String tokenB = token(emailB);

        // 교체 도중 PATCH가 실패하거나 도착하지 않은 상황 (응답 코드는 단정하지 않는다)
        rest.exchange("/users/me/profile-image", HttpMethod.DELETE, new HttpEntity<>(bearer(tokenA)), String.class);

        assertThat(login(emailA).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/posts?after=0&limit=100", tokenB).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void 동시에_여러_번_교체해도_로그인과_목록과_상세는_200() throws Exception {
        int rounds = 10;
        int n = 10;
        String emailA = unique("c") + "@test.com";
        long userA = register(emailA, unique("c"));
        long postA = createPost(userA);
        String tokenA = token(emailA);

        int failedRounds = 0;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            for (int r = 0; r < rounds; r++) {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> results = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    results.add(pool.submit(() -> {
                        start.await();
                        return patchImage(tokenA).getStatusCode().value();
                    }));
                }
                start.countDown();
                for (Future<Integer> f : results) f.get();

                boolean ok = login(emailA).getStatusCode().value() == 200
                        && get("/posts?after=0&limit=100", tokenA).getStatusCode().value() == 200
                        && get("/posts/" + postA, tokenA).getStatusCode().value() == 200;
                if (!ok) failedRounds++;
            }
        } finally {
            pool.shutdown();
        }
        System.out.println("concurrent profile image replace rounds=" + rounds + " n=" + n + " failedRounds=" + failedRounds);
        assertThat(failedRounds).isZero();
    }

    @Test
    void 사진을_교체한_뒤_댓글의_프로필_이미지는_새_대표_이미지() throws Exception {
        String emailA = unique("d") + "@test.com";
        long userA = register(emailA, unique("d"));
        long postA = createPost(userA);
        String tokenA = token(emailA);

        String newUrl = data(patchImage(tokenA)).get("profileImageUrl").asText();
        rest.exchange("/posts/" + postA + "/comments?content=hi", HttpMethod.POST,
                new HttpEntity<>(bearer(tokenA)), String.class);

        JsonNode comments = data(get("/posts/" + postA + "/comments?limit=10", tokenA)).get("comments");
        assertThat(comments.get(0).get("authorProfileImageUrl").asText()).isEqualTo(newUrl);
    }

    long register(String email, String nickname) {
        return fixture.registerActive(email, nickname);
    }

    long createPost(long userId) {
        postService.uploadPost(userId, new PostUploadRequeset("title", "content"), null);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM post WHERE user_id = ?", Long.class, userId);
    }

    ResponseEntity<String> login(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = """
                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD);
        return rest.postForEntity("/auth/login", new HttpEntity<>(json, headers), String.class);
    }

    String token(String email) {
        return data(login(email)).get("access_token").asText();
    }

    HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    ResponseEntity<String> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    }

    ResponseEntity<String> patchImage(String token) {
        HttpHeaders pngHeaders = new HttpHeaders();
        pngHeaders.setContentType(MediaType.IMAGE_PNG);
        ByteArrayResource png = new ByteArrayResource(new byte[]{1, 2, 3}) {
            @Override
            public String getFilename() {
                return "profile.png";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("profile_image", new HttpEntity<>(png, pngHeaders));

        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/users/me/profile-image", HttpMethod.PATCH, new HttpEntity<>(body, headers), String.class);
    }

    JsonNode data(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody()).get("data");
        } catch (Exception e) {
            throw new IllegalStateException("응답 파싱 실패: " + response, e);
        }
    }
}
