package com.dreampediatrics.app;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

/**
 * Payment dialog fragment that preserves a previously tapped payment option even when
 * the "I have Paid" flow hides/unchecks the radios.
 */
public class PaymentDialogFragment extends DialogFragment {

    private final Map<Integer, Account> accounts = new HashMap<>();
    private final Handler handler = new Handler();
    private Runnable finishRunnable;

    // Option B field: remember selected payment method short code ("cbe", "telebir", "ebirr", "manual", etc.)
    private String selectedPaymentMethod = "manual";

    // NEW: remember last tapped option id even if the view gets hidden/unchecked
    private int lastSelectedOptionId = -1;

    // NEW: indicates the user activated the "I have Paid" manual flow (transaction form visible)
    private boolean manualFlow = false;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        // Inflate the updated layout
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_payment, null); // updated file name

        SharedPreferences prefs = requireContext().getSharedPreferences("DreamPediatricsPrefs", 0);
        boolean paymentSubmitted = prefs.getBoolean("payment_submitted", false);

        // Populate accounts (now with method code)
        accounts.put(R.id.rbtele, new Account("+254701234567", "Dream Pediatrics Ltd", "telebir"));
        accounts.put(R.id.rbBank, new Account("1234567890 (ABC Bank)", "Dream Pediatrics Ltd", "cbe"));
        accounts.put(R.id.rbebirr, new Account("payments@dreampedi.com", "Dream Pediatrics", "ebirr"));

        // Views
        final RadioGroup rg = view.findViewById(R.id.rgOptions);
        final RadioButton rbPaid = view.findViewById(R.id.paid);

        final LinearLayout paymentTop = view.findViewById(R.id.payment_top);
        final LinearLayout paymentBottom = view.findViewById(R.id.payment);
        final LinearLayout pleaseWait = view.findViewById(R.id.please_wait);
        final LinearLayout thankYou = view.findViewById(R.id.thank_you);
        final LinearLayout formContainer = view.findViewById(R.id.form_container);

        final TextView tvAccName = view.findViewById(R.id.tvAccountName);
        final TextView tvAccNumber = view.findViewById(R.id.tvAccountNumber);
        final EditText etTx = view.findViewById(R.id.etTxId);
        final EditText etPayer = view.findViewById(R.id.etPayerName);
        final TextView tvError = view.findViewById(R.id.tvError);

        final Button btnSubmit = view.findViewById(R.id.btnSubmit);
        final Button btnCancel = view.findViewById(R.id.btnCancel);
        final Button btnClose = view.findViewById(R.id.btnClose);

        // Initial UI state
        tvAccName.setText("-");
        tvAccNumber.setText("-");
        paymentTop.setVisibility(View.VISIBLE);
        paymentBottom.setVisibility(View.GONE);
        pleaseWait.setVisibility(View.GONE);
        thankYou.setVisibility(View.GONE);
        formContainer.setVisibility(View.VISIBLE);
        tvError.setVisibility(View.GONE);
        rbPaid.setVisibility(View.VISIBLE); // ensure it's always visible

        // list of option radio ids for easier iteration
        final int[] optionIds = new int[] { R.id.rbBank, R.id.rbtele, R.id.rbebirr};

        // Helper: find first ImageView inside a parent view (robust to different icon ids)
        final java.util.function.Function<View, ImageView> findImageInParent = parent -> {
            if (parent == null) return null;
            if (parent instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) parent;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    if (child instanceof ImageView) return (ImageView) child;
                    // Also check one level deeper (common when you have wrappers)
                    if (child instanceof ViewGroup) {
                        ViewGroup vg2 = (ViewGroup) child;
                        for (int j = 0; j < vg2.getChildCount(); j++) {
                            View inner = vg2.getChildAt(j);
                            if (inner instanceof ImageView) return (ImageView) inner;
                        }
                    }
                }
            }
            return null;
        };

        // Helper to clear all option radio buttons (explicitly uncheck)
        final Runnable clearAllOptions = () -> {
            for (int id : optionIds) {
                RadioButton rb = view.findViewById(id);
                if (rb != null) rb.setChecked(false);
            }
        };

        // Helper to refresh visuals for selection (row background + icon background)
        Runnable refreshSelectionVisuals = () -> {
            int lightGreen = Color.parseColor("#E6F9EC");
            for (int optId : optionIds) {
                View rbView = view.findViewById(optId);
                if (rbView == null) continue;
                View parent = (View) rbView.getParent(); // wrapper LinearLayout
                ImageView iv = findImageInParent.apply(parent);

                boolean checked = ((RadioButton) rbView).isChecked();
                if (checked) {
                    parent.setBackgroundColor(lightGreen);
                    if (iv != null) iv.setBackgroundColor(lightGreen);
                } else {
                    parent.setBackgroundColor(Color.TRANSPARENT);
                    if (iv != null) iv.setBackgroundColor(Color.TRANSPARENT);
                }
            }
        };

        // Ensure radio buttons explicitly uncheck others when clicked and update UI
        for (int optId : optionIds) {
            RadioButton rb = view.findViewById(optId);
            if (rb == null) continue;

            rb.setOnClickListener(v -> {
                // When user clicks a radio, explicitly make that the only checked one
                for (int id : optionIds) {
                    RadioButton r = view.findViewById(id);
                    if (r == null) continue;
                    r.setChecked(id == optId);
                }

                // Show the account info for the selected id and store selectedPaymentMethod
                Account a = accounts.get(optId);
                if (a != null) {
                    tvAccName.setText(a.name);
                    tvAccNumber.setText(a.number);
                    selectedPaymentMethod = a.methodCode != null ? a.methodCode : "manual";
                    lastSelectedOptionId = optId; // NEW: remember this choice even if later hidden
                    manualFlow = false; // user explicitly chose an option, so not manual-only
                } else {
                    tvAccName.setText("-");
                    tvAccNumber.setText("-");
                    selectedPaymentMethod = "manual";
                    lastSelectedOptionId = -1;
                }

                // If "I have Paid" was checked, uncheck it (but keep it visible)
                if (rbPaid.isChecked()) rbPaid.setChecked(false);

                // Ensure payment form hidden when choosing an option
                paymentTop.setVisibility(View.VISIBLE);
                paymentBottom.setVisibility(View.GONE);

                // Update row backgrounds/icons
                refreshSelectionVisuals.run();
            });

            // Also allow keyboard/selection changes via RadioGroup listener below
        }

        // RadioGroup listener as a safety net (handles programmatic changes too)
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            // Explicitly ensure only the checkedId is checked
            if (checkedId == -1) {
                // no visible selection
                clearAllOptions.run();
                tvAccName.setText("-");
                tvAccNumber.setText("-");
                paymentTop.setVisibility(View.VISIBLE);
                paymentBottom.setVisibility(View.GONE);
                // do not wipe lastSelectedOptionId here — we preserve last tapped selection
                // but if you want to forget it when user explicitly clears, you could set -1 here
                // lastSelectedOptionId = -1;
                // For now keep the lastSelectedOptionId and selectedPaymentMethod as they are.
            } else {
                for (int id : optionIds) {
                    RadioButton r = view.findViewById(id);
                    if (r != null) r.setChecked(id == checkedId);
                }
                Account a = accounts.get(checkedId);
                if (a != null) {
                    tvAccName.setText(a.name);
                    tvAccNumber.setText(a.number);
                    selectedPaymentMethod = a.methodCode != null ? a.methodCode : "manual";
                    lastSelectedOptionId = checkedId; // remember the checked id
                } else {
                    tvAccName.setText("-");
                    tvAccNumber.setText("-");
                    selectedPaymentMethod = "manual";
                    lastSelectedOptionId = -1;
                }
                // Uncheck paid (but keep visible)
                if (rbPaid.isChecked()) rbPaid.setChecked(false);

                paymentTop.setVisibility(View.VISIBLE);
                paymentBottom.setVisibility(View.GONE);
            }

            // update visuals
            refreshSelectionVisuals.run();
        });

        // "I have Paid" behaviour: hide top options, show transaction form
        rbPaid.setOnClickListener(v -> {
            boolean nowChecked = rbPaid.isChecked();
            if (nowChecked) {
                // DO NOT destroy lastSelectedOptionId — preserve previously chosen option internally
                // Clear the visible radio buttons (keeps UI same), but keep lastSelectedOptionId value
                for (int id : optionIds) {
                    RadioButton r = view.findViewById(id);
                    if (r != null) r.setChecked(false);
                }
                tvAccName.setText("-");
                tvAccNumber.setText("-");

                // mark manual flow
                manualFlow = true;

                // If we have a previously selected option, keep its method code; otherwise fallback to manual
                if (lastSelectedOptionId != -1) {
                    Account prev = accounts.get(lastSelectedOptionId);
                    selectedPaymentMethod = (prev != null && !TextUtils.isEmpty(prev.methodCode)) ? prev.methodCode : "manual";
                } else {
                    selectedPaymentMethod = "manual";
                }

                // hide top and show transaction form
                paymentTop.setVisibility(View.GONE);
                paymentBottom.setVisibility(View.VISIBLE);
            } else {
                // user unchecked the paid radio (show top again)
                manualFlow = false;
                paymentTop.setVisibility(View.VISIBLE);
                paymentBottom.setVisibility(View.GONE);

                // If there's a persisted lastSelectedOptionId, restore its visuals (optionally)
                // Here we leave radios unchecked for visual simplicity; the stored value is still available.
            }
            // refresh visuals (clears them)
            refreshSelectionVisuals.run();
        });

        // Cancel/Close handlers
        btnCancel.setOnClickListener(v -> dismiss());
        btnClose.setOnClickListener(v -> dismiss());

        // Submit: validation + submit to firebase with payment method code (uses selectedPaymentMethod)
        btnSubmit.setOnClickListener(v -> {
            tvError.setVisibility(View.GONE);

            String tx = etTx.getText() != null ? etTx.getText().toString().trim() : "";
            String payer = etPayer.getText() != null ? etPayer.getText().toString().trim() : "";

            if (TextUtils.isEmpty(tx)) {
                tvError.setText("Please enter the transaction ID.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }
            if (TextUtils.isEmpty(payer)) {
                tvError.setText("Please enter your name.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            // NEW: prefer previously-chosen option (even if the UI was hidden/unchecked)
            String paymentMethodCode = "manual";
            if (!TextUtils.isEmpty(selectedPaymentMethod) && !selectedPaymentMethod.equals("manual")) {
                // a specific method was selected earlier (cbe/telebir/ebirr) -> use it
                paymentMethodCode = selectedPaymentMethod;
            } else {
                // no prior selection -> fallback to manual
                paymentMethodCode = "manual";
            }

            formContainer.setVisibility(View.GONE);
            pleaseWait.setVisibility(View.VISIBLE);
            thankYou.setVisibility(View.GONE);

            // Submit to Firebase, including paymentMethodCode
            submitToFirebase(paymentMethodCode, payer, tx, pleaseWait, thankYou);
        });

        // Payment already submitted - show pending
        if (paymentSubmitted) {
            showPendingState(view);
        } else {
            // ensure visuals reflect initial empty selection
            // using post to ensure views are measured
            view.post(() -> {
                for (int id : optionIds) {
                    View rbView = view.findViewById(id);
                    if (rbView != null) ((RadioButton) rbView).setChecked(false);
                }
                // clear visuals
                for (int id : optionIds) {
                    View rbView = view.findViewById(id);
                    if (rbView == null) continue;
                    View parent = (View) rbView.getParent();
                    if (parent != null) parent.setBackgroundColor(Color.TRANSPARENT);
                    ImageView iv = findImageInParent.apply(parent);
                    if (iv != null) iv.setBackgroundColor(Color.TRANSPARENT);
                }

                // start state: no lastSelectedOptionId
                lastSelectedOptionId = -1;
                selectedPaymentMethod = "manual";
                manualFlow = false;
            });
        }

        // Build dialog (same as before)
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND | WindowManager.LayoutParams.FLAG_DIM_BEHIND
            );
            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.dimAmount = 0.8f;
            lp.alpha = 0.98f;
            dialog.getWindow().setAttributes(lp);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.setCancelable(true);
        return dialog;
    }

    // add import at top of file if not present:
    // import com.google.firebase.messaging.FirebaseMessaging;

    private void submitToFirebase(String paymentMethodCode, String fullName, String transactionId,
                                  LinearLayout pleaseWait, LinearLayout thankYou) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showError("Authentication required. Please try again.", pleaseWait);
            return;
        }

        // Read FCM token from SharedPreferences (same prefs file you already use)
        SharedPreferences prefs = requireContext().getSharedPreferences("DreamPediatricsPrefs", 0);
        String fcmToken = prefs.getString("fcm_token", null);

        // If we don't have a token cached, request one from FirebaseMessaging, then submit
        if (TextUtils.isEmpty(fcmToken)) {
            FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(token -> {
                        if (!TextUtils.isEmpty(token)) {
                            // cache it
                            prefs.edit().putString("fcm_token", token).apply();
                        }
                        // proceed to submit with retrieved token (might be null if retrieval failed)
                        actuallySubmitPayment(user.getUid(), paymentMethodCode, fullName, transactionId, token, pleaseWait, thankYou);
                    })
                    .addOnFailureListener(e -> {
                        // token retrieval failed — proceed without token but inform user in logs
                        android.util.Log.w("PaymentDialog", "Failed to get FCM token: " + e.getMessage());
                        actuallySubmitPayment(user.getUid(), paymentMethodCode, fullName, transactionId, null, pleaseWait, thankYou);
                    });
        } else {
            // token found in prefs — submit immediately
            actuallySubmitPayment(user.getUid(), paymentMethodCode, fullName, transactionId, fcmToken, pleaseWait, thankYou);
        }
    }

    /**
     * Performs the actual Firebase write. This method is split out to allow async token fetch above.
     */
    private void actuallySubmitPayment(String uid,
                                       String paymentMethodCode,
                                       String fullName,
                                       String transactionId,
                                       @Nullable String fcmToken,
                                       LinearLayout pleaseWait,
                                       LinearLayout thankYou) {

        com.google.firebase.database.DatabaseReference paymentsRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("payments")
                .child(uid);

        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("paymentMethod", paymentMethodCode); // short code: cbe / telebir / ebirr / manual
        paymentData.put("fullName", fullName);
        paymentData.put("transactionId", transactionId);
        // include fcm token if available (store empty string if null to avoid null entries)
        paymentData.put("fcm", fcmToken != null ? fcmToken : "");
        // optionally include server timestamp for when payment was submitted
        //paymentData.put("serverTime", ServerValue.TIMESTAMP);

        paymentsRef.setValue(paymentData)
                .addOnSuccessListener(aVoid -> {
                    // Save to SharedPreferences (payment submitted flag)
                    SharedPreferences prefs = requireContext().getSharedPreferences("DreamPediatricsPrefs", 0);
                    prefs.edit().putBoolean("payment_submitted", true).apply();

                    // Update MainActivity purchase button
                    MainActivity mainActivity = (MainActivity) getActivity();
                    if (mainActivity != null) {
                        mainActivity.runOnUiThread(() -> {
                            MaterialButton purchaseButton = mainActivity.findViewById(R.id.btnPurchaseCard);
                            if (purchaseButton != null) {
                                purchaseButton.setText("Pending");
                                purchaseButton.setEnabled(false);
                            }
                        });
                    }

                    showPaymentReceived(pleaseWait, thankYou);
                })
                .addOnFailureListener(e -> {
                    showError("Failed to submit payment. Please try again.", pleaseWait);
                });
    }

    private void showPaymentReceived(LinearLayout pleaseWait, LinearLayout thankYou) {
        pleaseWait.setVisibility(View.GONE);
        thankYou.setVisibility(View.VISIBLE);

        handler.postDelayed(() -> {
            if (isAdded() && getDialog() != null && getDialog().isShowing()) {
                dismiss();
            }
        }, 3000);
    }

    private void showError(String message, LinearLayout pleaseWait) {
        pleaseWait.setVisibility(View.GONE);
        if (getDialog() != null) {
            TextView tvError = getDialog().findViewById(R.id.tvError);
            LinearLayout formContainer = getDialog().findViewById(R.id.form_container);
            if (tvError != null && formContainer != null) {
                tvError.setText(message);
                tvError.setVisibility(View.VISIBLE);
                formContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (finishRunnable != null) {
            handler.removeCallbacks(finishRunnable);
            finishRunnable = null;
        }
    }

    private void showPendingState(View view) {
        Button btnSubmit = view.findViewById(R.id.btnSubmit);
        TextView tvError = view.findViewById(R.id.tvError);
        RadioGroup rg = view.findViewById(R.id.rgOptions);
        EditText etTx = view.findViewById(R.id.etTxId);
        EditText etPayer = view.findViewById(R.id.etPayerName);

        btnSubmit.setText("Payment Pending");
        btnSubmit.setEnabled(false);
        rg.setEnabled(false);
        etTx.setEnabled(false);
        etPayer.setEnabled(false);

        for (int i = 0; i < rg.getChildCount(); i++) {
            rg.getChildAt(i).setEnabled(false);
        }

        tvError.setText("Your payment is being processed. Please wait for verification.");
        tvError.setVisibility(View.VISIBLE);
    }

    private static class Account {
        final String number;
        final String name;
        final String methodCode; // e.g. "cbe", "telebirr", "ebirr"

        Account(String number, String name, String methodCode) {
            this.number = number;
            this.name = name;
            this.methodCode = methodCode;
        }
    }
}

