# 💰 FinanceManager (PocketPal)

> **A minimalist, privacy-first personal finance, expense tracker, and budget manager built natively for Android.**

FinanceManager combines elegant iOS-inspired minimalism with powerful envelope budgeting, automatic bank SMS parsing, interactive financial reporting, and bidirectional cloud synchronization.

---

## ✨ Features

### 📱 **Minimalist iOS Aesthetic & Dynamic Animations**
- **iOS Design System**: Custom `iOSCard`, `iOSButton`, `iOSToggle`, and `iOSNavigationBar` components featuring smooth corner radii, neutral palettes, and crisp typography.
- **Fluid Micro-Interactions**: Bouncy 120Hz spring physics, animated progress rings, sweep donut charts, and pulsing golden celebration glows for completed savings goals.
- **Dark & Light Theme**: Native adaptivity with theme toggles and system-level dark mode support.

### 💬 **Smart SMS Transaction Parsing & IOU Settlement**
- **Automatic Bank SMS Detection**: On-device regex-based parsing for bank debit/credit SMS alerts without uploading message contents.
- **Interactive Approval Queue**: Categorize pending SMS transactions into expenses, income, self transfers, or IOU debt settlements.
- **Split IOU Money Settlement**: Settle borrowed/lent debts directly from incoming SMS logs with custom split payment amounts.
- **Matched Pairs**: A debit and the credit that cancels it are detected and resolved together — a self transfer between your own accounts becomes one transfer, and money that came back (refund, failed payment, unallotted IPO block) cancels out instead of sitting in your spending total.
- **One-Sided Self Transfers**: When only one bank bothers to text — the receiving account stays silent, or its alert never arrives — pick **Transfer** while reviewing the single alert and name the other account yourself. It logs the same single transfer a matched pair would: both balances move, no envelope is charged, and your net worth is untouched.

### 📊 **Interactive Reports & Financial Insights**
- **Dynamic Period Filtering**: Switch between *This Cycle*, *Last Month*, *Last 3 Months*, *Year to Date*, or *Custom Date Ranges* via Material 3 DateRangePicker.
- **Horizontal Swipe Navigation**: Swipe left or right across charts and trends to seamlessly navigate between past and present financial cycles.
- **Single-Day Deep Inspection**: Tap any bar on the Daily Spending Trend graph to isolate and filter page-wide transactions for that specific day.
- **Visual Category Breakdown**: Animated donut chart with proportional category shares and cash flow forecasts.

### 🔄 **Bidirectional Supabase Cloud Synchronization**
- **Offline-First Architecture**: Powered by Room Database for fast, zero-latency local operations.
- **Automatic Cloud Restoration**: The first sync on a new device pulls cloud data down before pushing anything up, so a reinstall recovers your real accounts and envelopes instead of keeping the seed ones.
- **Two-Way Merge**: Every row carries a last-changed timestamp, so each sync compares the two sides per row: the newer copy wins and edits travel in both directions.
- **Deletions Are Recorded, Never Inferred**: Deleting something writes a local tombstone, and that record is the only thing that can remove a row from the cloud. A row the server has and this device does not is treated as one created elsewhere and pulled down — so a transaction entered on your second phone can never be deleted by your first one before it arrives.
- **Your Messages Stay Put**: Only the parsed fields of a bank alert are synced. The original message text is never uploaded.
- **Off Whenever You Want**: A switch in Settings stops sync entirely; nothing leaves the device while it is off.
- **Silent Background Sync**: Background WorkManager execution with network error resilience and zero intrusive popups.

### 🎯 **Envelope Budgeting & Savings Goals**
- **Zero-Based Envelope Budgeting**: Assign income to custom category envelopes with rollover capabilities and drag-and-drop reordering.
- **Envelope Money Transfers**: Seamlessly shift funds between budget envelopes when spending priorities change.
- **Savings Goals**: Track target amounts, completion dates, and visual progress rings with celebration effects.

### 🔒 **100% Privacy & Security**
- **Zero Bank Credentials**: No Plaid or open-banking linking required.
- **On-Device OCR**: Scan physical receipt images locally using ML Kit with zero cloud uploads.
- **App Lock**: Biometrics or a PBKDF2-hashed PIN, re-armed whenever the app has been in the background past your chosen grace period, with balances kept out of screenshots and the recents preview.
- **Privacy Mode Everywhere**: One toggle masks every amount the app renders — including budget and bill notifications on your lock screen. Report exports wait until you turn it off rather than quietly printing real numbers.
- **Not in Google's Backup**: The database is left out of cloud backup, because it is unencrypted and holds every balance and every bank message you have parsed. Device-to-device transfer still carries it to a new phone.

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin (100%) |
| **UI Framework** | Jetpack Compose, Material 3, Custom iOS Design System |
| **Architecture** | MVVM, Repository Pattern, Kotlin Coroutines & StateFlow |
| **Local Database** | Room Database (SQLite) with custom migrations |
| **Backend & Cloud** | Supabase (Postgrest & GoTrue Auth) |
| **Background Processing** | Android WorkManager |
| **Machine Learning** | Google ML Kit Text Recognition (OCR) |
| **Build System** | Gradle (Kotlin DSL `.gradle.kts`) |

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1) or newer
- **JDK**: Version 17
- **Android SDK**: API Level 34+ (Android 14 / 15)
- **Target Device**: Android 8.0 (API 26) or higher

### Setup & Installation

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/prashantjagtap2002/Finance-manager.git
   cd Finance-manager
   ```

2. **Configure Supabase Credentials** (Optional for Cloud Sync):
   Add your Supabase URL and publishable key to `local.properties` in the project root. The keys
   are read by `app/build.gradle.kts`, so they must be spelled exactly like this:
   ```properties
   supabase.url=https://your-supabase-project.supabase.co
   supabase.key=your-supabase-anon-key
   ```
   Leave them out and the app simply runs without cloud sync.

   Two columns are optional but unlock the full behaviour. Without them the app falls back
   automatically — it retries the upload without whichever column the table doesn't have — so
   nothing breaks either way:
   ```sql
   -- Lets a sync tell which side of a row is newer, so edits travel between devices.
   alter table accounts               add column if not exists updatedat bigint default 0;
   alter table categories             add column if not exists updatedat bigint default 0;
   alter table transactions           add column if not exists updatedat bigint default 0;
   alter table savings_goals          add column if not exists updatedat bigint default 0;
   alter table recurring_transactions add column if not exists updatedat bigint default 0;
   alter table debts                  add column if not exists updatedat bigint default 0;
   alter table sms_transactions       add column if not exists updatedat bigint default 0;

   -- Preserves how a bank alert was resolved (self transfer, refund) across a restore.
   alter table sms_transactions add column if not exists loggedtransactionid bigint default 0;
   alter table sms_transactions add column if not exists linkedsmsid bigint default 0;
   alter table sms_transactions add column if not exists resolution text default '';
   ```

   Nothing has to be added for deletions. They are recorded in a local `deleted_records` table
   and applied to the cloud as ordinary `DELETE`s, so the tombstones themselves never leave the
   device — which also means a deletion made on one device reaches another only by way of the
   cloud row disappearing, never by the second device guessing.

   **Row Level Security is not optional.** The key above ships inside the APK and can be read
   out of any copy of it, so it identifies the *app*, not the user. The only thing separating
   one user's rows from another's is a policy on the table. Without the SQL below, anyone who
   pulls that key out of the APK can read and write every row in your project — every balance,
   every transaction, everybody's. The app filters by `user_id` on its own requests, but a
   client-side filter is a convenience, never a boundary.

   ```sql
   -- Every synced table, locked to the user who owns the row.
   do $$
   declare t text;
   begin
     foreach t in array array[
       'accounts', 'categories', 'transactions', 'savings_goals',
       'recurring_transactions', 'debts', 'sms_transactions'
     ] loop
       execute format('alter table %I enable row level security', t);
       execute format('drop policy if exists "own rows" on %I', t);
       execute format($f$
         create policy "own rows" on %I
           for all
           to authenticated
           using (user_id = (select auth.uid()))
           with check (user_id = (select auth.uid()))
       $f$, t);
     end loop;
   end $$;
   ```

   Two things to confirm afterwards, because a policy that never matches is as quiet as no
   policy at all:

   - Each table needs a `user_id uuid` column. The app sends the signed-in user id in it, and
     the policy compares against exactly that.
   - Anonymous sign-ins must be enabled in **Authentication → Providers**; the app creates an
     anonymous user on first sync and every row is written under it.

   To check the lock actually holds, query a table from the SQL editor with the anon role — it
   should come back empty rather than with somebody's rows:

   ```sql
   set local role anon;
   select count(*) from transactions;  -- expect 0
   reset role;
   ```

   **One currency, on purpose.** The home currency in Settings is what every amount in the app
   is rendered in, and totals add rows together on the strength of that. The stored per-row
   currency codes are re-stamped to match it once, which relabels rather than converts — no
   amount is touched. A row that still carries a different code (a backup brought over from a
   phone whose home currency was something else) is reported beside the total as its own figure
   rather than added into it, because ₹100,000 + $1,000 is not 101,000 of anything.

   **What the backup covers.** Settings → Export Backup writes a JSON file holding every table
   the app owns: accounts, envelopes, transactions, recurring templates, goals, IOUs, investment
   holdings and their trades, learned merchant rules, and shared-expense groups. Cloud sync
   deliberately covers less — the seven tables above — so the JSON backup is the complete one,
   and the Room database itself is kept out of Google cloud backup because it is unencrypted.

3. **Build the Project**:
   Open the project in Android Studio and run Gradle Sync, or build from terminal:
   ```bash
   ./gradlew assembleDebug
   ```

4. **Run on Device or Emulator**:
   Select your connected Android device (e.g. Pixel 9a) and press `Shift + F10` in Android Studio.

---

## 📱 Screenshots & Previews

| Dashboard | Reports & Insights | Envelope Budgeting | SMS Approval & IOU |
|---|---|---|---|
| *Clean account cards & recent transactions* | *Interactive daily trend & category donut* | *Drag & drop budget envelopes* | *Smart SMS parsing & debt settlement* |

---

## 📄 License

```text
Copyright (c) 2026 Prashant Jagtap

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction parties.
```
