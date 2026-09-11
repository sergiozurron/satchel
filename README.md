# Satchel

**Crypto Bank Platform API**

Satchel is the backend API for a personal crypto banking platform. Users deposit USDC to earn interest and borrow USDC against Ethereum collateral. The system manages segregated user vaults, an omnibus treasury, internal ledger accounting, interest accrual, and automated LTV-based liquidations.

## Core Concepts

### Lending & Borrowing
- **Depositors** send USDC and earn interest.
- **Borrowers** lock Ethereum as collateral and receive USDC loans.
- Interest paid by borrowers is distributed to depositors.
- Loans are purely ledger-based (no on-chain loan contracts). Granting or repaying a loan only updates the internal ledger.

### Treasury & Custody Model
Every user (lender or borrower) is assigned a **vault** (a group of wallets). Users deposit USDC or ETH into their personal vault. The internal ledger credits the corresponding account balances.

Periodically, deposits are **swept** from user vaults into a single **omnibus vault** that concentrates all platform assets.

Withdrawals (USDC or ETH) are paid directly from the omnibus vault to the user’s external wallet, with a corresponding debit on the internal ledger.

### Liquidation
If the value of a borrower’s Ethereum collateral falls below the configured Loan-to-Value (LTV) ratio, the collateral is liquidated to protect the principal.

### Automated Background Tasks
The platform periodically:
- Accrues interest on deposits and outstanding loans
- Checks LTV health of active loans and triggers liquidations when necessary

### Deposit Handling
Crypto deposits are processed via **Fireblocks** webhooks (`transaction.created`, `transaction.updated`, `vault_account.asset.balance_updated`).

## Tech Stack

| Component          | Technology                          |
|--------------------|-------------------------------------|
| Language           | Java 17                             |
| Framework          | Spring Boot 3.5.4                   |
| Persistence        | Spring Data JPA + PostgreSQL        |
| Security           | Spring Security + JWT               |
| Custody            | Fireblocks SDK                      |
| Mapping            | MapStruct                           |
| Utilities          | Lombok, Bean Validation             |
| Build              | Maven                               |

## Project Structure

```
src/main/java/com/jejo/satchel/
├── config/          # Security, JWT, Fireblocks, scheduling configuration
├── controller/      # REST endpoints (Auth, Wallet, Loan, FundsTransfer)
├── dto/             # Request/response DTOs
├── exception/       # Custom exceptions + GlobalExceptionHandler
├── mapper/          # MapStruct mappers
├── middleware/      # Filters / interceptors
├── model/           # JPA entities (User, Loan, DepositWallet, FundsTransfer…)
├── repository/      # Spring Data repositories
├── service/         # Business logic
│   ├── AccountService
│   ├── AssetCustodianService
│   ├── AssetPriceService
│   ├── AuthService
│   ├── DepositInterestService
│   ├── LoanService
│   ├── MailService
│   └── WalletService
├── util/
└── validator/
```

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+
- PostgreSQL
- Fireblocks account (API key + private key) configured for testnet
- (Optional) Mailtrap / SendGrid credentials for email verification

### Configuration

Edit `src/main/resources/application.properties`. Important settings:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/satchel
spring.datasource.username=postgres
spring.datasource.password=your_password

# Loan parameters
loan.ltv=0.9

# Interest
financial.interest.apy=0.05
financial.interest.accrual-period=30

# Fireblocks / Custodian (use testnet values)
custodian.api.key=...
custodian.api.secret=...
custodian.account.omnibus.id=...
custodian.account.omnibus.address=...
# ... other vault IDs and asset identifiers

# JWT
jwt.secret=...
jwt.expiration-time-ms=3600000
```

**Important:** Never commit real API keys or private keys. Prefer environment variables or a secrets manager in production.

### Running the Application

```bash
./mvnw spring-boot:run
```

Or build and run the JAR:

```bash
./mvnw clean package
java -jar target/satchel-0.0.1-SNAPSHOT.jar
```

The API starts on `http://localhost:8080` by default.

### Database

By default the application uses `spring.jpa.hibernate.ddl-auto=create` (convenient for development). Switch to `validate` or `none` for production and manage schema with Flyway/Liquibase.

## Supported Testnet Assets

| Asset ID              | Description                  | Network   |
|-----------------------|------------------------------|-----------|
| `USDC_ETH_TEST5_AN74` | USDC on Ethereum Sepolia     | Sepolia   |
| `ETH_TEST5`           | Native ETH on Sepolia        | Sepolia   |

These are the only assets currently accepted by the API validation rules.



## Tutorials (Testnet)

All examples assume the API is running at `http://localhost:8080`.  
Replace placeholders (`YOUR_JWT`, addresses, amounts, etc.) with real values.

### 1. Create an Account & Authenticate

**Sign up**

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Alice",
    "lastName": "Tester",
    "email": "alice@example.com",
    "password": "SecurePass123!"
  }'
```

Check your email for the verification link, then verify:

```bash
curl "http://localhost:8080/api/v1/auth/email-verification?token=THE_TOKEN_FROM_EMAIL"
```

**Log in** (returns a JWT)

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "password": "SecurePass123!"
  }'
```

Use the returned `token` in the `Authorization: Bearer <token>` header for all subsequent authenticated requests.



### 2. How to Deposit (USDC or ETH)

Deposits are credit-on-arrival: you create a deposit address belonging to your user vault, send testnet funds to it, and Fireblocks webhooks notify the platform so the internal ledger is credited.

**Step 1 – Create a deposit wallet**

```bash
# For USDC (Sepolia)
curl -X POST http://localhost:8080/api/v1/wallets \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "assetId": "USDC_ETH_TEST5_AN74"
  }'

# For ETH (Sepolia)
curl -X POST http://localhost:8080/api/v1/wallets \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "assetId": "ETH_TEST5"
  }'
```

**Step 2 – Retrieve your deposit addresses**

```bash
curl http://localhost:8080/api/v1/wallets \
  -H "Authorization: Bearer YOUR_JWT"
```

Note the address returned for the desired asset.

**Step 3 – Send testnet funds**

1. Obtain Sepolia ETH from a public faucet (e.g. Alchemy, Infura, or Sepolia faucet sites).
2. Obtain Sepolia USDC from a testnet faucet or bridge that supports the asset ID used by your Fireblocks workspace.
3. Send the tokens from any external wallet (MetaMask, etc.) to the deposit address you retrieved.

**Step 4 – Wait for confirmation**

Fireblocks will emit webhooks. Once the transaction reaches the configured completed/confirmed status, the platform credits your internal ledger balance. You can then use the funds for loans or withdrawals.

> Tip: In development the sweep from user vault → omnibus happens after a configurable delay (`custodian.deposit.sweep.delay`).



### 3. How to Withdraw

Withdrawals debit your internal ledger and send the assets from the omnibus vault to an external address you control.

```bash
curl -X POST http://localhost:8080/api/v1/funds_transfer/withdrawal \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "assetId": "USDC_ETH_TEST5_AN74",
    "destinationAddress": "0xYourExternalWalletAddress",
    "amount": 25.50
  }'
```

Supported `assetId` values: `USDC_ETH_TEST5_AN74` or `ETH_TEST5`.

The platform:
1. Checks that you have sufficient ledger balance.
2. Creates a withdrawal transaction from the omnibus vault via Fireblocks.
3. Debits your internal account once the transaction is confirmed.

Monitor the status through the Fireblocks console or by inspecting subsequent webhook processing.


### 4. How to Request a Loan (Borrow USDC against ETH)

You must already have ETH deposited as collateral (see Deposit tutorial).

**Request a loan**

```bash
curl -X POST http://localhost:8080/api/v1/loans \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "loanAssetId": "USDC_ETH_TEST5_AN74",
    "ltv": 0.75,
    "collateralAmount": 1.5,
    "collateralAssetId": "ETH_TEST5",
    "destinationAddress": "0xYourExternalWalletOrInternalDestination"
  }'
```

Field meanings:
- `loanAssetId` – the asset you want to borrow (currently USDC testnet).
- `ltv` – desired loan-to-value ratio (0–1). Must respect the platform maximum (default `loan.ltv=0.9`).
- `collateralAmount` – amount of ETH you are locking.
- `collateralAssetId` – must be `ETH_TEST5`.
- `destinationAddress` – where the borrowed USDC should be credited / sent.

On success the platform:
- Locks the specified collateral on the ledger.
- Credits the calculated loan amount (based on current ETH/USDC price and the requested LTV) to your account.
- Records an active `Loan` entity.

No on-chain loan contract is created; everything is ledger-driven.

**Repay a loan (partial or full)**

```bash
curl -X POST http://localhost:8080/api/v1/loans/repayment \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "loanId": 42,
    "amount": 100.00
  }'
```

The response includes the remaining outstanding amount and the new loan status (`ACTIVE`, `PAID`, etc.).

## API Overview

| Method | Endpoint                              | Description                          | Auth required |
|--------|---------------------------------------|--------------------------------------|---------------|
| POST   | `/api/v1/auth/signup`                 | Register a new user                  | No            |
| GET    | `/api/v1/auth/email-verification`     | Verify email with token              | No            |
| POST   | `/api/v1/auth/login`                  | Obtain JWT                           | No            |
| GET    | `/api/v1/wallets`                     | List user’s deposit wallets          | Yes           |
| POST   | `/api/v1/wallets`                     | Create a deposit wallet for an asset | Yes           |
| POST   | `/api/v1/funds_transfer/withdrawal`   | Initiate a withdrawal                | Yes           |
| POST   | `/api/v1/loans`                       | Request a new loan                   | Yes           |
| POST   | `/api/v1/loans/repayment`             | Repay (part of) a loan               | Yes           |
| POST   | `/api/v1/funds_transfer/funds_transfer` | Fireblocks webhook receiver        | (webhook)     |

## Security Notes

- All sensitive endpoints require a valid JWT.
- Fireblocks webhook authenticity should be verified (signature validation) in production.
- Private keys and secrets must never be committed.
- Rate limiting and additional input sanitization are recommended before public exposure.

## License

This is a personal project. All rights reserved unless otherwise stated.
