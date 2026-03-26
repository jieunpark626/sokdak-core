package com.sokdak.limit.application.usecases

import com.sokdak.limit.application.commands.ConsumeUsageLimitCommand
import com.sokdak.limit.application.exceptions.LimitExceededException
import com.sokdak.limit.application.exceptions.LimitNotFoundException
import com.sokdak.limit.domain.entities.UserUsageLimit
import com.sokdak.limit.domain.repositories.UserUsageLimitRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class ConsumeUsageLimitUseCase(
    private val limitRepository: UserUsageLimitRepository,
) {
    @Transactional
    fun execute(command: ConsumeUsageLimitCommand): UserUsageLimit {
        val limit =
            limitRepository.findByUserIdAndActionWithLock(command.userId, command.action)
                ?: throw LimitNotFoundException(command.userId, command.action.name)

        val today = LocalDate.now()
        limit.resetIfNeeded(today)

        if (!limit.canConsume(command.count)) {
            throw LimitExceededException(command.userId, command.action.name)
        }

        limit.consume(command.count)
        return limitRepository.save(limit)
    }
}
