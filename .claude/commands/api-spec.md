---
name: api-spec
description: "도메인의 requirements.md 를 기반으로 guideline/api-spec-template.md 규약에 맞춰 API 명세를 작성한다. 사용: /api-spec <도메인모델명>"
argument-hint: "<도메인모델명>"
---

# /api-spec — API 명세 작성

> 본 커맨드는 [docs/guideline/api-spec-template.md](../../docs/guideline/api-spec-template.md) 의 규약을 따른다.
> 인자: `$ARGUMENTS` — 도메인 모델명 (예: `user`, `order`).
> 입력 참고: `docs/domain/$ARGUMENTS/requirements.md`
> 출력 파일: `docs/domain/$ARGUMENTS/api-spec.md`

## 실행 절차

### 1) 입력 검증
- `$ARGUMENTS` 가 비어 있으면 즉시 중단하고 도메인 모델명을 묻는다.
- `docs/domain/$ARGUMENTS/requirements.md` 가 없으면 사용자에게 "요구사항 문서가 먼저 필요하다" 고 알리고 **종료**한다 — 임의 추정으로 명세를 만들지 않는다.

### 2) 자료 로드
1. `docs/guideline/api-spec-template.md` 를 읽어 **공통 응답 포맷(`§0`)**, **엔드포인트 작성 규약(`§1`)**, **빈 블록(`§2`)** 의 구조를 그대로 적용할 준비를 한다.
2. `docs/domain/$ARGUMENTS/requirements.md` 를 읽어 도출 가능한 엔드포인트(메서드/경로/입력/출력/실패 케이스) 를 식별한다.
3. `docs/domain/$ARGUMENTS/api-spec.md` 가 이미 있으면 그 내용을 읽어 **덮어쓰지 말고 보강·수정** 한다.

### 3) 명세 작성 규약
- 모든 응답은 `ApiResponse<T>` 로 감싼다 (`§0.1` 구조 준수).
- 각 엔드포인트는 템플릿 `§1` 의 5블록 순서를 정확히 따른다:
  `Request → (Request Body 표) → Response → (Response Body 표) → 실패 응답 표`.
- Request/Response 모두 **JSON 예시 + 필드 표** 를 짝으로 둔다.
- 응답 데이터가 없는 성공도 `data: null` 인 JSON 예시를 보여준다.
- 실패 응답 JSON 본문은 **반복하지 않는다** — 템플릿 `§0.3` 를 참조하도록 한다.
- 실패 응답 표는 **HTTP / errorCode / 케이스** 3개 컬럼을 유지한다.
- 표준 에러 코드(`§0.4`)를 우선 사용하고, 도메인 추가 코드(예: `DUPLICATE_LOGIN_ID`)는 같은 표에 명시한다.
- 비밀번호/토큰 등 민감 필드는 어떤 응답 예시에도 포함하지 않는다.
- 본문 톤은 한국어 `~다` 종결을 유지한다.

### 4) 출력 파일 구조
```
# {도메인} API 명세

> 본 문서는 `docs/guideline/api-spec-template.md` 의 규약을 따른다.
> 요구사항: `docs/domain/$ARGUMENTS/requirements.md`

## 1. {엔드포인트 한국어 이름}
... (§1 형식)

## 2. {엔드포인트 한국어 이름}
...
```

### 5) 작성 체크리스트 (커밋 전 확인)
- [ ] 메서드/경로/Content-Type/인증 요건이 `### Request` bullet 3줄에 모두 적혀 있다
- [ ] Request Body 가 없으면 "**Request Body**: 없음" 으로 명시한다
- [ ] Request/Response 가 JSON 예시 + 필드 표 짝을 이룬다
- [ ] 실패 응답 표가 HTTP / errorCode / 케이스 3컬럼이다
- [ ] 도메인 추가 errorCode 가 표에 명시되어 있다
- [ ] 민감 필드가 노출되지 않았다

---

이제 `$ARGUMENTS` 도메인의 requirements 를 기반으로 위 규약에 맞춰 `docs/domain/$ARGUMENTS/api-spec.md` 를 작성하라.
