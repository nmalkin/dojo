/* ktlint-disable no-wildcard-imports */

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection

/* Configuration */
/** Username & password (concatenated) to use for the basic auth for privileged requests */
val ADMIN_CREDENTIALS = System.getenv("ADMIN_CREDENTIALS")
    ?: "testing:changeit".also { System.err.println("WARNING: using insecure default credentials") }

/** The only host to allow with CORS. If null, will allow all hosts */
val ONLY_HOST: String? = System.getenv("ONLY_HOST")

/** A JDBC connection string to use for the app's database */
val DATABASE_URL: String = System.getenv("DATABASE_URL") ?: "jdbc:sqlite:db.sqlite"

/** Username and password for the database connection, separated by a : */
val DATABASE_CREDENTIALS: String = System.getenv("DATABASE_CREDENTIALS") ?: ":"

@Serializable
data class ErrorResponse(val error: String, val message: String?)

@Serializable
data class RetrieveAllResponse(
    val objects: List<StoredObject>,
    val next: Int?
)

@Serializable
data class NewObjectRequest(
    @SerialName("api_version") val apiVersion: Int,
    val name: String,
    val value: JsonElement,
    @SerialName("client_version") val clientVersion: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_label") val userLabel: String? = null,
)

@Serializable
data class NewObjectResponse(
    val id: Int,
    val token: String,
)

@Serializable
data class UpdateObjectRequest(
    @SerialName("api_version") val apiVersion: Int,
    @SerialName("client_version") val clientVersion: String,
    val token: String,
    val value: JsonElement,
    val revision: Int,
)

@Serializable
data class UpdateObjectResponse(
    val id: Int,
    val revision: Int,
)

fun Application.module() {
    install(DefaultHeaders)
    install(CallLogging)
    install(ContentNegotiation) {
        json()
    }

    install(CORS) {
        if (ONLY_HOST == null) {
            anyHost()
        } else {
            host(ONLY_HOST, schemes = listOf("http", "https"))
        }
    }

    install(Authentication) {
        basic("privilegedGet") {
            realm = "reading all objects"
            validate { credentials ->
                val combinedCredentials = "${credentials.name}:${credentials.password}"
                if (combinedCredentials == ADMIN_CREDENTIALS) UserIdPrincipal(credentials.name) else null
            }
        }
    }
    install(Routing) {
        get("/v1/version") {
            call.respondText(VERSION, ContentType.Text.Plain)
        }

        get("/v1/status") {
            try {
                val query = suspendedTransactionAsync {
                    Objects.selectAll().count()
                }

                query.await()

                call.respondText("OK", ContentType.Text.Plain)
            } catch (e: ExposedSQLException) {
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = ErrorResponse(error = "database error", message = e.stackTraceToString()),
                )
            }
        }

        authenticate("privilegedGet") {
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

                call.respondText("the end")
            }
        }

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
            }
        }

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
            }
        }
    }
}

/**
 * Given a JDBC connection string, return one of the known JDBC drivers to use with it)
 */
fun getDriverFromConnectionString(connection: String): String {
    if (connection.startsWith("jdbc:sqlite:")) {
        return "org.sqlite.JDBC"
    } else if (connection.startsWith("jdbc:postgresql:")) {
        return "org.postgresql.Driver"
    }

    throw IllegalArgumentException("unrecognized or invalid database in connection string: $connection")
}

fun initDatabase() {
    val (username, password) = DATABASE_CREDENTIALS.split(":").also {
        if (it.size < 2) {
            throw RuntimeException("DATABASE_CREDENTIALS environment variable must be specified in the format username:password")
        }
    }

    Database.connect(
        DATABASE_URL,
        driver = getDriverFromConnectionString(DATABASE_URL),
        user = username,
        password = password
    )
    TransactionManager.manager.defaultIsolationLevel =
        Connection.TRANSACTION_SERIALIZABLE
    transaction { SchemaUtils.create(Objects) }

    transaction {
        SchemaUtils.create(Objects)
    }
}

const val PORT = 8080

fun main() {
    initDatabase()

    embeddedServer(Netty, PORT, watchPaths = listOf("MainKt"), module = Application::module).start()
    println("Server is now running on port $PORT")
}
