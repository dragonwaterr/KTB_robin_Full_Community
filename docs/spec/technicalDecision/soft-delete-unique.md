# soft delete와 UNIQUE 제약

탈퇴(soft delete)한 회원의 이메일·닉네임을 다시 쓰려는 요청과, 같은 값의 동시 요청을 어떻게 처리할지 정한 결정이다. soft delete 조회 방식은 [soft-delete-query.md](soft-delete-query.md)를 본다.

## 1. 문제

- `User`는 `@SQLRestriction("deleted = false")`라 파생 쿼리 `existsByEmail`·`existsByNickname`이 탈퇴 회원을 보지 못한다. 반면 DB의 `UNIQUE(email)`·`UNIQUE(nickname)`는 탈퇴 회원 행도 본다.
- 그 결과 탈퇴 회원의 값으로 요청하면 중복 검사는 통과하고 INSERT·UPDATE에서 제약 위반이 나 500으로 응답했다.

| 경로 | 동작 |
|---|---|
| 회원가입 `POST /users` | 500 |
| 닉네임 변경 `PATCH /users/me/nickname` | 500 |
| 가용성 검사 `GET /availability/email`, `/nickname` | 200(사용 가능)으로 응답 |

- 같은 이메일로 동시에 가입하면 요청들이 사전 검사를 함께 통과하므로 마지막 방어선은 UNIQUE 제약이다. 이 위반도 500으로 응답했다.

## 2. 결정

### 2.1 정책: 탈퇴한 값을 다시 쓸 수 있는가

| 옵션 | 내용 | 판단 |
|---|---|---|
| 재사용 허용 | 탈퇴 회원 값으로 새 계정 가입 가능 | 서비스 동작으로 불친절하고 부자연스럽다 |
| 닉네임만 해제 | 탈퇴 시 닉네임을 무작위 값으로 바꾸고 이메일은 막음 | 이번 범위에서는 하지 않음 |
| **재사용 금지 (선택)** | 탈퇴한 값도 "사용 중"과 똑같이 409 | 스키마를 바꾸지 않고 검사 범위만 DB 제약과 맞춘다 |

로그인에서 탈퇴 계정을 따로 안내하지 않는다. 가입 이력을 잊은 사용자는 계정 찾기로 해결할 문제이고, 처음 가입하는 사용자에게는 별도 메시지가 혼란만 준다.

### 2.2 중복 검사를 탈퇴 회원까지 보게 하는 방법

| 옵션 | 내용 | 판단 |
|---|---|---|
| **네이티브 쿼리 (선택)** | `existsByEmail`·`existsByNickname`을 `@Query(nativeQuery = true)`로 교체 | 네이티브 SQL에는 `@SQLRestriction`이 붙지 않는다. 메서드 이름과 호출부(가입, 닉네임 변경, 가용성 검사)가 그대로라 한 곳만 고치면 된다 |
| Hibernate `@Filter`로 교체 | 검사할 때만 필터를 끈다 | soft delete를 쓰는 엔티티 6개의 조회 방식 전체를 바꿔야 한다 |
| 사전 검사 없이 UNIQUE만 사용 | 위반을 409로 변환 | 가용성 검사 API가 성립하지 않는다 |

- MySQL의 `SELECT EXISTS(...)`는 정수(0/1)를 반환해 `boolean`으로 매핑되지 않는다. 그래서 `CASE WHEN EXISTS(...) THEN 'true' ELSE 'false' END`로 문자열을 반환하고 `boolean` 시그니처를 유지했다.

### 2.3 동시 요청: UNIQUE 위반을 409로

- `GlobalExceptionHandler#handleDataIntegrityViolation`이 `DataIntegrityViolationException` 중 원인이 Hibernate `ConstraintViolationException`이고 종류가 `UNIQUE`인 경우만 409 `duplicate_resource`(`GenericError.CONFLICT`)로 응답한다.
- 같은 예외로 번역되는 다른 위반(예: 컬럼 길이 초과)은 409로 오인되지 않도록 500으로 둔다.
- 메시지는 이메일·닉네임을 구분하지 않는 공통 메시지다. 평소 중복은 사전 검사가 `existing_email`·`existing_nickname`으로 응답하고, 이 경로는 드문 경합에만 쓰인다. 구분하려면 제약 이름을 지정하고 예외 메시지를 해석해야 해서 택하지 않았다.

## 3. 검증

Testcontainers(MySQL 8.4) 통합 테스트: `WithdrawnUserDuplicateTest`, `ConcurrentRegisterTest`

| 경우 | 결과 |
|---|---|
| 탈퇴 회원 이메일로 가입 (대소문자만 다른 이메일 포함) | 409 `existing_email` |
| 탈퇴 회원 닉네임으로 가입 | 409 `existing_nickname` |
| 탈퇴 회원 닉네임으로 닉네임 변경 | `existing_nickname` |
| 가용성 검사에 탈퇴 회원 이메일·닉네임 | 409 |
| 활성 회원 이메일 중복, 처음 쓰는 이메일 | 409, 200 (기존 동작 유지) |
| 컬럼(`varchar(255)`)보다 긴 닉네임 | 409가 아님 |

같은 이메일 동시 가입 N=100:

| | 상태 코드 분포 | 해당 이메일 행 수 |
|---|---|---|
| 변경 전 | 200×1, 500×99 | 1 |
| 변경 후 | 200×1, 409×99 | 1 |
