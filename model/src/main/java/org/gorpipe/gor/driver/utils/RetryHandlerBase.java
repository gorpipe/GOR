package org.gorpipe.gor.driver.utils;

import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gorpipe.exceptions.GorException;
import org.gorpipe.exceptions.GorSystemException;
import java.util.concurrent.ExecutionException;

import static java.lang.Thread.sleep;

public abstract class RetryHandlerBase {

    public abstract <T> T perform(Action<T> action);

    public abstract void perform(ActionVoid action);

    public interface Action<T> {
        T perform();
    }

    public interface ActionVoid {
        void perform();
    }

    protected void threadSleep(long sleepMs, int tries, Throwable orginalException) {
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            // If interrupted waiting to retry, throw original exception
            Thread.currentThread().interrupt();
            throw new GorSystemException("Retry thread interrupted after " + tries + " retries", e);
        }
    }

    // Find the cause by ignoring ExecutionExceptions and GORExceptions (unless it is last one)
    protected Throwable getCause(Exception ex) {
        Throwable cause = ex;
        while (true) {
            if (cause.getCause() == null) {
                return cause;
            }

            if (cause instanceof ExecutionException
                    || cause instanceof UncheckedExecutionException
                    || cause instanceof GorException) {
                cause = cause.getCause();
            } else {
                return cause;
            }
        }
    }
}

