package com.sokdak.limit.adapter.inbound.api.controllers

import com.sokdak.limit.adapter.inbound.api.dto.requests.ConsumeUsageLimitRequest
import com.sokdak.limit.adapter.inbound.api.dto.responses.UserUsageLimitResponse
import com.sokdak.limit.adapter.inbound.api.mappers.toCommand
import com.sokdak.limit.adapter.inbound.api.mappers.toResponse
import com.sokdak.limit.adapter.inbound.api.mappers.toRestoreCommand
import com.sokdak.limit.application.usecases.ConsumeUsageLimitUseCase
import com.sokdak.limit.application.usecases.GetUserLimitsUseCase
import com.sokdak.limit.application.usecases.RestoreUsageLimitUseCase
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users/{userId}/limits")
class UserLimitController(
    private val getUserLimitsUseCase: GetUserLimitsUseCase,
    private val consumeUsageLimitUseCase: ConsumeUsageLimitUseCase,
    private val restoreUsageLimitUseCase: RestoreUsageLimitUseCase,
) {
    @GetMapping
    fun getUserLimits(
        @PathVariable userId: String,
    ): List<UserUsageLimitResponse> {
        val limits = getUserLimitsUseCase.execute(userId)
        return limits.map { it.toResponse() }
    }

    @PostMapping("/consume")
    @ResponseStatus(HttpStatus.OK)
    fun consumeUsageLimit(
        @PathVariable userId: String,
        @RequestBody request: ConsumeUsageLimitRequest,
    ): UserUsageLimitResponse {
        val limit = consumeUsageLimitUseCase.execute(request.toCommand(userId))
        return limit.toResponse()
    }

    @PostMapping("/restore")
    @ResponseStatus(HttpStatus.OK)
    fun restoreUsageLimit(
        @PathVariable userId: String,
        @RequestBody request: ConsumeUsageLimitRequest,
    ): UserUsageLimitResponse {
        val limit = restoreUsageLimitUseCase.execute(request.toRestoreCommand(userId))
        return limit.toResponse()
    }
}
