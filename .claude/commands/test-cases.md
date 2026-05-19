---
name: test-cases
description: "도메인의 requirements/api-spec 에서 테스트 케이스를 도출해 plan.md 를 생성·보강한다(멱등). 사용: /test-cases <도메인모델명>"
argument-hint: "<도메인모델명>"
---

# /test-cases — plan.md 생성·보강

> 인자: `$ARGUMENTS` — 도메인 모델명 (예: `user`, `order`).
> 입력: `docs/domain/$ARGUMENTS/requirements.md` (필수), `docs/domain/$ARGUMENTS/api-spec.md` (있으면 활용).
> 포맷 기준: [docs/guideline/plan-template.md](../../docs/guideline/plan-template.md).
> 출력: `docs/domain/$ARGUMENTS/plan.md` (없으면 생성, 있으면 보강).

본 커맨드는 **테스트 케이스 brainstorm 도구**다. 실제 테스트 코드 작성은 `/tdd` 가 맡는다 — 본 커맨드는 plan.md 의 `- [ ]` 체크리스트만 채운다.

## 실행 절차

### 1) 입력 검증
- `$ARGUMENTS` 가 비어 있으면 즉시 중단하고 도메인 모델명을 묻는다.
- `docs/domain/$ARGUMENTS/requirements.md` 가 없으면 "요구사항 문서가 먼저 필요하다 — `docs/domain/$ARGUMENTS/requirements.md` 를 작성하라" 고 알리고 **종료**한다. 추정으로 케이스를 만들지 않는다.

### 2) 자료 로드
1. `docs/guideline/plan-template.md` 를 읽어 **사용 규칙·진척 표시 규약·Phase 1~5 구조** 를 따를 준비를 한다.
2. `docs/domain/$ARGUMENTS/requirements.md` 를 읽어 비즈니스 규칙·정책·실패 케이스를 식별한다.
3. `docs/domain/$ARGUMENTS/api-spec.md` 가 있으면 함께 읽어 HTTP 계약·요청 검증·표준 에러 코드(`BAD_REQUEST`, `CONFLICT`, ...) 가 만들어내는 추가 경계 케이스를 도출한다.
4. `docs/domain/$ARGUMENTS/plan.md` 가 이미 있으면 그 내용을 읽어 **기존 항목과 비교** 한다.

### 3) 케이스 도출 규칙
다음 가드레일을 모두 지킨다:

- **관찰 가능한 행위만** 적는다 — 사용자/도메인 관점에서 외부에서 확인 가능한 결과로 서술한다.
  - 좋음: `동일한 이메일로 가입을 시도하면 DUPLICATE_EMAIL 예외가 발생한다`
  - 나쁨: `MemberService.signUp() 메서드 만들기`, `private validateEmail() 검증 추가`
- **구현 디테일/내부 메서드명 금지**. private 메서드는 직접 검증하지 않는다 (이 프로젝트의 일관된 방침).
- **simplest-first 정렬** — 가장 단순한 happy path → 분기/검증 → 경계값 → 실패/예외 → 동시성·중복 순.
- **행위/경계값/실패 케이스를 함께** 다룬다. 한쪽만 채우지 않는다.
- 한 줄 = 하나의 실패 테스트 = 하나의 Red→Green 사이클. 두 가지 행위를 한 줄에 묶지 않는다.
- 의미 있는 한국어 서술. 종결은 `~다`.
- 비밀번호/토큰 등 민감 필드는 케이스 텍스트에 노출하지 않는다.

### 4) Phase 분류 (본 프로젝트의 4계층)
도출한 케이스를 아래 5개 Phase 로 배치한다. plan-template 의 Phase 헤더/설명 문구를 그대로 유지한다.

| Phase | 위치 | 다룰 것 |
|---|---|---|
| 1. 도메인 모델 | `com.loopers.domain.$ARGUMENTS` | 값 객체·엔티티 불변식, 생성/상태전이 규칙 (순수 Kotlin) |
| 2. 도메인 서비스 | `com.loopers.domain.$ARGUMENTS.{Aggregate}Service` | 규칙 조합, Repository 인터페이스 통한 상태 변경 |
| 3. 인프라 어댑터 | `com.loopers.infrastructure.$ARGUMENTS` | JPA 매핑·Repository 구현 (testcontainers MySQL) |
| 4. Application Facade | `com.loopers.application.$ARGUMENTS` | 유스케이스 진입점, DTO 변환, 트랜잭션 경계, 예외 전파 |
| 5. Controller E2E | `com.loopers.interfaces.api.$ARGUMENTS` | HTTP 계약, `ApiResponse` + `ApiControllerAdvice` 표준 응답 |

- 명백히 한 계층에 속하지 않는 케이스는 가장 외곽 계층(상위 Phase)에 두지 않고 **가장 단순하게 검증할 수 있는 가장 내부 계층** 에 둔다 (예: 이메일 형식 검증은 Phase 1).
- 어떤 Phase 에 해당 케이스가 없다면 그 Phase 헤더는 비워둔다 (헤더 자체를 지우지 않는다).

### 5) 멱등 머지 규칙
plan.md 가 이미 있을 때:

- **체크 상태 보존**: `- [x]`, `- [~]` 항목은 절대 수정·제거하지 않는다.
- **중복 추가 금지**: 의미상 동일한 케이스(같은 행위·같은 실패 조건)는 다시 추가하지 않는다. 표현이 다르더라도 동일 의도면 추가하지 않고 보고만 한다.
- **신규 케이스만 append**: 해당 Phase 의 마지막에 `- [ ]` 로 덧붙인다.
- **누락된 Phase 헤더 생성**: plan.md 에 Phase 헤더가 빠져 있으면 plan-template 의 헤더를 그대로 삽입한다.
- 사용자 손글씨 메모/진행 로그 섹션은 건드리지 않는다.

### 6) 생성 시 파일 구조 (신규 plan.md)
```markdown
# $ARGUMENTS 도메인 — TDD 플랜

> **입력 명세**: [requirements.md](./requirements.md)
> **방법론**: [Kent Beck TDD & Tidy First](../../guideline/tdd-guideline.md)
> **시작일**: {오늘 날짜 YYYY-MM-DD}

---

(plan-template 의 "사용 규칙" / "진척 표시 규약" / "go 워크플로우" 섹션 그대로 포함)

---

### Phase 1 — 도메인 모델 (`com.loopers.domain.$ARGUMENTS`)
- [ ] ...
- [ ] ...

### Phase 2 — 도메인 서비스 (`com.loopers.domain.$ARGUMENTS.{Aggregate}Service`)
- [ ] ...

### Phase 3 — 인프라 어댑터 (`com.loopers.infrastructure.$ARGUMENTS`)
- [ ] ...

### Phase 4 — Application Facade (`com.loopers.application.$ARGUMENTS`)
- [ ] ...

### Phase 5 — Controller E2E (`com.loopers.interfaces.api.$ARGUMENTS`)
- [ ] ...

---

## 진행 로그
- {YYYY-MM-DD}: /test-cases 로 초기 케이스 도출
```

### 7) 작업 보고
완료 시 사용자에게 다음을 보고한다:
- 신규 생성인가 / 기존 보강인가
- Phase 별 추가된 `- [ ]` 항목 수
- requirements 에서 모호해서 케이스로 옮기지 못한 항목 (있으면 질문 리스트로)

---

이제 위 절차에 따라 `$ARGUMENTS` 도메인의 plan.md 를 생성·보강하라.
