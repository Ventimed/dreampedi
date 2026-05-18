# Payment Methods Update

## Overview
Expanded the payment bottom sheet to include 4 payment methods in the specified order: CBE, Telebirr, BOA, and Ebirr.

## Payment Methods

### 1. CBE Birr (Commercial Bank of Ethiopia)
- **Position**: First in list
- **Icon**: Green circular background
- **Account Number**: 1000123456789
- **Account Name**: Dream Pediatrics
- **Description**: "Transfer via Commercial Bank of Ethiopia"

### 2. Telebirr
- **Position**: Second in list
- **Icon**: Blue circular background
- **Account Number**: 0912 345 678
- **Account Name**: Dream Pediatrics
- **Description**: "Pay via Telebirr mobile money"

### 3. BOA (Bank of Abyssinia)
- **Position**: Third in list
- **Icon**: Red circular background
- **Account Number**: 1475321
- **Account Name**: Dream Pediatrics
- **Description**: "Transfer via Bank of Abyssinia"

### 4. Ebirr
- **Position**: Fourth in list
- **Icon**: Purple circular background
- **Account Number**: 0912 345 678 (same as Telebirr)
- **Account Name**: Dream Pediatrics
- **Description**: "Pay via Ebirr mobile wallet"

## Changes Made

### Layout Updates (bottom_sheet_payment.xml)

1. **Reordered Payment Method Cards**:
   - Moved CBE to the top
   - Kept Telebirr second
   - Added BOA as third
   - Added Ebirr as fourth

2. **Added New Payment Method Cards**:
   - BOA card with red icon background
   - Ebirr card with purple icon background

3. **Added Payment Method Title**:
   - Added `android:id="@+id/paymentMethodTitle"` to dynamically update title

### Code Updates (PaymentBottomSheet.java)

1. **Updated State Management**:
   ```java
   private String selectedPaymentMethod = ""; // cbe, telebirr, boa, or ebirr
   ```

2. **Added New View References**:
   ```java
   private MaterialCardView cbeCard, telebirrCard, boaCard, ebirrCard;
   private TextView paymentMethodTitle;
   ```

3. **Updated Click Listeners**:
   - Added click listeners for BOA and Ebirr cards
   - All 4 methods now update selection state

4. **Enhanced Selection Logic**:
   ```java
   private void updatePaymentMethodSelection() {
       // Handles all 4 payment methods
       // Highlights selected card with orange border
       // Enables/disables continue button
   }
   ```

5. **Dynamic Payment Details**:
   ```java
   private void showPaymentDetails() {
       switch (selectedPaymentMethod) {
           case "cbe":
               accountNumber = "1000123456789";
               methodTitle = "Pay via CBE Birr";
               break;
           case "telebirr":
               accountNumber = "0912 345 678";
               methodTitle = "Pay via Telebirr";
               break;
           case "boa":
               accountNumber = "1475321";
               methodTitle = "Pay via BOA";
               break;
           case "ebirr":
               accountNumber = "0912 345 678";
               methodTitle = "Pay via Ebirr";
               break;
       }
   }
   ```

### New Drawable Resources

1. **bg_payment_icon_red.xml**:
   - Light red background (#FFEBEE) for BOA
   - Oval shape

2. **bg_payment_icon_purple.xml**:
   - Light purple background (#F3E5F5) for Ebirr
   - Oval shape

## User Experience

### Payment Method Selection Flow

1. User sees 4 payment options in order:
   - CBE Birr (green icon)
   - Telebirr (blue icon)
   - BOA (red icon)
   - Ebirr (purple icon)

2. User taps a payment method:
   - Card highlights with orange border (4dp stroke)
   - Other cards reset to gray border (2dp stroke)
   - "Continue" button enables

3. User clicks "Continue":
   - Shows payment details screen
   - Title updates based on selected method
   - Account number updates based on selected method
   - Amount shows selected plan price

4. User enters transaction details and submits

### Visual Feedback

- **Unselected State**: 2dp gray border
- **Selected State**: 4dp orange border
- **Icon Colors**: 
  - CBE: Green (#E1F5EE)
  - Telebirr: Blue (#E3F2FD)
  - BOA: Red (#FFEBEE)
  - Ebirr: Purple (#F3E5F5)

## Firebase Data Structure

Payment submissions now include the selected method:

```json
{
  "paymentMethod": "cbe" | "telebirr" | "boa" | "ebirr",
  "plan": "yearly" | "6months",
  "amount": 400 | 250,
  "fullName": "User Name",
  "transactionId": "TXN123456",
  "fcm": "firebase_token",
  "timestamp": 1234567890
}
```

## Account Numbers Reference

| Payment Method | Account Number | Account Name |
|---------------|----------------|--------------|
| CBE Birr | 1000123456789 | Dream Pediatrics |
| Telebirr | 0912 345 678 | Dream Pediatrics |
| BOA | 1475321 | Dream Pediatrics |
| Ebirr | 0912 345 678 | Dream Pediatrics |

## Testing Checklist

- [x] CBE card selection and highlighting
- [x] Telebirr card selection and highlighting
- [x] BOA card selection and highlighting
- [x] Ebirr card selection and highlighting
- [x] Payment details update for CBE
- [x] Payment details update for Telebirr
- [x] Payment details update for BOA
- [x] Payment details update for Ebirr
- [x] Continue button enables/disables correctly
- [x] Payment method title updates dynamically
- [x] Account numbers display correctly
- [x] Firebase submission includes correct method

## Files Modified

### Java
- `app/src/main/java/com/dreampediatrics/app/PaymentBottomSheet.java`

### Layouts
- `app/src/main/res/layout/bottom_sheet_payment.xml`

### Drawables Created
- `app/src/main/res/drawable/bg_payment_icon_red.xml`
- `app/src/main/res/drawable/bg_payment_icon_purple.xml`

## Build Status

✅ **Build successful** - No compilation errors
✅ All 4 payment methods integrated
✅ Account numbers configured correctly
✅ Visual feedback working properly
✅ Firebase integration maintained

## Notes

- Telebirr and Ebirr share the same phone number (0912 345 678)
- BOA has a shorter account number (1475321)
- CBE has the longest account number (1000123456789)
- All methods use "Dream Pediatrics" as account name
- Payment method order: CBE → Telebirr → BOA → Ebirr
