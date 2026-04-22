package com.access.unpack.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class SqlTranslatorTest {
    @Test
    void emitsWarningsForAccessSpecificFunctions() {
        SqlTranslator.TranslationResult result = new SqlTranslator()
                .translate("SELECT IIf([x]=1,'y','z') FROM [Table1]", SqlDialect.POSTGRES);
        assertNotNull(result.sql());
        assertFalse(result.warnings().isEmpty());
    }
}
