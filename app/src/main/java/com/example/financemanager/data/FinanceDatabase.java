package com.example.financemanager.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.room.migration.Migration;

@Database(
    entities = {Account.class, Category.class, Transaction.class, RecurringTransaction.class, SavingsGoal.class, Debt.class, SmsTransaction.class, MerchantRule.class},
    version = 14,
    exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class FinanceDatabase extends RoomDatabase {

    public abstract FinanceDao financeDao();

    private static volatile FinanceDatabase INSTANCE;

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE categories ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN splitGroupId TEXT DEFAULT NULL");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `savings_goals` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`iconName` TEXT NOT NULL DEFAULT 'savings', " +
                "`colorHex` TEXT NOT NULL DEFAULT '#10B981', " +
                "`targetAmount` REAL NOT NULL, " +
                "`savedAmount` REAL NOT NULL DEFAULT 0, " +
                "`targetDate` INTEGER, " +
                "`createdAt` INTEGER NOT NULL DEFAULT 0)"
            );
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `debts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`personName` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`isSettled` INTEGER NOT NULL DEFAULT 0, " +
                "`date` INTEGER NOT NULL, " +
                "`notes` TEXT NOT NULL DEFAULT '')"
            );
        }
    };

    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE debts ADD COLUMN dueDate INTEGER DEFAULT NULL");
            database.execSQL("ALTER TABLE debts ADD COLUMN paidAmount REAL NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE debts ADD COLUMN interestRate REAL DEFAULT NULL");
            database.execSQL("ALTER TABLE debts ADD COLUMN minimumPayment REAL DEFAULT NULL");
        }
    };

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE recurring_transactions ADD COLUMN isPaused INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN isVerified INTEGER NOT NULL DEFAULT 1");
        }
    };

    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `sms_transactions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`smsHash` TEXT NOT NULL, " +
                "`sender` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, " +
                "`accountName` TEXT NOT NULL DEFAULT '', " +
                "`type` TEXT NOT NULL DEFAULT '', " +
                "`amount` TEXT NOT NULL DEFAULT '', " +
                "`balance` TEXT NOT NULL DEFAULT '', " +
                "`counterparty` TEXT NOT NULL DEFAULT '', " +
                "`reference` TEXT NOT NULL DEFAULT '', " +
                "`rawTimestamp` INTEGER NOT NULL DEFAULT 0, " +
                "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                "`isApproved` INTEGER NOT NULL DEFAULT 0, " +
                "`isIgnored` INTEGER NOT NULL DEFAULT 0, " +
                "`approvedCategoryId` INTEGER NOT NULL DEFAULT 0, " +
                "`approvedAccountId` INTEGER NOT NULL DEFAULT 0" +
                ")"
            );
        }
    };

    // Enforces uniqueness of smsHash so OnConflictStrategy.IGNORE in
    // FinanceDao#insertSmsTransaction actually prevents duplicate SMS rows (previously the
    // column had no constraint backing it, so the same SMS delivered twice — e.g. via both
    // SMS_RECEIVED and SMS_DELIVER broadcasts — created duplicate pending transactions).
    // Existing duplicate rows are collapsed to the earliest one before the index is created,
    // since CREATE UNIQUE INDEX fails if duplicates already exist.
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                "DELETE FROM sms_transactions WHERE id NOT IN (SELECT MIN(id) FROM sms_transactions GROUP BY smsHash)"
            );
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_sms_transactions_smsHash ON sms_transactions(smsHash)"
            );
        }
    };

    // Remembers the category a merchant was filed under so repeat SMS alerts arrive
    // pre-categorised instead of asking the user again every time.
    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `merchant_rules` (" +
                "`merchantKey` TEXT PRIMARY KEY NOT NULL, " +
                "`categoryId` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL DEFAULT 0, " +
                "`hitCount` INTEGER NOT NULL DEFAULT 1, " +
                "`updatedAt` INTEGER NOT NULL DEFAULT 0)"
            );
        }
    };

    // Adds the anchor for the ledger invariant. Existing balances are taken as correct and the
    // opening balance is derived to match, so upgrading never moves anybody's numbers — from here
    // on AccountLedger keeps the two in step.
    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN openingBalance REAL NOT NULL DEFAULT 0");
            database.execSQL(
                "UPDATE accounts SET openingBalance = balance" +
                " - COALESCE((SELECT SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE -t.amount END)" +
                "   FROM transactions t WHERE t.sourceAccountId = accounts.id), 0)" +
                " - COALESCE((SELECT SUM(t.amount) FROM transactions t" +
                "   WHERE t.destinationAccountId = accounts.id AND t.type = 'TRANSFER'), 0)"
            );
        }
    };

    public static FinanceDatabase getDatabase(final Context context, final kotlinx.coroutines.CoroutineScope scope) {
        if (INSTANCE == null) {
            synchronized (FinanceDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        FinanceDatabase.class,
                        "finance_database"
                    )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                    .addCallback(new RoomDatabase.Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
                            DatabaseSeeder.seed(INSTANCE.financeDao());
                        }
                    })
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
