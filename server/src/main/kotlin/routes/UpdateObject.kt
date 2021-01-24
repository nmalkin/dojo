/* ktlint-disable no-wildcard-imports */
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

@Serializable
private data class UpdateObjectRequest(
    @SerialName("api_version") val apiVersion: Int,
    @SerialName("client_version") val clientVersion: String,
    val token: String,
    val value: JsonElement,
    val revision: Int,
)

@Serializable
private data class UpdateObjectResponse(
    val id: Int,
    val revision: Int,
)

fun Route.updateObject() {
    post("/v1/objects/{id}") {
        val id: Int? = call.parameters["id"]?.toIntOrNull()
        if (id == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = "bad request",
                    message = "invalid id"
                )
            )
            return@post
        }
        try {
            val request = call.receive<UpdateObjectRequest>()

            val timestamp = currentUnixTime()

            newSuspendedTransaction {
                val rowsUpdated = Objects.update({
                    (Objects.id eq id) and (Objects.updateToken eq request.token) and (Objects.revision less request.revision)
                }) {
                    it[updatedAt] = timestamp
                    it[blob] = request.value.toString()
                    it[clientVersionLatest] = request.clientVersion
                    it[revision] = request.revision
                }

                if (rowsUpdated > 0) {
                    call.respond(message = UpdateObjectResponse(id, request.revision))
                } else {
                    call.respond(
                        status = HttpStatusCode.BadRequest,
                        message = ErrorResponse(error = "bad request", message = "invalid id, token, or revision")
                    )
                }
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
        } catch (e: Exception) {
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ErrorResponse(error = "unexpected error", message = e.stackTraceToString()),
            )
        }
    }
}
