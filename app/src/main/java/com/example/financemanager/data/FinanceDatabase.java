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
    entities = {Account.class, Category.class, Transaction.class, RecurringTransaction.class, SavingsGoal.class, Debt.class, SmsTransaction.class},
    version = 11,
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

    public static FinanceDatabase getDatabase(final Context context, final kotlinx.coroutines.CoroutineScope scope) {
        if (INSTANCE == null) {
            synchronized (FinanceDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        FinanceDatabase.class,
                        "finance_database"
                    )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
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
