package com.opero.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip)

    @Delete
    suspend fun delete(trip: Trip)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun byId(id: Long): Trip?

    @Query("SELECT * FROM trips WHERE type = 'OPEN' ORDER BY startTime DESC LIMIT 1")
    suspend fun openTrip(): Trip?

    @Query("SELECT * FROM trips WHERE startTime BETWEEN :from AND :to ORDER BY startTime ASC")
    fun between(from: Long, to: Long): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE clientId = :clientId AND type != 'OPEN' ORDER BY startTime DESC")
    fun forClient(clientId: Long): Flow<List<Trip>>
}

@Dao
interface ClientDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(client: Client): Long

    @Update
    suspend fun update(client: Client)

    @Query("SELECT * FROM clients WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): Client?

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun byId(id: Long): Client?

    @Query("SELECT * FROM clients ORDER BY lastVisit DESC")
    fun all(): Flow<List<Client>>
}

@Dao
interface MemoDao {
    @Insert
    suspend fun insert(memo: Memo): Long

    @Query("SELECT * FROM memos WHERE clientId = :clientId ORDER BY createdAt DESC")
    fun forClient(clientId: Long): Flow<List<Memo>>

    @Query("SELECT * FROM memos WHERE clientId = :clientId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(clientId: Long): Memo?

    @Query("SELECT COUNT(*) FROM memos WHERE clientId = :clientId")
    fun countFor(clientId: Long): Flow<Int>
}

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: Expense): Long

    @Query("SELECT * FROM expenses WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt ASC")
    fun between(from: Long, to: Long): Flow<List<Expense>>
}
