# 🚀 High-Throughput Transaction Processing Engine

A core trading desk system component designed to process high volumes of trade transactions efficiently, validate incoming trades, manage portfolio states, and persist data seamlessly.

---

# 📖 What is this Project?

The **High-Throughput Transaction Processing Engine** is built using **Java 17** and simulates an enterprise-grade trading desk backend processing system.

The engine:

- Loads daily trade transactions from a file  
- Validates incoming trades  
- Processes valid trades concurrently  
- Updates an in-memory portfolio state  
- Persists trading data into an embedded H2 relational database  
- Generates detailed execution reports using Java Streams  

This project demonstrates:

- Multithreading & Concurrency
- Thread-safe data structures
- JDBC database interaction
- Stream API aggregation
- Clean modular architecture

---

# ✨ Key Features

## 🔹 Concurrent Trade Processing
- Uses `ExecutorService` and thread pools  
- Enables high-throughput parallel trade execution  

## 🔹 In-Memory Portfolio Management
- Uses `ConcurrentHashMap`  
- Ensures thread-safe real-time updates  
- Maintains accurate positions per account  

## 🔹 Relational Database Persistence
- Persists processed trades into embedded H2 Database
- Uses JDBC for database interaction  
- Ensures durability and auditability  

## 🔹 Strict Trade Validation
- Rejects invalid trades:
  - Negative quantities  
  - Negative prices  
  - Malformed records  

## 🔹 Thread-Safe Data Integrity
- Uses synchronization and `AtomicInteger`
- Prevents race conditions  

## 🔹 Stream-Based Reporting
- Uses Java Streams API
- Aggregates trade results
- Generates end-of-day summary reports  

---

# ⚙️ Workflow / Processing Pipeline

The engine follows a structured 5-step pipeline:

## 1️⃣ Load Trades
- Reads trade requests from `trades.csv`
- Converts each record into a `Trade` object

## 2️⃣ Validate & Process Concurrently
- Each trade is validated
- Valid trades are submitted to an `ExecutorService`
- Multiple threads process trades in parallel

## 3️⃣ Database Persistence
- Valid trades are inserted into the embedded H2 database
- Ensures long-term storage and audit trail

## 4️⃣ Update Portfolio State
- Updates in-memory portfolio tracker
- Maintains:
  - Account
  - Symbol
  - Quantity
- Uses thread-safe structures for concurrency safety

## 5️⃣ Generate Reports
- After all trades complete:
  - Aggregates processed trade data
  - Displays execution summary
  - Prints final portfolio positions

---

# 🛠️ Tech Stack

- Java 17
- Maven
- H2 Embedded Database
- JDBC
- SLF4J + Logback
- Java Streams API
- ExecutorService (Multithreading)

---

# 🚀 How to Build and Run

## 📌 Prerequisites

- Java 17+
- Maven 3.x
- PowerShell (for automation scripts)

---

# 🔨 Building the Project

## Option 1: Using Build Script

```powershell
.\build.ps1
```

## Option 2: Using Maven Directly

```bash
mvn clean package -DskipTests
```

This generates an executable uber-jar at:

```
target/transaction-engine-jar-with-dependencies.jar
```

---

# ▶️ Running the Engine

## Option 1: Using Run Script

```powershell
.\run.ps1
```

## Option 2: Using Java Command

```bash
java -jar target/transaction-engine-jar-with-dependencies.jar trades.csv
```

---

# 📊 Logging

Logging is managed using:

- SLF4J
- Logback

Logs are:
- Printed to console
- Stored inside the `logs/` directory

Configuration file:
```
src/main/resources/logback.xml
```

---

# 🗂 Project Structure

```
Transaction_Engine/
│
├── pom.xml
├── build.ps1
├── run.ps1
├── setup_maven.ps1
├── trades.csv
├── trading_engine_db.mv.db
├── logs/
├── src/
└── target/
```

---

# 📂 Source Code Structure

```
src/main/java/com/tradingdesk/
```

## 📌 engine/
- `TransactionEngineMain.java`  
  - Application entry point  
  - Initializes components  
  - Loads CSV  
  - Starts thread pool  
  - Triggers report generation  

## 📌 db/
- `DatabaseManager.java`  
  - Manages H2 database connection  
  - Creates trade table  
  - Inserts processed trades via JDBC  

## 📌 loader/
- `TradeFileLoader.java`  
  - Reads `trades.csv`  
  - Parses each line  
  - Converts data into `Trade` objects  

## 📌 model/
- `Trade.java` – Represents a trade  
- `Position.java` – Represents holdings  
- `Account.java` – Represents trading account  

## 📌 portfolio/
- `PortfolioStateManager.java`  
  - Maintains in-memory portfolio  
  - Uses `ConcurrentHashMap`  
  - Thread-safe updates  

## 📌 processor/
- `TradeProcessor.java`  
  - Core execution logic  
  - Uses `ExecutorService`  
  - Handles validation, persistence, portfolio updates  

## 📌 report/
- `ReportGenerator.java`  
  - Generates final execution report  
  - Uses Java Streams  

## 📌 validation/
- `TradeValidator.java`  
  - Applies business validation rules  
  - Ensures positive quantity and price  

---

# 🎯 What This Project Demonstrates

- High-throughput concurrent processing  
- Thread safety in multithreaded environments  
- ACID-style persistence  
- Clean modular architecture  
- Real-world trading system simulation  
- Enterprise-level Java backend design  
