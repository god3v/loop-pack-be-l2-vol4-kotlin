package com.loopers.support.error

import org.springframework.http.HttpStatus

interface ErrorType {
    val status: HttpStatus
    val code: String
    val message: String
}
