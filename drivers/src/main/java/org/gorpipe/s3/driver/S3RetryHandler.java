package org.gorpipe.s3.driver;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gorpipe.exceptions.GorException;
import org.gorpipe.exceptions.GorResourceException;
import org.gorpipe.gor.driver.utils.RetryHandlerWithFixedWait;

import java.io.FileNotFoundException;
import java.nio.file.FileSystemException;
import java.util.concurrent.ExecutionException;

public class S3RetryHandler extends RetryHandlerWithFixedWait {
    public S3RetryHandler(long initialDuration, long totalDuration) {
        super(initialDuration, totalDuration);
    }

    @Override
    protected void onHandleError(GorException e, long delay, int tries) {

        var path = "";

        if (e instanceof GorResourceException gre) {
            path = gre.getUri();
        }

        if (e.getCause() instanceof FileNotFoundException) {
            throw e;
        } else if (e.getCause() instanceof FileSystemException) {
            throw e;
        }

        if (e.getCause() instanceof ExecutionException || e.getCause() instanceof UncheckedExecutionException) {
            var t = e.getCause();

            if (t instanceof AmazonS3Exception awsException) {
                var detail = awsException.getMessage();
                if (awsException.getStatusCode() == 400) {
                    throw new GorResourceException(String.format("Bad request for resource. Detail: %s. Original message: %s", detail, e.getMessage()), path, e);
                } else if (awsException.getStatusCode() == 401) {
                    throw new GorResourceException(String.format("Unauthorized. Detail: %s. Original message: %s", detail, e.getMessage()), path, e);
                } else if (awsException.getStatusCode() == 403) {
                    throw new GorResourceException(String.format("Access Denied. Detail: %s. Original message: %s", detail, e.getMessage()), path, e);
                } else if (awsException.getStatusCode() == 404) {
                    throw new GorResourceException(String.format("Not Found. Detail: %s. Original message: %s", detail, e.getMessage()), path, e);
                }
            } else if (t instanceof SdkClientException) {
                throw new GorResourceException("Amazon SDK client exception", path, t);
            }
        }
    }
}
