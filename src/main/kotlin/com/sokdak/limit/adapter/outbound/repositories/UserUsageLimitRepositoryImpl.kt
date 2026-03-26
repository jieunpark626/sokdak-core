package com.sokdak.limit.adapter.outbound.repositories

import com.sokdak.limit.adapter.outbound.mappers.toDomain
import com.sokdak.limit.adapter.outbound.mappers.toJpaEntity
import com.sokdak.limit.domain.entities.UserUsageLimit
import com.sokdak.limit.domain.enums.ActionType
import com.sokdak.limit.domain.repositories.UserUsageLimitRepository
import org.springframework.stereotype.Repository

@Repository
class UserUsageLimitRepositoryImpl(
    private val jpaRepository: UserUsageLimitJpaRepository,
) : UserUsageLimitRepository {
    override fun save(limit: UserUsageLimit): UserUsageLimit {
        val entity = limit.toJpaEntity()
        val saved = jpaRepository.save(entity)
        return saved.toDomain()
    }

    override fun findByUserIdAndAction(
        userId: String,
        action: ActionType,
    ): UserUsageLimit? =
        jpaRepository
            .findByUserIdAndAction(userId, action)
            ?.toDomain()

    override fun findByUserIdAndActionWithLock(
        userId: String,
        action: ActionType,
    ): UserUsageLimit? =
        jpaRepository
            .findByUserIdAndActionWithLock(userId, action)
            ?.toDomain()

    override fun findAllByUserId(userId: String): List<UserUsageLimit> =
        jpaRepository
            .findAllByUserId(userId)
            .map { it.toDomain() }
}
