---
name: tdd
description: "Kent Beck TDD + Tidy First 사이클로 지정 도메인의 plan.md 를 진행한다. 사용: /tdd <도메인모델명>"
argument-hint: "<도메인모델명>"
---

# /tdd — TDD 사이클 진행

> 본 커맨드는 [docs/guideline/tdd-guideline.md](../../docs/guideline/tdd-guideline.md) 를 진입점으로 삼는다.
> 인자: `$ARGUMENTS` — 도메인 모델명 (예: `user`, `order`, `product`).
> 대상 plan: `docs/domain/$ARGUMENTS/plan.md`

## 실행 절차

### 1) 입력 검증
- `$ARGUMENTS` 가 비어 있으면 즉시 중단하고 사용자에게 도메인 모델명을 묻는다.
- 도메인 모델명에는 공백/슬래시를 허용하지 않는다.

### 2) 가이드라인과 plan 로드
1. `docs/guideline/tdd-guideline.md` 를 읽어 본 세션의 TDD/Tidy First 규칙을 머릿속에 적재한다.
2. `docs/domain/$ARGUMENTS/plan.md` 를 읽는다.
   - 파일이 없으면 사용자에게 "plan.md 가 없다. 먼저 `/test-cases $ARGUMENTS` 로 생성하라" 고 알리고 **즉시 종료**한다 — 임의로 골격이나 케이스를 채워 넣지 않는다.
   - 파일에 미체크(`- [ ]`) 항목이 하나도 없으면 "더 진행할 케이스가 없다. 새 케이스가 필요하면 `/test-cases $ARGUMENTS` 로 보강하라" 고 알리고 종료한다.
   - 파일이 있고 미체크 항목이 남아 있으면 다음 단계로 진행한다.
3. 같은 도메인의 `docs/domain/$ARGUMENTS/requirements.md` 가 있으면 함께 읽어 컨텍스트로 둔다 (없으면 무시).

### 3) "go" 워크플로우
plan.md 위에서부터 처음 만나는 `- [ ]` 항목 **1개** 만 골라 다음을 수행한다:

1. **Red** — 실패 테스트 1개 작성 (행위·경계·실패 케이스를 의미 있는 이름으로).
2. **Green** — 그 테스트를 통과시키는 **최소 코드** 만 구현.
3. 모든 테스트 + `./gradlew ktlintCheck` 통과 확인.
4. 필요 시 **Refactor (Tidy First)** — 구조 변경은 **별도 커밋**.
5. plan.md 의 해당 체크박스를 `- [x]` 로 갱신한다.
6. 커밋 메시지에 구조/행위 여부를 명시한다 (예: `feat(order): ...` vs `refactor(order): ...`).

### 4) 1회 실행 = 1개 테스트
- 한 번의 `/tdd` 호출에서는 plan 항목을 **하나만** 처리한다 — 다음 항목은 사용자가 다시 `go` 라고 말할 때까지 대기한다.
- 처리 중 새로 떠오른 케이스는 plan.md 에 즉시 추가한다 (살아있는 문서).

### 5) 본 프로젝트 적용 규칙 (재확인)
- 패키지 위계: `domain → application → infrastructure → interfaces`. 도메인은 스프링/JPA 의존을 직접 끌어다 쓰지 않는다.
- 통합 테스트는 `modules:jpa` / `modules:redis` 의 `testFixtures` testcontainers 유틸을 재사용한다.
- `data class` 로 JPA 엔티티 만들지 않는다. `!!` 는 근거 코멘트와 함께만.
- 구조적 변경과 행위적 변경을 **같은 커밋에 섞지 않는다** — 둘 다 필요하면 구조부터.

---

이제 위 절차에 따라 `$ARGUMENTS` 도메인의 다음 미체크 테스트 1개를 진행하라.
