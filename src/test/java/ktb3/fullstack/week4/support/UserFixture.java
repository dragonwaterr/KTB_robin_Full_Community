package ktb3.fullstack.week4.support;

import ktb3.fullstack.week4.dto.users.JoinRequest;
import ktb3.fullstack.week4.repository.users.UserRepository;
import ktb3.fullstack.week4.service.users.UserService;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

/** 통합 테스트용 회원 준비 헬퍼. 서비스 계층을 그대로 거쳐 가입·탈퇴한다. */
public class UserFixture {

    private final UserService userService;
    private final UserRepository userRepository;

    public UserFixture(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    public long registerActive(String email, String nickname) {
        userService.register(JoinRequest.builder()
                .email(email)
                .password("Password1!")
                .nickname(nickname)
                .build(), pngImage());
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    public long registerWithdrawn(String email, String nickname) {
        long id = registerActive(email, nickname);
        userService.withdrawMemberShip(id);
        return id;
    }

    public static MockMultipartFile pngImage() {
        return new MockMultipartFile("image", "profile.png", "image/png", new byte[]{1, 2, 3});
    }

    public static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }
}
