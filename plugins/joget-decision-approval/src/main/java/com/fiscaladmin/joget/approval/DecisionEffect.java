package com.fiscaladmin.joget.approval;

import java.time.LocalDateTime;

/**
 * The platform's port for "what happens when a decision is approved". The approval
 * service is generic — it routes on the authority matrix and, on completion, runs the
 * effect registered for the action type. The <b>effect body</b> is domain code and is
 * supplied by the consumer, never by the platform: this is the dependency inversion
 * that lets the approval service know nothing about the domain it approves for.
 *
 * <p>Consumers register their effects at start-up via {@link DecisionEffects}.
 */
@FunctionalInterface
public interface DecisionEffect {

    /**
     * Execute the approved action and return a short human summary of what it did (echoed
     * into the approval service's result string).
     *
     * @param entity   logical entity name the record belongs to (informational)
     * @param recordId id of the record the decision was about
     * @param actor    the approver (or the system, for auto-passed actions)
     * @param now      decision timestamp
     * @return a short result message
     */
    String run(String entity, String recordId, String actor, LocalDateTime now);
}
