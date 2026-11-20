# 좋아요 (like)

게시글 좋아요 추가·취소를 정리한다.

## 1. 배경

- 좋아요는 회원과 게시글의 조합마다 행 하나(`likes`)를 가지며, `is_liked` 값으로 상태를 표현한다.
- 취소해도 행을 지우지 않고 `is_liked`를 바꾼다. 집계와 여부 조회는 `is_liked = true`인 행만 센다.

## 2. 목표가 아닌 것

- 게시글 응답의 좋아요 수(`likeCount`)와 `isLiked`는 [post.md](post.md)에서 다룬다.
- 좋아요 목록 조회 API는 없다.

## 3. 스키마

`likes` ([erd.md](../../erd.md) 참고)

| 컬럼 | 설명 |
|---|---|
| `id` | 기본키 |
| `user_id` | 회원 |
| `post_id` | 게시글 |
| `is_liked` | 현재 좋아요 상태 |
| `created_at`, `modified_at`, `deleted` | 공통 컬럼 |

## 4. API 명세

공통: 인증이 필요하다(`anyRequest().hasRole("USER")`). 응답 본문은 `ApiResponse`.

### 4.1 좋아요 `POST /posts/{id}/likes`

- 처리 로직 (`LikeService#likePost`)
  1. 회원·게시글 조회
  2. `LikeRepository#findByPostIdAndUserId`로 기존 행 조회
  3. 행이 없으면 `LikeDomainBuilder#buildLike`로 `is_liked = true`인 행을 만들어 저장
  4. 행이 있으면 `Like#flipIsLiked`로 `is_liked`를 반전
- 응답: `message = post_like_success`
- 오류: 404 `cannot_found_user`, 404 `cannot_found_post`

### 4.2 좋아요 취소 `DELETE /posts/{id}/likes`

- 처리 로직 (`LikeService#unlikePost`)
  1. 회원·게시글 조회
  2. `ErrorCheckServiceImpl#checkCanNotFoundLike`로 행 조회. 없으면 `GenericError.INVALID_REQUEST`
  3. `Like#flipIsLiked`
- 응답: `message = post_unlike_success`
- 오류: 400 `invalid_request`, 404 `cannot_found_user`, 404 `cannot_found_post`

## 5. 용어

| 용어 | 설명 |
|---|---|
| `is_liked` | 좋아요 상태 컬럼. 취소 시 행을 삭제하지 않고 값을 바꾼다 |
| `flipIsLiked` | `is_liked`를 현재 값의 반대로 바꾸는 `Like`의 메서드 |
