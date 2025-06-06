import kotlinx.datetime.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pt.isel.Event
import pt.isel.Location
import pt.isel.RuleEvent
import pt.isel.RuleLocation
import pt.isel.User
import pt.isel.rule.MockRuleRepository
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockRuleRepositoryTests {
    private val repo = MockRuleRepository()
    private val user =
        User(
            id = 1,
            username = "Bob",
            email = "bob@example.pt",
        )
    private val event =
        Event(
            id = 1,
            calendarId = 1,
            title = "Test Event",
        )
    private val location =
        Location(
            id = 1,
            name = "Test Location",
            latitude = 38.756387616516704,
            longitude = -9.11648919436834,
            radius = 50.0,
        )

    @BeforeEach
    fun setUp() {
        repo.clear()
    }

    @Test
    fun `createEventRule should create an event rule and return it`() {
        val sut =
            repo.createEventRule(
                event = event,
                user = user,
                startTime = Instant.parse("2025-06-01T00:00:00Z"),
                endTime = Instant.parse("2025-06-01T01:00:00Z"),
            )
        val expectedRule =
            RuleEvent(
                id = 0,
                startTime = Instant.parse("2025-06-01T00:00:00Z"),
                endTime = Instant.parse("2025-06-01T01:00:00Z"),
                event = event,
                creator = user,
            )
        assertEquals(expectedRule.startTime, sut.startTime)
        assertEquals(expectedRule.endTime, sut.endTime)
        assertEquals(expectedRule.id, sut.id)
        assertEquals(expectedRule.event, sut.event)
    }

    @Test
    fun `createLocationRule should create a location rule and return it`() {
        val sut =
            repo.createLocationRule(
                location = location,
                user = user,
                startTime = Instant.parse("2025-06-01T00:00:00Z"),
                endTime = Instant.parse("2025-06-01T01:00:00Z"),
            )
        val expectedRule =
            RuleLocation(
                id = 0,
                startTime = Instant.parse("2025-06-01T00:00:00Z"),
                endTime = Instant.parse("2025-06-01T01:00:00Z"),
                location = location,
                creator = user,
            )
        assertEquals(expectedRule.startTime, sut.startTime)
        assertEquals(expectedRule.endTime, sut.endTime)
        assertEquals(expectedRule.id, sut.id)
    }

    @Test
    fun `findAll should return all rules`() {
        val rule1 =
            repo.createEventRule(
                event = event,
                user = user,
                startTime = Instant.parse("2025-06-01T00:00:00Z"),
                endTime = Instant.parse("2025-06-01T01:00:00Z"),
            )
        val rule2 =
            repo.createLocationRule(
                location = location,
                user = user,
                startTime = Instant.parse("2025-06-01T02:00:00Z"),
                endTime = Instant.parse("2025-06-01T03:00:00Z"),
            )
        val sut = repo.findAll()
        assertEquals(2, sut.size)
        assertTrue(sut.contains(rule1))
        assertTrue(sut.contains(rule2))
    }

    @Test
    fun `findRuleEventById should return the rule with the given id`() {
        val rule =
            repo.createEventRule(
                event = event,
                user = user,
                startTime = Instant.parse("2025-06-01T00:00:00Z"),
                endTime = Instant.parse("2025-06-01T01:00:00Z"),
            )
        val sut = repo.findRuleEventById(rule.id)
        assertEquals(rule, sut)
    }

    @Test
    fun `findRuleLocationById should return the rule with the given id`() {
        val rule =
            repo.createLocationRule(
                user = user,
                startTime = Instant.parse("2025-06-01T00:00:00Z"),
                endTime = Instant.parse("2025-06-01T01:00:00Z"),
                location = location,
            )
        val sut = repo.findRuleLocationById(rule.id)
        assertEquals(rule, sut)
    }

    @Test
    fun `findRuleLocationById should return null if the rule with the given id does not exist`() {
        val sut = repo.findRuleLocationById(999)
        assertEquals(null, sut)
    }

    @Test
    fun `findRuleEventById should return null if the rule with the given id does not exist`() {
        val sut = repo.findRuleEventById(999)
        assertEquals(null, sut)
    }

    @Test
    fun `findByUserId should return all rules for the given user`() {
        val rule1 =
            repo.createEventRule(
                event = event,
                user = user,
                startTime = Instant.parse("2025-06-01T00:00:00Z"),
                endTime = Instant.parse("2025-06-01T01:00:00Z"),
            )
        val rule2 =
            repo.createLocationRule(
                location = location,
                user = user,
                startTime = Instant.parse("2025-06-01T02:00:00Z"),
                endTime = Instant.parse("2025-06-01T03:00:00Z"),
            )
        val sut = repo.findByUserId(user)
        assertEquals(2, sut.size)
        assertTrue(sut.contains(rule1))
        assertTrue(sut.contains(rule2))
    }

    @Test
    fun `update should update the rule event and return it`() {
        val rule =
            repo.createEventRule(
                event = event,
                user = user,
                startTime = Instant.parse("2025-06-01T00:00:00Z"),
                endTime = Instant.parse("2025-06-01T01:00:00Z"),
            )
        val sut =
            repo.updateRuleEvent(
                rule = rule,
                startTime = Instant.parse("2025-06-01T01:00:00Z"),
                endTime = Instant.parse("2025-06-01T02:00:00Z"),
            )
        assertEquals(rule.id, sut.id)
        assertEquals(rule.event, sut.event)
        assertEquals(Instant.parse("2025-06-01T01:00:00Z"), sut.startTime)
        assertEquals(Instant.parse("2025-06-01T02:00:00Z"), sut.endTime)
    }

    @Test
    fun `update should update the rule location and return it`() {
        val rule =
            repo.createLocationRule(
                user = user,
                startTime = Instant.parse("2025-06-01T00:00:00Z"),
                endTime = Instant.parse("2025-06-01T01:00:00Z"),
                location = location,
            )
        val sut =
            repo.updateRuleLocation(
                rule = rule,
                startTime = Instant.parse("2025-06-01T01:00:00Z"),
                endTime = Instant.parse("2025-06-01T02:00:00Z"),
            )
        assertEquals(rule.id, sut.id)
        assertEquals(rule.location, sut.location)
        assertEquals(Instant.parse("2025-06-01T01:00:00Z"), sut.startTime)
        assertEquals(Instant.parse("2025-06-01T02:00:00Z"), sut.endTime)
    }

    @Test
    fun `delete should remove the rule event and return true`() {
        val rule =
            repo.createEventRule(
                event = event,
                user = user,
                startTime = Instant.parse("2025-06-01T00:00:00Z"),
                endTime = Instant.parse("2025-06-01T01:00:00Z"),
            )
        val sut = repo.deleteRuleEvent(rule)
        assertEquals(true, sut)
        assertEquals(null, repo.findRuleEventById(rule.id))
    }

    @Test
    fun `delete should remove the rule location and return true`() {
        val rule =
            repo.createLocationRule(
                user = user,
                startTime = Instant.parse("2025-06-01T00:00:00Z"),
                endTime = Instant.parse("2025-06-01T01:00:00Z"),
                location = location,
            )
        val sut = repo.deleteLocationEvent(rule)
        assertEquals(true, sut)
        assertEquals(null, repo.findRuleLocationById(rule.id))
    }

    @Test
    fun `clear should remove all rules`() {
        repo.createEventRule(
            event = event,
            user = user,
            startTime = Instant.parse("2025-06-01T00:00:00Z"),
            endTime = Instant.parse("2025-06-01T01:00:00Z"),
        )
        repo.createLocationRule(
            location = location,
            user = user,
            startTime = Instant.parse("2025-06-01T02:00:00Z"),
            endTime = Instant.parse("2025-06-01T03:00:00Z"),
        )
        repo.clear()
        assertEquals(0, repo.findAll().size)
    }

    @Test
    fun `findByUserId should return an empty list if the user has no rules`() {
        val sut = repo.findByUserId(user)
        assertEquals(0, sut.size)
    }
}
