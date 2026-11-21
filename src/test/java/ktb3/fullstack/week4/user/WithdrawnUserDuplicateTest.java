package ktb3.fullstack.week4.user;

import ktb3.fullstack.week4.common.error.codes.UserError;
import ktb3.fullstack.week4.common.error.exception.ApiException;
import ktb3.fullstack.week4.dto.users.NicknameUpdateRequest;
import ktb3.fullstack.week4.repository.users.UserRepository;
import ktb3.fullstack.week4.service.users.UserService;
import ktb3.fullstack.week4.support.MySqlContainerTest;
import ktb3.fullstack.week4.support.UserFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static ktb3.fullstack.week4.support.UserFixture.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 탈퇴 회원의 이메일·닉네임은 재사용할 수 없다 (정책: 이미 쓰인 값과 똑같이 409). */
@AutoConfigureMockMvc
class WithdrawnUserDuplicateTest extends MySqlContainerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;

    UserFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new UserFixture(userService, userRepository);
    }

    @Test
    void 탈퇴_회원_이메일로_가입하면_409() throws Exception {
        String email = unique("e") + "@test.com";
        fixture.registerWithdrawn(email, unique("n"));

        register(email, unique("n"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("existing_email"));
    }

    @Test
    void 대소문자만_다른_탈퇴_회원_이메일로_가입하면_409() throws Exception {
        String u = unique("");
        fixture.registerWithdrawn("Dup" + u + "@Test.com", unique("n"));

        register("dup" + u + "@test.com", unique("n"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("existing_email"));
    }

    @Test
    void 탈퇴_회원_닉네임으로_가입하면_409() throws Exception {
        String nickname = unique("n");
        fixture.registerWithdrawn(unique("e") + "@test.com", nickname);

        register(unique("e") + "@test.com", nickname)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("existing_nickname"));
    }

    @Test
    void 탈퇴_회원_닉네임으로_닉네임을_바꾸면_예외() {
        String nickname = unique("n");
        fixture.registerWithdrawn(unique("e") + "@test.com", nickname);
        long id = fixture.registerActive(unique("e") + "@test.com", unique("n"));

        Throwable thrown = catchThrowable(() ->
                userService.changeNickname(id, new NicknameUpdateRequest(nickname)));

        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).getCode()).isEqualTo(UserError.EXISTING_NICKNAME);
    }

    @Test
    void 가용성_API는_탈퇴_회원_이메일을_사용_불가로_응답() throws Exception {
        String email = unique("e") + "@test.com";
        fixture.registerWithdrawn(email, unique("n"));

        mockMvc.perform(get("/availability/email").param("value", email))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("existing_email"));
    }

    @Test
    void 가용성_API는_탈퇴_회원_닉네임을_사용_불가로_응답() throws Exception {
        String nickname = unique("n");
        fixture.registerWithdrawn(unique("e") + "@test.com", nickname);

        mockMvc.perform(get("/availability/nickname").param("value", nickname))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("existing_nickname"));
    }

    @Test
    void 활성_회원_이메일로_가입하면_409() throws Exception {
        String email = unique("e") + "@test.com";
        fixture.registerActive(email, unique("n"));

        register(email, unique("n"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("existing_email"));
    }

    @Test
    void 처음_쓰는_이메일은_사용_가능() throws Exception {
        mockMvc.perform(get("/availability/email").param("value", unique("new") + "@test.com"))
                .andExpect(status().isOk());
    }

    @Test
    void 컬럼보다_긴_닉네임은_중복_응답이_아니다() throws Exception {
        // varchar(255)를 넘는 값은 "Data too long" → 안전망 409(duplicate_resource)로 오인하면 안 된다
        register(unique("e") + "@test.com", "n".repeat(300))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(409));
    }

    private ResultActions register(String email, String nickname) throws Exception {
        String json = """
                {"email":"%s","password":"Password1!","nickname":"%s"}""".formatted(email, nickname);
        MockMultipartFile dto = new MockMultipartFile(
                "dto", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/users").file(dto).file(UserFixture.pngImage()));
    }
}
