package com.sokdak.limit.adapter.outbound.repositories

import com.sokdak.limit.adapter.outbound.entities.UserUsageLimitJpaEntity
import com.sokdak.limit.domain.enums.ActionType
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface UserUsageLimitJpaRepository : JpaRepository<UserUsageLimitJpaEntity, String> {
    fun findByUserIdAndAction(
        userId: String,
        action: ActionType,
    ): UserUsageLimitJpaEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserUsageLimitJpaEntity u WHERE u.userId = :userId AND u.action = :action")
    fun findByUserIdAndActionWithLock(
        userId: String,
        action: ActionType,
    ): UserUsageLimitJpaEntity?

    fun findAllByUserId(userId: String): List<UserUsageLimitJpaEntity>
}
