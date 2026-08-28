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
- **Interactive Approval Queue**: Categorize pending SMS transactions into expenses, income, or IOU debt settlements.
- **Split IOU Money Settlement**: Settle borrowed/lent debts directly from incoming SMS logs with custom split payment amounts.
- **Matched Pairs**: A debit and the credit that cancels it are detected and resolved together — a self transfer between your own accounts becomes one transfer, and money that came back (refund, failed payment, unallotted IPO block) cancels out instead of sitting in your spending total.

### 📊 **Interactive Reports & Financial Insights**
- **Dynamic Period Filtering**: Switch between *This Cycle*, *Last Month*, *Last 3 Months*, *Year to Date*, or *Custom Date Ranges* via Material 3 DateRangePicker.
- **Horizontal Swipe Navigation**: Swipe left or right across charts and trends to seamlessly navigate between past and present financial cycles.
- **Single-Day Deep Inspection**: Tap any bar on the Daily Spending Trend graph to isolate and filter page-wide transactions for that specific day.
- **Visual Category Breakdown**: Animated donut chart with proportional category shares and cash flow forecasts.

### 🔄 **Bidirectional Supabase Cloud Synchronization**
- **Offline-First Architecture**: Powered by Room Database for fast, zero-latency local operations.
- **Automatic Cloud Restoration**: Two-way sync pulls cloud data down to new devices or updated OS installations before pushing local changes, protecting your data against device resets.
- **Silent Background Sync**: Background WorkManager execution with network error resilience and zero intrusive popups.

### 🎯 **Envelope Budgeting & Savings Goals**
- **Zero-Based Envelope Budgeting**: Assign income to custom category envelopes with rollover capabilities and drag-and-drop reordering.
- **Envelope Money Transfers**: Seamlessly shift funds between budget envelopes when spending priorities change.
- **Savings Goals**: Track target amounts, completion dates, and visual progress rings with celebration effects.

### 🔒 **100% Privacy & Security**
- **Zero Bank Credentials**: No Plaid or open-banking linking required.
- **On-Device OCR**: Scan physical receipt images locally using ML Kit with zero cloud uploads.

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
   Create a `local.properties` file in the project root directory and add your Supabase URL and Anon Key:
   ```properties
   SUPABASE_URL=https://your-supabase-project.supabase.co
   SUPABASE_KEY=your-supabase-anon-key
   ```

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
