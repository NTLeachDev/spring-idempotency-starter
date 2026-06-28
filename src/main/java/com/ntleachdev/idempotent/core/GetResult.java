package com.ntleachdev.idempotent.core;

import java.util.Optional;

public final class GetResult {

    public enum Status { ACQUIRED, REPLAY }

    private final Status status;
    private final Optional<StoredResponse> response;

    private GetResult(final Status status, final Optional<StoredResponse> response) {
        this.status = status;
        this.response = response;
    }

    public static GetResult acquired() {
        return new GetResult(Status.ACQUIRED, Optional.empty());
    }

    public static GetResult replay(final StoredResponse response) {
        return new GetResult(Status.REPLAY, Optional.ofNullable(response));
    }

    public Status status() {
        return status;
    }

    public Optional<StoredResponse> response() {
        return response;
    }
}

