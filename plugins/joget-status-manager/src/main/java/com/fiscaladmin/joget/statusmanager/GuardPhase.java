package com.fiscaladmin.joget.statusmanager;

/**
 * One guard phase of a case-lifecycle guard. Consumers implement their own phases
 * (e.g. open / pre-close / close) and run them in order against a {@link GuardContext};
 * the platform ships the SPI, not the domain phases.
 */
public interface GuardPhase {

    /** @throws InvalidTransitionException when the transition must be rejected */
    void run(GuardContext ctx) throws InvalidTransitionException;
}
