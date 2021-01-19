import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow

/** Objects and their metadata, as stored in the database */
object Objects : IntIdTable() {
    val createdAt: Column<Int> = integer("created_at")
    val updatedAt: Column<Int> = integer("updated_at")
    val name: Column<String> = varchar("name", 32)
    val blob: Column<String> =
        text("object") // XXX: if this framework ever gets a JSON column type, should switch to that
    val updateToken: Column<String> = varchar("update_token", 32)
    val clientVersionInitial: Column<String> = varchar("client_version_first", 16)
    val clientVersionLatest: Column<String> = varchar("client_version_last", 16)
    val userId: Column<String> = varchar("user_id", 32)
    val userLabel: Column<String?> = varchar("user_label", 32).nullable()
    val revision: Column<Int> = integer("revision")
}

@Serializable
/** Objects and their metadata, as returned to clients */
data class StoredObject(
    val id: Int,
    @SerialName("created_at") val createdAt: Int,
    @SerialName("updated_at") val updatedAt: Int,
    @SerialName("name") val name: String,
    @SerialName("object") val blob: JsonElement,
    @SerialName("update_token") val updateToken: String,
    @SerialName("client_version_first") val clientVersionInitial: String,
    @SerialName("client_version_last") val clientVersionLatest: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_label") val userLabel: String?,
    @SerialName("revision") val revision: Int,
) {
    companion object {
        fun fromRow(row: ResultRow): StoredObject {
            val alreadyStringifiedBlob = row[Objects.blob]
            val parsedBlob = Json.parseToJsonElement(alreadyStringifiedBlob)

            return StoredObject(
                id = row[Objects.id].value,

                createdAt = row[Objects.createdAt],
                updatedAt = row[Objects.updatedAt],
                name = row[Objects.name],
                blob = parsedBlob,
                updateToken = row[Objects.updateToken],
                clientVersionInitial = row[Objects.clientVersionInitial],
                clientVersionLatest = row[Objects.clientVersionLatest],
                userId = row[Objects.userId],
                userLabel = row[Objects.userLabel],
                revision = row[Objects.revision],
            )
        }
    }
}

const val PAGE_SIZE = 50
