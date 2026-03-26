package com.sokdak.limit.application.usecases

import com.sokdak.limit.application.commands.ConsumeUsageLimitCommand
import com.sokdak.limit.application.exceptions.LimitExceededException
import com.sokdak.limit.domain.entities.UserUsageLimit
import com.sokdak.limit.domain.enums.ActionType
import com.sokdak.limit.domain.repositories.UserUsageLimitRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * ConsumeUsageLimitUseCase 동시성 통합 테스트
 *
 * [핵심 설계]
 * - @Transactional(NOT_SUPPORTED): 테스트 자체에 트랜잭션을 걸면 UseCase의 각 트랜잭션이
 *   독립적으로 커밋되지 않아 동시성 충돌이 재현되지 않음. 반드시 NOT_SUPPORTED 필요.
 * - @MockitoBean RedisConnectionFactory: limit 모듈은 Redis를 사용하지 않으므로
 *   Mock으로 대체해 Redis 실행 환경과 무관하게 테스트 가능.
 */
@Tag("concurrency")
@SpringBootTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("ConsumeUsageLimitUseCase 동시성 테스트")
class ConsumeUsageLimitConcurrencyTest @Autowired constructor(
    private val consumeUsageLimitUseCase: ConsumeUsageLimitUseCase,
    private val limitRepository: UserUsageLimitRepository,
) {
    // limit 모듈은 Redis 미사용 → Mock으로 대체해 포트/연결 설정에 무관하게 실행
    @MockitoBean
    private lateinit var redisConnectionFactory: RedisConnectionFactory

    // 테스트 간 데이터 간섭 방지: 매 테스트마다 고유한 userId 생성
    private lateinit var testUserId: String

    @BeforeEach
    fun setUp() {
        testUserId = "user-${UUID.randomUUID()}"
    }

    private fun createLimit(
        userId: String,
        dailyLimit: Int,
        dailyUsed: Int = 0,
    ): UserUsageLimit =
        limitRepository.save(
            UserUsageLimit(
                id = UUID.randomUUID().toString(),
                userId = userId,
                action = ActionType.AI_CHAT,
                dailyLimit = dailyLimit,
                dailyUsed = dailyUsed,
                lastResetDate = LocalDate.now(),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )

    /**
     * userIds 개수만큼 스레드를 띄워 CountDownLatch로 동시에 출발시킨 뒤
     * 전체 소요 시간(ms)을 반환한다.
     */
    private fun runConcurrently(
        userIds: List<String>,
        onSuccess: () -> Unit = {},
        onLimitExceeded: () -> Unit = {},
    ): Long {
        val latch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(userIds.size)
        return measureTimeMillis {
            userIds.forEach { uid ->
                executor.submit {
                    try {
                        latch.await()
                        consumeUsageLimitUseCase.execute(
                            ConsumeUsageLimitCommand(uid, ActionType.AI_CHAT, 1),
                        )
                        onSuccess()
                    } catch (e: LimitExceededException) {
                        onLimitExceeded()
                    }
                }
            }
            latch.countDown()
            executor.shutdown()
            executor.awaitTermination(30, TimeUnit.SECONDS)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 정합성 검증
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("정합성: Pessimistic Lock이 Lost Update를 방지한다")
    inner class CorrectnessTests {

        @Test
        @DisplayName("동시 요청 10개 / 한도 5 → 정확히 5개만 성공하고 dailyUsed가 한도를 초과하지 않는다")
        fun `동시 요청이 한도를 초과하지 않는다`() {
            val dailyLimit = 5
            val totalRequests = 10
            createLimit(testUserId, dailyLimit)

            val successCount = AtomicInteger(0)
            val failCount = AtomicInteger(0)

            runConcurrently(
                userIds = List(totalRequests) { testUserId },
                onSuccess = { successCount.incrementAndGet() },
                onLimitExceeded = { failCount.incrementAndGet() },
            )

            val result = limitRepository.findByUserIdAndAction(testUserId, ActionType.AI_CHAT)!!
            println("[정합성] 한도=${dailyLimit}, 요청=${totalRequests}, 성공=${successCount.get()}, 실패=${failCount.get()}, dailyUsed=${result.dailyUsed}")

            assertThat(successCount.get())
                .describedAs("한도만큼만 성공해야 한다")
                .isEqualTo(dailyLimit)
            assertThat(failCount.get())
                .describedAs("초과 요청은 모두 LimitExceededException으로 실패해야 한다")
                .isEqualTo(totalRequests - dailyLimit)
            assertThat(result.dailyUsed)
                .describedAs("dailyUsed는 한도를 초과하면 안 된다")
                .isEqualTo(dailyLimit)
        }

        @Test
        @DisplayName("잔여 한도 1개 / 동시 요청 8개 → 정확히 1개만 성공한다")
        fun `잔여 한도 1개에 동시 요청이 몰려도 1건만 성공한다`() {
            // dailyLimit=5, dailyUsed=4 → 잔여 1개
            val dailyLimit = 5
            createLimit(testUserId, dailyLimit, dailyUsed = 4)
            val totalRequests = 8
            val successCount = AtomicInteger(0)

            runConcurrently(
                userIds = List(totalRequests) { testUserId },
                onSuccess = { successCount.incrementAndGet() },
            )

            val result = limitRepository.findByUserIdAndAction(testUserId, ActionType.AI_CHAT)!!
            println("[정합성] 잔여=1, 요청=${totalRequests}, 성공=${successCount.get()}, dailyUsed=${result.dailyUsed}")

            assertThat(successCount.get())
                .describedAs("잔여 한도 1개에 대해 정확히 1건만 성공해야 한다")
                .isEqualTo(1)
            assertThat(result.dailyUsed)
                .describedAs("dailyUsed는 dailyLimit과 같아야 한다")
                .isEqualTo(dailyLimit)
        }

        @Test
        @DisplayName("한도 이미 소진 / 동시 요청 5개 → 전부 실패하고 dailyUsed는 변하지 않는다")
        fun `한도 소진 후 동시 요청은 모두 실패한다`() {
            val dailyLimit = 3
            createLimit(testUserId, dailyLimit, dailyUsed = dailyLimit)
            val totalRequests = 5
            val failCount = AtomicInteger(0)

            runConcurrently(
                userIds = List(totalRequests) { testUserId },
                onLimitExceeded = { failCount.incrementAndGet() },
            )

            val result = limitRepository.findByUserIdAndAction(testUserId, ActionType.AI_CHAT)!!
            println("[정합성] 한도소진, 요청=${totalRequests}, 실패=${failCount.get()}, dailyUsed=${result.dailyUsed}")

            assertThat(failCount.get())
                .describedAs("모든 요청이 LimitExceededException으로 실패해야 한다")
                .isEqualTo(totalRequests)
            assertThat(result.dailyUsed)
                .describedAs("이미 소진된 dailyUsed는 변하지 않아야 한다")
                .isEqualTo(dailyLimit)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 성능 측정
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("성능: Pessimistic Lock 지연과 락 범위 분석")
    inner class PerformanceTests {

        /**
         * 같은 유저에 대한 동시 요청은 Pessimistic Lock으로 인해 직렬화된다.
         * 따라서 동시 처리 시간 ≈ 순차 처리 시간이어야 한다.
         * 동시 처리가 순차보다 극단적으로 빠르다면(ratio < 0.3) 락이 동작하지 않는 것.
         */
        @Test
        @DisplayName("순차 vs 동시 처리 비교 - 락 직렬화로 동시 처리는 순차보다 극단적으로 빠를 수 없다")
        fun `동시 처리 시간을 순차 처리와 비교한다`() {
            val requestCount = 10
            val seqUserId = "seq-${UUID.randomUUID()}"
            val conUserId = "con-${UUID.randomUUID()}"
            createLimit(seqUserId, requestCount * 2)
            createLimit(conUserId, requestCount * 2)

            // 순차 처리 (기준선)
            val sequentialMs = measureTimeMillis {
                repeat(requestCount) {
                    consumeUsageLimitUseCase.execute(
                        ConsumeUsageLimitCommand(seqUserId, ActionType.AI_CHAT, 1),
                    )
                }
            }

            // 동시 처리 (같은 유저 → 락 직렬화 발생)
            val concurrentMs = runConcurrently(
                userIds = List(requestCount) { conUserId },
            )

            val ratio = if (sequentialMs > 0) concurrentMs.toDouble() / sequentialMs else 0.0

            println(
                """
                [성능] 락 직렬화 측정 (동일 유저 ${requestCount}건)
                  순차 처리  : ${sequentialMs}ms
                  동시 처리  : ${concurrentMs}ms
                  ratio      : ${"%.2f".format(ratio)}x  (1.0 근접 = 락 직렬화 정상, < 0.3 = 락 미동작 의심)
                """.trimIndent(),
            )

            assertThat(limitRepository.findByUserIdAndAction(seqUserId, ActionType.AI_CHAT)!!.dailyUsed).isEqualTo(
                requestCount
            )
            assertThat(limitRepository.findByUserIdAndAction(conUserId, ActionType.AI_CHAT)!!.dailyUsed).isEqualTo(
                requestCount
            )

            assertThat(ratio)
                .describedAs("락 직렬화로 동시 처리는 순차 대비 30% 이상의 시간이 걸려야 한다")
                .isGreaterThan(0.3)
        }

        /**
         * Pessimistic Lock은 (user_id, action) 단일 ROW에만 걸린다.
         * 따라서 서로 다른 유저의 동시 요청은 서로를 블로킹하지 않아야 한다.
         * 다중 유저 동시 처리가 단일 유저 동시 처리보다 훨씬 빠른 것이 기대 결과다.
         */
        @Test
        @DisplayName("row-level lock 범위 확인 - 다른 유저 요청은 서로 블로킹하지 않는다")
        fun `서로 다른 유저의 동시 요청은 블로킹 없이 처리된다`() {
            val userCount = 8
            val singleUserId = "single-${UUID.randomUUID()}"
            val multiUserIds = List(userCount) { "multi-${UUID.randomUUID()}" }

            createLimit(singleUserId, userCount * 2)
            multiUserIds.forEach { createLimit(it, 10) }

            // 단일 유저 동시 요청 → 같은 row에 락 경합 발생
            val singleUserMs = runConcurrently(
                userIds = List(userCount) { singleUserId },
            )

            // 다중 유저 동시 요청 → 각자 다른 row → 락 경합 없음
            val multiSuccessCount = AtomicInteger(0)
            val multiUserMs = runConcurrently(
                userIds = multiUserIds,
                onSuccess = { multiSuccessCount.incrementAndGet() },
            )

            println(
                """
                [성능] row-level lock 범위 분석 (${userCount}건)
                  단일 유저 동시 처리 : ${singleUserMs}ms  (락 직렬화)
                  다중 유저 동시 처리 : ${multiUserMs}ms   (락 경합 없음)
                  기대: 다중 유저 << 단일 유저
                """.trimIndent(),
            )

            assertThat(multiSuccessCount.get())
                .describedAs("다중 유저 요청은 모두 성공해야 한다")
                .isEqualTo(userCount)
            multiUserIds.forEach { uid ->
                assertThat(limitRepository.findByUserIdAndAction(uid, ActionType.AI_CHAT)!!.dailyUsed)
                    .describedAs("각 유저의 dailyUsed는 정확히 1이어야 한다")
                    .isEqualTo(1)
            }
            // 다중 유저가 단일 유저보다 5배 이상 느리면 table-level lock 의심
            assertThat(multiUserMs.toDouble())
                .describedAs("다중 유저 동시 처리는 단일 유저 대비 5배 이상 느리면 안 된다 (table-level lock 의심)")
                .isLessThan(singleUserMs * 5.0)
        }

        /**
         * 동일 유저 20건 동시 처리 기준으로 TPS와 건당 평균 레이턴시를 측정한다.
         * H2 in-memory 기준 수치이며, PostgreSQL 환경에서는 lock wait로 인해 낮아진다.
         */
        @Test
        @DisplayName("TPS 측정 - 동시 20건 처리량과 건당 평균 레이턴시를 출력한다")
        fun `동시 처리 TPS와 평균 레이턴시를 측정한다`() {
            val totalRequests = 20
            createLimit(testUserId, totalRequests * 2)
            val successCount = AtomicInteger(0)

            val totalMs = runConcurrently(
                userIds = List(totalRequests) { testUserId },
                onSuccess = { successCount.incrementAndGet() },
            )

            val tps = if (totalMs > 0) successCount.get() * 1000.0 / totalMs else 0.0
            val avgMs = if (successCount.get() > 0) totalMs.toDouble() / successCount.get() else 0.0

            println(
                """
                [성능] TPS 측정 (Pessimistic Lock, H2 in-memory 기준)
                  총 요청      : ${totalRequests}건
                  성공         : ${successCount.get()}건
                  총 소요 시간 : ${totalMs}ms
                  TPS          : ${"%.1f".format(tps)} req/sec
                  건당 평균    : ${"%.1f".format(avgMs)}ms
                  (PostgreSQL 환경에서는 lock wait 추가로 수치가 낮아질 수 있음)
                """.trimIndent(),
            )

            assertThat(successCount.get())
                .describedAs("충분한 한도가 있으므로 모든 요청이 성공해야 한다")
                .isEqualTo(totalRequests)
            assertThat(limitRepository.findByUserIdAndAction(testUserId, ActionType.AI_CHAT)!!.dailyUsed)
                .describedAs("모든 요청이 정확히 반영되어야 한다")
                .isEqualTo(totalRequests)
        }
    }
}