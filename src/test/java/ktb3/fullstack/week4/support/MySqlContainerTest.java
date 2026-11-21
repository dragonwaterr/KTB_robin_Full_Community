package ktb3.fullstack.week4.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 실제 MySQL(Testcontainers) 위에서 도는 통합 테스트의 베이스 클래스.
 * 스키마는 src/test/resources/schema.sql로 만들고 ddl-auto: validate로 엔티티 매핑을 검증한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        // 테스트 전용 값 (Base64, 디코딩 시 32바이트)
        "jwt.access-secret=dGVzdC1hY2Nlc3Mtc2VjcmV0LWtleS0zMi1ieXRlcy0hIQ==",
        "jwt.refresh-secret=dGVzdC1yZWZyZXNoLXNlY3JldC1rZXktMzItYnl0ZXMtIQ==",
        "file.profileDir=build/test-uploads/profile/",
        "file.postDir=build/test-uploads/post/"
})
public abstract class MySqlContainerTest {

    // 테스트 클래스 간 컨테이너 하나를 공유한다 (Spring 컨텍스트 캐시와 수명을 맞춤)
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withInitScript("schema.sql");

    static {
        MYSQL.start();
    }
}
