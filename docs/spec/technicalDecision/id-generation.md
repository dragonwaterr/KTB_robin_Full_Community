# 기본키 생성 전략

동시 쓰기에서 커넥션 풀 교착이 생기던 원인을 분석하고, 기본키 생성 전략을 `SEQUENCE`에서 `IDENTITY`로 바꾼 결정이다. 현재 기본키 구성은 [erd.md](../../erd.md)를 본다.

## 1. 문제

같은 이메일 동시 가입 N=100을 기본 Hikari 풀(최대 10)에서 실행하면 일부만 처리되고 나머지는 약 30초 뒤 500으로 실패했다.

| Hikari 풀 | 상태 코드 분포 | 소요 |
|---|---|---|
| 10 | 200×1, 409×8, 500×91 | 약 47초 |
| 120 | 200×1, 409×99 | 약 17초 |

- 500 대부분은 `CannotCreateTransactionException`(트랜잭션 시작 시 커넥션을 받지 못함)이었다.
- 두 실행의 차이(약 30초)가 Hikari `connectionTimeout` 기본값과 같다. 처리가 느린 것이 아니라 진행이 멈췄다가 타임아웃으로 풀린 것이다.

## 2. 원인

### 2.1 MySQL에서 `SEQUENCE`가 동작하는 방식 (Hibernate 6.6)

- MySQL은 시퀀스 객체가 없다(`MySQLDialect#getSequenceSupport` = `NoSequenceSupport`). `GenerationType.SEQUENCE`는 엔티티마다 `*_seq` 테이블(`next_val` 한 행)로 동작한다(`TableStructure`).
- id는 블록 단위(`allocationSize` 기본 50)로 받고, 블록 안에서는 메모리에서 꺼낸다(`PooledOptimizer`).
- 블록이 바닥나면 `PooledOptimizer#generate`가 JVM 락을 잡고, `TableStructure`가 `IsolationDelegate`로 **업무 트랜잭션과 별개인 커넥션을 같은 풀에서 하나 더** 빌려 `*_seq`를 갱신한 뒤 바로 커밋한다.
- 첫 조회 값이 초기값과 같으면 첫 블록은 id 1개뿐이라, 애플리케이션 시작 직후에는 생성기마다 블록 조회가 연달아 두 번 일어난다.

### 2.2 교착

업무 트랜잭션은 시작할 때 커넥션을 잡고(1차 점유) 커밋까지 놓지 않는다. 블록 경계에서 채번에 커넥션이 하나 더 필요하다(2차 점유).

| 스레드 | 쥐고 있는 것 | 기다리는 것 |
|---|---|---|
| 락 보유 1개 | 커넥션 1 + 생성기 락 | 채번용 커넥션 |
| 커넥션 보유 9개 | 커넥션 1 | 생성기 락 |
| 나머지 | 없음 | 트랜잭션 시작용 커넥션 |

- 풀 10개가 모두 1차 점유된 상태에서, 락을 쥔 스레드는 커넥션을 기다리고 커넥션을 쥔 스레드들은 락을 기다린다(점유 대기 + 순환 대기).
- 커넥션 풀과 JVM 락이라는 서로 다른 자원에 걸친 대기라 DB 데드락 감지에 걸리지 않고, 커넥션 대기 타임아웃 후에야 풀린다.
- 확인: 동시 가입 중 스레드 덤프에서 위 표의 상태를 확인했다. id 블록을 미리 확보해 블록 안에서만 채번하도록 한 실험(풀 10, N=40)에서는 교착 없이 200×1, 409×39였다.

### 2.3 Hibernate 설정으로 피할 수 있는가

없다. `TableStructure`는 채번을 항상 별도 커넥션(`IsolationDelegate`)에서 하고, 그 커넥션은 세션과 같은 커넥션 공급원에서 빌린다. 블록 크기·풀 크기 조정이나 미리 채번은 교착의 확률만 낮춘다.

## 3. 결정

| 옵션 | 동작 | 판단 |
|---|---|---|
| **`IDENTITY` (선택)** | INSERT할 때 같은 커넥션에서 `AUTO_INCREMENT`로 채번 | 2차 점유가 사라져 교착 조건이 없어진다. 쓰기 지연과 JDBC 배치 insert는 쓸 수 없다 |
| 채번 전용 커넥션 풀 분리 | 업무 풀과 분리된 작은 DataSource로 `*_seq`를 갱신하는 커스텀 생성기 | 교착 조건은 없어지지만 커스텀 생성기와 DataSource 이중화를 유지해야 한다 |
| 애플리케이션 채번 (TSID 등) | DB 없이 시간순 64비트 id 생성 | 교착 조건은 없어지지만 id 체계와 라이브러리가 바뀐다 |

`SEQUENCE`는 나중에 배치 insert를 쓸 수 있다는 기대로 택했지만, 배치 설정(`hibernate.jdbc.batch_size`)을 둔 적이 없어 실제로 쓰인 적은 없다. `IDENTITY`로 바꿔도 SQL 개수는 같다(INSERT 시점만 `persist` 즉시로 바뀐다). 그래서 프레임워크 기본 기능만으로 끝나는 `IDENTITY`를 선택했다.

## 4. 변경

- `User`, `Post`, `Comment`, `Like`, `ProfileImage`, `PostImage`, `RefreshToken`: `@GeneratedValue(strategy = GenerationType.IDENTITY)`. `PostView`는 `@MapsId`로 `Post`의 기본키를 쓰므로 생성기를 쓰지 않는다.
- 스키마: `*_seq` 테이블 7개 제거, 각 테이블 `id`에 `AUTO_INCREMENT`. `ddl-auto: validate`라 기존 DB는 `ALTER TABLE ... MODIFY id bigint NOT NULL AUTO_INCREMENT`와 `*_seq` 삭제를 직접 실행한다(기존 id 유지, 다음 id는 `MAX(id)+1`).

## 5. 검증

Testcontainers(MySQL 8.4) `ConcurrentRegisterTest`, 같은 이메일 동시 가입 N=100, Hikari 풀 기본값(10):

| | 상태 코드 분포 | 해당 이메일 행 수 |
|---|---|---|
| 변경 전 | 200×1, 409×8, 500×91 | 1 |
| 변경 후 | 200×1, 409×99 | 1 |

- 가입은 `ProfileImage`를 `User`보다 먼저 저장한다. SQL 로그상 변경 전후 모두 `insert profile_image` → `insert users` → `update profile_image`이고, 변경 후에는 `*_seq` 조회·갱신 SQL만 사라졌다.
- 기존 DB 변경 SQL을 이전 스키마에 적용한 결과가 새 스키마와 같음(`information_schema`의 컬럼·제약 비교)을 확인했다.
