package com.example.financemanager.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.room.migration.Migration;
import java.security.SecureRandom;

@Database(
    entities = {Account.class, Category.class, Transaction.class, RecurringTransaction.class, SavingsGoal.class, Debt.class, SmsTransaction.class, MerchantRule.class, Investment.class, InvestmentTransaction.class, ExpenseGroup.class, ExpenseGroupMember.class, ExpenseGroupExpense.class, DeletedRecord.class},
    version = 21,
    // Exported schemas are what MigrationTestHelper replays old databases against. Without them
    // nothing checks that the hand-written migrations below still agree with the entities.
    exportSchema = true
)
@TypeConverters({Converters.class})
public abstract class FinanceDatabase extends RoomDatabase {

    /**
     * The DAO Room generates. Prefer {@link #dao()} — this one cannot make the ledger's
     * write-then-adjust pairs atomic.
     */
    public abstract FinanceDao financeDao();

    private volatile FinanceDao transactionalDao;

    /**
     * The DAO every caller should use: the generated one wrapped so that a group of writes can be
     * committed as a single all-or-nothing transaction.
     *
     * Recording a transaction and moving the balances behind it are one event described in two
     * statements. Run apart, a process death between them leaves the ledger disagreeing with the
     * balances and nothing able to tell that apart from genuine drift. See
     * {@code TransactionalFinanceDao} and {@code AccountLedger}.
     */
    public FinanceDao dao() {
        FinanceDao existing = transactionalDao;
        if (existing != null) return existing;
        synchronized (this) {
            if (transactionalDao == null) {
                transactionalDao = new TransactionalFinanceDao(this, financeDao());
            }
            return transactionalDao;
        }
    }

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

    // Lets a captured SMS remember what it became. A self transfer and an IPO refund each arrive
    // as two alerts that cancel out, and resolving them means undoing the leg that was already
    // logged — which needs the transaction id, not a guess based on amount and date.
    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE sms_transactions ADD COLUMN loggedTransactionId INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE sms_transactions ADD COLUMN linkedSmsId INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE sms_transactions ADD COLUMN resolution TEXT NOT NULL DEFAULT ''");
        }
    };

    // Adds stock/mutual-fund holding tracking: one row per holding (Investment) and one row per
    // buy/sell/SIP installment against it (InvestmentTransaction), mirroring how
    // accounts/transactions are split.
    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `investments` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`symbol` TEXT, " +
                "`exchange` TEXT, " +
                "`type` TEXT NOT NULL, " +
                "`currentPrice` REAL NOT NULL DEFAULT 0, " +
                "`lastPriceUpdate` INTEGER NOT NULL DEFAULT 0, " +
                "`currency` TEXT NOT NULL DEFAULT 'INR', " +
                "`notes` TEXT NOT NULL DEFAULT '')"
            );
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `investment_transactions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`investmentId` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`quantity` REAL NOT NULL, " +
                "`pricePerUnit` REAL NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`date` INTEGER NOT NULL, " +
                "`sourceAccountId` INTEGER NOT NULL, " +
                "`isSip` INTEGER NOT NULL DEFAULT 0)"
            );
        }
    };

    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `expense_groups` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `currency` TEXT NOT NULL DEFAULT 'INR', " +
                "`createdAt` INTEGER NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `expense_group_members` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`groupId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`upiId` TEXT NOT NULL DEFAULT '', `isCurrentUser` INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_group_members_groupId` ON `expense_group_members` (`groupId`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `expense_group_expenses` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`groupId` INTEGER NOT NULL, `description` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, `paidByMemberId` INTEGER NOT NULL, " +
                "`participantMemberIds` TEXT NOT NULL, `date` INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_group_expenses_groupId` ON `expense_group_expenses` (`groupId`)");
        }
    };

    /**
     * Adds the indices the hot queries need. Creating an index neither rewrites nor drops a row,
     * so this is safe on a populated database; the names must match what Room generates for the
     * entity declarations or validation fails on open.
     */
    public static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_sourceAccountId` ON `transactions` (`sourceAccountId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_destinationAccountId` ON `transactions` (`destinationAccountId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_transactions_rawTimestamp` ON `sms_transactions` (`rawTimestamp`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_investment_transactions_investmentId` ON `investment_transactions` (`investmentId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_date` ON `debts` (`date`)");
        }
    };

    /**
     * Gives every synced table a last-changed stamp, so a sync can tell which side of a row is
     * newer instead of only being able to add rows it had never seen before.
     *
     * Existing rows are stamped with the upgrade time rather than 0. They are as current as this
     * device knows how to be, and a cloud row whose own column doesn't exist yet decodes as 0 —
     * so this device stays authoritative until the server has something newer to say, which is
     * exactly the behaviour the app had before the column existed.
     */
    public static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            String[] tables = {
                "accounts", "categories", "transactions", "savings_goals",
                "recurring_transactions", "debts", "sms_transactions"
            };
            for (String table : tables) {
                database.execSQL("ALTER TABLE " + table + " ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0");
                database.execSQL("UPDATE " + table + " SET updatedAt = CAST(strftime('%s','now') AS INTEGER) * 1000");
            }
        }
    };

    /** Every table that mints its primary key with AUTOINCREMENT. See {@link #claimIdBand}. */
    private static final String[] AUTOINCREMENT_TABLES = {
        "accounts", "categories", "transactions", "savings_goals", "recurring_transactions",
        "debts", "sms_transactions", "investments", "investment_transactions",
        "expense_groups", "expense_group_members", "expense_group_expenses"
    };

    /** How many ids each database gets to itself, and how many such bands there are to pick from. */
    private static final long ID_BAND_WIDTH = 1_000_000_000L;
    private static final int ID_BAND_COUNT = 8000;

    /**
     * Claims a band of primary keys for this database and mints every future id inside it.
     *
     * Ids are what the cloud merge matches rows on, and AUTOINCREMENT starts at 1 on every device.
     * Two devices on one account therefore both hand out 1, 2, 3… for unrelated rows, and the merge
     * reads them as the same row — one device's coffee silently overwriting the other's rent. That
     * cannot happen while every install has its own anonymous user, but it is waiting for the day
     * real auth puts two devices behind one account.
     *
     * Pushing SQLite's own sequence counter forward is enough to avoid it: two installs landing in
     * the same billion-wide band is a 1-in-8000 chance, and a band still has a billion rows of room.
     * The ids stay well inside the range a Postgres bigint and a JSON number both carry exactly.
     *
     * This deliberately does NOT rewrite ids that already exist. Rows written before the band was
     * claimed keep their low ids, so two databases that both predate this version can still overlap
     * in their old rows — nothing can fix that without rewriting keys the cloud already knows. The
     * first sync between any two devices is a restore, where the server wins outright, which is the
     * same answer that overlap would need anyway.
     */
    static void claimIdBand(SupportSQLiteDatabase database) {
        long band = (1L + new SecureRandom().nextInt(ID_BAND_COUNT)) * ID_BAND_WIDTH;
        for (String table : AUTOINCREMENT_TABLES) {
            // sqlite_sequence only carries a row once a table has been inserted into...
            database.execSQL(
                "INSERT INTO sqlite_sequence (name, seq) SELECT ?, ? " +
                    "WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = ?)",
                new Object[]{table, band, table}
            );
            // ...and the counter may only ever move forward. Lowering it would hand out ids that
            // rows in this very database already hold.
            database.execSQL(
                "UPDATE sqlite_sequence SET seq = ? WHERE name = ? AND seq < ?",
                new Object[]{band, table, band}
            );
        }
    }

    /**
     * Moves an existing database into its own id band. Nothing about the schema changes — see
     * {@link #claimIdBand} for what this buys and what it deliberately leaves alone.
     */
    public static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            claimIdBand(database);
        }
    };

    /**
     * Adds the tombstone table that makes deleting a cloud row safe.
     *
     * Before it, the sync had to read "row on the server, not on this device" as a deletion, which
     * is also what a row created on a second device looks like — so the first device could delete
     * the user's new data out of the cloud before it had ever pulled it. A deletion is now a fact
     * this device wrote down rather than something inferred from absence. Existing databases start
     * with no tombstones, which is exactly right: nothing they are missing was provably deleted, so
     * nothing gets removed from the cloud on the strength of a guess.
     */
    public static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `deleted_records` (" +
                "`tableName` TEXT NOT NULL, " +
                "`rowId` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`tableName`, `rowId`))"
            );
            // Lets an investment trade say which ledger row moved its cash, so editing or deleting
            // the trade can undo that exact movement. Existing trades keep 0, which the repository
            // reads as "no link" and handles with an offsetting adjustment instead.
            database.execSQL(
                "ALTER TABLE investment_transactions ADD COLUMN ledgerTransactionId INTEGER NOT NULL DEFAULT 0"
            );
        }
    };

    public static FinanceDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (FinanceDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        FinanceDatabase.class,
                        "finance_database"
                    )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21)
                    // The migration chain starts at 2: there is no record of what version 1 looked
                    // like, so no 1 -> 2 migration can be written. Room's answer to a missing
                    // migration is to throw on open, which for a version-1 database means an app
                    // that crashes on every launch and can only be fixed by clearing its data —
                    // the same loss, arrived at through a crash loop. Naming version 1 explicitly
                    // keeps that recovery automatic without weakening anything above it: every
                    // other version still has to migrate or fail loudly.
                    .fallbackToDestructiveMigrationFrom(true, 1)
                    .addCallback(new RoomDatabase.Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
                            // Before the seeder runs, so even the seed rows are minted inside
                            // this database's own id band.
                            claimIdBand(db);
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
