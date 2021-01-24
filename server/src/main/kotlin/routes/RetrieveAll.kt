/* ktlint-disable no-wildcard-imports */
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

@Serializable
private data class RetrieveAllResponse(
    val objects: List<StoredObject>,
    val next: Int?
)

fun Route.retrieveAll() {
    get("/v1/objects/all") {
        // Authenticate request
        val principal: UserIdPrincipal? = call.authentication.principal()
        if (principal == null) {
            call.respondText("Invalid credentials!", status = HttpStatusCode.Unauthorized)
            return@get
        }

        // Parse query parameters to get cursor (start index)
        val startIndex: Int? = call.request.queryParameters["start"]?.toIntOrNull()

        newSuspendedTransaction {
            // Build query, depending on whether cursor was provided
            val queryBase =
                if (startIndex == null) Objects.selectAll() else Objects.select { Objects.id greaterEq startIndex }
            val query = queryBase.limit(PAGE_SIZE + 1)

            // Retrieve results
            val objects = query.map { StoredObject.fromRow(it) }.toMutableList()

            // Determine whether there are more results beyond this page
            val nextPageStartId: Int? =
                if (objects.size > PAGE_SIZE) { // if there are more results than fit in the current page

                    // Remove the extra object we retrieved to check if there are extras
                    val removedObject = objects.removeAt(objects.size - 1)
                    // But save its ID: it will be used as the cursor for the next page
                    removedObject.id
                } else null

            // Serialize and return results
            val response = RetrieveAllResponse(objects = objects, next = nextPageStartId)
            call.respond(response)
        }
    }
}
