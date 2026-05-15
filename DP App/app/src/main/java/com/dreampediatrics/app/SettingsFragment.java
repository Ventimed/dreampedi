package com.dreampediatrics.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.animation.ObjectAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import java.util.concurrent.atomic.AtomicBoolean;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsFragment extends Fragment {
    private TextView userNameView;
    private TextView userEmailView;
    private TextView userUidView;
    private SwitchMaterial darkModeSwitch;
    private SwitchMaterial notificationsSwitch;
    private LinearLayout settingsContainer;
    private ImageView darkIcon;
    private SharedPreferences prefs;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        prefs = requireContext().getSharedPreferences("DreamPediatricsPrefs", Context.MODE_PRIVATE);

        initializeViews(view);
        setupUserProfileFromPrefs();
        setupSettingsHandlers();

        return view;
    }

    private void initializeViews(View view) {
        userNameView = view.findViewById(R.id.userName);
        userEmailView = view.findViewById(R.id.userEmail);
        userUidView = view.findViewById(R.id.userUid);
        darkModeSwitch = view.findViewById(R.id.darkModeSwitch);
        notificationsSwitch = view.findViewById(R.id.notificationsSwitch);
        settingsContainer = view.findViewById(R.id.settingsContainer);
        darkIcon = view.findViewById(R.id.dark);
    }

    public void setupUserProfileFromPrefs() {
        MainActivity main = (MainActivity) requireActivity();
        if (main == null) return;

        // Name (existing behavior)
        String plainName = prefs.getString("username", null);
        if (TextUtils.isEmpty(plainName) && main.getUserSettings() != null) {
            plainName = main.getUserSettings().getUsername();
        }
        if (TextUtils.isEmpty(plainName)) plainName = "Sarah Johnson";

        String display = plainName;
        if (!display.toLowerCase().startsWith("dr.")) display = "Dr. " + display;
        userNameView.setText(display);

        // Email & UID: prefer Firebase user, fallback to saved prefs
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            userEmailView.setText(firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "No email");
            userUidView.setText(firebaseUser.getUid());
            // Also persist email to DreamPediatricsPrefs for other parts of app if desired
            prefs.edit().putString("email", firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "").apply();
        } else {
            userEmailView.setText(prefs.getString("email", "No email"));
            userUidView.setText(prefs.getString("uid", "No UID"));
        }

        // Badge visibility for features unlocked flag
        boolean featuresUnlocked = prefs.getBoolean("features_unlocked", false);
        View badge = settingsContainer.findViewById(R.id.badge); // optional id; if not present use darkIcon
        if (badge != null) badge.setVisibility(featuresUnlocked ? View.VISIBLE : View.GONE);

        // Also update darkIcon state (existing behavior)
        boolean darkModeFromPrefs = prefs.getBoolean("dark_mode", false);
        darkModeSwitch.setChecked(darkModeFromPrefs);
        boolean notifFromPrefs = prefs.getBoolean("notifications", true);
        notificationsSwitch.setChecked(notifFromPrefs);
        if (darkIcon != null) {
            darkIcon.setRotation(0f);
            darkIcon.setImageResource(darkModeFromPrefs ? R.drawable.ic_sun : R.drawable.ic_dark);
        }

        // Setup password change if view is available
        View changePassView = settingsContainer.findViewById(R.id.changePasswordItem);
        if (changePassView != null) {
            changePassView.setOnClickListener(v -> {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user == null || user.getEmail() == null) {
                    Toast.makeText(requireContext(), "No signed-in user", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Show dialog asking for current password and new password
                LinearLayout layout = new LinearLayout(requireContext());
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(32,8,32,8);
                final EditText current = new EditText(requireContext()); current.setHint("Current password"); current.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
                final EditText nw = new EditText(requireContext()); nw.setHint("New password (min 8)"); nw.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
                layout.addView(current); layout.addView(nw);

                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Change Password")
                        .setView(layout)
                        .setPositiveButton("Change", (d, which) -> {
                            String currPass = current.getText().toString().trim();
                            String newPass = nw.getText().toString().trim();
                            if (TextUtils.isEmpty(currPass) || TextUtils.isEmpty(newPass) || newPass.length() < 8) {
                                Toast.makeText(requireContext(), "Please provide valid passwords (new >= 8 chars)", Toast.LENGTH_LONG).show();
                                return;
                            }
                            // Reauthenticate then update password
                            AuthCredential cred = EmailAuthProvider.getCredential(user.getEmail(), currPass);
                            user.reauthenticate(cred).addOnCompleteListener(reauth -> {
                                if (reauth.isSuccessful()) {
                                    user.updatePassword(newPass).addOnCompleteListener(upd -> {
                                        if (upd.isSuccessful()) {
                                            Toast.makeText(requireContext(), "Password changed successfully", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(requireContext(), "Failed to update password: " + (upd.getException() != null ? upd.getException().getMessage() : "unknown"), Toast.LENGTH_LONG).show();
                                        }
                                    });
                                } else {
                                    Toast.makeText(requireContext(), "Re-authentication failed: " + (reauth.getException() != null ? reauth.getException().getMessage() : "invalid current password"), Toast.LENGTH_LONG).show();
                                }
                            });
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        // Make logout button functional (uses MainActivity.logout())
        View logoutView = settingsContainer.findViewById(R.id.logoutItem);
        if (logoutView != null) {
            logoutView.setOnClickListener(v -> {
                MainActivity ma = (MainActivity) requireActivity();
                if (ma != null) ma.logout();
            });
        }
    }

    private void setupSettingsHandlers() {
        MainActivity main = (MainActivity) getActivity();
        if (main == null) return;

        // Edit profile: open dialog, save plain name to prefs, update UI & MainActivity
        View editProfileItem = settingsContainer.findViewById(R.id.editProfileItem);
        editProfileItem.setOnClickListener(v -> {
            final EditText input = new EditText(requireContext());
            String currentPlain = prefs.getString("username", main.getUserSettings().getUsername());
            input.setText(currentPlain);

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Edit Username")
                    .setView(input)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String newPlain = input.getText().toString().trim();
                        if (!TextUtils.isEmpty(newPlain)) {
                            // save plain name (no auto "Dr." prefix stored)
                            prefs.edit().putString("username", newPlain).apply();
                            // update UI display with "Dr. " prefix
                            String display = newPlain;
                            if (!display.toUpperCase().startsWith("dr.")) display = "Dr. " + display;
                            userNameView.setText(display);

                            // notify MainActivity to update stored in-memory name + toolbar initial + home greeting
                            main.updateGreeting(newPlain);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // ✅ FIXED: Replace the dark mode switch handler in setupSettingsHandlers():
        // Find this part in setupSettingsHandlers() method and replace it:

        // Dark mode toggle -> save to prefs and apply theme
        // Dark mode toggle -> animate icon smoothly, swap icon at midpoint, then apply theme change
        darkModeSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            // Save preference immediately so state persists across restarts/crashes
            prefs.edit().putBoolean("dark_mode", isChecked).apply();

            // Update in-memory userSettings if present
            if (main.getUserSettings() != null) {
                main.getUserSettings().setDarkMode(isChecked);
            }

            // If icon view is not present just apply mode (fallback)
            if (darkIcon == null) {
                // apply theme change after a tiny delay to keep UX consistent
                new Handler().postDelayed(() -> {
                    if (isChecked) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    }
                }, 100);
                return;
            }

            // Prevent multiple toggles while animating - disable switch temporarily
            darkModeSwitch.setEnabled(false);

            // Cancel any running animations and normalize rotation
            darkIcon.animate().cancel();
            darkIcon.setRotation(0f);

            // Create an ObjectAnimator to rotate the icon
            final ObjectAnimator rot = ObjectAnimator.ofFloat(darkIcon, "rotation", 0f, isChecked ? 360f : -360f);
            rot.setDuration(600); // smooth timing (ms). Adjust as desired.
            rot.setInterpolator(new DecelerateInterpolator());

            // Swap icon at half way through the rotation (animatedFraction >= 0.5)
            final AtomicBoolean swapped = new AtomicBoolean(false);
            rot.addUpdateListener(animation -> {
                float fraction = animation.getAnimatedFraction();
                if (!swapped.get() && fraction >= 0.5f) {
                    swapped.set(true);
                    // switch drawable on UI thread
                    requireActivity().runOnUiThread(() -> {
                        if (isChecked) {
                            darkIcon.setImageResource(R.drawable.ic_sun);
                        } else {
                            darkIcon.setImageResource(R.drawable.ic_dark);
                        }
                    });
                }
            });

            rot.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Apply theme change AFTER the animation completes -> avoids cutting animation short
                    if (isChecked) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                        if (main != null) main.showNotification("Dark mode enabled");
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                        if (main != null) main.showNotification("Light mode enabled");
                    }

                    // Re-enable the switch after a short delay to avoid rapid toggles that race with recreate
                    new Handler().postDelayed(() -> {
                        try {
                            darkModeSwitch.setEnabled(true);
                        } catch (Exception ignored) {}
                    }, 400);

                    // Optionally smooth the activity transition with a fade
                    // If the activity recreates, calling overridePendingTransition here (on the Activity)
                    // improves perceived smoothness. We post it so it runs on UI thread in activity context.
                    requireActivity().runOnUiThread(() -> {
                        try {
                            requireActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                        } catch (Exception ignored) {}
                    });
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    // Ensure the icon is in a consistent state if animation cancelled
                    requireActivity().runOnUiThread(() -> {
                        if (!swapped.get()) {
                            if (isChecked) darkIcon.setImageResource(R.drawable.ic_sun);
                            else darkIcon.setImageResource(R.drawable.ic_dark);
                        }
                        darkIcon.setRotation(0f);
                        darkModeSwitch.setEnabled(true);
                    });
                }
            });

            // Start animation
            rot.start();
        });


        // Notifications toggle -> store and delegate notification behavior if needed
        notificationsSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("notifications", isChecked).apply();
            main.toggleNotifications();
        });

        // about / logout etc - call main directly
        settingsContainer.findViewById(R.id.aboutItem).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), AboutActivity.class);
                view.getContext().startActivity(intent);
            }
        });
    }


    @Override
    public void onResume() {
        super.onResume();
        setupUserProfileFromPrefs(); // refresh if changed externally
    }
}
