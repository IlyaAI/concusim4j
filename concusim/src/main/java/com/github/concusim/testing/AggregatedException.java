package com.github.concusim.testing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class AggregatedException extends RuntimeException {
    private static final class CauseEntry {
        final NamedWorker worker;
        final Throwable cause;

        CauseEntry(NamedWorker worker, Throwable cause) {
            this.worker = worker;
            this.cause = cause;
        }
    }
    static final class Builder {
        private final List<CauseEntry> causes = new ArrayList<>();

        @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
        Builder addCauseIfAny(@NotNull WorkerThread thread) {
            Throwable cause = thread.getCause();
            if (cause != null) {
                causes.add(new CauseEntry(thread.getNamedWorker(), cause));
            }
            return this;
        }

        void throwIfAny() throws AggregatedException {
            if (!causes.isEmpty()) {
                CauseEntry[] entries = new CauseEntry[causes.size()];
                throw new AggregatedException(causes.toArray(entries));
            }
        }
    }

    private final CauseEntry[] causes;

    private AggregatedException(CauseEntry[] causes) {
        this.causes = causes;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(512);

        sb.append("The following exceptions were thrown:\n");
        for (CauseEntry entry: causes) {
            sb.append("  ").append(entry.worker)
                .append(":: ").append(entry.cause)
                .append("\n");
        }

        return sb.toString();
    }

    public @Nullable Throwable getCauseFor(@NotNull Runnable worker) {
        for (CauseEntry entry: causes) {
            if (entry.worker.getRunnable() == worker)
                return entry.cause;
        }

        return null;
    }
}
