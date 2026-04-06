# Cordys CRM Skill Compatibility Modification Log

## Scope

This update adds compatibility for the companion `CordysCRM-skills` repository and validates the local runtime path.

## Modified Files

- `backend/crm/src/main/java/cn/cordys/common/security/ApiKeyHandler.java`
- `backend/crm/src/main/java/cn/cordys/crm/clue/controller/ClueController.java`
- `backend/crm/src/main/java/cn/cordys/crm/clue/controller/PoolClueController.java`
- `backend/crm/src/main/java/cn/cordys/crm/contract/controller/BusinessTitleController.java`
- `backend/crm/src/main/java/cn/cordys/crm/contract/controller/ContractController.java`
- `backend/crm/src/main/java/cn/cordys/crm/contract/controller/ContractInvoiceController.java`
- `backend/crm/src/main/java/cn/cordys/crm/contract/controller/ContractPaymentPlanController.java`
- `backend/crm/src/main/java/cn/cordys/crm/contract/controller/ContractPaymentRecordController.java`
- `backend/crm/src/main/java/cn/cordys/crm/customer/controller/CustomerController.java`
- `backend/crm/src/main/java/cn/cordys/crm/customer/controller/PoolCustomerController.java`
- `backend/crm/src/main/java/cn/cordys/crm/opportunity/controller/OpportunityController.java`
- `backend/crm/src/main/java/cn/cordys/crm/opportunity/controller/OpportunityQuotationController.java`
- `backend/crm/src/main/java/cn/cordys/crm/product/controller/ProductController.java`
- `backend/crm/src/main/java/cn/cordys/crm/system/controller/SkillCompatibilityController.java`
- `backend/crm/src/test/java/cn/cordys/common/security/ApiKeyHandlerTests.java`
- `backend/crm/src/test/java/cn/cordys/crm/system/controller/SkillCompatibilityControllerTests.java`

## Functional Changes

### 1. API key compatibility

- Added support for skill-style headers:
  - `X-Access-Key`
  - `X-Secret-Key`
- Existing authorization logic remains available.

### 2. Compatibility endpoints

Added a dedicated compatibility controller with the following routes:

- `GET /settings/fields?module=...`
- `GET /contact/module/form`
- `POST /contact/page`
- `GET /contact/{id}`
- `GET /contact/get/{id}`
- `GET /contact/view/list`

### 3. Detail route aliases

Added `/{id}` aliases alongside existing `/get/{id}` routes for skill-friendly access on multiple CRM modules.

## Validation

### Automated tests

Executed successfully:

- `SkillCompatibilityControllerTests`
- `ApiKeyHandlerTests`

### Local integration

Verified locally against a running Cordys CRM instance:

- login
- API key creation
- `GET /settings/fields?module=account`
- `GET /contact/module/form`
- skill header authentication using:
  - `X-Access-Key`
  - `X-Secret-Key`

## Notes

- The local runtime was verified on Windows.
- Docker-based Testcontainers verification was replaced with local standalone/controller tests because Docker access was restricted in the execution environment.
