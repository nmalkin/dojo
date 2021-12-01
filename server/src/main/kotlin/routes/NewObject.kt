/* ktlint-disable no-wildcard-imports */
import io.ktor.application.call
import io.ktor.features.UnsupportedMediaTypeException
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

@Serializable
private data class NewObjectRequest(
    @SerialName("api_version") val apiVersion: Int,
    val name: String,
    val value: JsonElement,
    @SerialName("client_version") val clientVersion: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_label") val userLabel: String? = null,
)

@Serializable
private data class NewObjectResponse(
    val id: Int,
    val token: String,
)

fun Route.newObject() {
    post("/v1/objects") {
        try {
            val request = call.receive<NewObjectRequest>()

            val token = Random.getEncodedRandomBytes()
            val timestamp = currentUnixTime()

            newSuspendedTransaction {
                val newObjectId = Objects.insertAndGetId {
                    it[createdAt] = timestamp
                    it[updatedAt] = timestamp
                    it[name] = request.name
                    it[blob] = request.value.toString()
                    it[updateToken] = token
                    it[clientVersionInitial] = request.clientVersion
                    it[clientVersionLatest] = request.clientVersion
                    it[userId] = request.userId
                    it[userLabel] = request.userLabel
                    it[revision] = 0
                }

                call.respond(message = NewObjectResponse(newObjectId.value, token))
            }
        } catch (e: SerializationException) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponse(error = "bad request", message = e.toString())
            )
        } catch (e: ExposedSQLException) {
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ErrorResponse(error = "database error", message = e.stackTraceToString()),
            )
        } catch (e: UnsupportedMediaTypeException) {
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ErrorResponse(
                    error = "content-type error",
                    message = "make sure you're calling this endpoint with 'Accept: application/json'"
                ),
            )
        } catch (e: Exception) {
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ErrorResponse(error = "unexpected error", message = e.stackTraceToString()),
            )
        }
    }
}
