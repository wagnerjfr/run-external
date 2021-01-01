/* Copyright (c) 2014, 2020, Oracle and/or its affiliates. */
package event;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;

/**
 * Implement a single event inspired by Algorithm 4.6 in "Operating System Principles"
 * by Per Brinch Hansen, 1973.
 */
public class SingleEvent {
    public static final long NANOS_PER_MILLI = 1000000L;

    private boolean eventFlag = false;
    private final String message;

    /**
     * Create event.
     *
     * @param message
     */
    public SingleEvent(String message) {
        this.message = message;
    }

    /**
     * Reset the event so It can be reused (
     */
    public void reset() {
        synchronized (this) {
            eventFlag = false;
        }
    }

    /**
     * Cause the event to happen.
     */
    public void cause() {
        synchronized (this) {
            eventFlag = true;
            this.notifyAll();
        }
    }

    /**
     * Get the event flag. True if the event has happend.
     *
     * @return
     */
    public boolean getEventFlag() {
        synchronized (this) {
            return eventFlag;
        }
    }

    /**
     * Wait for the event, but time out with exception if it does not happen before the timeout.
     *
     * @param timeout
     * @throws IOException
     */
    public void await(TemporalAmount timeout) throws IOException {
        long start = System.nanoTime();
        long timeoutNanos = timeout.get(ChronoUnit.SECONDS) * 1000 * NANOS_PER_MILLI;
        synchronized (this) {
            while (!eventFlag) {
                long timeLeft = timeoutNanos
                        - (System.nanoTime() - start);
                if (timeLeft > 0) {
                    try {
                        this.wait(timeLeft/NANOS_PER_MILLI, (int)(timeLeft % NANOS_PER_MILLI));
                    } catch (InterruptedException ex) {
                        // Ignore and loop;
                    }
                } else {
                    throw new IOException("Wait for event '" + message
                            + "' took more than " + timeout);
                }
            }

        }
    }

    /**
     * Wait for the event, but time out with exception if it does not happen before the given instant.
     *
     * @param end
     * @throws IOException
     */
    public void await(Instant end) throws IOException {
        await(Duration.between(Instant.now(), end));
    }

    /**
     * Wait (forever) for the event.
     */
    public void await() {
        synchronized (this) {
            while (!eventFlag) {
                try {
                    this.wait();
                } catch (InterruptedException ex) {
                    // Ignore and loop;
                }
            }
        }
    }
}
