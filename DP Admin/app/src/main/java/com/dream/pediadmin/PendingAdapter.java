package com.dream.pediadmin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class PendingAdapter extends RecyclerView.Adapter<PendingAdapter.ViewHolder> {

    public interface OnItemClick {
        void onItemClick(UserModel user);
    }

    private List<UserModel> users;
    private OnItemClick listener;

    public PendingAdapter(List<UserModel> users, OnItemClick listener) {
        this.users = users;
        this.listener = listener;
    }

    @Override
    public PendingAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_verification, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(PendingAdapter.ViewHolder holder, int position) {
        final UserModel u = users.get(position);
        holder.userName.setText(u.fullName);
        holder.uid.setText(u.uid);
        holder.paymentMethod.setText(u.paymentMethod);
        holder.transactionId.setText(u.transactionId);
        holder.statusChip.setText(u.status != null ? u.status.toUpperCase() : "PENDING");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(u);
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public void removeUser(String uid) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).uid.equals(uid)) {
                users.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView userName, uid, paymentMethod, transactionId, statusChip;
        public ViewHolder(View v) {
            super(v);
            userName = v.findViewById(R.id.user_name);
            uid = v.findViewById(R.id.uid);
            paymentMethod = v.findViewById(R.id.payment_method);
            transactionId = v.findViewById(R.id.transaction_id);
            statusChip = v.findViewById(R.id.status_chip);
        }
    }
}
