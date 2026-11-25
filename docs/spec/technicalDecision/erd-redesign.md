# ERD 재설계

JPA 도입 전에 ERD를 다시 설계하면서 내린 결정을 정리한다. 현재 테이블 구조는 [erd.md](../../erd.md)를 본다.

## 1. 생성·수정 일시

**요구사항**: 데이터가 언제 만들어지고 바뀌었는지 추적하고, 최신순으로 정렬할 수 있어야 한다.

**선택: 모든 테이블에 생성 일시를 둔다** — 통계·추적과 최신순 정렬에 쓴다.

현재 코드: `BaseTimeEntity`가 `created_at`, `modified_at`(`DATETIME(6)`)을 모든 엔티티에 제공한다. 댓글 목록은 `modified_at` 내림차순으로 정렬한다([comment.md](../domain/comment.md)).

## 2. 삭제 방식: 물리 삭제 vs soft delete

**요구사항**: 회원·게시글·댓글·좋아요·이미지를 삭제할 수 있어야 한다.

| 옵션 | 내용 |
|---|---|
| 물리 삭제 | 행을 `DELETE`한다 |
| soft delete | 행을 남기고 삭제 여부 컬럼만 바꾼다 |

**선택: 조회수를 제외한 테이블에 soft delete**

- 복구가 쉽다.
- 물리 삭제보다 비용과 위험이 낮다.

현재 코드: `SoftDeletetionEntity`의 `deleted` 컬럼과 `deleteEntity()`. `users`, `post`, `comment`, `likes`, `profile_image`, `post_image`가 상속한다. `post_view`와 `refresh_token`에는 `deleted` 컬럼이 없다. 조회 시 삭제된 행을 거르는 방식은 [soft-delete-query.md](soft-delete-query.md)를 본다.

## 3. 회원·게시글 사진

**요구사항**: 회원 프로필과 게시글에 사진을 등록한다. 나중에 여러 장을 등록하게 될 수 있다.

| 옵션 | 내용 |
|---|---|
| 컬럼 | `users`, `post` 테이블에 사진 컬럼을 둔다 |
| 별도 테이블 | 사진 테이블을 따로 두고 소유자를 참조한다 |

**선택: 별도 테이블** — 여러 장 등록 요구가 생기면 컬럼이 계속 늘어나는 구조를 피한다.

현재 코드: `profile_image`, `post_image` 테이블. 공통 부모 `Image`(`image_url`, `display_order`), `is_primary`는 `post_image`에만 있고 대표 프로필 이미지는 `users.primary_profile_image_id`가 가리킨다. 상세는 [image.md](../domain/image.md).

## 4. 좋아요 기본키

**요구사항**: 좋아요는 회원과 게시글의 조합이다.

| 옵션 | 내용 |
|---|---|
| 복합 기본키 | `(post_id, user_id)` |
| 대리키 | 별도 `id` 컬럼 |

**선택: 대리키**

- 요구사항이 바뀔 때 유연하다.
- JPA에서 복합키 구현이 복잡하다.

현재 코드: `Like#id`(`@Id @GeneratedValue`), `user_id`·`post_id`는 `@ManyToOne` 외래키.

## 5. 좋아요 상태

**요구사항**: 좋아요 추가와 취소가 자주 일어난다.

| 옵션 | 내용 |
|---|---|
| 행 추가·삭제 | 좋아요 시 행을 넣고 취소 시 지운다 |
| 상태 컬럼 | 행을 남기고 `is_liked` 값을 바꾼다 |

**선택: 상태 컬럼** — 추가·삭제가 잦은데 매번 행을 넣고 지우는 것은 비효율이라고 판단했다.

현재 코드: `Like#isLiked`(`is_liked`), `Like#flipIsLiked`. 상세는 [like.md](../domain/like.md).
