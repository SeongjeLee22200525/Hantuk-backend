# 한턱 Spring Boot 서버

## 최신 프론트용 v2 API

`8c56fe0`의 새 프론트는 `/api/v2`를 사용합니다. 아이디 로그인(hantuck01, leader01~03, store01, admin01 / welcome123!), 그룹 가입 승인·즐겨찾기, 가게 심사·반려·재신청, 금액/선결제 후원, 10분 QR, 사용 승인·취소, 감사 메시지·사진을 지원합니다. 상세 계약은 [v2 연동 문서](API-V2.md)를 참고하세요. 기존 DB는 Flyway V2로 확장하며 아래 v1 API와 데이터는 보존합니다.

아래 설명은 기존 이메일 로그인 기반 v1 API 문서입니다.


Flutter의 `lib/src/models.dart`, `store.dart`, `pages/account.dart`, `pages/merchant.dart`와 `test/store_test.dart`의 거래 규칙을 서버로 옮긴 REST API입니다.

Java 17, Spring Boot 3.5.16, Spring JDBC, Flyway, BCrypt를 사용합니다. 기본 DB는 파일형 H2입니다. H2 파일에 계정·세션·거래·환불·정산이 저장되어 재시작해도 유지됩니다. PostgreSQL 드라이버와 Flyway 지원도 포함했으며, 자동화 테스트는 H2에서 수행합니다.

## 로컬 실행

Java 17 이상이 필요합니다. 최초 실행에는 Maven 및 의존성 다운로드를 위한 인터넷이 필요합니다.

```powershell
cd server
./mvnw.cmd test
./mvnw.cmd spring-boot:run
```

macOS/Linux에서는 `./mvnw`를 사용합니다. 실행 위치를 `server/`로 유지하세요. DB는 실행 디렉터리의 `data/hantteok.mv.db`에 생성됩니다.

- 서버: http://127.0.0.1:8081
- 상태 확인: `GET /api/health`
- Flutter 개발 서버: http://127.0.0.1:8080
- 기본 허용 Origin: `http://127.0.0.1:8080`, `http://localhost:8080`

실행 가능한 JAR 생성:

```powershell
./mvnw.cmd clean verify
java -jar target/hantteok-server-0.1.0.jar
```

Flutter 기본 진입점은 이제 이 서버의 API에 연결됩니다. `lib/src/remote_store.dart`가 비동기 명령·계정별 스냅샷·QR 폴링을 처리하며 서버 응답을 상태의 기준으로 사용합니다. Flutter에서 `--dart-define=API_BASE_URL=http://127.0.0.1:8081/api`로 주소를 지정할 수 있습니다. 기존 오프라인 `AppStore`는 화면 테스트용으로만 유지합니다.

이용자와 가게는 별도 세션으로 로그인합니다. 로컬 서버의 `demo@hantteok.app` 계정에는 가게 권한이 없고, `merchant@hantteok.app`이 가게를 소유합니다. UI의 빠른 로그인도 실제 `/auth/login`을 호출합니다.

## 로컬 데모 계정

기본 `local` 프로필에서만 최초 DB 생성 시 아래 데이터를 넣습니다. 모든 가게·공동체는 가상입니다.

|역할|이메일|비밀번호|권한|
|---|---|---|---|
|이용자|demo@hantteok.app|demo1234|학교 인증, b3 선결제 소유|
|가게|merchant@hantteok.app|merchant1234|s1~s4 소유|
|운영자|admin@hantteok.app|admin1234|고객지원 상태 처리|
|선결제자|donor@hantteok.app|donor1234|b1, b2, b4 소유|

서버는 이용자/가게/운영자 세션을 구분합니다. `merchantAccess=true` 같은 클라이언트 입력으로 권한을 얻을 수 없습니다. 회원가입은 USER만 생성하며 공동체 인증이나 가게 권한을 부여하지 않습니다.

학교 인증은 로컬 데모에서 `student@hanmaeum.ac.kr` / `123456`입니다. 실제 메일은 보내지 않습니다. 다른 공동체의 초대 코드는 `WARM2026`, `YOUTH2026`이며 서버에 BCrypt 해시로 저장합니다. 학교 `CAMPUS2026`는 Flutter의 기획 정보에 대응하는 시드 값이고, 학교 API는 이메일+인증번호 방식만 허용합니다.

## 인증과 응답

`POST /api/auth/login` 또는 `/register` 응답의 `accessToken`을 이후 요청에 넣습니다.

```http
Authorization: Bearer <accessToken>
```

토큰은 256비트 난수이며 DB에는 SHA-256 해시만 저장합니다. 기본 유효 기간은 24시간입니다. 로그아웃은 해당 토큰을 폐기합니다. 비밀번호는 BCrypt로 저장하며 8자 이상, UTF-8 72바이트 이하를 허용합니다. 기존 데모 로그인은 호환용 8자 비밀번호를 사용합니다.

성공 응답은 JSON 객체 또는 배열입니다. 금액은 정수 원 단위, 시간은 UTC ISO-8601, 정산일은 서울 시간 기준 `yyyy-MM-dd`입니다. QR 응답의 `qrPayload`는 `hantteok:<요청 ID>`입니다. 가게는 문자열의 접두어를 확인한 다음 요청 ID로 API를 호출합니다. QR은 3분간 유효합니다.

오류 형식:

```json
{"code":"INVALID_STATE","message":"만료되었거나 이미 처리된 QR입니다. 다시 발급해 주세요.","path":"/api/qr-requests/.../approve","timestamp":"2026-10-23T08:00:00Z"}
```

주요 상태 코드: 400 입력 오류, 401 인증/세션 오류, 403 권한 부족, 404 대상 없음, 409 거래 상태 충돌/중복 키 충돌, 422 모의 결제 실패, 501 외부 제공자 미연결.

## API 목록

`/api/health` 및 로그인/회원가입을 제외한 모든 API에 인증이 필요합니다. 경로는 아래 표에 `/api`를 붙입니다.

|메서드|경로|기능|
|---|---|---|
|POST|/auth/register|email, password, nickname, agreed:true로 가입|
|POST|/auth/login|email, password로 로그인|
|POST|/auth/logout|현재 세션 폐기|
|GET, PATCH|/me|프로필 조회/수정. 수정: nickname, realName, communityId|
|PATCH|/me/notification-settings|usageAlert, exhaustedAlert, refundAlert|
|GET|/communities|공동체 및 본인 인증 여부|
|POST|/communities/{id}/verify|email(학교), code로 소속 인증|
|GET|/shops|query, category 필터|
|GET|/shops/{id}|가게·메뉴 상세|
|GET|/benefits|communityId, shopId, mine, state 필터|
|GET|/benefits/{id}|공동 잔액, 개인 누적 사용액, 개인 사용 가능 금액|
|POST|/benefits|모의 선결제. 아래 예제 참고|
|GET|/benefits/{id}/usages|선결제자 본인의 사용 기록. 수혜자 식별 정보 제외|
|GET|/usages|본인의 사용 내역|
|GET|/usages/{id}|본인의 사용 상세|
|POST|/qr-requests|benefitId로 QR 발급. 이전 미완료 요청 취소|
|GET|/qr-requests/{id}|본인 QR 상태 조회/폴링|
|POST|/qr-requests/{id}/approve|이용자 승인, 잔액 차감과 사용 내역 생성|
|POST|/qr-requests/{id}/cancel|이용자 요청 취소|
|GET, POST|/tickets|본인 신청 목록/접수. targetId, type, reason, evidence|
|GET|/tickets/{id}|본인 고객지원 상세|
|GET|/notices|본인 알림 목록|
|POST|/notices/{id}/read|본인 알림 읽음 처리|
|GET|/merchant/shops/{id}/dashboard|가게 운영 현황|
|PATCH|/merchant/shops/{id}|description, hours, menu:[{name,price}] 수정|
|GET|/merchant/shops/{id}/benefits|공동체별 선결제 조회|
|GET|/merchant/shops/{id}/transactions|사용 완료 거래|
|GET|/merchant/qr-requests/{id}|소유 가게의 QR 확인|
|POST|/merchant/qr-requests/{id}/order|menu, amount로 이용자 확인 요청|
|GET|/merchant/shops/{id}/settlements|날짜별 정산 목록|
|GET|/merchant/shops/{id}/settlements/{day}|날짜별 정산 상세|
|POST|/merchant/shops/{id}/settlements/{day}/complete|모의 정산 완료|
|GET|/admin/tickets|운영자 고객지원 목록|
|POST|/admin/tickets/{id}/advance|접수 → 검토 중 → 처리 완료|

현재 목록 API는 로컬 프로토타입 규모에 맞춰 전체 결과를 반환합니다. 운영 데이터가 늘기 전에 페이지네이션을 적용하세요.

## QR 사용 흐름 예제 (PowerShell)

새 DB의 데모 b1을 이용합니다. 이 예제는 실제 사용 내역을 1건 만들므로 반복하면 개인 한도가 소진됩니다.

```powershell
$api = 'http://127.0.0.1:8081/api'
$user = Invoke-RestMethod "$api/auth/login" -Method Post -ContentType 'application/json' -Body '{"email":"demo@hantteok.app","password":"demo1234"}'
$merchant = Invoke-RestMethod "$api/auth/login" -Method Post -ContentType 'application/json' -Body '{"email":"merchant@hantteok.app","password":"merchant1234"}'
$u = @{Authorization="Bearer $($user.accessToken)"}
$m = @{Authorization="Bearer $($merchant.accessToken)"}
$qr = Invoke-RestMethod "$api/qr-requests" -Method Post -Headers $u -ContentType 'application/json' -Body '{"benefitId":"b1"}'
$order = @{menu='따뜻한 제육 덮밥';amount=8000} | ConvertTo-Json
Invoke-RestMethod "$api/merchant/qr-requests/$($qr.id)/order" -Method Post -Headers $m -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($order))
Invoke-RestMethod "$api/qr-requests/$($qr.id)/approve" -Method Post -Headers $u
# applied=5000, extra=3000. 같은 요청을 다시 승인하면 409.
```

선결제 본문:

```json
{
  "shopId":"s1", "communityId":"school", "amount":30000, "limit":4000,
  "expiresAt":"2027-01-01T00:00:00Z", "donor":"다정한 이웃", "message":"힘내세요",
  "menus":[], "paymentKey":"client-generated-unique-key", "simulateFailure":false
}
```

`expiresAt`은 요청 시점보다 미래여야 합니다. 동일 이용자의 동일 `paymentKey` 재요청은 기존 결제를 반환하고, 다른 내용으로 재사용하면 409입니다. `simulateFailure:true`는 422이며 DB에 선결제를 만들지 않습니다. `menus:[]`는 가게 전체 메뉴를 허용합니다.

고객지원 `type`은 `취소·환불`, `문제 신고`, `정산 문의`입니다. 환불 대상은 본인의 선결제 ID, 신고는 혜택/가게/본인의 사용 ID, 정산 문의는 본인 소유 가게 ID입니다. 사유는 앞뒤 공백을 제거한 후 5자 이상입니다. 환불 신청 즉시 혜택은 `refund`가 되어 승인과 발급이 막힙니다. 완료 시 `cancelled`로 바꾸고 남은 잔액을 `refundAmount`로 기록합니다. 이미 사용한 금액은 환불하지 않습니다.

## 거래 정합성

- 가게 주문 제출은 잔액을 차감하지 않습니다.
- 이용자 승인 시 `min(주문액, 공동 잔액, 개인 누적 한도 잔여)`를 계산합니다.
- QR 변경은 이용자 → 혜택 → 요청 순서로 `SELECT FOR UPDATE` 잠금을 잡습니다. 같은 혜택을 쓰는 서로 다른 이용자의 승인도 직렬화됩니다.
- 사용 기록의 `request_id` UNIQUE 제약으로 중복 차감을 추가로 막습니다.
- 다른 이용자의 QR/사용/지원/알림 데이터에 접근할 수 없습니다. 선결제자·가게 조회에서도 수혜자 이메일이나 사용자 ID를 반환하지 않습니다.
- 정산 대상은 승인 완료된 `usages`뿐입니다. 날짜는 서울 시간으로 구분하며, 완료 처리 재요청은 중복 지급 상태를 만들지 않습니다. 실제 은행 송금은 없습니다.
- 유효한 QR은 서버 DB에 저장되므로 프로토타입의 메모리 QR과 달리 서버 재시작 후에도 만료 전까지 유지됩니다.

## 설정 및 외부 연동 경계

`SERVER_ADDRESS`, `SERVER_PORT`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ORIGINS` 환경변수를 지원합니다. 기본 바인딩은 로컬 전용 `127.0.0.1`입니다.

```powershell
$env:SPRING_PROFILES_ACTIVE='production'
$env:DB_URL='jdbc:postgresql://localhost:5432/hantteok'
$env:DB_USERNAME='hantteok'
$env:DB_PASSWORD='<database-password>'
$env:CORS_ORIGINS='https://your-frontend.example'
./mvnw.cmd spring-boot:run
```

`production` 프로필은 데모 데이터/고정 학교 인증을 끕니다. 실제 공동체·가게·운영자 계정 프로비저닝 API, 이메일 인증 제공자, PG 결제·환불 웹훅, 은행 송금은 아직 연결하지 않았습니다. 기본 `payment-mode: mock`은 모든 프로필에서 모의 결제입니다. 이 설정을 바꾸면 선결제 API는 501을 반환합니다. production이라는 프로필 이름이 실제 결제나 운영 준비 완료를 뜻하지 않습니다. 운영 배포 전 HTTPS, 요청 제한, 이메일 소유권 검증, 비밀번호 복구, 감사로그, 페이지네이션과 실제 PostgreSQL 환경 테스트가 필요합니다.

## 검증

`ServerIntegrationTest`는 MockMvc로 실제 HTTP 인증/QR 흐름과 CORS·입력 검증을 실행합니다. 서비스 통합 테스트는 실제 H2 트랜잭션을 사용해 중복 승인, 두 이용자의 동시 차감, 개인 누적 한도, 만료·재발급·취소, 메뉴 제한, 가게 권한, 환불 동결, 결제 멱등성, 개인정보 분리, 정산을 검증합니다.

공식 참고: [Spring Boot 3.5 시스템 요구사항](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Jakarta Persistence의 비관적 잠금 설명](https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/lockmodetype). 구현은 JPA 대신 Spring JDBC의 명시적 SQL 잠금을 사용합니다.


## Windows 제한 실행 환경의 파일 DB

일부 Windows 제한 토큰에서는 실제 파일 쓰기가 가능해도 `Files.isWritable`이 false를 반환하여 H2가 기존 파일을 읽기 전용으로 여는 경우가 있습니다. 제공한 `outputs/run-server.ps1`은 로컬 기본 DB에 한해 `-Dhantteok.h2.writeProbe=true`와 `jdbc:h2:writeprobe:./data/hantteok;...`를 사용합니다. 이 어댑터는 기존 파일을 쓰기 모드로 열 수 있는지만 확인하며 바이트나 OS 권한을 변경하지 않습니다. `DB_URL`을 명시하면 어댑터를 사용하지 않습니다. 일반 환경에서 `mvnw spring-boot:run`과 표준 `jdbc:h2:file:` URL은 그대로 사용할 수 있습니다.
