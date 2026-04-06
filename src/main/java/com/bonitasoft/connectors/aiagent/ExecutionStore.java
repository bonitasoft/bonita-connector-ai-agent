package com.bonitasoft.connectors.aiagent;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for agent execution sessions.
 * Uses ConcurrentHashMap for thread safety across connector invocations.
 */
public final class ExecutionStore {

    private static final ConcurrentHashMap<String, ExecutionState> STORE = new ConcurrentHashMap<>();

    private ExecutionStore() {}

    public static void put(String executionId, ExecutionState state) {
        STORE.put(executionId, state);
    }

    public static ExecutionState get(String executionId) {
        return STORE.get(executionId);
    }

    public static ExecutionState remove(String executionId) {
        return STORE.remove(executionId);
    }

    public static boolean contains(String executionId) {
        return STORE.containsKey(executionId);
    }

    /** Visible for testing. */
    static void clear() {
        STORE.clear();
    }
}
