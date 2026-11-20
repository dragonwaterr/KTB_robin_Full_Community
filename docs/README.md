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
