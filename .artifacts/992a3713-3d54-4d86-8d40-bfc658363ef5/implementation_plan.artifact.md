# Implementation Plan - Refine SmsParser Merchant Extraction (Iteration 2)

The previous fix improved date handling but introduced/exposed regressions in specific formats:
1.  **D-Mart Fail**: `on 4046 D MART` was skipped because `4046` matched an overly broad numeric negative lookahead.
2.  **Axis Fail**: `RTGS/ICICR...` was not correctly prioritized, allowing a URL fragment (`/ccm`) to be picked up by the `in` keyword.

## Proposed Changes

### [app]

#### [MODIFY] [SmsParser.kt](file:///C:/Users/kisho/AndroidStudioProjects/LedgerPop/app/src/main/java/app/ledgerpop/data/parser/SmsParser.kt)

1.  **Refine `on` keyword negative lookahead**:
    - Change `on(?!\s+(?:[0-9\-:]+)\b)` to `on(?!\s+\d{1,2}[-/](?:[a-zA-Z]{3}|\d{1,2})[-/]\d{2,4}\b)`.
    - This allows numeric prefixes like `4046` while still skipping dates like `12-Sep-26`.

2.  **Improve technical ID patterns**:
    - Update Pattern 3 to support `Info -` as well as `Info:`.
    - Add a specific pattern for raw institutional IDs (e.g., `ICICR...`) that often follow `Info - RTGS/`.

3.  **Strict URL junk filtering**:
    - Improve the `/` check in `isJunk` to catch more URL fragments (e.g., segments containing `.bank.in`).
    - Add `http` and `https` to `junkWords`.

4.  **Keyword order**:
    - Ensure technical patterns (RTGS, IMPS) are checked before the generic `in` keyword.

#### [MODIFY] [SmsParserTest.kt](file:///C:/Users/kisho/AndroidStudioProjects/LedgerPop/app/src/test/java/app/ledgerpop/data/parser/SmsParserTest.kt)

- Update tests to ensure these specific cases pass with the *exact* expected merchant names.

## Verification Plan

### Automated Tests
- Run tests in `SmsParserTest.kt`.

### Manual Verification
- Deploy to emulator and re-send the Axis and ICICI SMS samples.
- Verify the "Transactions" list.
