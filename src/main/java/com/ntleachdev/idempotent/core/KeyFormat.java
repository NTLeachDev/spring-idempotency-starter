package com.ntleachdev.idempotent.core;

public enum KeyFormat {
    ANY,             // No validation
    UUID,            // Must be valid UUID format
    ALPHANUMERIC,    // Only letters and numbers
    CUSTOM           // User-Defined
}
