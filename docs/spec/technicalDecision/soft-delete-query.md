# soft delete 조회 방식

soft delete를 도입한 뒤, 조회에서 삭제된 행을 거르는 방법을 정한 결정이다. soft delete를 택한 이유는 [erd-redesign.md](erd-redesign.md) 2장을 본다.

## 요구사항

- 인메모리 저장소에서 Spring Data JPA로 옮기면서 엔티티가 `deleted` 컬럼을 갖게 됐다.
- 삭제된 행은 모든 조회에서 빠져야 한다.
- 파생 쿼리 메서드로 처리하면 모든 메서드 이름에 `AndDeletedFalse`를 붙여야 한다.

## 옵션

| # | 옵션 | 내용 |
|---|---|---|
| ① | `@Query` | 조회마다 JPQL을 직접 쓰고 `deleted = false` 조건을 넣는다 |
| ② | 애플리케이션 필터 | 전부 가져온 뒤 코드에서 삭제된 행을 거른다 |
| ③ | `@SQLRestriction` | 엔티티에 `@SQLRestriction("deleted = false")`를 선언해 Hibernate가 조회 SQL에 조건을 붙인다 |

## 비교

| | ① `@Query` | ② 애플리케이션 필터 | ③ `@SQLRestriction` |
|---|---|---|---|
| 조건을 쓰는 곳 | 쿼리마다 | 조회하는 코드마다 | 엔티티 한 곳 |
| 삭제된 행 조회 | DB에서 제외 | 가져온 뒤 제외 | DB에서 제외 |

## 선택: `@SQLRestriction`

조건을 엔티티 한 곳에만 선언하면 되고, 파생 쿼리 메서드 이름을 그대로 쓸 수 있다.

선택할 때 모든 조회 경로에 자동으로 적용되지는 않는다는 점을 메모했다. 네이티브 쿼리가 그 예다.

## 현재 코드

- `User`, `Post`, `Comment`, `Like`, `ProfileImage`, `PostImage` 엔티티에 `@SQLRestriction("deleted = false")`
- 컬렉션 필드에도 같은 어노테이션을 선언했다: `User#profileImages`, `Post`의 `postImages`·`comments`·`likes`
- 삭제는 `SoftDeletetionEntity#deleteEntity`가 `deleted = true`로 바꾼다
