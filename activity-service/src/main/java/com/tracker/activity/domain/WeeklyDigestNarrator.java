package com.tracker.activity.domain;

import java.util.Optional;

/**
 * Turns one user's {@link DigestFacts} into a short coaching narrative (issue #65). The backend
 * behind an implementation is a runtime/config concern, never a caller concern -- see
 * {@link ChatModelWeeklyDigestNarrator}, which covers both the Ollama and the OpenAI-compatible
 * (Docker Model Runner, vLLM, LocalAI, ...) backends through Spring AI's {@code ChatModel}
 * abstraction with no branch in the code.
 */
public interface WeeklyDigestNarrator {

    /**
     * Return a short coaching narrative, or {@link Optional#empty()} if none can be produced. MUST
     * NOT throw: this sits on a user-facing {@code GET}, and a model outage must degrade to
     * numbers-only, never turn a working request into an error. Implementations must treat every
     * string in {@link DigestFacts#noteLines()} as untrusted data, never as instructions.
     */
    Optional<String> narrate(DigestFacts facts);
}
