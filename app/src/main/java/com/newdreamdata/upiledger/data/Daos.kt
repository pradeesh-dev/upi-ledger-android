package com.newdreamdata.upiledger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(e: Expense): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(list: List<Expense>): List<Long>

    @Update
    suspend fun update(e: Expense)

    @Delete
    suspend fun delete(e: Expense)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun byId(id: Long): Expense?

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun observeById(id: Long): Flow<Expense?>

    @Query("SELECT * FROM expenses WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp ASC")
    suspend fun between(from: Long, to: Long): List<Expense>

    @Query("SELECT * FROM expenses ORDER BY timestamp ASC")
    suspend fun all(): List<Expense>

    @Query("SELECT COUNT(*) FROM expenses WHERE needsReview = 1")
    fun observeReviewCount(): Flow<Int>

    @Query("SELECT * FROM expenses WHERE needsReview = 1 ORDER BY timestamp DESC")
    fun observeNeedsReview(): Flow<List<Expense>>

    @Query("SELECT COUNT(*) FROM expenses WHERE txnId = :txnId AND txnId != ''")
    suspend fun countByTxn(txnId: String): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE amountPaise = :amountPaise AND ABS(timestamp - :timestamp) < :windowMs AND (upiId = :upiId OR :upiId = '')")
    suspend fun countSimilar(amountPaise: Long, timestamp: Long, upiId: String, windowMs: Long): Int

    @Query(
        """SELECT category, SUM(amountPaise) AS totalPaise, COUNT(*) AS count FROM expenses
           WHERE timestamp >= :from AND timestamp < :to GROUP BY category ORDER BY totalPaise DESC"""
    )
    fun observeCategoryTotals(from: Long, to: Long): Flow<List<CategoryTotal>>

    @Query(
        """SELECT CASE WHEN payee = '' THEN upiId ELSE payee END AS payee, upiId, SUM(amountPaise) AS totalPaise, COUNT(*) AS count
           FROM expenses WHERE timestamp >= :from AND timestamp < :to
           GROUP BY CASE WHEN payee = '' THEN upiId ELSE payee END ORDER BY totalPaise DESC LIMIT :limit"""
    )
    fun observePayeeTotals(from: Long, to: Long, limit: Int): Flow<List<PayeeTotal>>

    /** Most frequent payees in the last [since] window, with the last amount paid to each. */
    @Query(
        """SELECT e.payee AS payee, e.upiId AS upiId, e.category AS category, e.amountPaise AS lastAmountPaise, e.app AS app, c.cnt AS count
           FROM expenses e
           JOIN (SELECT CASE WHEN payee = '' THEN upiId ELSE payee END AS k, MAX(timestamp) AS maxTs, COUNT(*) AS cnt
                 FROM expenses WHERE timestamp >= :since AND (payee != '' OR upiId != '') AND needsReview = 0
                 GROUP BY k) c
           ON (CASE WHEN e.payee = '' THEN e.upiId ELSE e.payee END) = c.k AND e.timestamp = c.maxTs
           GROUP BY c.k ORDER BY c.cnt DESC, c.maxTs DESC LIMIT :limit"""
    )
    fun observeQuickPayees(since: Long, limit: Int): Flow<List<QuickPayee>>

    @Query("SELECT category FROM expenses WHERE (payee = :payee AND payee != '') ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastCategoryForPayee(payee: String): String?

    @Query("SELECT DISTINCT category FROM expenses ORDER BY category")
    fun observeCategories(): Flow<List<String>>
}

@Dao
interface MappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(m: Mapping): Long

    @Delete
    suspend fun delete(m: Mapping)

    @Query("SELECT * FROM mappings ORDER BY category, upiId")
    fun observeAll(): Flow<List<Mapping>>

    @Query("SELECT * FROM mappings")
    suspend fun all(): List<Mapping>

    @Query("SELECT * FROM mappings WHERE upiId = :upiId LIMIT 1")
    suspend fun byUpi(upiId: String): Mapping?

    @Query("SELECT * FROM mappings WHERE payeeKey = :payeeKey AND payeeKey != '' LIMIT 1")
    suspend fun byPayeeKey(payeeKey: String): Mapping?

    @Query("SELECT DISTINCT category FROM mappings ORDER BY category")
    fun observeCategories(): Flow<List<String>>
}
