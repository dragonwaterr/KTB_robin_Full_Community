# REST API 설계

API 명세를 정할 때 내린 결정과 코드 리뷰 피드백을 반영한 내용을 정리한다. 엔드포인트별 상세는 [도메인 명세](../../README.md#도메인-명세)를 본다.

## 1. 수정 메서드: PUT vs PATCH

**요구사항**: 게시글 수정은 제목·본문·이미지를 함께 받는다. 다른 수정 API(닉네임, 비밀번호, 댓글)는 일부 필드만 바꾼다.

| 옵션 | 내용 |
|---|---|
| PUT | 게시글 수정만 리소스 전체 교체로 본다 |
| PATCH | 모든 수정 API를 부분 수정으로 통일한다 |

**선택: PATCH로 통일**

- 게시글 이미지는 필수가 아니어서 게시글 수정은 부분 수정에 가깝다.
- 다른 수정 API와 메서드가 일관된다.

현재 코드: `PATCH /posts/{id}`, `PATCH /posts/{postId}/comments/{commentId}`, `PATCH /users/me/nickname`, `PATCH /users/me/password`, `PATCH /users/me/profile-image`

## 2. 응답의 `data: null`

**요구사항**: 데이터가 없는 응답(삭제 성공, 오류 등)에서 `data` 필드를 어떻게 다룰지 정한다.

| 옵션 | 내용 |
|---|---|
| 필드 생략 | 데이터가 없으면 `data`를 응답에 넣지 않는다 |
| `data: null` 유지 | 모든 응답이 `message`, `data` 두 필드를 가진다 |

**선택: `data: null` 유지** — API를 추가하거나 수정할 때 응답 구조를 바꾸지 않아도 된다.

현재 코드: `ApiResponse#ok(String)`, `ApiResponse#error`가 `data = null`로 응답을 만든다.

## 3. 게시글 목록(무한 스크롤)

**요구사항**: 게시글 목록은 스크롤을 내리면 이어서 불러온다. 사용자가 목록을 끝까지 보지 않고 다른 화면으로 이동할 수 있다.

| 옵션 | 내용 |
|---|---|
| 전체 전송 | 목록 전체를 한 번에 응답한다 |
| limit + 커서 | `limit`개만 잘라 응답하고, 다음 요청은 커서(`next_cursor`)로 이어서 받는다 |

**선택: limit + 커서** — 전체를 보내면 끝까지 보지 않고 이동할 때 나머지 전송이 낭비된다.

### 3.1 offset 방식에서 커서 방식으로

처음 구현은 offset + limit로 게시글을 직접 가져왔다. soft delete와 함께 쓰면서 다음 경우가 생겼다.

- 조회 범위의 게시글이 전부 삭제된 글이면 결과가 비어 오류가 난다.
- 삭제된 글을 걸러 `limit`개를 채우려면 매번 선형 탐색이 필요하다.

현재는 마지막으로 받은 게시글 id를 커서로 쓴다.

- `GET /posts?after=&limit=`
- `PostRepository#findByIdGreaterThan`이 `id > after`인 게시글을 id 오름차순으로 `limit`개 `Slice`로 조회한다(`PostService#getPostList`).
- 삭제된 글은 `@SQLRestriction`이 조회에서 제외하므로 `limit`개를 채워 응답한다.

## 4. 코드 리뷰 피드백 반영

| # | 피드백 | 반영 | 현재 코드 |
|---|---|---|---|
| 1 | 댓글 등록 `POST /comments`의 URI에 게시글 계층이 없다 | 다른 API처럼 계층을 표현한다 | `POST /posts/{postId}/comments` |
| 2 | 회원 정보는 PATCH, 프로필 이미지는 PUT이다 | 프로필 이미지를 회원 정보의 한 필드로 보고 PATCH로 바꾼다 | `PATCH /users/me/profile-image` |
| 3 | 404를 상세 조회에만 정의했다 | 조회·수정·삭제 모두 404를 정의한다 | `PostError.CANNOT_FOUND_POST`, `CommentError.CANNOT_FOUND_COMMENT`, `UserError.CANNOT_FOUND_USER` |
| 4 | `check-email` 등 URI에 동사가 있다 | "사용 가능 여부"도 리소스로 보고 `availability`로 바꾼다 | `GET /availability/email`, `GET /availability/nickname` |
| 5 | `/auth/login`은 동사다. `POST /auth/sessions`도 있다 | `sessions`는 직관적이지 않다고 보고 `/auth/login`을 유지한다 | `POST /auth/login` |
