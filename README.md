# Satchel

**Crypto Bank Platform API**

Satchel is the backend API for a personal crypto banking platform. Users deposit any supported asset to earn interest and can borrow any supported asset against collateral they already hold. The system manages segregated user vaults, an omnibus treasury, internal ledger accounting, and per-asset interest accrual.

## Core Concepts

### Lending & Borrowing
- **Depositors** send any supported asset and earn interest on that asset.
- **Borrowers** lock a supported asset as collateral and receive a loan in a supported asset.
- Interest rates, loan-to-value (LTV), the platform’s share of deposit interest (fin margin), and sweep minimums are configured per asset.
- Loans are purely ledger-based (no on-chain loan contracts). Granting or repaying a loan only updates the internal ledger.

### Treasury & Custody Model
Every user (lender or borrower) is assigned a **vault** (a group of wallets). Users deposit any supported asset into their personal vault. The internal ledger credits the corresponding account balances.

Periodically, deposits are **swept** from user vaults into a single **omnibus vault** that concentrates all platform assets. An asset is swept once its vault balance exceeds that asset’s configured sweep minimum. When the vault’s gas token (the asset’s base asset) is low, the platform fuels the vault before sweeping.

Withdrawals of any supported asset are paid directly from the omnibus vault to the user’s external wallet, with a corresponding debit on the internal ledger.

### Loan-to-Value
When a loan is opened, the platform sizes it from the collateral asset’s configured LTV: `ltv × collateral amount`, converted into the borrowed asset at the current price. LTV comes from configuration for that collateral asset. It applies at origination.

### Automated Background Tasks
The platform accrues interest on deposits and outstanding loans on `satchel.financial.accrual-period`:

- Deposit interest for an asset is that asset’s annual rate multiplied by `(1 − fin margin)`.
- Loan interest uses the borrowed asset’s annual rate.

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

# Assets. Every id in `supported` must appear in each map.
# Map keys are quoted so hyphenated ids such as ETH-AETH_SEPOLIA stay literal.
# accrual-period is milliseconds.
satchel.financial.accrual-period=60000
satchel.financial.assets.supported=ETH_TEST5,USDC_ETH_TEST5_AN74,AVAXTEST,ETH-AETH_SEPOLIA,AMOY_POLYGON_TEST,BNB_TEST
satchel.financial.assets.interest-rate={'ETH_TEST5':0.05,'USDC_ETH_TEST5_AN74':0.05,'AVAXTEST':0.05,'ETH-AETH_SEPOLIA':0.05,'AMOY_POLYGON_TEST':0.05,'BNB_TEST':0.05}
satchel.financial.assets.ltv={'ETH_TEST5':0.8,'USDC_ETH_TEST5_AN74':0.8,'AVAXTEST':0.8,'ETH-AETH_SEPOLIA':0.8,'AMOY_POLYGON_TEST':0.8,'BNB_TEST':0.8}
satchel.financial.assets.fin-margin={'ETH_TEST5':0.1,'USDC_ETH_TEST5_AN74':0.1,'AVAXTEST':0.1,'ETH-AETH_SEPOLIA':0.1,'AMOY_POLYGON_TEST':0.1,'BNB_TEST':0.1}
satchel.financial.assets.deposit-sweep-minimum={'ETH_TEST5':0.01,'USDC_ETH_TEST5_AN74':0.01,'AVAXTEST':0.01,'ETH-AETH_SEPOLIA':0.01,'AMOY_POLYGON_TEST':0.01,'BNB_TEST':0.01}
satchel.financial.assets.base-asset={'ETH_TEST5':'ETH_TEST5','USDC_ETH_TEST5_AN74':'ETH_TEST5','AVAXTEST':'AVAXTEST','ETH-AETH_SEPOLIA':'ETH-AETH_SEPOLIA','AMOY_POLYGON_TEST':'AMOY_POLYGON_TEST','BNB_TEST':'BNB_TEST'}

# Fireblocks / Custodian (use testnet values)
satchel.custodian.api.key=...
satchel.custodian.api.secret=...
satchel.custodian.account.omnibus.id=...
satchel.custodian.account.omnibus.address=...
# ... other vault IDs and asset identifiers

# JWT
satchel.jwt.secret=...
satchel.jwt.expiration-time-ms=3600000
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

The API accepts the Fireblocks asset ids in `satchel.financial.assets.supported`. This is the set configured for the testnet workspace:

| Asset ID              | Description   | Network                    |
|-----------------------|---------------|----------------------------|
| `ETH_TEST5`           | Native ETH    | Ethereum Sepolia           |
| `USDC_ETH_TEST5_AN74` | USDC          | Ethereum Sepolia           |
| `AVAXTEST`            | Native AVAX   | Avalanche Fuji             |
| `ETH-AETH_SEPOLIA`    | Native ETH    | Arbitrum Sepolia           |
| `AMOY_POLYGON_TEST`   | Native POL    | Polygon Amoy               |
| `BNB_TEST`            | Native BNB    | BNB Smart Chain testnet    |

`USDC_ETH_TEST5_AN74` uses `ETH_TEST5` as its gas token (`base-asset`). Each of the other assets uses itself.

### Adding an asset

1. Enable the asset in the Fireblocks workspace.
2. Append its Fireblocks asset id to `satchel.financial.assets.supported`.
3. Add a quoted entry for that id to each of `interest-rate`, `ltv`, `fin-margin`, `deposit-sweep-minimum`, and `base-asset`.
4. Add the id to `ConversionRateService.CURRENCY_MAPPING` with its CoinGecko id, so loans can price it. That map lives in code.
5. Restart. Wallet creation, withdrawals, and loan requests all validate against the supported list.



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



### 2. How to Deposit

Deposits are credit-on-arrival: you create a deposit address belonging to your user vault, send testnet funds to it, and Fireblocks webhooks notify the platform so the internal ledger is credited.

**Step 1 – Create a deposit wallet**

The response is the created wallet, with the same fields as one item from `GET /api/v1/wallets` (`id`, `address`, `assetId`, `balance`, `lockedBalance`, `availableBalance`, `accruedInterest`, `openedAt`, `daysSinceOpened`). Use `address` as the deposit destination.

`assetId` is any id from the supported-assets table. The example uses Sepolia USDC.

```bash
curl -X POST http://localhost:8080/api/v1/wallets \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "assetId": "USDC_ETH_TEST5_AN74"
  }'
```

**Step 2 – Retrieve your deposit addresses**

```bash
curl http://localhost:8080/api/v1/wallets \
  -H "Authorization: Bearer YOUR_JWT"
```

Note the address returned for the desired asset.

**Get one deposit wallet**

```bash
curl http://localhost:8080/api/v1/wallets/42 \
  -H "Authorization: Bearer YOUR_JWT"
```

The response is one wallet in the same shape as a list item. A missing id, or an id that belongs to another user, is HTTP 404 with error `deposit_wallet_not_found`.

**Step 3 – Send testnet funds**

1. Obtain the testnet asset that matches the deposit wallet’s `assetId` from a public faucet or testnet bridge for that network.
2. Send it from any external wallet (MetaMask, etc.) to the deposit address you retrieved.

**Step 4 – Wait for confirmation**

Fireblocks will emit webhooks. Once the transaction reaches the configured completed/confirmed status, the platform credits your internal ledger balance. You can then use the funds for loans or withdrawals.

> Tip: In development the sweep from user vault → omnibus happens after a configurable delay (`satchel.custodian.deposit.sweep.delay`).



### 3. How to Withdraw

Withdrawals debit your internal ledger and send the asset from the omnibus vault to an external address you control. `assetId` is any id from the supported-assets table.

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

The platform:
1. Checks that you have sufficient ledger balance.
2. Creates a withdrawal transaction from the omnibus vault via Fireblocks.
3. Debits your internal account once the transaction is confirmed.

Monitor the status through the Fireblocks console or by listing your transfers.

**List your funds transfers**

```bash
curl http://localhost:8080/api/v1/funds_transfer \
  -H "Authorization: Bearer YOUR_JWT"
```

The response is the current user's transfers, newest first. Each item has `id`, `amount` (positive for a deposit, negative for a withdrawal), `timestamp`, `transactionId`, `counterpartyAddress`, `isCompleted`, `walletId`, and `assetId`. A pending withdrawal has `isCompleted` false until the webhook confirms it. An empty history is `[]`.


### 4. How to Request a Loan

You need a deposit wallet for the collateral asset and for the asset you want to borrow, and the collateral wallet must hold enough available balance (see Deposit tutorial). Both asset ids must be in the supported list. Any pair is valid. The example borrows Sepolia USDC against Sepolia ETH.

**Request a loan**

```bash
curl -X POST http://localhost:8080/api/v1/loans \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "loanAssetId": "USDC_ETH_TEST5_AN74",
    "collateralAmount": 1.5,
    "collateralAssetId": "ETH_TEST5"
  }'
```

Field meanings:
- `loanAssetId` – the supported asset you want to borrow.
- `collateralAssetId` – the supported asset you are locking.
- `collateralAmount` – amount of that collateral you are locking.

The platform looks up LTV from `satchel.financial.assets.ltv` for the collateral asset (`0.8` for every asset currently configured). Loan size is `ltv × collateralAmount`, converted from the collateral asset into the loan asset.

On success the platform:
- Locks the specified collateral on the ledger.
- Credits the calculated loan amount to your deposit wallet for the loan asset.
- Records an active `Loan`.
- Returns the created loan, in the same shape as a repayment response (id, assets, amounts, outstanding amount, interest rate, status, accrued interest, and granted time).

No on-chain loan contract is created; everything is ledger-driven.

**Get a loan**

```bash
curl http://localhost:8080/api/v1/loans/42 \
  -H "Authorization: Bearer YOUR_JWT"
```

The response is one loan in the same shape as a list item. A missing id, or an id that belongs to another user, is HTTP 404 with error `loan_not_found`.

**Repay a loan (partial or full)**

```bash
curl -X PUT http://localhost:8080/api/v1/loans \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "loanId": 42,
    "amount": 100.00
  }'
```

The response is the updated loan, including the remaining outstanding amount and the new status (`ACTIVE` or `PAID`). Repayment is debited from your deposit wallet for the loan’s asset.

## API Overview

| Method | Endpoint                              | Description                          | Auth required |
|--------|---------------------------------------|--------------------------------------|---------------|
| POST   | `/api/v1/auth/signup`                 | Register a new user                  | No            |
| GET    | `/api/v1/auth/email-verification`     | Verify email with token              | No            |
| POST   | `/api/v1/auth/login`                  | Obtain JWT                           | No            |
| GET    | `/api/v1/wallets`                     | List user’s deposit wallets          | Yes           |
| GET    | `/api/v1/wallets/{id}`                | Get one of the current user's deposit wallets | Yes    |
| POST   | `/api/v1/wallets`                     | Create a deposit wallet and return it | Yes           |
| GET    | `/api/v1/funds_transfer`              | List the current user's transfers    | Yes           |
| POST   | `/api/v1/funds_transfer/withdrawal`   | Initiate a withdrawal                | Yes           |
| GET    | `/api/v1/loans`                       | List the current user's loans        | Yes           |
| GET    | `/api/v1/loans/{id}`                  | Get one of the current user's loans  | Yes           |
| POST   | `/api/v1/loans`                       | Request a new loan and return it     | Yes           |
| PUT    | `/api/v1/loans`                       | Repay (part of) a loan               | Yes           |
| POST   | `/api/v1/funds_transfer/funds_transfer` | Fireblocks webhook receiver        | (webhook)     |

## Security Notes

- All sensitive endpoints require a valid JWT.
- Fireblocks webhook authenticity should be verified (signature validation) in production.
- Private keys and secrets must never be committed.
- Rate limiting and additional input sanitization are recommended before public exposure.

## License

This is a personal project. All rights reserved unless otherwise stated.
