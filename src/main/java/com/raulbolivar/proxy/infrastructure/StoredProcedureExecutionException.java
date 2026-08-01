package com.raulbolivar.proxy.infrastructure;

public class StoredProcedureExecutionException extends RuntimeException {

    private final int vendorCode;
    private final String sqlState;

    public StoredProcedureExecutionException(int vendorCode, String sqlState, String message, Throwable cause) {
        super(message, cause);
        this.vendorCode = vendorCode;
        this.sqlState = sqlState;
    }

    public int vendorCode() { return vendorCode; }
    public String sqlState() { return sqlState; }
}
