package ktb3.fullstack.week4.user;

import ktb3.fullstack.week4.support.MySqlContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ktb3.fullstack.week4.support.UserFixture.unique;
import static org.assertj.core.api.Assertions.assertThat;

/** 같은 이메일로 동시에 가입하면 사전 검사를 함께 통과하므로 마지막 방어선은 DB UNIQUE다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrentRegisterTest extends MySqlContainerTest {

    static final int N = 100;

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void 동시에_같은_이메일로_100명이_가입하면_1명만_성공하고_나머지는_409() throws Exception {
        String email = unique("race") + "@test.com";
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(N);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            String nickname = unique("r");
            results.add(pool.submit(() -> {
                start.await();
                return rest.postForEntity("/users", registerRequest(email, nickname), String.class)
                        .getStatusCode().value();
            }));
        }
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get());
        pool.shutdown();
        Map<Integer, Long> distribution = statuses.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        System.out.println("concurrent register N=" + N + " distribution=" + distribution);

        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        assertThat(distribution.get(200)).isEqualTo(1L);
        assertThat(distribution.get(409)).isEqualTo((long) N - 1);
        assertThat(rows).isEqualTo(1);
    }

    private HttpEntity<MultiValueMap<String, Object>> registerRequest(String email, String nickname) {
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        String json = """
                {"email":"%s","password":"Password1!","nickname":"%s"}""".formatted(email, nickname);

        HttpHeaders pngHeaders = new HttpHeaders();
        pngHeaders.setContentType(MediaType.IMAGE_PNG);
        ByteArrayResource png = new ByteArrayResource(new byte[]{1, 2, 3}) {
            @Override
            public String getFilename() {
                return "profile.png";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("dto", new HttpEntity<>(json, jsonHeaders));
        body.add("image", new HttpEntity<>(png, pngHeaders));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }
}
