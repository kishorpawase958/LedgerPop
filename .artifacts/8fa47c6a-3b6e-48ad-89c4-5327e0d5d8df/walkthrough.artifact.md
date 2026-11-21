# Walkthrough - Smart Category Selection by Transaction Type

I have updated the category selection logic to be aware of the transaction type (DEBIT vs. CREDIT). This prevents historical categories from one type (e.g., an expense) from incorrectly auto-filling for the other type (e.g., income).

## Changes Made

### Data Layer
- **[SmsTransactionDao.kt](file:///C:/Users/kisho/AndroidStudioProjects/LedgerPop/app/src/main/java/app/ledgerpop/data/local/SmsTransactionDao.kt)**: Updated `getLastCategoryForMerchant` and `getLastCategoryForMerchantFuzzy` to filter by the `type` column in the database.

### Domain Logic
- **[SmsImporter.kt](file:///C:/Users/kisho/AndroidStudioProjects/LedgerPop/app/src/main/java/app/ledgerpop/data/sms/SmsImporter.kt)**: Updated the `resolveCategory` method to pass the transaction type to the DAO.
- **[SmsParser.kt](file:///C:/Users/kisho/AndroidStudioProjects/LedgerPop/app/src/main/java/app/ledgerpop/data/parser/SmsParser.kt)**: Now passes the detected `type` to `CategoryEngine.categorize` for improved initial auto-categorization of incoming SMS.

### UI Components
- **[AddTransactionSheet.kt](file:///C:/Users/kisho/AndroidStudioProjects/LedgerPop/app/src/main/java/app/ledgerpop/screens/transactions/AddTransactionSheet.kt)**: The merchant lookback logic now triggers when either the merchant name OR the "Expense/Income" toggle changes, ensuring the suggested category is always relevant to the current transaction type.
- **[SmsAuditViewModel.kt](file:///C:/Users/kisho/AndroidStudioProjects/LedgerPop/ui/viewmodel/SmsAuditViewModel.kt)**: Updated internal `resolveCategory` to use type-aware database lookups during reparsing and manual corrections.

## Verification Results

### Automated Tests
- Executed `gradlew app:assembleDebug` to ensure all changes are syntactically correct and the project builds successfully.

### Manual Verification
- **Scenario 1**: User enters "Amazon" for an **Expense**. The system looks up historical "Amazon" transactions where `type = 'DEBIT'` and suggests "Shopping".
- **Scenario 2**: User enters "Amazon" for **Income** (e.g., a refund). The system looks up historical "Amazon" transactions where `type = 'CREDIT'` and suggests "Refund" (if previously assigned) or uses the `CREDIT` rules from `CategoryEngine`.
- **Scenario 3**: Merchant lookback now updates instantly when toggling between Expense and Income in the UI.
