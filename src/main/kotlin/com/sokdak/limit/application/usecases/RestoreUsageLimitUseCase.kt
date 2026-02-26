package com.sokdak.limit.application.usecases

import com.sokdak.limit.application.commands.RestoreUsageLimitCommand
import com.sokdak.limit.application.exceptions.LimitNotFoundException
import com.sokdak.limit.domain.entities.UserUsageLimit
import com.sokdak.limit.domain.repositories.UserUsageLimitRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RestoreUsageLimitUseCase(
    private val limitRepository: UserUsageLimitRepository,
) {
    @Transactional
    fun execute(command: RestoreUsageLimitCommand): UserUsageLimit {
        val limit =
            limitRepository.findByUserIdAndActionWithLock(command.userId, command.action)
                ?: throw LimitNotFoundException(command.userId, command.action.name)

        limit.restore(command.count)
        return limitRepository.save(limit)
    }
}
