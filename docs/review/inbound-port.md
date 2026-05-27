# 리뷰 포인트 — 인바운드 포트 도입 보류

> 상태: **보류 (Deferred)**
> 작성일: 2026-05-28
> 관련 문서: [docs/architecture.md](../architecture.md)

## 배경

`UserRepository` 인터페이스를 `domain.user` 에서 `application.user.port` 로 이동하면서, 엄격 헥사고날(strict hexagonal) 의 **outbound port + adapter** 구조를 도입했다. 이로써 다음이 명확해졌다.

- `domain` 은 Repository 개념 자체를 모른다 (순수 모델만 보유).
- `application` 이 자신이 필요로 하는 추상화(`port.UserRepository`) 를 소유한다.
- `infrastructure.UserRepositoryImpl` 이 그 port 를 구현한다 — DIP 의 모범 사례.

그런데 같은 논리를 **반대편(inbound)** 에 적용하면 비대칭이 드러난다.

## 문제 — DIP 의 비대칭

현재 `interfaces` 계층의 inbound adapter 가 `application` 의 구체 클래스 `UserFacade` 를 직접 의존한다.

```kotlin
// apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/auth/AuthInterceptor.kt
class AuthInterceptor(
    private val userFacade: UserFacade,  // ← 구체 클래스에 의존
) : HandlerInterceptor { ... }

// apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Controller.kt
class UserV1Controller(
    private val userFacade: UserFacade,  // ← 구체 클래스에 의존
)
```

방향 자체는 헥사고날 규칙(외부 → 내부) 을 어기지 않는다. 다만 **의존 대상이 추상화가 아닌 구체** 라는 점에서 DIP 가 헐겁다.

| 방향 | 어댑터 | 의존 대상 | DIP |
|---|---|---|---|
| outbound | `UserRepositoryImpl` | `application.user.port.UserRepository` (I) | ✅ 추상화 |
| inbound | `AuthInterceptor`, `UserV1Controller` | `application.user.UserFacade` (구체) | ⚠️ 구체 |

outbound 와 inbound 가 같은 형태로 정렬되어 있지 않다.

## 고려한 해결책

### 옵션 1 — 현 상태 유지 (구체 Facade 의존)

`interfaces` 가 `UserFacade` 를 그대로 의존한다.

- 보일러플레이트 없음.
- Spring/Kotlin 실무에서 가장 흔한 절충.
- Facade 가 자기 코드라 안정적이며, 어댑터가 하나(REST) 뿐이라 다중화 필요 없음.
- DIP 는 "형식적으로" 헐겁지만 실무적으로 비용/이득 균형이 잡힘.

### 옵션 2 — 유스케이스 단위 inbound port

유스케이스마다 인터페이스를 분리한다.

```
application.user/
  port/
    in/
      SignUpUseCase.kt            interface { fun signUp(cmd: SignUpCommand): SignUpResult }
      AuthenticateUseCase.kt      interface { fun authenticate(loginId: String, pw: String): User }
      GetMyInfoUseCase.kt         interface { fun getMyInfo(loginId: String): MyInfoResult }
      ChangePasswordUseCase.kt    interface { fun changePassword(cmd: ChangePasswordCommand) }
    UserRepository.kt
  UserFacade.kt   ← 위 네 인터페이스를 모두 구현
```

```kotlin
class AuthInterceptor(
    private val authenticateUseCase: AuthenticateUseCase,
) : HandlerInterceptor { ... }
```

- DIP 엄격 충족.
- outbound 와 대칭.
- 가장 보일러플레이트가 큼 — 메서드 1개짜리 인터페이스가 4~5개씩 생성됨.
- 인터페이스 분리 원칙(ISP) 도 만족.
- 어댑터 다중화 (gRPC, MQ consumer) 시 즉시 대응 가능.

### 옵션 3 — 도메인 단위 inbound port (단일 인터페이스)

도메인 한 덩어리에 인터페이스 하나를 둔다.

```kotlin
// application.user.port.UserUseCase.kt
interface UserUseCase {
    fun signUp(command: SignupCommand): SignupResult
    fun authenticate(loginId: String, plainPassword: String): User
    fun getMyInfo(loginId: String): MyInfoResult
    fun changePassword(command: ChangePasswordCommand)
}

class UserFacade(...) : UserUseCase { ... }

class AuthInterceptor(private val userUseCase: UserUseCase) : ...
```

- DIP 는 형식적으로 만족.
- 옵션 2 보다 보일러플레이트 적음.
- 인터페이스가 도메인 전체 유스케이스를 모아 god-interface 가 될 위험.
- ISP 는 만족하지 않음 — Interceptor 는 `authenticate` 만 필요한데 다른 4개도 같이 알게 됨.

## 트레이드오프 요약

| 항목 | 옵션 1 (유지) | 옵션 2 (유스케이스별) | 옵션 3 (도메인 단일) |
|---|---|---|---|
| DIP | 헐거움 | 엄격 | 형식 충족 |
| outbound 와 대칭성 | 비대칭 | 완전 대칭 | 부분 대칭 |
| 보일러플레이트 | 없음 | 큼 | 중간 |
| ISP | 자연 만족 | 만족 | 위반 가능 |
| 어댑터 다중화 비용 | 리팩토링 필요 | 즉시 가능 | 즉시 가능 |
| 테스트 mocking | 구체 mock | 인터페이스 mock | 인터페이스 mock |
| 학습 가치 (헥사고날 체험) | 낮음 | 높음 | 중간 |

## 잠정 결정 — 옵션 1 유지

다음 이유로 inbound port 도입은 보류한다.

- 현재 어댑터는 REST 하나뿐. 다중 어댑터 시나리오가 아직 없다.
- `UserFacade` 는 안정적인 자기 코드이며, 변경 빈도가 낮다 — DIP 가 막아야 할 "휘발성 구체 의존" 에 해당하지 않는다.
- 옵션 2 의 보일러플레이트 비용이 학습 프로젝트 단계에서 회수되지 않는다.

다만 outbound 와의 **비대칭** 은 의도된 절충임을 문서로 명시한다. 헥사고날을 "양쪽 다 port" 가 정통이라는 점은 인지하고 있다.

## 재검토 트리거

다음 중 하나라도 발생하면 옵션 2 (유스케이스 단위 inbound port) 도입을 다시 검토한다.

- 두 번째 inbound adapter 추가 (gRPC, Kafka consumer, CLI 등).
- `UserFacade` 가 너무 커져 책임 분리가 필요해질 때 — 유스케이스 단위로 자연스럽게 쪼개는 계기로 활용.
- 팀 차원의 헥사고날 엄격 적용 합의가 형성될 때.

## 멘토 / 리뷰어 질문 거리

1. 실무에서 inbound port 를 도입하시는 기준이 따로 있으신가요? (어댑터 수? 팀 규모? 도메인 안정성?)
2. 옵션 3 (도메인 단일 인터페이스) 처럼 ISP 와 DIP 가 충돌할 때 어느 쪽을 우선하시나요?
3. `UserFacade` 와 `UserUseCase` 가 사실상 1:1 이라면, 단순히 `final` 만 풀고 클래스로 의존하는 절충은 어떻게 보시나요?
