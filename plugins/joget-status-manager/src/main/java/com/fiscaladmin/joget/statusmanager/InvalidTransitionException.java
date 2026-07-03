package com.fiscaladmin.joget.statusmanager;

/**
 * Thrown by the status machine or a guard phase when a state transition is not
 * permitted by the metamodel configuration (invalid transitions are rejected with
 * user notification). Carries the attempted from/to states for the caller to report.
 */
public class InvalidTransitionException extends RuntimeException {

    private final String fromState;
    private final String toState;

    public InvalidTransitionException(String fromState, String toState, String reason) {
        super(reason);
        this.fromState = fromState;
        this.toState = toState;
    }

    public String getFromState() {
        return fromState == null ? "" : fromState;
    }

    public String getToState() {
        return toState == null ? "" : toState;
    }
}
