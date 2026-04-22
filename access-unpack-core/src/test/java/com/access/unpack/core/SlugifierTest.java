package com.access.unpack.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SlugifierTest {
    @Test
    void preservesUnicodeLettersWhileMakingSafeFileNames() {
        assertEquals("水利地益tbl2010", Slugifier.slug("水利地益TBL2010"));
    }

    @Test
    void fallsBackWhenNameHasNoLetters() {
        assertEquals("unnamed", Slugifier.slug("!!!"));
    }
}
