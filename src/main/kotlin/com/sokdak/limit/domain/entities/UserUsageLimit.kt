package com.sokdak.limit.domain.entities

import com.sokdak.limit.domain.enums.ActionType
import java.time.Instant
import java.time.LocalDate

data class UserUsageLimit(
    val id: String,
    val userId: String,
    val action: ActionType,
    val dailyLimit: Int,
    var dailyUsed: Int,
    var lastResetDate: LocalDate,
    val createdAt: Instant,
    var updatedAt: Instant,
) {
    fun canConsume(count: Int = 1): Boolean = dailyUsed + count <= dailyLimit

    fun consume(count: Int = 1) {
        require(canConsume(count)) {
            "Daily limit exceeded for action $action. Used: $dailyUsed, Limit: $dailyLimit, Requested: $count"
        }
        dailyUsed += count
        updatedAt = Instant.now()
    }

    fun restore(count: Int = 1) {
        dailyUsed = maxOf(0, dailyUsed - count)
        updatedAt = Instant.now()
    }

    fun resetIfNeeded(currentDate: LocalDate = LocalDate.now()) {
        if (lastResetDate.isBefore(currentDate)) {
            dailyUsed = 0
            lastResetDate = currentDate
            updatedAt = Instant.now()
        }
    }

    fun getRemainingLimit(): Int = maxOf(0, dailyLimit - dailyUsed)
}
