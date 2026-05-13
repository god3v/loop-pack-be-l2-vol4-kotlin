# plan.md — TDD 진척 체크리스트 (Template)

> 이 문서는 [docs/tdd-guideline.md](./tdd-guideline.md) 의 "go" 명령어가 참조하는 **테스트 체크리스트** 템플릿이다.
> 실제 작업용 plan.md 는 `docs/<주차 또는 도메인>/plan.md` 경로에 복사해 사용한다.

---

## 사용 규칙

- **한 줄 = 하나의 실패 테스트 = 하나의 Red → Green → Refactor 사이클**
- **단순한 것 → 복잡한 것** 순으로 배치 (Beck 의 "simplest test first")
- **행위/시나리오**를 적는다 — 구현 디테일이 아닌, 사용자/도메인이 관찰 가능한 행위로 서술
  - ❌ "MemberService 클래스 만들기"
  - ✅ "동일한 이메일로 가입을 시도하면 DUPLICATE_EMAIL 예외가 발생한다"
- 작업 중 떠오른 케이스는 즉시 plan.md 에 추가 (살아있는 문서)

### 진척 표시 규약
| 마크 | 의미 |
|---|---|
| `- [ ]` | 미시작 |
| `- [~]` | 진행 중 (Red 만 작성, Green 미완) |
| `- [x]` | 완료 (Green + 필요 시 Refactor 까지) |

### "go" 워크플로우
1. 사용자가 **"go"** → Claude 는 위에서부터 첫 번째 `- [ ]` 항목 1개를 찾는다
2. 실패 테스트(Red) 작성 → 최소 구현(Green) → 필요 시 구조 정리(Refactor)
3. 사용자 확인 후 체크박스 갱신 + 커밋 (구조/행위 분리)
4. 다음 "go" 까지 대기

---

## 회원 도메인 — Phase 골격 (예시 스켈레톤)

> 아래는 본 프로젝트의 4계층 구조(`domain → application → infrastructure → interfaces`)에 맞춘 일반적인 진행 순서다.
> 실제 테스트 케이스는 `docs/week1/requirements.md` 같은 요구사항 명세를 기반으로 채워 넣는다.

### Phase 1 — 도메인 모델 (`com.loopers.domain.member`)
> 값 객체, 엔티티, 도메인 규칙. 스프링/JPA 의존 없이 순수 Kotlin 로 작성.

- [ ] (예) 이메일 형식이 잘못되면 도메인 모델 생성 시 예외가 발생한다
- [ ] (예) 비밀번호가 정책을 만족하지 않으면 예외가 발생한다

### Phase 2 — 도메인 서비스 (`com.loopers.domain.member.MemberService`)
> 도메인 규칙 조합 + Repository 인터페이스를 통한 상태 변경.

- [ ] (예) 동일한 이메일이 이미 존재하면 DUPLICATE_EMAIL 예외가 발생한다
- [ ] (예) 정상 가입 시 신규 회원 ID 가 발급된다

### Phase 3 — 인프라 어댑터 (`com.loopers.infrastructure.member`)
> JPA 매핑, Repository 구현. Testcontainers(MySQL) 통합 테스트.

- [ ] (예) MemberRepositoryImpl 이 영속화와 식별자 기반 조회를 수행한다
- [ ] (예) 동일 이메일로 저장 시 DB 유니크 제약을 위반한다

### Phase 4 — Application Facade (`com.loopers.application.member.MemberFacade`)
> 유스케이스 진입점. DTO 변환 + (필요 시) 트랜잭션 경계.

- [ ] (예) 회원가입 Facade 가 MemberInfo 를 반환한다
- [ ] (예) 도메인 예외가 그대로 위로 전파된다

### Phase 5 — Controller E2E (`com.loopers.interfaces.api.member`)
> HTTP 계약 검증. `ApiResponse` + `ApiControllerAdvice` 표준 응답.

- [ ] (예) POST /api/v1/members 가 201 과 표준 응답을 반환한다
- [ ] (예) 검증 실패 요청은 400 + 표준 에러 응답을 반환한다

---

## 진행 로그

날짜별 주요 사건만 짧게 기록한다 (커밋 해시는 적지 않는다 — git 이 가지고 있다).

- YYYY-MM-DD: 시작
