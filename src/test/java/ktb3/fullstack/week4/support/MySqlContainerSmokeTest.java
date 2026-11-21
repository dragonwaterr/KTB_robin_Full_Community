package ktb3.fullstack.week4.support;

import ktb3.fullstack.week4.domain.users.Role;
import ktb3.fullstack.week4.domain.users.User;
import ktb3.fullstack.week4.repository.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class MySqlContainerSmokeTest extends MySqlContainerTest {

    @Autowired
    UserRepository userRepository;

    @Test
    void 스키마_검증을_통과하고_회원을_저장_조회한다() {
        User saved = userRepository.saveAndFlush(User.builder()
                .email("smoke@test.com")
                .hashedPassword("hashed")
                .nickname("smoke")
                .role(Role.ROLE_USER.toString())
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(userRepository.findByEmail("smoke@test.com")).isPresent();
    }
}
