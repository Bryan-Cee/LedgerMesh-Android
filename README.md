# LedgerMesh

A fully offline Android personal finance app that aggregates transactions from SMS messages, CSV files, and PDF statements into a single encrypted ledger. No internet permission — your financial data never leaves your device.

## Features

- **Multi-source ingestion** — Import transactions from bank SMS messages, CSV exports, and PDF statements
- **Smart reconciliation** — CRDT-based engine automatically detects and merges duplicate transactions across sources, with confidence scoring
- **Encrypted storage** — All data stored in a SQLCipher-encrypted Room database
- **Dashboard** — At-a-glance balance, spending by category, and a review banner for low-confidence matches
- **Ledger** — Full transaction list with search, filters, and smart date grouping (Today / This Week / Earlier)
- **Analytics** — 6-month spending trends and category breakdowns
- **Transaction editing** — Split, merge, and edit transactions with a full operations audit log
- **Background SMS monitoring** — Optional WorkManager-based periodic scan for new bank SMS messages
- **Sender profiles** — Configurable regex-based parsing profiles for different bank SMS formats

## Tech Stack

| Layer | Libraries |
|-------|-----------|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| DI | Hilt |
| Database | Room + SQLCipher |
| Background work | WorkManager |
| Ingestion | OpenCSV, PdfBox Android |
| Serialization | Kotlinx Serialization |

- **Language:** Kotlin 2.0.21
- **Min SDK:** 30 (Android 11)
- **Target/Compile SDK:** 36
- **Build:** AGP 9.0.1, KSP

## Architecture

```
com.example.ledgermesh/
├── data/
│   ├── db/entity/      # Room entities (Observation, Aggregate, OpsLog, etc.)
│   ├── db/dao/          # Data access objects
│   └── repository/      # Repository layer
├── domain/
│   ├── model/           # Enums (SourceType, TransactionDirection, etc.)
│   └── usecase/         # Business logic (ReconciliationEngine, TransactionOps)
├── ingestion/
│   ├── csv/             # CSV parsing and import
│   └── sms/             # SMS parsing, reader, sender profiles
├── ui/
│   ├── dashboard/       # Home screen with balance and categories
│   ├── ledger/          # Transaction list with search and filters
│   ├── import_center/   # Import flow for SMS, PDF, and CSV
│   ├── analytics/       # Trends and category charts
│   ├── detail/          # Transaction detail with edit/split/merge
│   ├── review/          # Review queue for low-confidence matches
│   ├── settings/        # App settings and sender profiles
│   └── navigation/      # NavHost and route definitions
└── di/                  # Hilt modules
```

## Building

Open the project in Android Studio or build from the command line:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

## Permissions

| Permission | Purpose |
|------------|---------|
| `READ_SMS` | Read bank transaction SMS (requested at runtime) |
| `INTERNET` | **Explicitly removed** — the app is offline-only |

## License

All rights reserved.
