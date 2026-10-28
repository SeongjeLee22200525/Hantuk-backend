# Hantuck Backend

한턱 Flutter 앱을 위한 Spring Boot 서버입니다. 최신 프론트는 `/api/v2`를 사용하며 기존 `/api`도 호환성을 위해 유지합니다.

## 시작하기

Java 17을 설치하고 `JAVA_HOME`을 지정하세요. Gradle은 별도 설치 없이 저장소의 Wrapper를 사용합니다.

```powershell
# Windows: 테스트와 실행 JAR 생성
.\gradlew.bat clean build
# 개발 서버 실행
.\gradlew.bat bootRun
```

```bash
# macOS / Linux
./gradlew clean build
./gradlew bootRun
```

- 서버: http://127.0.0.1:8081
- 상태: http://127.0.0.1:8081/api/v2/health
- 실행 JAR: `build/libs/hantteok-server-0.1.0.jar`
- 테스트 보고서: `build/reports/tests/test/index.html`
- 기본 DB: 실행 폴더의 `data/hantteok.mv.db` (H2 파일, 재실행 후 유지)

## 프로젝트 구조

```text
hantuk-backend/
├── build.gradle                 # 의존성, Java 17, JUnit, Spring Boot 빌드
├── settings.gradle
├── gradle/wrapper/              # Gradle 8.14.3 + 배포본 SHA-256 검증
├── gradlew / gradlew.bat
├── docs/
│   ├── api-v2.md                # 최신 API, 권한, 테스트 계정, 한계
│   └── api-v1.md                # 기존 API 참고
└── src/
    ├── main/
    │   ├── java/app/hantteok/
    │   │   ├── ServerApplication.java
    │   │   ├── config/          # 웹·CORS, 시계, 비밀번호 인코더
    │   │   ├── controller/      # HTTP 요청·응답과 API 라우팅
    │   │   ├── service/         # 인증·후원·가게·QR 등 비즈니스 로직
    │   │   ├── repository/      # JDBC 공통 데이터 접근 (DatabaseRepository)
    │   │   ├── dto/             # 요청 데이터와 유효성 검증
    │   │   ├── bootstrap/       # local 프로필 샘플 데이터 초기화
    │   │   ├── common/
    │   │   │   ├── error/       # 공통 예외·HTTP 오류 응답
    │   │   │   └── security/    # 토큰 해시
    │   │   └── infrastructure/h2/ # 로컬 환경용 선택적 파일 어댑터
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/   # Flyway V1·V2: 기존 스키마 유지
    └── test/
        ├── java/app/hantteok/  # 기능별 통합 테스트와 공통 시계
        └── resources/         # 테스트 런타임 설정
```

컨트롤러·서비스·리포지토리 계층으로 패키지를 묶었습니다. v1과 v2 클래스는 각 계층 안에서 클래스명과 API 경로로 구분합니다. 현재 repository의 Database는 공통 JDBC 실행을 담당하며 SQL과 도메인 검증은 기존 서비스에 유지합니다. v2는 현재 프로토타입의 상태 JSON과 트랜잭션 잠금을 사용합니다. 엔티티별 DB 정규화는 별도 작업이며 이번 빌드·구조 변경은 저장 데이터와 API 계약을 유지합니다.

## 계정과 환경 설정

local 프로필이 기본입니다. `hantuck01`, `leader01`, `store01`, `admin01`의 공통 비밀번호는 `welcome123!`입니다. 이메일 인증 코드는 local에서만 `123456`을 사용합니다. 실제 메일·PG 결제·송금은 연동되지 않았습니다.

운영 DB는 `SPRING_PROFILES_ACTIVE=production`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ORIGINS` 환경 변수로 지정하세요. 운영 메일 제공자도 별도 연결해야 합니다. 비밀번호·실제 DB 파일은 Git에 추가하지 마세요.

```powershell
java -jar build/libs/hantteok-server-0.1.0.jar
```

Windows 제한 토큰 환경에서 기존 H2 파일이 읽기 전용으로 잘못 판정될 때만 `-Dhantteok.h2.writeProbe=true`와 `-Dspring.datasource.url="jdbc:h2:writeprobe:./data/hantteok;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;LOCK_TIMEOUT=10000"`를 `-jar` 앞에 지정할 수 있습니다. 일반 실행에는 필요하지 않습니다.

자세한 최신 계약은 [v2 API 문서](docs/api-v2.md), 기존 계약은 [v1 문서](docs/api-v1.md)를 확인하세요.
