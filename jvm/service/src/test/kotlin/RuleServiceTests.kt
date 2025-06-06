import kotlinx.datetime.toInstant
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import pt.isel.Failure
import pt.isel.Rule
import pt.isel.RuleError
import pt.isel.RuleEvent
import pt.isel.RuleLocation
import pt.isel.RuleService
import pt.isel.Sha256TokenEncoder
import pt.isel.Success
import pt.isel.User
import pt.isel.UserService
import pt.isel.UsersDomain
import pt.isel.UsersDomainConfig
import pt.isel.transaction.TransactionManager
import pt.isel.transaction.TransactionManagerInMem
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class RuleServiceTests {
    companion object {
        @JvmStatic
        fun transactionManagers(): Stream<TransactionManager> =
            Stream.of(
                TransactionManagerInMem().also { cleanup(it) },
                // add JDBI TODO
            )

        private fun cleanup(trxManager: TransactionManager) {
            trxManager.run {
                userRepo.clear()
                sessionRepo.clear()
                ruleRepo.clear()
                exclusionRepo.clear()
                eventRepo.clear()
                locationRepo.clear()
            }
        }

        private val usersDomain =
            UsersDomain(
                BCryptPasswordEncoder(),
                Sha256TokenEncoder(),
                UsersDomainConfig(
                    tokenSizeInBytes = 256 / 8,
                    tokenTtl = 30.days,
                    tokenRollingTtl = 30.minutes,
                    maxTokensPerUser = 3,
                ),
            )

        private fun createUserService(
            trxManager: TransactionManager,
            testClock: TestClock,
        ) = UserService(
            trxManager,
            usersDomain,
            testClock,
        )
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule event should succeed`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut =
            ruleService.createRuleEvent(
                user.value.id,
                1L,
                1L,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(sut is Success)
        assertIs<Rule>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule event should fail with negative eventId`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut =
            ruleService.createRuleEvent(
                user.value.id,
                -1L,
                1L,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule event should fail with blank title`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut =
            ruleService.createRuleEvent(
                user.value.id,
                1L,
                1L,
                "",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.TitleCannotBeBlank>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule event should fail with negative calendarId`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Alice", "alice@example.com", "Safe_2027")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut =
            ruleService.createRuleEvent(
                user.value.id,
                1L,
                -1L,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule event should fail when start time is after end time`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut =
            ruleService.createRuleEvent(
                user.value.id,
                1L,
                1L,
                "Title",
                "2025-06-23T11:00:00Z".toInstant(),
                "2025-06-23T10:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.StartTimeMustBeBeforeEndTime>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule event should fail when start time is the same as end time`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut =
            ruleService.createRuleEvent(
                user.value.id,
                1L,
                1L,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T10:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.StartTimeMustBeBeforeEndTime>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule event should fail when user is not found`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val sut =
            ruleService.createRuleEvent(
                9999,
                1L,
                1L,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.UserNotFound>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule event should fail when rule already exists for given time`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val startTime = "2025-06-23T10:00:00Z".toInstant()
        val endTime = "2025-06-23T11:00:00Z".toInstant()
        val firstRule =
            ruleService.createRuleEvent(
                user.value.id,
                1L,
                1L,
                "First Rule",
                startTime,
                endTime,
            )
        assertTrue(firstRule is Success)
        val sut =
            ruleService.createRuleEvent(
                user.value.id,
                2L,
                1L,
                "Second Rule",
                startTime,
                endTime,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.RuleAlreadyExistsForGivenTime>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule location should succeed`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(sut is Success)
        assertIs<Rule>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule location should return error if title is blank`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut =
            ruleService.createLocationRule(
                user.value.id,
                "",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.TitleCannotBeBlank>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule location should return error if startTime is after endTime`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T11:00:00Z".toInstant(),
                "2025-06-23T10:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.StartTimeMustBeBeforeEndTime>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule location should return error if starTime and endTime are the same`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T11:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.StartTimeMustBeBeforeEndTime>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule location should return error if latitude is invalid`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                -100.0,
                -9.0,
                100.0,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.InvalidLatitude>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule location should return error if longitude is invalid`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -200.0,
                100.0,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.InvalidLongitude>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule location should return error if radius is invalid`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                -100.0,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.InvalidRadius>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule location should return error if user is not found`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val sut =
            ruleService.createLocationRule(
                9999,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.UserNotFound>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `create rule location returns error if rule already exists for given time`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.RuleAlreadyExistsForGivenTime>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `getEventRule should succeed`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<RuleEvent>(rule.value)
        val sut = ruleService.getEventRuleById(user.value.id, rule.value.id)
        assertTrue(sut is Success)
        assertIs<RuleEvent>(sut.value)
        assertEquals(rule.value.id, sut.value.id)
        assertEquals(rule.value.startTime, sut.value.startTime)
        assertEquals(rule.value.endTime, sut.value.endTime)
        assertEquals(rule.value.event, sut.value.event)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `getEventRule should return error if user id is negative`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<RuleEvent>(rule.value)
        val sut = ruleService.getEventRuleById(-1, rule.value.id)
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `getEventRule should return error if rule id is negative`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<RuleEvent>(rule.value)
        val sut = ruleService.getEventRuleById(user.value.id, -1)
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `getEventRule should return error if user does not exists`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<RuleEvent>(rule.value)
        val sut = ruleService.getEventRuleById(9999, rule.value.id)
        assertTrue(sut is Failure)
        assertIs<RuleError.UserNotFound>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `getEventRule should return error if rule does not exists`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut = ruleService.getEventRuleById(user.value.id, 999999)
        assertTrue(sut is Failure)
        assertIs<RuleError.RuleNotFound>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `getEventRule should return error if rule is not from user`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val user2 = userService.register("Alice", "alice@example.com", "Tasa_2025")
        assertTrue(user2 is Success)
        assertIs<User>(user2.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<RuleEvent>(rule.value)
        val sut = ruleService.getEventRuleById(user2.value.id, rule.value.id)
        assertTrue(sut is Failure)
        assertIs<RuleError.NotAllowed>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `getLocationRule should succeed`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<RuleLocation>(rule.value)
        val sut = ruleService.getLocationRuleById(user.value.id, rule.value.id)
        assertTrue(sut is Success)
        assertIs<RuleLocation>(sut.value)
        assertEquals(rule.value.id, sut.value.id)
        assertEquals(rule.value.startTime, sut.value.startTime)
        assertEquals(rule.value.endTime, sut.value.endTime)
        assertEquals(rule.value.location, sut.value.location)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `getLocationRuleById should return error if user id is negative`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<RuleLocation>(rule.value)
        val sut = ruleService.getLocationRuleById(-1, rule.value.id)
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `getLocationRuleById should return error if rule id is negative`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<RuleLocation>(rule.value)
        val sut = ruleService.getLocationRuleById(user.value.id, -1)
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `getLocationRuleById should return error if user does not exists`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<RuleLocation>(rule.value)
        val sut = ruleService.getLocationRuleById(99999, rule.value.id)
        assertTrue(sut is Failure)
        assertIs<RuleError.UserNotFound>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `getLocationRuleById should return error if rule does not exists`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut = ruleService.getLocationRuleById(user.value.id, 999999)
        assertTrue(sut is Failure)
        assertIs<RuleError.RuleNotFound>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `getLocationRuleById should return error if rule does not belong to the user`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val user2 = userService.register("Alice", "alice@example.com", "Tasa_2025")
        assertTrue(user2 is Success)
        assertIs<User>(user2.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<RuleLocation>(rule.value)
        val sut = ruleService.getLocationRuleById(user2.value.id, rule.value.id)
        assertTrue(sut is Failure)
        assertIs<RuleError.NotAllowed>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `getRulesByUser should succeed`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule0 =
            ruleService.createLocationRule(
                user.value.id,
                "Title0",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule0 is Success)
        assertIs<RuleLocation>(rule0.value)
        val rule1 =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title1",
                "2025-06-23T11:01:00Z".toInstant(),
                "2025-06-23T12:00:00Z".toInstant(),
            )
        assertTrue(rule1 is Success)
        assertIs<RuleEvent>(rule1.value)
        val sut = ruleService.getRulesByUser(user.value.id)
        assertTrue(sut is Success)
        assertIs<List<Rule>>(sut.value)
        assertTrue(sut.value.contains(rule0.value))
        assertTrue(sut.value.contains(rule1.value))
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `getRulesByUser should return error if user id is negative`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val sut = ruleService.getRulesByUser(-1)
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `getRulesByUser should return error if user does not exists`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val sut = ruleService.getRulesByUser(99999999)
        assertTrue(sut is Failure)
        assertIs<RuleError.UserNotFound>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update rule event should succeed`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.updateEventRule(
                user.value.id,
                rule.value.id,
                "2025-06-23T11:00:00Z".toInstant(),
                "2025-06-23T12:00:00Z".toInstant(),
            )
        assertTrue(sut is Success)
        assertIs<RuleEvent>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update event rule should return error userId is negative`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.updateEventRule(
                -1,
                rule.value.id,
                "2025-06-23T11:00:00Z".toInstant(),
                "2025-06-23T12:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update event rule should return error if ruleId is negative`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut =
            ruleService.updateEventRule(
                user.value.id,
                -1,
                "2025-06-23T11:00:00Z".toInstant(),
                "2025-06-23T12:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update event rule should return error if startTime is after endTime`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.updateEventRule(
                user.value.id,
                rule.value.id,
                "2025-06-23T11:00:00Z".toInstant(),
                "2025-06-23T10:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.StartTimeMustBeBeforeEndTime>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update event rule should return error if user id is negative`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.updateEventRule(
                -1,
                rule.value.id,
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update event rule should return error if rule id is negative`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.updateEventRule(
                user.value.id,
                -1,
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update event rule should return error if user does not exists`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.updateEventRule(
                9999,
                rule.value.id,
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.UserNotFound>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update rule event should return error if rule is not found`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val sut =
            ruleService.updateEventRule(
                user.value.id,
                999999,
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.RuleNotFound>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update rule event should return error if already exists rule for the given time`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val rule2 =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T11:30:00Z".toInstant(),
                "2025-06-23T12:00:00Z".toInstant(),
            )
        assertTrue(rule2 is Success)
        assertIs<Rule>(rule2.value)
        val sut =
            ruleService.updateEventRule(
                user.value.id,
                rule.value.id,
                "2025-06-23T11:45:00Z".toInstant(),
                "2025-06-23T12:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.RuleAlreadyExistsForGivenTime>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update event rule should return error if rule is not from user`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val user2 = userService.register("Alice", "alice@example.com", "Tasa_2025")
        assertTrue(user2 is Success)
        assertIs<User>(user2.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.updateEventRule(
                user2.value.id,
                rule.value.id,
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.NotAllowed>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update rule location should succeed`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.updateLocationRule(
                user.value.id,
                rule.value.id,
                "2025-06-23T10:30:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(sut is Success)
        assertIs<RuleLocation>(sut.value)
        assertEquals(rule.value.id, sut.value.id)
        assertEquals(
            "2025-06-23T10:30:00Z".toInstant(),
            sut.value.startTime,
        )
        assertEquals(
            "2025-06-23T11:00:00Z".toInstant(),
            sut.value.endTime,
        )
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update rule location should return error if startTime is after endTime`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.updateLocationRule(
                user.value.id,
                rule.value.id,
                "2025-06-23T11:00:00Z".toInstant(),
                "2025-06-23T10:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.StartTimeMustBeBeforeEndTime>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update rule location should return error if userId is negative`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.updateLocationRule(
                -1,
                rule.value.id,
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update rule location should return error if rule id is negative`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.updateLocationRule(
                user.value.id,
                -1,
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update rule location should return error if user does not exists`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.updateLocationRule(
                99999,
                rule.value.id,
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.UserNotFound>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update rule location should return error if rule does not exists`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.updateLocationRule(
                user.value.id,
                999999,
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.RuleNotFound>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `update rule location should return error if rule already exists for given time`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val rule2 =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T11:01:00Z".toInstant(),
                "2025-06-23T12:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule2 is Success)
        assertIs<Rule>(rule2.value)
        val sut =
            ruleService.updateLocationRule(
                user.value.id,
                rule.value.id,
                "2025-06-23T11:30:00Z".toInstant(),
                "2025-06-23T12:00:00Z".toInstant(),
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.RuleAlreadyExistsForGivenTime>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `delete rule event should succeed`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val user2 = userService.register("Alice", "alice@example.com", "Tasa_2025")
        assertTrue(user2 is Success)
        assertIs<User>(user2.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.deleteRuleEvent(
                user.value.id,
                rule.value.id,
            )
        assertTrue(sut is Success)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `delete rule event should return error if user id is negative`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.deleteRuleEvent(
                -1,
                rule.value.id,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `delete rule event should return error if rule id is negative`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.deleteRuleEvent(
                user.value.id,
                -1,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `delete rule event should return error if user does not exists`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.deleteRuleEvent(
                999999,
                rule.value.id,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.UserNotFound>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `delete rule event should return error if rule does not exists`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.deleteRuleEvent(
                user.value.id,
                999999,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.RuleNotFound>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `delete rule event should return error if rule is not from user`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val user2 = userService.register("Alice", "alice@example.com", "Tasa_2025")
        assertTrue(user2 is Success)
        assertIs<User>(user2.value)
        val rule =
            ruleService.createRuleEvent(
                user.value.id,
                1,
                1,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.deleteRuleEvent(
                user2.value.id,
                rule.value.id,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.NotAllowed>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `delete rule location should succeed`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.deleteLocationRule(
                user.value.id,
                rule.value.id,
            )
        assertTrue(sut is Success)
        assertIs<Unit>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `delete rule location should return error if userId is negative`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.deleteLocationRule(
                -1,
                rule.value.id,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `delete rule location should return error if rule id is negative`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.deleteLocationRule(
                user.value.id,
                -1,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.NegativeIdentifier>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `delete rule location should return error if user does not exists`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.deleteLocationRule(
                999999,
                rule.value.id,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.UserNotFound>(sut.value)
    }

    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `delete rule location should return error if rule does not belong to user`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val userService = createUserService(trxManager, TestClock())
        val user = userService.register("Bob", "bob@example.com", "Tasa_2025")
        assertTrue(user is Success)
        assertIs<User>(user.value)
        val user2 = userService.register("Alice", "alice@example.com", "Tasa_2025")
        assertTrue(user2 is Success)
        assertIs<User>(user2.value)
        val rule =
            ruleService.createLocationRule(
                user.value.id,
                "Title",
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "ISEL",
                38.0,
                -9.0,
                100.0,
            )
        assertTrue(rule is Success)
        assertIs<Rule>(rule.value)
        val sut =
            ruleService.deleteLocationRule(
                user2.value.id,
                rule.value.id,
            )
        assertTrue(sut is Failure)
        assertIs<RuleError.NotAllowed>(sut.value)
    }

    // TODO move
    @ParameterizedTest
    @MethodSource("transactionManagers")
    fun `checkCollisionTime with multiple times`(trxManager: TransactionManager) {
        val ruleService = RuleService(trxManager)
        val sut0 =
            ruleService.checkCollisionTime(
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "2025-06-23T10:30:00Z".toInstant(),
                "2025-06-23T11:30:00Z".toInstant(),
            )
        assertTrue(sut0)
        val sut1 =
            ruleService.checkCollisionTime(
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "2025-06-23T12:00:00Z".toInstant(),
            )
        assertTrue(sut1)
        val sut2 =
            ruleService.checkCollisionTime(
                "2025-06-23T10:30:00Z".toInstant(),
                "2025-06-23T11:30:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "2025-06-23T12:00:00Z".toInstant(),
            )
        assertTrue(sut2)
        val sut3 =
            ruleService.checkCollisionTime(
                "2025-06-23T10:00:00Z".toInstant(),
                "2025-06-23T11:00:00Z".toInstant(),
                "2025-06-23T11:01:00Z".toInstant(),
                "2025-06-23T12:30:00Z".toInstant(),
            )
        assertFalse(sut3)
    }
}
