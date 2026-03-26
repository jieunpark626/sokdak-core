package com.sokdak.limit.domain.repositories

import com.sokdak.limit.domain.entities.UserUsageLimit
import com.sokdak.limit.domain.enums.ActionType

interface UserUsageLimitRepository {
    fun save(limit: UserUsageLimit): UserUsageLimit

    fun findByUserIdAndAction(
        userId: String,
        action: ActionType,
    ): UserUsageLimit?

    fun findByUserIdAndActionWithLock(
        userId: String,
        action: ActionType,
    ): UserUsageLimit?

    fun findAllByUserId(userId: String): List<UserUsageLimit>
}
