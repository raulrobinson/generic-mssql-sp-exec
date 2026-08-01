package com.raulbolivar.proxy.domain;

public record ProcedureParameter(
        int ordinal,
        String name,
        String sqlTypeName,
        int jdbcType,
        int maxLength,
        int precision,
        int scale,
        boolean output,
        boolean nullable,
        boolean hasDefault
) { }
