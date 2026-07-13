package com.danno.invoice_generator.application;

public class StatusCount {

    private final String status;
    private final long count;

    public StatusCount(String status, long count) {
        this.status = status;
        this.count = count;
    }

    public String getStatus() {
        return status;
    }

    public long getCount() {
        return count;
    }
}
