# Fineract Self-Service REST API Documentation

The Fineract Self-Service module exposes a dedicated set of REST API endpoints designed to be consumed by client-facing applications (such as mobile apps or customer portals). These APIs restrict access strictly to the authenticated customer's own details, preventing them from accessing other clients' accounts.

---

## Authentication & Session Management

All client-facing operations are accessed under `/v1/self`. Authenticators return access keys used for subsequent API calls.

### Endpoints

| HTTP Method | Endpoint | Description | Request Payload | Response Fields |
|:---|:---|:---|:---|:---|
| **POST** | `/v1/self/authentication` | Authenticates credentials and returns user details along with an access token. | `{ "username": "...", "password": "..." }` | `username`, `userId`, `base64EncodedAuthenticationKey`, `permissions`, `clients` list |
| **POST** | `/v1/self/authentication/token` | Exchanges a valid refresh token for a new access token and refresh token pair. | `{ "refreshToken": "..." }` | `base64EncodedAuthenticationKey`, `refreshToken` |
| **POST** | `/v1/self/authentication/logout` | Invalidates the current access token, terminating the user session. | *None (uses Authorization header)* | `{ "status": "success", "message": "..." }` |

> **Note:** All subsequent API requests should pass the `base64EncodedAuthenticationKey` in the HTTP header as `Authorization: Basic [token]`.

---

## Self-Registration & Enrollment

Allows new users to self-register or enroll in the financial platform.

### Endpoints

| HTTP Method | Endpoint | Description | Request Payload | Response |
|:---|:---|:---|:---|:---|
| **POST** | `/v1/self/registration` | Submits a registration request pending internal approval. | `{ "firstName": "...", "lastName": "...", "mobileNumber": "..." }` | Confirmation message |
| **POST** | `/v1/self/registration/user` | Creates an active self-service user from an approved registration request. | `{ "requestId": ..., "username": "...", "password": "..." }` | Serialized Command Result |
| **POST** | `/v1/self/registration/client-user` | **Self Enrollment Flow**: Creates a Fineract Client and disabled user, sending a verification token via SMS/Email. | `{ "username": "...", "email": "...", "documentNumber": "..." }` | Confirmation message |
| **POST** | `/v1/self/registration/client-user/confirm` | Validates the enrollment verification token and activates the self-service user. | `{ "token": "...", "password": "..." }` | Serialized Command Result |
| **GET** | `/v1/self/registration/identifiers` | Lists all supported client identity documents (e.g. Passport, National ID). | *None* | List of identity types |

---

## Client Management

Exposes general client profile details and associations.

### Endpoints

| HTTP Method | Endpoint | Description | Query Parameters |
|:---|:---|:---|:---|
| **GET** | `/v1/self/clients` | Lists all clients mapped to the authenticated self-service user. | *None* |
| **GET** | `/v1/self/clients/{clientId}` | Retrieves detailed profile information for a specific client. | *None* |
| **GET** | `/v1/self/clients/{clientId}/accounts` | Lists all accounts (Savings, Loans, Shares) belonging to the client. | *None* |
| **GET** | `/v1/self/clients/{clientId}/charges` | Lists charges associated with the client. | *None* |
| **GET** | `/v1/self/clients/{clientId}/transactions` | Lists transactions made by the client. | *None* |
| **GET** | `/v1/self/clients/{clientId}/images` | Downloads the profile image of the client. | *None* |
| **POST** | `/v1/self/clients/{clientId}/images` | Uploads/updates the profile image of the client. | Multipart file data |
| **DELETE**| `/v1/self/clients/{clientId}/images` | Deletes the profile image of the client. | *None* |

---

## Loans (Self-Service)

Allows customers to view their loan accounts, apply for new loans, or withdraw applications.

### Endpoints

| HTTP Method | Endpoint | Description | Query/Path Params |
|:---|:---|:---|:---|
| **GET** | `/v1/self/loans/{loanId}` | Retrieves details, repayment schedules, and transactions of a specific loan. | `associations=repaymentSchedule,transactions` |
| **GET** | `/v1/self/loans/{loanId}/transactions/{transactionId}` | Retrieves details of a specific loan transaction. | `fields=id,date,amount` |
| **GET** | `/v1/self/loans/{loanId}/charges` | Lists all charges applied to the loan. | *None* |
| **GET** | `/v1/self/loans/{loanId}/charges/{chargeId}` | Retrieves details of a specific loan charge. | *None* |
| **GET** | `/v1/self/loans/{loanId}/guarantors` | Lists all guarantors associated with the loan. | *None* |
| **GET** | `/v1/self/loans/template` | Retrieves the loan application template (defaults, interest rates, frequencies). | `templateType=individual&clientId=1` |
| **POST** | `/v1/self/loans` | Calculates repayment schedule **OR** submits a new loan application. | `command=calculateLoanSchedule` (for schedule only) |
| **POST** | `/v1/self/loans/{loanId}` | Withdraws a loan application before disbursement. | `command=withdrawnByApplicant` |

---

## Savings Accounts (Self-Service)

Allows customers to inspect their savings accounts, view transaction histories, and apply for new savings accounts.

### Endpoints

| HTTP Method | Endpoint | Description | Query/Path Params |
|:---|:---|:---|:---|
| **GET** | `/v1/self/savingsaccounts/{accountId}` | Retrieves details and transactions of a specific savings account. | `associations=transactions` |
| **GET** | `/v1/self/savingsaccounts/{accountId}/transactions/{transactionId}` | Retrieves details of a specific savings transaction. | *None* |
| **GET** | `/v1/self/savingsaccounts/{accountId}/charges` | Lists all charges applied to the savings account. | `chargeStatus=all` |
| **GET** | `/v1/self/savingsaccounts/{accountId}/charges/{chargeId}` | Retrieves details of a specific savings charge. | *None* |
| **GET** | `/v1/self/savingsaccounts/template` | Retrieves the savings account application template. | `clientId=1&productId=1` |
| **POST** | `/v1/self/savingsaccounts` | Submits a new savings account application. | *None* |

---

## Third-Party Transfers & Beneficiaries

Supports transferring funds to external accounts, internal client accounts, and managing transfer templates.

### Beneficiary Management Endpoints

| HTTP Method | Endpoint | Description | Request/Query Params |
|:---|:---|:---|:---|
| **GET** | `/v1/self/beneficiaries/tpt` | Lists all registered third-party transfer beneficiaries. | *None* |
| **GET** | `/v1/self/beneficiaries/tpt/{beneficiaryId}` | Retrieves details of a specific beneficiary. | *None* |
| **GET** | `/v1/self/beneficiaries/tpt/template` | Retrieves the beneficiary registration template. | *None* |
| **POST** | `/v1/self/beneficiaries/tpt` | Registers a new third-party transfer beneficiary. | `{ "name": "...", "accountNumber": "...", "accountType": 1 }` |
| **PUT** | `/v1/self/beneficiaries/tpt/{beneficiaryId}` | Updates details of a registered beneficiary. | `{ "name": "..." }` |
| **DELETE**| `/v1/self/beneficiaries/tpt/{beneficiaryId}` | Deletes a registered beneficiary. | *None* |

### Account Transfer Endpoints

| HTTP Method | Endpoint | Description | Request/Response Payload |
|:---|:---|:---|:---|
| **POST** | `/v1/self/accounttransfers/prepare` | Prepares and validates a transfer request (checks balances, source, destination). | `{ "fromAccountId": 1, "toAccountId": 2, "transferAmount": 100 }` |
| **POST** | `/v1/self/accounttransfers/quote` | Calculates transaction fees and exchanges rates for a prepared transfer. | Returns quote with transfer fees |
| **POST** | `/v1/self/accounttransfers/confirm` | **Confirm Transfer**: Executes the transfer. If no OTP is passed, sends OTP. If OTP is passed, performs execution. | `{ "otp": "..." }` |

---

## Supporting Resources

### Offices & Products
* **GET** `/v1/self/offices`: Lists offices available in the system.
* **GET** `/v1/self/loanproducts`: Lists loan products available for application.
* **GET** `/v1/self/savingsproducts`: Lists savings products available for application.
* **GET** `/v1/self/shareproducts`: Lists share products available for purchase.

### Share Accounts
* **GET** `/v1/self/shareaccounts`: Lists share accounts mapped to the user.
* **POST** `/v1/self/shareaccounts`: Submits a new share account application.

### Reporting & SPM
* **GET** `/v1/self/runreports`: Lists reports available for self-service execution.
* **POST** `/v1/self/runreports`: Executes a specific report.
* **GET** `/v1/self/scorecards`: Lists scorecards available for survey.
* **GET** `/v1/self/spm`: Lists active Survey Platforms.

---

## Running Integration Tests

The `:fineract-selfservice` module contains full end-to-end integration tests using RestAssured. These can be run in two modes:

### 1. Against an already running Fineract Backend (E2E Mode)
To run the integration tests against your already running local Fineract instance (e.g. started via bootRun) without starting separate Docker Testcontainers, use the `-PcargoDisabled=true` flag:

```bash
# Run all integration tests against the running backend
./gradlew :fineract-selfservice:test -PcargoDisabled=true

# Run a specific integration test class
./gradlew :fineract-selfservice:test --tests "org.apache.fineract.selfservice.loanaccount.api.SelfLoansApiIntegrationTest" -PcargoDisabled=true
```

By default, this mode connects to Fineract at `https://localhost:8443` and the database at `jdbc:mariadb://localhost:3318/fineract_default`. You can override these connection parameters via system properties:

```bash
./gradlew :fineract-selfservice:test -PcargoDisabled=true \
  -Dfineract.it.port=8443 \
  -Dfineract.it.jdbcUrl=jdbc:mariadb://localhost:3318/fineract_default \
  -Dfineract.it.dbUsername=root \
  -Dfineract.it.dbPassword=mysql
```

### 2. Isolated Mode (using Docker Testcontainers)
To run the tests in an isolated, self-managed environment where Testcontainers spins up a dedicated Postgres database and Fineract backend instance, run:

```bash
./gradlew :fineract-selfservice:test
```

---

> **Important:** To execute any of the self-service APIs, the self-service feature configuration must be enabled in Fineract Global Configurations. If it is disabled, all self-service requests will reject with `SelfServiceDisabledException`.
