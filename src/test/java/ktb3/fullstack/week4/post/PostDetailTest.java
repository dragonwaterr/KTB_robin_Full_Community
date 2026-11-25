package ktb3.fullstack.week4.post;

import ktb3.fullstack.week4.dto.posts.PostDetailResponse;
import ktb3.fullstack.week4.dto.posts.PostUploadRequeset;
import ktb3.fullstack.week4.repository.users.UserRepository;
import ktb3.fullstack.week4.service.posts.PostService;
import ktb3.fullstack.week4.service.users.UserService;
import ktb3.fullstack.week4.support.MySqlContainerTest;
import ktb3.fullstack.week4.support.UserFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static ktb3.fullstack.week4.support.UserFixture.unique;
import static org.assertj.core.api.Assertions.assertThat;

/** 게시글 이미지는 등록할 때 선택이므로 상세 조회도 이미지 없이 동작해야 한다. */
class PostDetailTest extends MySqlContainerTest {

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired PostService postService;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void 이미지_없이_쓴_게시글도_상세를_조회하면_대표_이미지는_null() {
        long userId = new UserFixture(userService, userRepository).registerActive(unique("p") + "@test.com", unique("p"));
        postService.uploadPost(userId, new PostUploadRequeset("title", "content"), null);
        long postId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM post WHERE user_id = ?", Long.class, userId);

        PostDetailResponse.PostInfo post = postService.getSinglePostDeatil(userId, postId).getPost();

        assertThat(post.getPrimaryImageUrl()).isNull();
        assertThat(post.getRestImageUrls()).isEmpty();
    }
}
