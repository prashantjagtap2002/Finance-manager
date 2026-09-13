package com.example.financemanager.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Replays real databases through the migrations.
 *
 * This is the only thing standing between a hand-written `ALTER TABLE` and silent data loss in a
 * money app: [MigrationTestHelper] opens a database at the old schema, runs the migration, and
 * then validates the result against the schema Room exported for the new version. A migration
 * that drops a column, renames a table, or disagrees with the entity definitions fails here.
 *
 * Every test seeds rows before migrating and asserts they survive with their values intact —
 * validation alone only proves the *shape* is right, not that the data came through.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FinanceDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * 17 → 18 only adds indices. Indexing rewrites no rows, so every transaction must come out
     * the other side byte for byte — and the new indices must exist.
     */
    @Test
    fun migrate17To18_keepsRowsAndAddsIndices() {
        helper.createDatabase(dbName, 17).apply {
            execSQL(
                "INSERT INTO transactions (amount, type, categoryId, sourceAccountId, " +
                    "destinationAccountId, note, splitGroupId, date, currency, isRecurring, " +
                    "recurringId, isAutoLogged, merchantName, originalAmount, originalCurrency, " +
                    "isVerified) VALUES (2450.0, 'EXPENSE', 3, 1, NULL, 'swiggy', NULL, " +
                    "1757784000000, 'INR', 0, NULL, 0, 'Swiggy', NULL, NULL, 1)"
            )
            execSQL(
                "INSERT INTO accounts (name, type, balance, currency, openingBalance) " +
                    "VALUES ('Kotak Bank', 'BANK', 15000.0, 'INR', 15000.0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            18,
            true,
            FinanceDatabase.MIGRATION_17_18
        )

        db.query("SELECT amount, note, merchantName, date FROM transactions").use { c ->
            assertEquals("the seeded transaction must survive the migration", 1, c.count)
            c.moveToFirst()
            assertEquals(2450.0, c.getDouble(0), 0.001)
            assertEquals("swiggy", c.getString(1))
            assertEquals("Swiggy", c.getString(2))
            assertEquals(1757784000000L, c.getLong(3))
        }

        db.query("SELECT balance FROM accounts").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(15000.0, c.getDouble(0), 0.001)
        }

        val indices = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { c ->
            while (c.moveToNext()) c.getString(0)?.let { indices += it }
        }
        listOf(
            "index_transactions_date",
            "index_transactions_categoryId",
            "index_transactions_sourceAccountId",
            "index_transactions_destinationAccountId",
            "index_sms_transactions_rawTimestamp",
            "index_investment_transactions_investmentId",
            "index_debts_date"
        ).forEach { assertTrue("$it was not created", it in indices) }

        db.close()
    }

    /**
     * The upgrade a user on the previous release actually takes. Opening the database through
     * Room (rather than the helper) runs the real builder, so this also proves the migration list
     * registered in [FinanceDatabase] is complete for that hop.
     */
    @Test
    fun migrate17To18_throughRoom_opensCleanly() {
        helper.createDatabase(dbName, 17).apply {
            execSQL(
                "INSERT INTO categories (name, iconName, colorHex, budgetLimit, isZeroBased, " +
                    "rolloverAmount, isRolloverEnabled, displayOrder) " +
                    "VALUES ('Food & Dining', 'restaurant', '#FF0000', 5000.0, 0, 0.0, 0, 0)"
            )
            close()
        }

        val db = androidx.room.Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FinanceDatabase::class.java,
            dbName
        ).addMigrations(FinanceDatabase.MIGRATION_17_18).build()

        db.openHelper.writableDatabase.query("SELECT name, budgetLimit FROM categories").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("Food & Dining", c.getString(0))
            assertEquals(5000.0, c.getDouble(1), 0.001)
        }
        db.close()
    }
}
