# 문서

| 문서 | 내용 |
|---|---|
| [architecture.md](architecture.md) | 계층·패키지 책임, 요청 흐름, 보안 필터 체인, 예외 처리 흐름 |
| [erd.md](erd.md) | 엔티티 → 테이블, 연관관계, UNIQUE·soft delete 컬럼 |

## 도메인 명세

| 문서 | 내용 |
|---|---|
| [spec/domain/auth.md](spec/domain/auth.md) | 로그인, 토큰 재발급, 로그아웃 |
| [spec/domain/user.md](spec/domain/user.md) | 회원가입, 가용성 검사, 정보 수정, 회원 탈퇴 |
| [spec/domain/post.md](spec/domain/post.md) | 게시글 등록, 목록(커서), 상세, 수정, 삭제 |
| [spec/domain/comment.md](spec/domain/comment.md) | 댓글 등록, 목록(커서), 수정, 삭제 |
| [spec/domain/like.md](spec/domain/like.md) | 좋아요 추가·취소 |
| [spec/domain/image.md](spec/domain/image.md) | 프로필·게시글 이미지, 프로필 이미지 교체 흐름 |

## 설계 결정

| 문서 | 내용 |
|---|---|
| [spec/technicalDecision/rest-api.md](spec/technicalDecision/rest-api.md) | 수정 메서드, `data: null`, 게시글 목록 커서, 코드 리뷰 피드백 반영 |
| [spec/technicalDecision/erd-redesign.md](spec/technicalDecision/erd-redesign.md) | 생성·수정 일시, soft delete, 사진 테이블 분리, 좋아요 대리키·상태 컬럼 |
| [spec/technicalDecision/soft-delete-query.md](spec/technicalDecision/soft-delete-query.md) | 삭제된 행을 거르는 조회 방식 (`@SQLRestriction`) |
| [spec/technicalDecision/user-delete-facade.md](spec/technicalDecision/user-delete-facade.md) | 회원 탈퇴 Facade 분리, `Propagation.MANDATORY` |
| [spec/technicalDecision/soft-delete-unique.md](spec/technicalDecision/soft-delete-unique.md) | 탈퇴 회원 값의 재사용 정책, 중복 검사 범위, UNIQUE 위반 409 |
| [spec/technicalDecision/id-generation.md](spec/technicalDecision/id-generation.md) | 동시 쓰기 커넥션 풀 교착 원인, 기본키 생성 전략 `SEQUENCE` → `IDENTITY` |
| [spec/technicalDecision/primary-profile-image.md](spec/technicalDecision/primary-profile-image.md) | 대표 프로필 이미지를 회원이 가리키는 구조, 대안(비관적 락·UNIQUE) 비교 |
