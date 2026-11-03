package ktb3.fullstack.week4.dto.posts;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 게시글 상세조회 JPQL 생성자 표현식(PostRepository.findPostDetailByIdAndUserId)의 결과를 받는 DTO.
 *
 * 주의: @AllArgsConstructor 는 필드 선언 순서대로 생성자를 만든다.
 * 필드 순서가 JPQL SELECT 절의 인자 순서와 다르면 런타임에 생성자를 찾지 못하므로 순서를 바꾸지 말 것.
 * 집계(COUNT)와 LEFT JOIN 결과는 null 이거나 래퍼 타입으로 넘어오므로 모두 래퍼 타입으로 받는다.
 */
@Getter
@AllArgsConstructor
public class PostDetailDto {

    private final Long id;                      // p.id
    private final String title;                 // p.title
    private final String content;               // p.content
    private final LocalDateTime modifiedAt;     // p.modifiedAt
    private final Long authorId;                // u.id
    private final String authorNickname;        // u.nickname
    private final String authorProfileImageUrl; // pi.imageUrl (LEFT JOIN)
    private final Long viewCount;               // pv.viewCount (LEFT JOIN)
    private final Long likeCount;               // COUNT(Like, isLiked = true)
    private final Long commentCount;            // COUNT(Comment)
    private final Boolean liked;                // 요청 사용자의 좋아요 여부
}

