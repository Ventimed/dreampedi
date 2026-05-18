package com.dreampediatrics.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class PaymentBottomSheet extends BottomSheetDialogFragment {

    private final Handler handler = new Handler();
    
    // State management
    private String selectedPlan = "yearly"; // yearly or 6months
    private String selectedPaymentMethod = ""; // cbe, telebirr, boa, or ebirr
    private int selectedPlanPrice = 400;
    
    // Views - Plan Selection
    private LinearLayout planSelectionView;
    private MaterialCardView yearlyPlanCard, sixMonthsPlanCard;
    private MaterialButton btnContinueToPlan;
    
    // Views - Payment Method
    private LinearLayout paymentMethodView;
    private MaterialCardView cbeCard, telebirrCard, boaCard, ebirrCard;
    private TextView selectedPlanText;
    private MaterialButton btnContinueToPayment;
    
    // Views - Payment Details
    private LinearLayout paymentDetailsView;
    private TextView paymentMethodTitle;
    private TextView paymentAccountNumber, paymentAccountName, paymentAmount;
    private EditText etTransactionId, etPayerName;
    private MaterialButton btnSubmitPayment;
    private TextView btnChangeMethod;
    
    // Views - Success
    private LinearLayout successView;
    private MaterialButton btnGotIt;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_payment, container, false);
        
        initializeViews(view);
        setupListeners();
        
        // Start with plan selection
        showPlanSelection();
        
        return view;
    }

    private void initializeViews(View view) {
        // Plan Selection
        planSelectionView = view.findViewById(R.id.planSelectionView);
        yearlyPlanCard = view.findViewById(R.id.yearlyPlanCard);
        sixMonthsPlanCard = view.findViewById(R.id.sixMonthsPlanCard);
        btnContinueToPlan = view.findViewById(R.id.btnContinueToPlan);
        
        // Payment Method
        paymentMethodView = view.findViewById(R.id.paymentMethodView);
        cbeCard = view.findViewById(R.id.cbeCard);
        telebirrCard = view.findViewById(R.id.telebirrCard);
        boaCard = view.findViewById(R.id.boaCard);
        ebirrCard = view.findViewById(R.id.ebirrCard);
        selectedPlanText = view.findViewById(R.id.selectedPlanText);
        btnContinueToPayment = view.findViewById(R.id.btnContinueToPayment);
        
        // Payment Details
        paymentDetailsView = view.findViewById(R.id.telebirrPaymentView);
        paymentMethodTitle = view.findViewById(R.id.paymentMethodTitle);
        paymentAccountNumber = view.findViewById(R.id.telebirrAccountNumber);
        paymentAccountName = view.findViewById(R.id.telebirrAccountName);
        paymentAmount = view.findViewById(R.id.telebirrAmount);
        etTransactionId = view.findViewById(R.id.etTransactionId);
        etPayerName = view.findViewById(R.id.etPayerName);
        btnSubmitPayment = view.findViewById(R.id.btnSubmitPayment);
        btnChangeMethod = view.findViewById(R.id.btnChangeMethod);
        
        // Success
        successView = view.findViewById(R.id.successView);
        btnGotIt = view.findViewById(R.id.btnGotIt);
    }

    private void setupListeners() {
        // Plan selection
        yearlyPlanCard.setOnClickListener(v -> {
            selectedPlan = "yearly";
            selectedPlanPrice = 400;
            updatePlanSelection();
        });
        
        sixMonthsPlanCard.setOnClickListener(v -> {
            selectedPlan = "6months";
            selectedPlanPrice = 250;
            updatePlanSelection();
        });
        
        btnContinueToPlan.setOnClickListener(v -> showPaymentMethod());
        
        // Payment method selection
        cbeCard.setOnClickListener(v -> {
            selectedPaymentMethod = "cbe";
            updatePaymentMethodSelection();
        });
        
        telebirrCard.setOnClickListener(v -> {
            selectedPaymentMethod = "telebirr";
            updatePaymentMethodSelection();
        });
        
        boaCard.setOnClickListener(v -> {
            selectedPaymentMethod = "boa";
            updatePaymentMethodSelection();
        });
        
        ebirrCard.setOnClickListener(v -> {
            selectedPaymentMethod = "ebirr";
            updatePaymentMethodSelection();
        });
        
        btnContinueToPayment.setOnClickListener(v -> showPaymentDetails());
        
        // Payment submission
        btnSubmitPayment.setOnClickListener(v -> submitPayment());
        btnChangeMethod.setOnClickListener(v -> showPaymentMethod());
        
        // Success
        btnGotIt.setOnClickListener(v -> dismiss());
    }

    private void showPlanSelection() {
        planSelectionView.setVisibility(View.VISIBLE);
        paymentMethodView.setVisibility(View.GONE);
        paymentDetailsView.setVisibility(View.GONE);
        successView.setVisibility(View.GONE);
        
        // Default to yearly
        selectedPlan = "yearly";
        selectedPlanPrice = 400;
        updatePlanSelection();
    }

    private void updatePlanSelection() {
        if (selectedPlan.equals("yearly")) {
            yearlyPlanCard.setStrokeWidth(4);
            yearlyPlanCard.setStrokeColor(getResources().getColor(R.color.primary));
            sixMonthsPlanCard.setStrokeWidth(2);
            sixMonthsPlanCard.setStrokeColor(getResources().getColor(R.color.outline));
        } else {
            sixMonthsPlanCard.setStrokeWidth(4);
            sixMonthsPlanCard.setStrokeColor(getResources().getColor(R.color.primary));
            yearlyPlanCard.setStrokeWidth(2);
            yearlyPlanCard.setStrokeColor(getResources().getColor(R.color.outline));
        }
    }

    private void showPaymentMethod() {
        planSelectionView.setVisibility(View.GONE);
        paymentMethodView.setVisibility(View.VISIBLE);
        paymentDetailsView.setVisibility(View.GONE);
        successView.setVisibility(View.GONE);
        
        // Update selected plan text
        String planText = selectedPlan.equals("yearly") 
            ? "✓ Yearly — 400 ETB" 
            : "✓ 6 months — 250 ETB";
        selectedPlanText.setText(planText);
        
        // Clear selection
        selectedPaymentMethod = "";
        updatePaymentMethodSelection();
    }

    private void updatePaymentMethodSelection() {
        int primaryColor = getResources().getColor(R.color.primary);
        int outlineColor = getResources().getColor(R.color.outline);
        
        // Reset all cards
        cbeCard.setStrokeWidth(2);
        cbeCard.setStrokeColor(outlineColor);
        telebirrCard.setStrokeWidth(2);
        telebirrCard.setStrokeColor(outlineColor);
        boaCard.setStrokeWidth(2);
        boaCard.setStrokeColor(outlineColor);
        ebirrCard.setStrokeWidth(2);
        ebirrCard.setStrokeColor(outlineColor);
        
        // Highlight selected card
        switch (selectedPaymentMethod) {
            case "cbe":
                cbeCard.setStrokeWidth(4);
                cbeCard.setStrokeColor(primaryColor);
                btnContinueToPayment.setEnabled(true);
                break;
            case "telebirr":
                telebirrCard.setStrokeWidth(4);
                telebirrCard.setStrokeColor(primaryColor);
                btnContinueToPayment.setEnabled(true);
                break;
            case "boa":
                boaCard.setStrokeWidth(4);
                boaCard.setStrokeColor(primaryColor);
                btnContinueToPayment.setEnabled(true);
                break;
            case "ebirr":
                ebirrCard.setStrokeWidth(4);
                ebirrCard.setStrokeColor(primaryColor);
                btnContinueToPayment.setEnabled(true);
                break;
            default:
                btnContinueToPayment.setEnabled(false);
                break;
        }
    }

    private void showPaymentDetails() {
        planSelectionView.setVisibility(View.GONE);
        paymentMethodView.setVisibility(View.GONE);
        paymentDetailsView.setVisibility(View.VISIBLE);
        successView.setVisibility(View.GONE);
        
        // Update payment details based on selected method
        String accountNumber = "";
        String accountName = "Dream Pediatrics";
        String methodTitle = "";
        
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
        
        paymentMethodTitle.setText(methodTitle);
        paymentAccountNumber.setText(accountNumber);
        paymentAccountName.setText(accountName);
        paymentAmount.setText(selectedPlanPrice + " ETB");
    }

    private void submitPayment() {
        String transactionId = etTransactionId.getText().toString().trim();
        String payerName = etPayerName.getText().toString().trim();
        
        if (TextUtils.isEmpty(transactionId)) {
            etTransactionId.setError("Required");
            return;
        }
        
        if (TextUtils.isEmpty(payerName)) {
            etPayerName.setError("Required");
            return;
        }
        
        // Disable button during submission
        btnSubmitPayment.setEnabled(false);
        btnSubmitPayment.setText("Submitting...");
        
        // Submit to Firebase
        submitToFirebase(selectedPaymentMethod, payerName, transactionId);
    }

    private void submitToFirebase(String paymentMethod, String fullName, String transactionId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            btnSubmitPayment.setEnabled(true);
            btnSubmitPayment.setText("Submit for verification");
            return;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences("DreamPediatricsPrefs", 0);
        String fcmToken = prefs.getString("fcm_token", null);

        if (TextUtils.isEmpty(fcmToken)) {
            FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(token -> {
                        if (!TextUtils.isEmpty(token)) {
                            prefs.edit().putString("fcm_token", token).apply();
                        }
                        actuallySubmitPayment(user.getUid(), paymentMethod, fullName, transactionId, token);
                    })
                    .addOnFailureListener(e -> {
                        actuallySubmitPayment(user.getUid(), paymentMethod, fullName, transactionId, null);
                    });
        } else {
            actuallySubmitPayment(user.getUid(), paymentMethod, fullName, transactionId, fcmToken);
        }
    }

    private void actuallySubmitPayment(String uid, String paymentMethod, String fullName, 
                                      String transactionId, @Nullable String fcmToken) {
        DatabaseReference paymentsRef = FirebaseDatabase.getInstance()
                .getReference("payments")
                .child(uid);

        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("paymentMethod", paymentMethod);
        paymentData.put("plan", selectedPlan);
        paymentData.put("amount", selectedPlanPrice);
        paymentData.put("fullName", fullName);
        paymentData.put("transactionId", transactionId);
        paymentData.put("fcm", fcmToken != null ? fcmToken : "");
        paymentData.put("timestamp", System.currentTimeMillis());

        paymentsRef.setValue(paymentData)
                .addOnSuccessListener(aVoid -> {
                    SharedPreferences prefs = requireContext().getSharedPreferences("DreamPediatricsPrefs", 0);
                    prefs.edit().putBoolean("payment_submitted", true).apply();
                    
                    showSuccess();
                })
                .addOnFailureListener(e -> {
                    btnSubmitPayment.setEnabled(true);
                    btnSubmitPayment.setText("Submit for verification");
                });
    }

    private void showSuccess() {
        planSelectionView.setVisibility(View.GONE);
        paymentMethodView.setVisibility(View.GONE);
        paymentDetailsView.setVisibility(View.GONE);
        successView.setVisibility(View.VISIBLE);
        
        // Auto dismiss after 3 seconds
        handler.postDelayed(() -> {
            if (isAdded()) {
                dismiss();
            }
        }, 3000);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }
}
