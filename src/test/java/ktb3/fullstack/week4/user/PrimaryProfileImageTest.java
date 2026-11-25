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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Autowired TransactionTemplate transactionTemplate;

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

    @Test
    void 대표_이미지_없이_회원을_넣으면_DB가_거부() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (deleted, created_at, modified_at, email, nickname, primary_profile_image_id) VALUES (0, NOW(), NOW(), ?, ?, NULL)",
                unique("np") + "@test.com", unique("np")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 탈퇴한_회원이_있어도_다른_회원의_목록과_상세와_댓글은_200() {
        String emailA = unique("e") + "@test.com";
        long userA = register(emailA, unique("e"));
        createPost(userA);
        String emailB = unique("f") + "@test.com";
        long userB = register(emailB, unique("f"));
        long postB = createPost(userB);
        rest.exchange("/posts/" + postB + "/comments?content=hi", HttpMethod.POST,
                new HttpEntity<>(bearer(token(emailA))), String.class);

        userService.withdrawMemberShip(userA);

        String tokenB = token(emailB);
        assertThat(get("/posts?after=0&limit=100", tokenB).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/posts/" + postB, tokenB).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/posts/" + postB + "/comments?limit=10", tokenB).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void 닉네임_변경이_그사이_바뀐_대표_이미지를_되돌리지_않는다() throws Exception {
        String emailA = unique("h") + "@test.com";
        long userA = register(emailA, unique("h"));

        // 닉네임 트랜잭션이 회원을 읽은 뒤, 커밋 전에 다른 트랜잭션이 대표 이미지를 바꾸고 커밋한다
        String newUrl = transactionTemplate.execute(status -> {
            userRepository.findById(userA).orElseThrow().changeNickName(unique("n"));
            try {
                return Executors.newSingleThreadExecutor()
                        .submit(() -> userService.changeProfileImage(userA, UserFixture.pngImage()))
                        .get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        String primaryUrl = jdbcTemplate.queryForObject(
                "SELECT pi.image_url FROM users u JOIN profile_image pi ON pi.id = u.primary_profile_image_id WHERE u.id = ?",
                String.class, userA);
        assertThat(primaryUrl).isEqualTo(newUrl);
    }

    // 타이밍 의존이라 결정적이지 않다. 교체가 users 행 전체를 덮어쓰는 회귀를 잡는 보조 테스트
    @Test
    void 교체가_동시에_바뀐_닉네임을_되돌리지_않는다() throws Exception {
        String emailA = unique("g") + "@test.com";
        long userA = register(emailA, unique("g"));
        String tokenA = token(emailA);
        String newNickname = unique("n");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> image = pool.submit(() -> { start.await(); return patchImage(tokenA); });
            Future<?> nickname = pool.submit(() -> {
                start.await();
                HttpHeaders headers = bearer(tokenA);
                headers.setContentType(MediaType.APPLICATION_JSON);
                return rest.exchange("/users/me/nickname", HttpMethod.PATCH,
                        new HttpEntity<>("{\"newNickname\":\"" + newNickname + "\"}", headers), String.class);
            });
            start.countDown();
            image.get();
            nickname.get();
        } finally {
            pool.shutdown();
        }

        String current = jdbcTemplate.queryForObject("SELECT nickname FROM users WHERE id = ?", String.class, userA);
        assertThat(current).isEqualTo(newNickname);
    }

    long register(String email, String nickname) {
        return fixture.registerActive(email, nickname);
    }

    long createPost(long userId) {
        postService.uploadPost(userId, new PostUploadRequeset("title", "content"), UserFixture.pngImage());
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
