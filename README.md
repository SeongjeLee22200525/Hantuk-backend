> 프로젝트 위치: `C:\Users\user\Hantuk\back-end`. IntelliJ에서 이 폴더를 열고 `Server (Gradle Java 17)` 실행 구성을 사용하세요. 프론트는 형제 폴더 `../front-end`입니다.

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
    │   │   ├── controller/      # Auth, Group, Store, Donation, Usage,
    │   │   │                   # Snapshot, Health, 호환 CommandController
    │   │   │   └── legacy/     # 기존 v1 ApiController
    │   │   ├── service/         # 기능별 비즈니스 로직
    │   │   │                   # Group/Store/Donation/Usage/Favorite/Snapshot
    │   │   │                   # PrototypeService: 트랜잭션·명령 조정
    │   │   ├── repository/      # 공통 JDBC + PrototypeStateRepository
    │   │   │                   # 상태 잠금·저장·중복 명령 기록
    │   │   ├── dto/             # 요청 데이터와 유효성 검증
    │   │   ├── bootstrap/       # local 프로필 샘플 데이터 초기화
    │   │   ├── common/
    │   │   │   ├── error/       # 공통 예외·HTTP 오류 응답
    │   │   │   ├── security/    # 토큰 해시
    │   │   │   ├── validation/  # 문자열·날짜·이모지 길이 검증
    │   │   │   └── state/       # 현재 JSON 상태 구조의 공통 접근
    │   │   └── infrastructure/h2/ # 로컬 환경용 선택적 파일 어댑터
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/   # Flyway V1·V2: 기존 스키마 유지
    └── test/
        ├── java/app/hantteok/  # 기능별 통합 테스트와 공통 시계
        └── resources/         # 테스트 런타임 설정
```

컨트롤러·서비스·리포지토리 계층으로 패키지를 묶었습니다. v1과 v2 클래스는 각 계층 안에서 클래스명과 API 경로로 구분합니다. DatabaseRepository는 공통 JDBC 실행을, PrototypeStateRepository는 v2 상태·명령 기록의 SQL을 담당합니다. 인증과 기존 v1 SQL은 해당 서비스에서 공통 JDBC 계층을 사용합니다. v2는 현재 프로토타입의 상태 JSON과 트랜잭션 잠금을 사용합니다. 엔티티별 DB 정규화는 별도 작업이며 이번 빌드·구조 변경은 저장 데이터와 API 계약을 유지합니다.

## 계정과 환경 설정

local 프로필이 기본입니다. `hantuck01`, `leader01`, `store01`, `admin01`의 공통 비밀번호는 `welcome123!`입니다. 이메일 인증 코드는 local에서만 `123456`을 사용합니다. 실제 메일·PG 결제·송금은 연동되지 않았습니다.

운영 DB는 `SPRING_PROFILES_ACTIVE=production`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ORIGINS` 환경 변수로 지정하세요. 운영 메일 제공자도 별도 연결해야 합니다. 비밀번호·실제 DB 파일은 Git에 추가하지 마세요.

```powershell
java -jar build/libs/hantteok-server-0.1.0.jar
```

Windows 제한 토큰 환경에서 기존 H2 파일이 읽기 전용으로 잘못 판정될 때만 `-Dhantteok.h2.writeProbe=true`와 `-Dspring.datasource.url="jdbc:h2:writeprobe:./data/hantteok;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;LOCK_TIMEOUT=10000"`를 `-jar` 앞에 지정할 수 있습니다. 일반 실행에는 필요하지 않습니다.

자세한 최신 계약은 [v2 API 문서](docs/api-v2.md), 기존 계약은 [v1 문서](docs/api-v1.md)를 확인하세요.

기능별 v2 API는 [컨트롤러·경로 표](docs/api-v2.md#기능별-컨트롤러와-경로)를 참고하세요. 기존 `/api/v2/commands`도 호환용으로 지원합니다.

## 코드 읽기와 스타일

요청 흐름과 클래스별 책임은 [서버 구조 안내](docs/architecture.md)에 정리했습니다. 기능 수정은 해당 컨트롤러와 서비스부터 확인하세요. `PrototypeService`는 상태 잠금, 중복 요청 확인, 기능 서비스 호출, 저장 순서만 조정합니다.

```powershell
# Java 소스·테스트 자동 정렬 (들여쓰기 4칸)
.\gradlew.bat formatJava
# 수정 없이 포맷 검사
.\gradlew.bat checkJavaFormat
```

`build`의 `check` 단계에도 포맷 검사가 포함됩니다. 포맷 도구는 버전을 고정했고 애플리케이션 런타임에는 포함되지 않습니다. `.editorconfig`는 UTF-8, 줄 끝, 기본 들여쓰기 규칙을 지정합니다.
