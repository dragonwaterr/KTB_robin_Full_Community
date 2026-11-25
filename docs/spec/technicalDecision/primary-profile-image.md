# 대표 프로필 이미지

"회원당 대표 프로필 이미지는 정확히 1장"이라는 규칙을 앱 코드 대신 스키마가 지키도록 바꾼 결정이다. 현재 구조는 [erd.md](../../erd.md), 교체 흐름은 [image.md](../domain/image.md) 6장을 본다.

## 1. 문제

대표 여부는 `profile_image.is_primary`였고, 대표를 1장으로 유지하는 일은 FE 호출 순서와 `UserService#changeProfileImage`가 맡았다. 대표를 읽는 쪽은 1장을 전제로 단건 조회했다(`findByUserIdAndIsPrimaryIsTrue(...).getImageUrl()`).

| 상태 | 만들어지는 경로 | 결과 |
|---|---|---|
| 0장 | FE가 `DELETE /users/me/profile-image` → `PATCH` 순서로 호출. `DELETE`만 성공하고 `PATCH`가 실패(용량 초과, 네트워크, 탭 닫힘) | 본인 로그인 500. 그 회원 글이 섞인 게시글 목록은 **모든 회원에게** 500 |
| 2장 이상 | 교체가 잠금 없이 "기존 대표 해제 → 새 대표 저장". 동시 요청은 서로의 커밋 전 INSERT를 보지 못해 각자 대표를 만든다 | 단건 조회 결과가 여러 건 → 로그인·목록·상세 500 |
| 엉뚱한 이미지 | 댓글 목록이 대표가 아닌 `getProfileImages().getFirst()`를 읽음 | 댓글에 옛 이미지 |

같은 회원이 `PATCH` 10개를 동시에 보내면 요청은 모두 200인데 대표가 10장이 됐다(10라운드 모두 재현).

`PATCH`가 이미 기존 대표 해제와 새 대표 지정을 한 트랜잭션에서 하므로, 교체에 `DELETE`는 필요 없었다.

## 2. 결정

0장과 2장을 함께 막는 방법을 비교했다.

| 옵션 | 0장 | 2장 | 동시 교체 결과 | 판단 |
|---|---|---|---|---|
| **회원이 대표를 가리킴 (선택)**: `users.primary_profile_image_id` NOT NULL·UNIQUE·FK | 스키마가 막음 (NOT NULL) | 구조상 불가능 (값이 한 칸) | 모두 200, 나중에 커밋한 쪽이 대표 (`users` 행 락) | 스키마 변경과 기존 데이터 이전이 필요하다 |
| 회원 행 비관적 락(`FOR UPDATE NOWAIT`) + 대표 UNIQUE(생성 컬럼) | 앱 코드 | 락 + 스키마 | 1개 200, 나머지 409 | 락을 트랜잭션 첫 쿼리에 둬야 한다(REPEATABLE READ 스냅샷). `IDENTITY`라 INSERT가 해제 UPDATE보다 먼저 나가므로 해제 후 `flush`가 필요하다 |
| 회원 행 비관적 락만 | 앱 코드 | 앱 코드 | 1개 200, 나머지 409 | 가장 작지만 락을 빠뜨린 경로나 직접 실행한 SQL은 못 막는다 |

낙관적 락(`@Version`)은 `users`의 모든 수정 경로에 영향을 주고, 충돌이 커밋 시점에 드러나 파일을 저장한 뒤에야 실패하므로 제외했다.

규칙이 깨지면 다른 회원까지 실패하는 문제라서, 정상 경로뿐 아니라 실수한 경로까지 스키마가 막는 첫 번째 옵션을 택했다. 같은 회원이 사진을 연달아 바꾸는 상황이라 동시 교체는 거절보다 "나중 것이 남는다"가 자연스럽다고 봤다.

### 2.1 대표 정보는 한 곳에만

`profile_image.is_primary`를 지웠다. 남겨 두면 포인터와 어긋날 수 있다. `is_primary`는 공통 부모 `Image`에서 `PostImage`로 옮겼다(게시글 이미지는 계속 쓴다).

### 2.2 포인터는 전용 쿼리로만 바꾼다

Hibernate는 엔티티를 수정하면 `users`의 모든 컬럼을 UPDATE한다. 그대로 두면 두 방향으로 덮어쓰기가 생긴다.

- 교체가 엔티티 수정으로 포인터를 바꾸면, 그사이 커밋된 닉네임을 옛 값으로 덮는다.
- 닉네임·비밀번호 변경이 회원을 읽은 뒤 교체가 커밋되면, 닉네임 쪽 커밋이 포인터를 옛 이미지로 덮는다.

그래서 `User#primaryProfileImage`에 `@JoinColumn(updatable = false)`를 걸고, 교체는 `UserRepository#updatePrimaryProfileImage`(`update User u set u.primaryProfileImage = :image where u.id = :userId`)로 포인터 컬럼만 바꾼다.

## 3. 변경

- 스키마: `users.primary_profile_image_id bigint NOT NULL`, UNIQUE, FK → `profile_image(id)`. `profile_image.is_primary` 제거
- 가입 `UserService#register`: 이미지 저장 → `User#changePrimaryProfileImage` → 회원 저장. `IDENTITY`라 이미지 id가 먼저 나와 NOT NULL을 만족한다
- 교체 `UserService#changeProfileImage`: 기존 이미지 `display_order` +1 → 새 이미지 저장 → `updatePrimaryProfileImage`
- 읽기: `AuthService#login`, `PostService#getPostList`, `PostRepository#findPostDetailByIdAndUserId`(`LEFT JOIN u.primaryProfileImage pi`), `CommentService#getCommentList`가 포인터를 읽는다
- `DELETE /users/me/profile-image` 제거. FE(`editUserInfo.js`)는 `PATCH` 한 번만 호출한다
- 기존 DB: 컬럼을 NULL 허용으로 추가 → 삭제 안 된 대표 중 최신, 없으면 삭제 안 된 이미지 중 최신, 그래도 없으면 가장 최근 이미지로 채움(활성 회원이면 그 이미지를 되살림) → NOT NULL·UNIQUE·FK 추가 → `is_primary` 삭제

탈퇴 회원의 포인터는 soft delete된 이미지를 가리킨다. 탈퇴할 때 글·댓글도 soft delete되므로 이 포인터를 읽는 경로는 없다(`UserDeleteFacade#deleteUser`).

## 4. 검증

Testcontainers(MySQL 8.4) `PrimaryProfileImageTest`:

| 시나리오 | 변경 전 | 변경 후 |
|---|---|---|
| `DELETE` 후 `PATCH` 미도착 | 본인 로그인 500, 다른 회원 목록 500 | 200 |
| 같은 회원 `PATCH` 10개 동시 × 10라운드 | 라운드마다 대표 10장, 10/10 실패 | 0/10 |
| 교체 후 댓글의 프로필 이미지 | 옛 이미지 | 새 대표 |
| 대표 없이 `users` INSERT | 제약 없음 | NOT NULL 위반 |
| 탈퇴 회원이 있을 때 다른 회원의 목록·상세·댓글 | — | 200 |
| 닉네임 변경 트랜잭션 도중 대표 교체 커밋 | — | 교체한 대표 유지 |

- 기존 DB 변경 SQL을 이전 스키마에 적용한 결과가 새 스키마와 같음(`information_schema`의 컬럼·제약 비교)을 확인했다. 대표 1장, 대표 삭제됨, 이미지 전부 삭제된 활성 회원, 탈퇴 회원의 포인터가 위 규칙대로 채워졌다.

## 5. 남은 것

- 파일을 디스크에 먼저 쓰고 DB에 커밋하므로, 트랜잭션이 실패하면 저장한 파일이 남는다.
- 동시 교체에서 이력용 `display_order`가 중복될 수 있다. 화면에서는 쓰지 않는다.
