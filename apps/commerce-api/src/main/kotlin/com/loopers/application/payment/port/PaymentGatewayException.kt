package com.loopers.application.payment.port

/**
 * 외부 PG 연동 실패를 표현하는 포트 레벨 예외 — 통신 실패·타임아웃·회로 차단을 애플리케이션 경계로 변환한다.
 * RestClient 등 인프라 예외가 상위 계층으로 누수되지 않도록 어댑터가 이 타입으로 감싼다.
 * 일시 장애(transient)로 간주되며, 결제 요청 경로는 이를 잡아 REQUESTED 로 두고 폴링으로 복구한다(Fallback).
 */
class PaymentGatewayException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
