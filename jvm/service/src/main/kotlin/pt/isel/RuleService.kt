package pt.isel

import jakarta.inject.Named
import kotlinx.datetime.Instant
import pt.isel.transaction.TransactionManager

/**
 * Represents the possible errors that can occur while applying or validating rules.
 * This is a sealed class, with specific error types defined as data objects inherited from it.
 */
sealed class RuleError {
    data object NegativeIdentifier : RuleError()

    data object UserNotFound : RuleError()

    data object RuleAlreadyExistsForGivenTime : RuleError()

    data object RuleNotFound : RuleError()

    data object TitleCannotBeBlank : RuleError()

    data object StartTimeMustBeBeforeEndTime : RuleError()

    data object EndTimeMustBeBeforeEndTime : RuleError()

    data object InvalidCoordinate : RuleError()

    data object InvalidLatitude : RuleError()

    data object InvalidLongitude : RuleError()

    data object InvalidRadius : RuleError()

    data object NotAllowed : RuleError()
}

/**
 * Service responsible for managing and manipulating rules.
 * Rules can be associated with events or locations
 * and are specific to a user.
 * This class uses a transactional context to ensure consistent state changes.
 *
 * @constructor
 * @property trxManager Manages transactional contexts for database operations.
 */
@Named
class RuleService(
    private val trxManager: TransactionManager,
) {
    /**
     * Creates a new event rule for a specific user and associates it with the specified event
     * and time range.
     *
     * @param userId the ID of the user for whom the rule is being created
     * @param eventId the ID of the event associated with the rule
     * @param calendarId the ID of the calendar associated with the event
     * @param title the title of the event
     * @param startTime the start time of the event
     * @param endTime the end time of the event
     * @return an [Either] instance containing either the created [Rule] wrapped in [Either.Right]
     *         or an instance of [RuleError] wrapped in [Either.Left] if validation fails
     */
    fun createRuleEvent(
        userId: Int,
        eventId: Long,
        calendarId: Long,
        title: String,
        startTime: Instant,
        endTime: Instant,
    ): Either<RuleError, RuleEvent> =
        trxManager.run {
            if (userId < 0 || eventId < 0 || calendarId < 0) {
                return@run failure(RuleError.NegativeIdentifier)
            }
            if (title.isBlank()) return@run failure(RuleError.TitleCannotBeBlank)
            if (startTime > endTime || startTime == endTime) {
                return@run failure(RuleError.StartTimeMustBeBeforeEndTime)
            }
            val user = userRepo.findById(userId) ?: return@run failure(RuleError.UserNotFound)
            if (ruleRepo.findByUserId(user).any {
                    checkCollisionTime(
                        it.startTime,
                        it.endTime,
                        startTime,
                        endTime,
                    )
                }
            ) {
                return@run failure(RuleError.RuleAlreadyExistsForGivenTime)
            }
            val event =
                eventRepo.findById(eventId, calendarId, user)
                    ?: eventRepo.create(eventId, calendarId, title, user)
            val rule =
                ruleRepo.createEventRule(
                    event,
                    user,
                    startTime,
                    endTime,
                )
            success(rule)
        }

    /**
     * Creates a new location-based rule.
     *
     * @param userId the ID of the user for whom the rule is being created
     * @param title the title of the location rule
     * @param startTime the start time of the rule
     * @param endTime the end time of the rule
     * @param name the name of the location
     * @param latitude the latitude of the location
     * @param longitude the longitude of the location
     * @param radius the radius around the location
     * @return an [Either] instance containing either the created [RuleLocation] wrapped in [Either.Right]
     *         or an instance of [RuleError] wrapped in [Either.Left] if validation fails
     */
    fun createLocationRule(
        userId: Int,
        title: String,
        startTime: Instant,
        endTime: Instant,
        name: String,
        latitude: Double,
        longitude: Double,
        radius: Double,
    ): Either<RuleError, RuleLocation> =
        trxManager.run {
            if (title.isBlank()) return@run failure(RuleError.TitleCannotBeBlank)
            if (startTime > endTime || startTime == endTime) {
                return@run failure(RuleError.StartTimeMustBeBeforeEndTime)
            }
            if (latitude !in Location.MIN_LATITUDE..Location.MAX_LATITUDE) {
                return@run failure(RuleError.InvalidLatitude)
            }
            if (longitude !in Location.MIN_LONGITUDE..Location.MAX_LONGITUDE) {
                return@run failure(RuleError.InvalidLongitude)
            }
            if (radius <= 0.0) return@run failure(RuleError.InvalidRadius)
            if (userId < 0) return@run failure(RuleError.NegativeIdentifier)
            val user = userRepo.findById(userId) ?: return@run failure(RuleError.UserNotFound)
            if (ruleRepo.findByUserId(user).any {
                    checkCollisionTime(
                        it.startTime,
                        it.endTime,
                        startTime,
                        endTime,
                    )
                }
            ) {
                return@run failure(RuleError.RuleAlreadyExistsForGivenTime)
            }
            val location =
                locationRepo.findByUserId(user).find {
                    it.name == name &&
                        it.latitude == latitude && it.longitude == longitude && it.radius == radius
                } ?: locationRepo.create(
                    name,
                    latitude,
                    longitude,
                    radius,
                    user,
                )
            val rule =
                ruleRepo.createLocationRule(
                    location,
                    user,
                    startTime,
                    endTime,
                )
            success(rule)
        }

    /**
     * Retrieves a specific event rule by its ID.
     *
     * @param userId the ID of the user who owns the rule
     * @param ruleId the ID of the rule to be retrieved
     * @return an [Either] instance containing the [RuleEvent] wrapped in [Either.Right]
     */
    fun getEventRuleById(
        userId: Int,
        ruleId: Int,
    ): Either<RuleError, RuleEvent> =
        trxManager.run {
            if (userId < 0 || ruleId < 0) return@run failure(RuleError.NegativeIdentifier)
            val user = userRepo.findById(userId) ?: return@run failure(RuleError.UserNotFound)
            val rule = ruleRepo.findRuleEventById(ruleId) ?: return@run failure(RuleError.RuleNotFound)
            if (rule.creator != user) return@run failure(RuleError.NotAllowed)
            return@run success(rule)
        }

    /**
     * Retrieves a specific location rule by its ID.
     *
     * @param userId the ID of the user who owns the rule
     * @param ruleId the ID of the rule to be retrieved
     * @return an [Either] instance containing the [RuleLocation] wrapped in [Either.Right]
     */
    fun getLocationRuleById(
        userId: Int,
        ruleId: Int,
    ): Either<RuleError, RuleLocation> =
        trxManager.run {
            if (userId < 0 || ruleId < 0) return@run failure(RuleError.NegativeIdentifier)
            val user = userRepo.findById(userId) ?: return@run failure(RuleError.UserNotFound)
            val rule = ruleRepo.findRuleLocationById(ruleId) ?: return@run failure(RuleError.RuleNotFound)
            if (rule.creator != user) return@run failure(RuleError.NotAllowed)
            return@run success(rule)
        }

    /**
     * Retrieves the list of rules associated with a specific user.
     *
     * @param userId the ID of the user for whom the rules are being retrieved.
     *               If the ID is negative, a [RuleError.NegativeIdentifier] is returned.
     * @return an [Either] instance containing a list of [Rule] objects wrapped in [Either.Right]
     *         if successful, or a [RuleError] wrapped in [Either.Left] in case of failure.
     */
    fun getRulesByUser(userId: Int): Either<RuleError, List<Rule>> =
        trxManager.run {
            if (userId < 0) return@run failure(RuleError.NegativeIdentifier)
            val user = userRepo.findById(userId) ?: return@run failure(RuleError.UserNotFound)
            val rules = ruleRepo.findByUserId(user)
            return@run success(rules)
        }

    /**
     * Updates the specified event rule with new start and end times.
     *
     * @param userId the ID of the user who owns the rule
     * @param ruleId the ID of the rule to be updated
     * @param startTime the new start time for the rule
     * @param endTime the new end time for the rule
     * @return an [Either] instance containing either the updated [RuleEvent] wrapped in [Either.Right]
     *         or an instance of [RuleError] wrapped in [Either.Left] if any validation fails
     */
    fun updateEventRule(
        userId: Int,
        ruleId: Int,
        startTime: Instant,
        endTime: Instant,
    ): Either<RuleError, RuleEvent> =
        trxManager.run {
            if (startTime >= endTime) return@run failure(RuleError.StartTimeMustBeBeforeEndTime)
            if (userId < 0 || ruleId < 0) return@run failure(RuleError.NegativeIdentifier)
            val user = userRepo.findById(userId) ?: return@run failure(RuleError.UserNotFound)
            val rule = ruleRepo.findRuleEventById(ruleId) ?: return@run failure(RuleError.RuleNotFound)
            val userRules = ruleRepo.findByUserId(user)
            if (userRules.any {
                    it.id != ruleId &&
                        checkCollisionTime(
                            it.startTime,
                            it.endTime,
                            startTime,
                            endTime,
                        )
                }
            ) {
                return@run failure(RuleError.RuleAlreadyExistsForGivenTime)
            }
            if (rule.creator != user) return@run failure(RuleError.NotAllowed)
            val updatedRule = ruleRepo.updateRuleEvent(rule, startTime, endTime)
            return@run success(updatedRule)
        }

    /**
     * Updates the specified location rule with new start and end times.
     *
     * @param userId the ID of the user who owns the rule
     * @param ruleId the ID of the rule to be updated
     * @param startTime the new start time for the rule
     * @param endTime the new end time for the rule
     * @return an [Either] instance containing either the updated [RuleLocation] wrapped in [Either.Right]
     *        or an instance of [RuleError] wrapped in [Either.Left] if any validation fails.
     */
    fun updateLocationRule(
        userId: Int,
        ruleId: Int,
        startTime: Instant,
        endTime: Instant,
    ): Either<RuleError, RuleLocation> =
        trxManager.run {
            if (startTime >= endTime) return@run failure(RuleError.StartTimeMustBeBeforeEndTime)
            if (userId < 0 || ruleId < 0) return@run failure(RuleError.NegativeIdentifier)
            val user = userRepo.findById(userId) ?: return@run failure(RuleError.UserNotFound)
            val rule = ruleRepo.findRuleLocationById(ruleId) ?: return@run failure(RuleError.RuleNotFound)
            if (rule.creator != user) return@run failure(RuleError.NotAllowed)
            if (ruleRepo.findByUserId(user).any {
                    it.id != ruleId &&
                        checkCollisionTime(
                            it.startTime,
                            it.endTime,
                            startTime,
                            endTime,
                        )
                }
            ) {
                return@run failure(RuleError.RuleAlreadyExistsForGivenTime)
            }
            val updatedRule = ruleRepo.updateRuleLocation(rule, startTime, endTime)
            return@run success(updatedRule)
        }

    /**
     * Deletes an existing rule associated with a specific user.
     *
     * @param userId the ID of the user who owns the rule
     * @param ruleId the ID of the rule to be deleted
     * @return an [Either] instance containing [Unit] wrapped in [Either.Right] if the rule was successfully deleted,
     *         or an instance of [RuleError] wrapped in [Either.Left] if the operation failed
     */
    fun deleteRuleEvent(
        userId: Int,
        ruleId: Int,
    ): Either<RuleError, Unit> =
        trxManager.run {
            if (userId < 0 || ruleId < 0) return@run failure(RuleError.NegativeIdentifier)
            val user = userRepo.findById(userId) ?: return@run failure(RuleError.UserNotFound)
            val rule =
                ruleRepo.findRuleEventById(ruleId)
                    ?: return@run failure(RuleError.RuleNotFound)
            if (rule.creator != user) return@run failure(RuleError.NotAllowed)
            ruleRepo.deleteRuleEvent(rule)
            return@run success(Unit)
        }

    /**
     * Deletes an existing location rule associated with a specific user.
     *
     * @param userId the ID of the user who owns the rule
     * @param ruleId the ID of the rule to be deleted
     * @return an [Either] instance containing [Unit] wrapped in [Either.Right] if the rule was successfully deleted,
     *        or an instance of [RuleError] wrapped in [Either.Left] if the operation failed
     */
    fun deleteLocationRule(
        userId: Int,
        ruleId: Int,
    ): Either<RuleError, Unit> =
        trxManager.run {
            if (userId < 0 || ruleId < 0) return@run failure(RuleError.NegativeIdentifier)
            val user = userRepo.findById(userId) ?: return@run failure(RuleError.UserNotFound)
            val rule = ruleRepo.findRuleLocationById(ruleId) ?: return@run failure(RuleError.RuleNotFound)
            if (rule.creator != user) {
                return@run failure(RuleError.NotAllowed)
            }
            // TODO use return?
            ruleRepo.deleteLocationEvent(rule)
            return@run success(Unit)
        }

    // TODO add delete with a array of ids

    // TODO move
    fun checkCollisionTime(
        startTimeX: Instant,
        endTimeX: Instant,
        startTimeZ: Instant,
        endTimeZ: Instant,
    ): Boolean {
        return startTimeX <= endTimeZ && endTimeX >= startTimeZ
    }
}
