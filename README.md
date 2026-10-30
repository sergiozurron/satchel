# Satchel

Satchel is a custodial crypto-banking API that lets you deposit a supported asset to earn interest and borrow another asset against collateral you already hold.

Satchel turns a Fireblocks vault into an account. You get a ledger balance, deposit interest, and a collateralized loan through HTTP, with Fireblocks holding the keys. Loans are ledger entries. The public instance uses the Fireblocks testnet asset ids in [Core concepts](#core-concepts).

## Table of contents

- [Quickstart](#quickstart)
- [Authentication](#authentication)
- [Core concepts](#core-concepts)
- [API overview](#api-overview)
- [Error handling](#error-handling)
- [Full documentation](#full-documentation)
- [Contributing](#contributing)
- [License](#license)

## Quickstart

The public API is `https://satchel.sergiozurron.site`.

Create an account. `firstName` and `lastName` are letters only, with no spaces.

```bash
curl -X POST https://satchel.sergiozurron.site/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Alice",
    "lastName": "Tester",
    "email": "alice@example.com",
    "password": "SecurePass123!"
  }'
```

```json
{"message":"Signup successful. Please check your email for verification."}
```

Open the link in that email. It calls `GET /api/v1/auth/email-verification?token=...` on the same host and returns `{"message":"Email verification successful. You can now log in."}`.

Log in and keep the `token`:

```bash
curl -X POST https://satchel.sergiozurron.site/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"SecurePass123!"}'
```

```json
{"message":"Login successful.","token":"<jwt>"}
```

Create a Sepolia ETH deposit wallet:

```bash
curl -X POST https://satchel.sergiozurron.site/api/v1/wallets \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"assetId":"ETH_TEST5"}'
```

The response includes `address`. Send testnet ETH to that address to credit the balance. Loans, withdrawals, and errors are covered below.

## Authentication

Signup, the verification link, and login are public.

Signing up again with an email that is already verified returns HTTP 409 and `email_taken`. Signing up again with an unverified email resends the message only after 60 seconds. Inside that window the same success message is returned and no email is sent.

Login returns a bearer JWT. The server sets how long it lasts. Send it as `Authorization: Bearer <token>`.

Revoke the current token:

```bash
curl -X POST https://satchel.sergiozurron.site/api/v1/auth/logout \
  -H "Authorization: Bearer <jwt>"
```

These routes are public: `GET /api/v1`, `GET /actuator/health`, `/api/v1/auth/**`, and `POST /api/v1/funds_transfer` (the Fireblocks webhook). Every other route requires a valid token. A missing or invalid token is rejected before the controller runs.

## Core concepts

### Assets

The API accepts these Fireblocks testnet ids:

| Asset ID | Asset | Network |
| --- | --- | --- |
| `ETH_TEST5` | ETH | Ethereum Sepolia |
| `USDC_ETH_TEST5_AN74` | USDC | Ethereum Sepolia |
| `AVAXTEST` | AVAX | Avalanche Fuji |
| `ETH-AETH_SEPOLIA` | ETH | Arbitrum Sepolia |
| `AMOY_POLYGON_TEST` | POL | Polygon Amoy |
| `BNB_TEST` | BNB | BNB Smart Chain testnet |

Any other id is rejected.

### Deposit wallets

Each user has at most one wallet per asset. A wallet has `id`, `address`, `assetId`, `balance`, `lockedBalance`, `availableBalance`, `accruedInterest`, `openedAt`, and `daysSinceOpened`. Available balance is balance minus locked balance. Creating a second wallet for the same asset returns HTTP 409.

### Deposits

Create the wallet, then send the matching testnet asset to `address`. Fireblocks posts `POST /api/v1/funds_transfer`. The ledger is credited when that transfer is completed and confirmed.

### Withdrawals

A withdrawal body is `assetId`, `destinationAddress`, and `amount`. The available balance must cover the amount.

- Your own address for that asset returns HTTP 400 `self_transfer`.
- Another Satchel user's address for that asset is an immediate internal transfer: the source is debited and the destination is credited.
- Any other address is paid from the omnibus pool. The caller is debited when the webhook confirms the transfer.

The withdrawal call returns HTTP 200 and the caller’s funds transfer. An internal transfer also records a credit on the destination wallet. That credit is not in this response.

### Loans

You need a deposit wallet for the collateral asset and a deposit wallet for the asset you want to borrow. The body is `loanAssetId`, `collateralAssetId`, and `collateralAmount`.

Satchel sizes the loan from the collateral asset's origination LTV and the CoinGecko price, locks that collateral, and credits the loan asset to its wallet. On the current asset list every origination LTV is 0.8, so borrowing against 1 ETH at an ETH/USDC price of 3000 credits 2400 USDC. Origination sends the locked collateral from the omnibus vault to the Coinbase collateral address. Unlocking asks the trading service to send that amount back to the omnibus address.

`collateralAmount` stays the amount locked at origination. `currentCollateral` is what remains. Outstanding debt is `amount + accruedInterest - returnedAmount`. `isPaid` is true only when that debt is zero.

### Repayment

`POST /api/v1/loans/{id}/repayments` with `{"amount": ...}` repays part or all of a loan. The applied amount is the smaller of the request and the outstanding debt. Collateral stays locked. Repaying a loan that is already paid returns HTTP 409 `loan_not_active`.

### Unlocking collateral

`POST /api/v1/loans/{id}/collateral` with `{"amount": ...}` releases that amount of pledged collateral and asks the trading service to send it back to the omnibus address. After the release, the loan's LTV must still be at or under the collateral asset's origination LTV. A paid loan can release whatever `currentCollateral` remains. A request above that limit returns HTTP 400 `unhealthy_ltv`.

### Interest and liquidation

Deposit interest for an asset is that asset's annual rate multiplied by `(1 − fin margin)`. It accrues on the wallet balance and is credited to that balance once a day at 00:00 UTC. After the credit, `accruedInterest` is zero and later accrual compounds on the new balance. Loan interest uses the borrowed asset's annual rate and accrues while the loan is unpaid.

If current LTV rises above the collateral asset's liquidation LTV, Satchel reduces `currentCollateral` on the ledger back toward the origination LTV. That reduction stays on the ledger and leaves Fireblocks balances unchanged.

On the current list every interest rate is 0.05 and every fin margin is 0.1. Liquidation LTVs are 0.85 for `ETH_TEST5`, 0.95 for `USDC_ETH_TEST5_AN74`, 0.83 for `AVAXTEST`, 0.85 for `ETH-AETH_SEPOLIA`, 0.88 for `AMOY_POLYGON_TEST`, and 0.86 for `BNB_TEST`.

## API overview

| Method | Path | Purpose | Auth |
| --- | --- | --- | --- |
| GET | `/api/v1` | Welcome message | No |
| GET | `/actuator/health` | Health | No |
| POST | `/api/v1/auth/signup` | Register | No |
| GET | `/api/v1/auth/email-verification` | Verify email | No |
| POST | `/api/v1/auth/login` | Obtain a JWT | No |
| POST | `/api/v1/auth/logout` | Revoke the bearer token | Bearer |
| GET | `/api/v1/wallets` | List the caller's wallets | Yes |
| GET | `/api/v1/wallets/{id}` | Get one wallet | Yes |
| POST | `/api/v1/wallets` | Create a deposit wallet | Yes |
| GET | `/api/v1/funds_transfer` | List the caller's transfers | Yes |
| POST | `/api/v1/funds_transfer/withdrawal` | Withdraw or transfer internally | Yes |
| POST | `/api/v1/funds_transfer` | Fireblocks webhook | Webhook |
| GET | `/api/v1/loans` | List the caller's loans, newest first | Yes |
| GET | `/api/v1/loans/{id}` | Get one loan | Yes |
| POST | `/api/v1/loans` | Open a loan | Yes |
| POST | `/api/v1/loans/{id}/repayments` | Repay part or all of a loan | Yes |
| POST | `/api/v1/loans/{id}/collateral` | Unlock collateral up to the origination LTV | Yes |
| GET | `/api/v1/pool/{assetId}` | Omnibus available balance for one asset | Yes |

## Error handling

Validation failures are HTTP 400 and a JSON array. Each item has `error` and `message`. `error` is the constraint name, such as `NotBlank`, `Email`, or `ValidAssetId`.

Domain failures are one object with `error` and `message`.

| HTTP | `error` | When |
| --- | --- | --- |
| 401 | `unauthorized` | Login failed |
| 400 | `evt_expired`, `evt_used` | Verification token expired or already used |
| 400 | `user_already_verified` | Verification token belongs to a user who is already verified |
| 404 | `evt_not_found` | Unknown verification token |
| 409 | `email_taken` | Email already verified |
| 409 | `deposit_wallet_already_exists` | Wallet for that asset already exists |
| 404 | `deposit_wallet_not_found` | Missing wallet, including another user's id |
| 404 | `loan_not_found` | Missing loan, including another user's id |
| 409 | `loan_not_active` | Repayment of a loan that is already paid |
| 400 | `unhealthy_ltv` | Unlock would put the loan above the collateral asset's origination LTV |
| 400 | `invalid_repayment_amount` | Repayment amount is not positive |
| 400 | `insufficient_funds` | Available balance or collateral is too low |
| 400 | `insufficient_pool_funds` | Omnibus pool cannot cover an external withdrawal or a new loan |
| 400 | `self_transfer` | Destination is the caller's own wallet |
| 400 | `unverified_webhook` | Fireblocks signature check failed |
| 502 | `unable_to_get_address` | Custodian did not return a deposit address |
| 429 | `rate_limit_exceeded` | More than 120 requests in 60 seconds from one IP |
| 500 | `internal_server_error` | Anything else |

An empty list is HTTP 200 and `[]`. A missing or invalid token on a protected route is rejected before this format is used.

## Full documentation

Find the OpenAPI specification at [satchel.sergiozurron.site/docs](https://satchel.sergiozurron.site/docs).

### Wallets

Create a wallet with `{ "assetId" }`. List and get return the same object:

| Field | Meaning |
| --- | --- |
| `id` | Wallet id |
| `address` | Deposit address |
| `assetId` | Fireblocks asset id |
| `balance` | Ledger balance |
| `lockedBalance` | Amount locked as collateral |
| `availableBalance` | `balance` minus `lockedBalance` |
| `accruedInterest` | Interest accrued since the last daily credit |
| `openedAt` | When the wallet was created |
| `daysSinceOpened` | Whole days since `openedAt` |

A new wallet has zero balances and `daysSinceOpened` 0. `GET /api/v1/wallets/{id}` for an id you do not own returns HTTP 404 `deposit_wallet_not_found`.

```bash
curl https://satchel.sergiozurron.site/api/v1/wallets \
  -H "Authorization: Bearer <jwt>"
```

### Transfers

`GET /api/v1/funds_transfer` returns the caller's transfers, newest first. Each item has `id`, `amount`, `timestamp`, `transactionId`, `counterpartyAddress`, `isCompleted`, `walletId`, and `assetId`. `amount` is positive for a deposit and negative for a withdrawal. A pending external withdrawal has `isCompleted` false.

Withdraw with `assetId`, `destinationAddress`, and `amount`. Success is HTTP 200 and the caller’s transfer, with the same fields as one list item. `amount` is negative. An external withdrawal has `isCompleted` false until the webhook confirms it. An internal transfer also records a completed credit on the destination wallet. The response is the caller’s debit.

```bash
curl -X POST https://satchel.sergiozurron.site/api/v1/funds_transfer/withdrawal \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{
    "assetId": "USDC_ETH_TEST5_AN74",
    "destinationAddress": "0xYourExternalWalletAddress",
    "amount": 25.50
  }'
```

### Loans

Open a loan with `loanAssetId`, `collateralAssetId`, and `collateralAmount`. The loan id comes back in the response.

List, get, create, repay, and unlock all return:

| Field | Meaning |
| --- | --- |
| `id` | Loan id |
| `loanAssetId` | Asset that was borrowed |
| `amount` | Principal credited at origination |
| `returnedAmount` | Principal and interest repaid so far |
| `outstandingAmount` | `amount + accruedInterest - returnedAmount` |
| `collateralAssetId` | Asset locked as collateral |
| `collateralAmount` | Amount locked at origination |
| `currentCollateral` | Collateral still held against the loan |
| `interestRate` | Annual rate of the borrowed asset |
| `isPaid` | True when outstanding debt is zero |
| `accruedInterest` | Interest accrued so far |
| `grantedAt` | When the loan was opened |

`GET /api/v1/loans/{id}` for an id you do not own returns HTTP 404 `loan_not_found`.

```bash
curl -X POST https://satchel.sergiozurron.site/api/v1/loans \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{
    "loanAssetId": "USDC_ETH_TEST5_AN74",
    "collateralAssetId": "ETH_TEST5",
    "collateralAmount": 1.5
  }'
```

Repay with the loan id in the path and `amount` in the body:

```bash
curl -X POST https://satchel.sergiozurron.site/api/v1/loans/42/repayments \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00}'
```

The response is the updated loan.

Unlock collateral with the loan id in the path and `amount` in the body. The amount is released when the loan's LTV afterward is still at or under that collateral asset's origination LTV. On the current asset list every origination LTV is 0.8.

```bash
curl -X POST https://satchel.sergiozurron.site/api/v1/loans/42/collateral \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"amount": 0.25}'
```

The response is the updated loan. A request above the cap returns HTTP 400 `unhealthy_ltv`.

### Pool

`GET /api/v1/pool/{assetId}` returns the omnibus available balance for one supported asset. A new loan and an external withdrawal are checked against this balance.

| Field | Meaning |
| --- | --- |
| `assetId` | Fireblocks asset id |
| `availableLiquidity` | Omnibus available balance |

A custodian lookup that fails is reported as `availableLiquidity` 0. An unsupported asset id is HTTP 400 `ValidAssetId`.

```bash
curl https://satchel.sergiozurron.site/api/v1/pool/ETH_TEST5 \
  -H "Authorization: Bearer <jwt>"
```

## Contributing

Development uses JDK 17 and PostgreSQL. Build and run with the Maven wrapper:

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

The default profile is `dev`. It reads a gitignored `.env` file.

Tests must use the `test` profile so they use the `satchel_test` database:

```bash
SPRING_PROFILES_ACTIVE=test ./mvnw test
```

On PowerShell, quote a `-Dtest` list so the comma stays in one argument:

```powershell
$env:SPRING_PROFILES_ACTIVE="test"
.\mvnw.cmd "-Dtest=WalletControllerIntTest,LoanControllerIntTest" test
```

Schema changes are new scripts in `src/main/resources/db/migration`. Keep secrets out of git.

## License

This is a personal project. All rights reserved unless otherwise stated.
