/* ktlint-disable no-wildcard-imports */

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
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
            retrieveAll()
        }

        newObject()
        updateObject()
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
