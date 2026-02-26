package com.sokdak.limit.application.commands

import com.sokdak.limit.domain.enums.ActionType

data class RestoreUsageLimitCommand(
    val userId: String,
    val action: ActionType,
    val count: Int = 1,
)
