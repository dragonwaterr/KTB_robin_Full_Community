# 회원 탈퇴 Facade

회원 탈퇴 로직을 `UserService`에서 분리하고 트랜잭션 경계를 강제한 결정이다. 탈퇴 API와 처리 순서는 [user.md](../domain/user.md)를 본다.

## 1. 연관 데이터 삭제를 어디에 둘지

**요구사항**

- 회원이 탈퇴하면 프로필 이미지, 게시글(과 게시글의 이미지·댓글·좋아요), 회원이 쓴 댓글, 좋아요를 함께 삭제해야 한다.
- 삭제는 soft delete라서 JPA의 `orphanRemoval`이나 cascade 삭제로 처리할 수 없다. 연관 데이터를 직접 찾아 `deleteEntity()`를 호출해야 한다.
- 이 메서드 하나 때문에 `UserService`가 게시글·댓글·좋아요·리프레시 토큰 리포지토리를 모두 의존하게 된다.

| 옵션 | 내용 |
|---|---|
| `UserService`에 구현 | `UserService`가 탈퇴에 필요한 리포지토리를 모두 주입받는다 |
| 중간 계층(Facade) | 탈퇴 로직을 별도 컴포넌트로 분리하고 `UserService`는 그것만 호출한다 |

**선택: Facade로 분리** — `UserService`의 의존성이 탈퇴 메서드 하나 때문에 늘어나는 것을 막는다.

현재 코드

- `UserDeleteFacade#deleteUser`가 `PostRepository`, `CommentRepository`, `LikeRepository`, `RefreshTokenRepository`, `PostDeleteFacade`를 의존한다.
- `UserService#withdrawMemberShip`는 `UserDeleteFacade#deleteUser`만 호출한다.
- 게시글 삭제도 같은 방식이다. `PostDeleteFacade#deletePost`가 게시글의 이미지·댓글·좋아요를 삭제하고, `PostService#removePost`와 `UserDeleteFacade#deleteUser`가 함께 쓴다.

## 2. 트랜잭션 경계

**요구사항**: 탈퇴에 포함된 모든 soft delete는 한 트랜잭션에서 일어나야 한다. 일부만 반영되면 안 된다.

| 옵션 | 동작 |
|---|---|
| `REQUIRED` (기본값) | 상위 트랜잭션이 있으면 참여하고, 없으면 새로 시작한다 |
| `MANDATORY` | 상위 트랜잭션이 있으면 참여하고, 없으면 예외를 던진다 |

**선택: `MANDATORY`** — 상위 트랜잭션 없이는 실행할 수 없게 해서, 호출하는 쪽이 탈퇴 전체를 한 트랜잭션으로 묶도록 강제한다.

현재 코드

- `UserDeleteFacade#deleteUser`, `PostDeleteFacade#deletePost`: `@Transactional(propagation = Propagation.MANDATORY)`
- 호출하는 쪽 `UserService#withdrawMemberShip`, `PostService#removePost`: `@Transactional`
