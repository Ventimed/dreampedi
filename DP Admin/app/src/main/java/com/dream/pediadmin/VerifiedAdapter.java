package com.dream.pediadmin;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class VerifiedAdapter extends RecyclerView.Adapter<VerifiedAdapter.ViewHolder> {

    private List<UserModel> users = new ArrayList<>();
    private Context ctx;

    public VerifiedAdapter(Context context, List<UserModel> users) {
        this.users = users != null ? users : new ArrayList<>();
        this.ctx = context;
    }

    @Override
    public VerifiedAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_verified, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(VerifiedAdapter.ViewHolder holder, int position) {
        UserModel u = users.get(position);
        holder.name.setText(u.fullName != null ? u.fullName : "");
        holder.paymentMethod.setText(u.paymentMethod != null ? u.paymentMethod : "");
        holder.transactionId.setText(u.transactionId != null ? u.transactionId : "");
        holder.uid.setText(u.uid != null ? u.uid : "");

        holder.copyTxn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("txn", u.transactionId != null ? u.transactionId : "");
            cm.setPrimaryClip(clip);
            Toast.makeText(ctx, "Transaction ID copied", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() { return users.size(); }

    public void updateList(List<UserModel> newList) {
        users = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, paymentMethod, transactionId, uid;
        ImageView copyTxn;
        public ViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.v_user_name);
            paymentMethod = v.findViewById(R.id.v_payment_method);
            transactionId = v.findViewById(R.id.v_transaction_id);
            uid = v.findViewById(R.id.uid);
            copyTxn = v.findViewById(R.id.v_copy_txn);
        }
    }
}
