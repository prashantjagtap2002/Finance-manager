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
     * 18 → 19 adds `updatedAt` to every synced table. Rows must survive, and they must come out
     * carrying a real timestamp rather than 0 — a row stamped 0 loses every comparison against the
     * server, so a whole existing database would be silently overwritten on the next sync.
     */
    @Test
    fun migrate18To19_keepsRowsAndStampsThem() {
        helper.createDatabase(dbName, 18).apply {
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
            execSQL(
                "INSERT INTO debts (personName, amount, type, isSettled, date, paidAmount, notes) " +
                    "VALUES ('Ravi', 500.0, 'LENT', 0, 1757784000000, 0.0, 'lunch')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            19,
            true,
            FinanceDatabase.MIGRATION_18_19
        )

        db.query("SELECT amount, note, updatedAt FROM transactions").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(2450.0, c.getDouble(0), 0.001)
            assertEquals("swiggy", c.getString(1))
            assertTrue("an existing row must be stamped, not left at 0", c.getLong(2) > 0L)
        }

        db.query("SELECT balance, openingBalance, updatedAt FROM accounts").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(15000.0, c.getDouble(0), 0.001)
            assertEquals(15000.0, c.getDouble(1), 0.001)
            assertTrue(c.getLong(2) > 0L)
        }

        db.query("SELECT personName, amount, updatedAt FROM debts").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("Ravi", c.getString(0))
            assertEquals(500.0, c.getDouble(1), 0.001)
            assertTrue(c.getLong(2) > 0L)
        }

        db.close()
    }

    /**
     * The upgrade a user on the previous release actually takes. Opening the database through
     * Room (rather than the helper) runs the real builder, so this also proves the migration list
     * registered in [FinanceDatabase] is complete for that hop.
     */
    @Test
    fun migrate18To19_throughRoom_opensCleanly() {
        helper.createDatabase(dbName, 18).apply {
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
        ).addMigrations(
            FinanceDatabase.MIGRATION_18_19,
            FinanceDatabase.MIGRATION_19_20,
            FinanceDatabase.MIGRATION_20_21
        ).build()

        db.openHelper.writableDatabase.query("SELECT name, budgetLimit, updatedAt FROM categories").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("Food & Dining", c.getString(0))
            assertEquals(5000.0, c.getDouble(1), 0.001)
            assertTrue(c.getLong(2) > 0L)
        }
        db.close()
    }

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
            // Opening through Room takes the database all the way to the current version, so every
            // hop from 17 onwards has to be here — not just the one this test is named after.
        ).addMigrations(
            FinanceDatabase.MIGRATION_17_18,
            FinanceDatabase.MIGRATION_18_19,
            FinanceDatabase.MIGRATION_19_20,
            FinanceDatabase.MIGRATION_20_21
        ).build()

        db.openHelper.writableDatabase.query("SELECT name, budgetLimit FROM categories").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("Food & Dining", c.getString(0))
            assertEquals(5000.0, c.getDouble(1), 0.001)
        }
        db.close()
    }

    /**
     * 19 -> 20 claims an id band. It changes no schema and must move no row, but every table's
     * next id has to land inside the band — that is the whole point of the hop — while the ids
     * already handed out stay exactly where they are.
     */
    @Test
    fun migrate19To20_keepsRowsAndBandsFutureIds() {
        helper.createDatabase(dbName, 19).apply {
            execSQL(
                "INSERT INTO accounts (id, name, type, balance, currency, openingBalance, updatedAt) " +
                    "VALUES (1, 'Kotak Bank', 'BANK', 15000.0, 'INR', 15000.0, 1757784000000)"
            )
            execSQL(
                "INSERT INTO transactions (id, amount, type, categoryId, sourceAccountId, " +
                    "destinationAccountId, note, splitGroupId, date, currency, isRecurring, " +
                    "recurringId, isAutoLogged, merchantName, originalAmount, originalCurrency, " +
                    "isVerified, updatedAt) VALUES (1, 2450.0, 'EXPENSE', 3, 1, NULL, 'swiggy', " +
                    "NULL, 1757784000000, 'INR', 0, NULL, 0, 'Swiggy', NULL, NULL, 1, 1757784000000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            20,
            true,
            FinanceDatabase.MIGRATION_19_20
        )

        // Existing rows keep the ids the cloud already knows them by.
        db.query("SELECT id, amount, note FROM transactions").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
            assertEquals(2450.0, c.getDouble(1), 0.001)
            assertEquals("swiggy", c.getString(2))
        }
        db.query("SELECT id, balance FROM accounts").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
            assertEquals(15000.0, c.getDouble(1), 0.001)
        }

        // Every autoincrement table now mints above the band floor, including the ones that had
        // no rows at all and so had no sequence row to raise.
        val bandFloor = 1_000_000_000L
        db.execSQL(
            "INSERT INTO transactions (amount, type, categoryId, sourceAccountId, " +
                "destinationAccountId, note, splitGroupId, date, currency, isRecurring, " +
                "recurringId, isAutoLogged, merchantName, originalAmount, originalCurrency, " +
                "isVerified, updatedAt) VALUES (10.0, 'EXPENSE', 3, 1, NULL, 'after', NULL, " +
                "1757784000001, 'INR', 0, NULL, 0, NULL, NULL, NULL, 1, 1757784000001)"
        )
        db.query("SELECT id FROM transactions WHERE note = 'after'").use { c ->
            c.moveToFirst()
            assertTrue("a new id must be minted inside the band", c.getLong(0) > bandFloor)
        }

        db.execSQL(
            "INSERT INTO savings_goals (name, iconName, colorHex, targetAmount, savedAmount, " +
                "targetDate, createdAt, updatedAt) " +
                "VALUES ('Trip', 'savings', '#10B981', 50000.0, 0.0, NULL, 1757784000000, 1757784000000)"
        )
        db.query("SELECT id FROM savings_goals").use { c ->
            c.moveToFirst()
            assertTrue("an empty table must be banded too", c.getLong(0) > bandFloor)
        }

        db.close()
    }

    /**
     * 20 -> 21 adds the deletion-record table and the investment trade's ledger link.
     *
     * The table has to arrive *empty*. A tombstone is an instruction to delete a row from the
     * cloud, so inventing any here would have the first sync after upgrading delete data the user
     * never asked it to. Existing trades must likewise come through with `ledgerTransactionId` at
     * 0, which is what the repository reads as "this trade predates the link".
     */
    @Test
    fun migrate20To21_addsTombstonesEmptyAndLinksTrades() {
        helper.createDatabase(dbName, 20).apply {
            execSQL(
                "INSERT INTO investments (id, name, symbol, exchange, type, currentPrice, " +
                    "lastPriceUpdate, currency, notes) " +
                    "VALUES (1, 'TCS', 'TCS', 'NSE', 'STOCK', 3900.0, 0, 'INR', '')"
            )
            execSQL(
                "INSERT INTO investment_transactions (id, investmentId, type, quantity, " +
                    "pricePerUnit, amount, date, sourceAccountId, isSip) " +
                    "VALUES (1, 1, 'BUY', 10.0, 3500.0, 35000.0, 1757784000000, 1, 0)"
            )
            execSQL(
                "INSERT INTO accounts (id, name, type, balance, currency, openingBalance, updatedAt) " +
                    "VALUES (1, 'Kotak Bank', 'BANK', 15000.0, 'INR', 15000.0, 1757784000000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            21,
            true,
            FinanceDatabase.MIGRATION_20_21
        )

        db.query("SELECT COUNT(*) FROM deleted_records").use { c ->
            c.moveToFirst()
            assertEquals("an upgrade must not invent deletions", 0, c.getInt(0))
        }

        db.query("SELECT quantity, amount, ledgerTransactionId FROM investment_transactions").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(10.0, c.getDouble(0), 0.001)
            assertEquals(35000.0, c.getDouble(1), 0.001)
            assertEquals("a trade from before the link must read as unlinked", 0L, c.getLong(2))
        }

        db.query("SELECT balance FROM accounts").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(15000.0, c.getDouble(0), 0.001)
        }

        // The composite key is what makes re-deleting the same row idempotent.
        db.execSQL("INSERT OR REPLACE INTO deleted_records (tableName, rowId, deletedAt) VALUES ('transactions', 7, 1)")
        db.execSQL("INSERT OR REPLACE INTO deleted_records (tableName, rowId, deletedAt) VALUES ('transactions', 7, 2)")
        db.query("SELECT COUNT(*) FROM deleted_records WHERE tableName = 'transactions'").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }

        db.close()
    }
}
