package com.sokdak.limit.adapter.inbound.api.mappers

import com.sokdak.limit.adapter.inbound.api.dto.requests.ConsumeUsageLimitRequest
import com.sokdak.limit.adapter.inbound.api.dto.responses.UserUsageLimitResponse
import com.sokdak.limit.application.commands.ConsumeUsageLimitCommand
import com.sokdak.limit.application.commands.RestoreUsageLimitCommand
import com.sokdak.limit.domain.entities.UserUsageLimit

fun UserUsageLimit.toResponse(): UserUsageLimitResponse =
    UserUsageLimitResponse(
        userId = userId,
        action = action,
        dailyLimit = dailyLimit,
        dailyUsed = dailyUsed,
        remaining = getRemainingLimit(),
        lastResetDate = lastResetDate,
    )

fun ConsumeUsageLimitRequest.toCommand(userId: String): ConsumeUsageLimitCommand =
    ConsumeUsageLimitCommand(
        userId = userId,
        action = action,
        count = count,
    )

fun ConsumeUsageLimitRequest.toRestoreCommand(userId: String): RestoreUsageLimitCommand =
    RestoreUsageLimitCommand(
        userId = userId,
        action = action,
        count = count,
    )
