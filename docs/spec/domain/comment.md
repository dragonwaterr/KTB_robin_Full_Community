# 댓글 (comment)

댓글 등록·목록·수정·삭제를 정리한다. 모든 API는 게시글 하위 경로(`/posts/{postId}/comments`)로 제공한다.

## 1. 배경

- 댓글은 본문, 작성자, 소속 게시글을 가진다.
- 목록은 `modified_at`과 `id`를 함께 쓰는 커서 방식이다.
- 삭제는 soft delete다.

## 2. 목표가 아닌 것

- 게시글 응답의 댓글 수(`commentCount`)는 [post.md](post.md)에서 다룬다.
- 대댓글(댓글의 댓글) 구조는 없다. `Comment`는 `User`와 `Post`만 참조한다.

## 3. 스키마

`comment` ([erd.md](../../erd.md) 참고)

| 컬럼 | 설명 |
|---|---|
| `id` | 기본키 |
| `user_id` | 작성자 |
| `post_id` | 게시글 |
| `content` | 본문 |
| `created_at`, `modified_at`, `deleted` | 공통 컬럼 |

## 4. API 명세

공통: 인증이 필요하다(`anyRequest().hasRole("USER")`). 응답 본문은 `ApiResponse`. 경로의 `postId`로 게시글 존재를 확인한다(없으면 404 `cannot_found_post`).

### 4.1 등록 `POST /posts/{postId}/comments`

- 요청: 쿼리/폼 파라미터 `content`
- 처리 로직 (`CommentService#addComment`): 회원·게시글 조회 → `CommentDomainBuilder#buildComment` → `Comment#linkPost` → 저장
- 응답: `message = comment_add_success`
- 오류: 404 `cannot_found_user`, 404 `cannot_found_post`

### 4.2 목록 `GET /posts/{postId}/comments?modifiedBefore=&cursorId=&limit=`

- 요청
  - `modifiedBefore`(선택): `yyyy-MM-dd HH:mm:ss.SSSSSS` 형식 문자열 (`CustomDateTimeFormatter`가 소수 6자리를 요구)
  - `cursorId`(선택): 댓글 id
  - `limit`: 한 번에 가져올 개수
- 처리 로직 (`CommentService#getCommentList`)
  1. 회원 조회
  2. `modifiedBefore` 또는 `cursorId`가 없으면 `time = 현재 + 100년`, `cursorId = Long.MAX_VALUE`로 처음부터 조회
  3. `CommentRepository#findByModifiedAtLessThan` — `modifiedAt < :time` 또는 (`modifiedAt = :time` 이고 `id < :cursorId`), 정렬은 `modifiedAt DESC, id DESC`, `Slice`로 `limit`개
  4. 댓글마다 작성자 닉네임, 본문, 작성자 프로필 이미지 URL(`getProfileImages().getFirst()`), `modified_at` 구성
  5. `next_cursor_id` = 응답의 마지막 댓글 id − 1 (비어 있으면 null), `has_next` = `Slice#hasNext`
- 응답: `message = comments_fetch_success`, `data` = `CommentListResponse`

| 필드 | 설명 |
|---|---|
| `comments[]` | `id`, `author`, `content`, `authorProfileImageUrl`, `modified_at` |
| `next_cursor_id` | 다음 요청의 `cursorId` |
| `has_next` | 다음 페이지 존재 여부 |

- 오류: 404 `cannot_found_user`

### 4.3 수정 `PATCH /posts/{postId}/comments/{commentId}`

- 요청: `content`
- 처리 로직 (`CommentService#editComment`)
  1. 회원·게시글 조회
  2. `content`가 null 또는 공백이면 `GenericError.INVALID_REQUEST`
  3. 댓글 조회. 없으면 `CommentError.CANNOT_FOUND_COMMENT`
  4. 작성자가 아니면 `CommentError.CANNOT_EDIT_OTHERS_COMMENT`
  5. `Comment#editContent`
- 응답: `message = comment_edit_success`
- 오류: 400 `invalid_request`, 404 `cannot_found_user`, 404 `cannot_found_post`, 404 `cannot_found_comment`, 403 `cannot_edit_others_comment`

### 4.4 삭제 `DELETE /posts/{postId}/comments/{commentId}`

- 처리 로직 (`CommentService#removeComment`): 회원·게시글 조회 → 댓글 조회 → 작성자가 아니면 `CommentError.CANNOT_DELETE_OTHERS_COMMENT` → `deleteEntity`
- 응답: `message = comment_remove_success`
- 오류: 404 `cannot_found_user`, 404 `cannot_found_post`, 404 `cannot_found_comment`, 403 `cannot_delete_others_comment`

## 5. 용어

| 용어 | 설명 |
|---|---|
| 커서 | 목록의 다음 시작 위치. 댓글은 (`modified_at`, `id`) 쌍 |
| `Slice` | 다음 페이지 존재 여부만 아는 페이지 결과 |
| 소수 6자리 | `JacksonConfig`가 `LocalDateTime`을 `yyyy-MM-dd HH:mm:ss.SSSSSS`로 직렬화하고, 커서로 받을 때도 같은 형식을 파싱한다 |
