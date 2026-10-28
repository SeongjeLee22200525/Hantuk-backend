# 서버 구조와 코드 읽는 순서

최신 v2 화면과 기존 v1 호환 API가 함께 있습니다. 같은 JSON 키, API 경로, DB 스키마를 유지하며 구현 책임을 나눴습니다.

## 최신 요청 흐름

```text
기능별 Controller
    → CommandService: 세션 인증과 공통 명령 진입
    → PrototypeService: 하나의 트랜잭션
        1. PrototypeStateRepository에서 상태 행 잠금
        2. 같은 계정·commandId의 처리 결과와 요청 지문 확인
        3. 기능 서비스에서 권한·입력·현재 상태 확인 및 변경
        4. 새 상태와 명령 결과를 함께 저장
        5. SnapshotService로 사용자에게 공개할 데이터만 투영
```

상태 변경 서비스는 현재 요청의 상태 Map을 인자로 받습니다. 빈의 필드에 사용자 상태·잔액·Map을 보관하지 않습니다. 행 잠금과 `@Transactional`은 조정 서비스에 유지해 분리된 기능 사이에서도 중복 차감과 부분 저장을 방지합니다.

## 클래스별 책임

| 위치 | 책임 | 수정할 때 확인할 것 |
|---|---|---|
| controller/*Controller | URL, HTTP 본문 검증, 응답 | 기능별 경로와 인증 헤더 전달 |
| service/CommandService | 세션을 사용자 ID로 바꾸는 공통 진입점 | 클라이언트의 userId를 권한 판단에 사용하지 않음 |
| service/PrototypeService | 상태 잠금, 명령 분기, 중복 처리, 저장 | 트랜잭션·명령 지문·호환 경로의 일관성 |
| service/GroupService | 단체 가입 신청, 초대 가입, 그룹장 승인 | 가입 방식과 그룹장 권한 |
| service/StoreService | 가게 신청·재신청, 심사, 사진 선택, 메뉴 | 신청 이력과 메뉴 동시 수정 검사 |
| service/DonationService | 후원 완료와 선결제 생성 | checkoutId 재시도, 금액, 사용 기한 |
| service/UsageService | QR 발급·조회, 금액 요청, 승인, 취소, 감사 메시지 | 상태 전이, 소속, 소유자, 기한, 잔액 |
| service/ThanksPhotoService | 감사 사진 Base64·서명·용량 검증 | PNG/JPEG/WebP, 최대 5MB |
| service/FavoriteService | 단체·가게 즐겨찾기의 공통 처리 | 사용자별 독립 저장과 중복 요청 |
| service/SnapshotService | 계정별 조회 범위와 민감 정보 제외 | 초대 해시 제외, 가게에 감사 본문·사진 비공개 |
| service/PrototypeAuthService | v2 계정, 세션, 이메일 인증 | BCrypt, 토큰 해시, 인증 횟수·기한·소비 처리 |
| repository/PrototypeStateRepository | 상태·명령 기록 SQL | 읽기와 잠금의 구분, 같은 트랜잭션에서 저장 |
| repository/DatabaseRepository | JDBC 공통 실행과 시계 | 매개변수 바인딩과 DB 시간 |
| common/validation/RequestValues | 입력값 파싱 | 기존 길이 제한·이모지 글자 수·오류 메시지 |
| common/state/StateData | JSON 컬렉션 접근, 소속·소유권·금액 공통 검사 | 영속 JSON 키 유지 |

## 기존 v1 코드

`controller/legacy/ApiController`가 기존 경로를 담당합니다. `AuthService`, `BenefitService`, `CatalogService`, `MerchantService`, `QrService`, `SupportService`가 기존 계약을 지원합니다. 이번 변경에서는 전체 포맷과 제어문 블록을 정리하고 호환 동작을 유지합니다.

## 스타일과 검증

- 파일명과 클래스명은 동일하며 컨트롤러·서비스·리포지토리는 각각 역할 접미사를 사용합니다.
- 한 줄에 여러 문장을 넣지 않고 if·반복문에는 중괄호를 사용합니다.
- 입력·현재 상태·신청·이용 요청 등 역할을 드러내는 이름을 사용합니다.
- Controller에 거래 로직을 추가하지 않습니다. 상태 변경은 명령 조정 서비스를 거칩니다.
- `formatJava`로 정렬하고 `build`로 컴파일·포맷 검사·JUnit을 실행합니다.
- 주요 테스트는 권한, 개인정보 공개 범위, 중복 승인, 동시 차감, 취소 복원, 인증 만료, 이모지 길이와 HTTP 경로를 검증합니다.

## 현재 구조의 한계

v2 도메인은 프로토타입 규모에 맞춰 상태 JSON 한 행을 사용합니다. 서비스와 리포지토리를 분리한 이번 작업이 엔티티별 정규화나 실 PostgreSQL 검증을 대신하지 않습니다. 운영 확장 시에는 테이블별 모델·행 잠금, 페이지네이션, 사진 스토리지 분리가 필요합니다. 메일·PG 결제·송금은 계속 모의 처리입니다.
