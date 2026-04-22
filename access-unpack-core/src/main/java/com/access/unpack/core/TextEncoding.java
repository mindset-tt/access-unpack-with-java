package com.access.unpack.core;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public enum TextEncoding {
    UTF8("UTF-8", StandardCharsets.UTF_8, false),
    UTF8_BOM("UTF-8 with BOM", StandardCharsets.UTF_8, true),
    WINDOWS_31J("Windows-31J", Charset.forName("Windows-31J"), false),
    SHIFT_JIS("Shift_JIS", Charset.forName("Shift_JIS"), false);

    private final String label;
    private final Charset charset;
    private final boolean bom;

    TextEncoding(String label, Charset charset, boolean bom) {
        this.label = label;
        this.charset = charset;
        this.bom = bom;
    }

    public Charset charset() {
        return charset;
    }

    public boolean bom() {
        return bom;
    }

    @Override
    public String toString() {
        return label;
    }
}
