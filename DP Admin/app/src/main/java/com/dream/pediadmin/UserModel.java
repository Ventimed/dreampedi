package com.dream.pediadmin;

public class UserModel {
    public String uid;
    public String fullName;
    public String paymentMethod;
    public String transactionId;
    public String fcm;
    public String amount;
    public String date;
    public String status;

    public UserModel() {}

    public UserModel(String uid, String fullName, String paymentMethod, String transactionId, String fcm, String amount, String date, String status) {
        this.uid = uid;
        this.fullName = fullName;
        this.paymentMethod = paymentMethod;
        this.transactionId = transactionId;
        this.fcm = fcm;
        this.amount = amount;
        this.date = date;
        this.status = status;
    }
}
