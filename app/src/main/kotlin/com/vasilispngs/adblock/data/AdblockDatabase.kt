package com.vasilispngs.adblock.data

import android.content.Context
import androidx.paging.PagingSource
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Upsert
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.flow.Flow

internal const val DEFAULT_LIST_URL = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/pro.mini.txt"
internal const val DEFAULT_LIST_NAME = "HaGeZi's Multi PRO mini (best for browser/mobile ad blockers)"

enum class Action { Allow, Block }

enum class Origin { List, Cname, Custom, Allow, None, Cache, Stale }

@Entity(tableName = "sources")
data class ListSource(
    @PrimaryKey val url: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val domains: Int? = null,
    val version: String? = null,
    val etag: String? = null,
    @ColumnInfo(name = "last_modified") val lastModified: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long? = null,
    @ColumnInfo(name = "failed_at") val failedAt: Long? = null
)

@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey val host: String,
    val action: Action,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(tableName = "queries", indices = [Index("at")])
data class LoggedQuery(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val name: String,
    val type: String,
    val action: Action,
    val origin: Origin,
    val rule: String?
)

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY created_at, url")
    fun observe(): Flow<List<ListSource>>

    @Query("SELECT * FROM sources ORDER BY created_at, url")
    suspend fun all(): List<ListSource>

    @Query("SELECT * FROM sources WHERE url = :url")
    suspend fun get(url: String): ListSource?

    @Upsert
    suspend fun save(source: ListSource)

    @Query("DELETE FROM sources WHERE url = :url")
    suspend fun delete(url: String)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY created_at DESC")
    fun observe(): Flow<List<Rule>>

    @Upsert
    suspend fun save(rule: Rule)

    @Query("DELETE FROM rules WHERE host = :host")
    suspend fun delete(host: String)
}

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface QueryDao {
    @Query(
        "SELECT * FROM queries WHERE at >= :since AND (:action IS NULL OR action = :action) " +
            "AND (:pattern = '' OR name LIKE :pattern ESCAPE '\\') ORDER BY at DESC, id DESC"
    )
    fun page(since: Long, action: Action?, pattern: String): PagingSource<Int, LoggedQuery>

    @Insert
    suspend fun insert(queries: List<LoggedQuery>)

    @Query("DELETE FROM queries WHERE at < :cutoff")
    suspend fun deleteBefore(cutoff: Long)
}

@Database(entities = [ListSource::class, Rule::class, LoggedQuery::class], version = 1)
abstract class AdblockDatabase : RoomDatabase() {
    abstract fun sources(): SourceDao

    abstract fun rules(): RuleDao

    abstract fun queries(): QueryDao

    companion object {
        fun create(context: Context): AdblockDatabase = Room.databaseBuilder<AdblockDatabase>(context, "adblock.db")
            .setDriver(AndroidSQLiteDriver())
            .addCallback(
                object : Callback() {
                    override suspend fun onCreate(connection: SQLiteConnection) {
                        connection.execSQL(
                            "INSERT INTO sources (url, name, created_at) VALUES ('$DEFAULT_LIST_URL', '${DEFAULT_LIST_NAME.replace("'", "''")}', 0)"
                        )
                    }
                }
            )
            .build()
    }
}
