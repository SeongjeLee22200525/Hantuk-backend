# 최신 프론트 서버 연동 계약

원격 기준 커밋: `8c56fe0`. 작업 브랜치: `feat/latest-frontend-backend`. 기존 연동 브랜치 `feat/backend-api-integration`은 그대로 보존합니다.

## 실행과 계정

기본 서버는 http://127.0.0.1:8081, 프론트는 http://127.0.0.1:8080, API는 `/api/v2`입니다. 이 PC의 `outputs/run-server.ps1`, `outputs/run-app.ps1`도 새 버전으로 갱신했습니다. 다른 위치에서 소스를 실행할 때는 Java 17 이상과 Flutter가 필요합니다.

|아이디|비밀번호|역할|
|---|---|---|
|hantuck01|welcome123!|일반 회원·예시 선결제 후원자|
|leader01, leader02, leader03|welcome123!|해당 예시 그룹의 그룹장|
|store01|welcome123!|예시 가게 1의 소유자|
|admin01|welcome123!|가게 신청 심사 관리자|

새 계정은 서버에서 아이디 중복 확인, 이메일 인증 요청·확인, 가입을 순서대로 진행합니다. local 프로필은 실제 메일을 보내지 않고 123456을 사용합니다. 서버 인증 증명은 이메일에 결합되고 10분 후 만료되며 5회 실패·가입 후 재사용을 막습니다. production 프로필에서는 외부 메일 제공자를 연결하기 전 인증 요청이 501을 반환합니다. 회원가입으로 관리자·가게·그룹장 권한을 얻을 수 없습니다.

기존 이메일 로그인 기반 v1 API와 데이터는 유지합니다. 새 화면의 계정과 데이터는 v2 전용 테이블에 저장하며 서로 자동 변환하지 않습니다.

## API

- `GET /api/v2/health`: 상태 및 demo 여부
- `GET /api/v2/auth/availability?id=...`: 아이디 사용 가능 여부
- `POST /api/v2/auth/email-challenges`: `{email}` → challengeId
- `POST /api/v2/auth/email-verify`: `{challengeId,code}` → verificationToken
- `POST /api/v2/auth/register`: `{id,password,name,birthDate,email,phoneNumber,verificationToken}`
- `POST /api/v2/auth/login`: `{id,password}` → accessToken, account
- `POST /api/v2/auth/logout`: 현재 세션 폐기
- `GET /api/v2/snapshot`: 인증된 계정이 조회할 수 있는 화면 데이터
- `POST /api/v2/commands`: `{commandId,action,data}` → `{result,snapshot}`

로그아웃·스냅샷·명령은 `Authorization: Bearer <accessToken>`이 필요합니다. 브라우저에는 세션 토큰만 저장하며 서버 비밀번호는 BCrypt 해시로, 세션 토큰은 SHA-256 해시로 보관합니다. 세션은 24시간 후 만료됩니다. API 오류는 로컬 결제 성공으로 대체하지 않습니다.

명령은 다음과 같습니다. 계정 ID·소유자 ID를 클라이언트가 보내더라도 권한은 세션의 계정으로 판정합니다.

|action|data의 핵심 필드|
|---|---|
|group.join|groupId, introduction|
|group.invite|groupId, code|
|group.approve|applicationId|
|group.favorite|groupId|
|store.favorite|storeId|
|store.apply|name, registrationNumber, address, category, phoneNumber, description, resubmissionOf?|
|store.approve / store.reject|applicationId, reason(반려)|
|store.thumbnail|storeId, thumbnail|
|store.menu.save|storeId, index?, item:{name,description,price,recommended}, expectedMenu|
|store.menu.remove|storeId, index, expectedMenu|
|donation.complete|checkoutId, type:money/prepaid, groupId, amount, storeId?, expiresOn?|
|usage.begin|offerId|
|usage.find|storeId, code|
|usage.amount|storeId, requestId, amount|
|usage.approve / usage.cancel|requestId|
|usage.reverse|storeId, receiptId, reason?|
|usage.thanks|receiptId, message, photo?:{name,base64}|

금액은 양의 정수 원 단위이며 웹에서 정확하게 표현할 수 있는 9,007,199,254,740,991원 이하입니다. 날짜는 `yyyy-MM-dd`, 시간은 UTC ISO-8601입니다. 기한 판정은 서울 날짜를 사용하며 종료일 23:59:59까지 유효합니다. QR 코드 형식은 `hantuck:prepaid:<6자리 코드>`이고 10분간 유효합니다.

## 규칙과 개인정보

- 공개 그룹은 가입 신청 후 그룹장 승인, 비공개 그룹은 올바른 초대 코드로 가입합니다. 후원·즐겨찾기는 가입 권한을 만들지 않습니다.
- 가게 신청은 관리자 승인 전 공개되지 않습니다. 반려 후 최신 신청만 재신청할 수 있고 기존 차수·사유는 변경하지 않습니다.
- 메뉴 수정은 클라이언트가 읽었던 expectedMenu와 현재 값이 다르면 409를 반환하여 동시 수정 손실을 막습니다.
- QR 발급과 가게의 금액 요청은 잔액을 차감하지 않습니다. 이용자 승인 시 소속·만료·현재 잔액·가게 권한을 다시 확인하고 한 번만 차감합니다. 잔액을 넘는 요청은 부분 처리하지 않고 거절합니다.
- 같은 요청을 재승인하면 기존 영수증을 반환합니다. 사용 취소는 해당 가게 소유자만 가능하고 원래 선결제에 한 번만 복구합니다. 원래 기한을 연장하지 않습니다.
- 감사 메시지·사진은 본인이 완료한 미취소 거래에 한 번만 보낼 수 있습니다. 본인과 해당 후원자만 본문·사진을 받고 가게에는 전달하지 않습니다. 사진은 PNG/JPEG/WebP 서명 및 5MB 한도를 검사하고 인증된 스냅샷을 통해 전달합니다.
- 스냅샷은 본인 정보, 본인/그룹장 신청, 본인/관리자 가게 신청, 본인/후원자/가게 소유자의 거래를 역할에 맞춰 제한합니다. 초대 코드 해시·비밀번호·다른 계정의 즐겨찾기를 반환하지 않습니다.

## DB와 한계

Flyway `V2__latest_frontend.sql`은 기존 v1 테이블을 지우지 않고 다음 테이블을 추가합니다.

- prototype_accounts: 새 화면의 아이디·프로필·비밀번호 해시
- prototype_sessions: 세션 토큰 해시·유효 기간
- prototype_email_challenges: 이메일 인증 시도·증명·만료
- prototype_commands: 계정별 명령 ID·요청 지문·처리 결과
- prototype_state: 서버가 관리하는 도메인 상태 JSON과 revision

현재 v2는 프로토타입 규모에 맞춰 하나의 상태 행을 `SELECT FOR UPDATE`로 잠그고 검증·차감·기록을 같은 트랜잭션으로 처리합니다. 두 이용자가 동시에 승인해도 잔액을 초과해 차감하지 않습니다. 같은 명령 ID로 같은 내용을 보내면 기존 결과를 돌려주며 다른 내용은 409입니다. 클라이언트가 잔액이나 전체 상태를 덮어쓰는 API는 없습니다.

이 구조는 단순한 정합성을 위한 선택입니다. 운영 규모가 커지면 엔티티별 정규화·행 잠금, 페이지네이션, 사진 오브젝트 스토리지, 요청 제한 및 실 PostgreSQL 테스트가 필요합니다. 현재 사진은 Base64로 상태에 저장하므로 대규모 파일 저장 용도로 설계하지 않았습니다. 외부 PG·실제 메일·송금·사업자 진위 확인·푸시는 미연동입니다.

## 검증

```powershell
flutter analyze
flutter test
# local 서버 실행 후: 전용 테스트 계정·모의 거래·가게 신청 생성
flutter test test/live_server_v2_test.dart --dart-define=LIVE_API_V2=true
flutter build web --release --no-web-resources-cdn
# server 폴더
./mvnw.cmd verify
```

기존 화면 테스트 103개, 새 서버 모드 위젯 테스트, 실제 서버를 사용한 전체 흐름 테스트를 실행했습니다. 서버 테스트는 기존 15개와 새 v2 11개로 구성합니다. 동시 승인, 권한 위조, 코드 만료, 초대 인증, 결제 재시도, 사용 복구, 감사 사진 공개 범위와 이모지 글자 수를 확인합니다.
