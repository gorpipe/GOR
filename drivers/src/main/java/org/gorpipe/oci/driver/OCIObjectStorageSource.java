package org.gorpipe.oci.driver;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.model.Range;
import com.oracle.bmc.objectstorage.ObjectStorageAsync;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.HeadObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.HeadObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import org.gorpipe.base.streams.LimitedOutputStream;
import org.gorpipe.exceptions.GorException;
import org.gorpipe.exceptions.GorResourceException;
import org.gorpipe.exceptions.GorSystemException;
import org.gorpipe.gor.binsearch.GorIndexType;
import org.gorpipe.gor.driver.meta.DataType;
import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.driver.meta.SourceType;
import org.gorpipe.gor.driver.providers.stream.RequestRange;
import org.gorpipe.gor.driver.providers.stream.sources.StreamSource;
import org.gorpipe.gor.driver.providers.stream.sources.StreamSourceMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.*;
import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Represents an object in OCI.
 */
public class OCIObjectStorageSource implements StreamSource {
    final static Logger log = LoggerFactory.getLogger(OCIObjectStorageSource.class);

    private static final boolean USE_META_CACHE = true ;
    private final SourceReference sourceReference;
    private final OCIUrl url;
    private final String bucket;
    private final String key;
    private final String namespace;
    private final ObjectStorageAsync client;
    private StreamSourceMetadata meta;

    private static final Cache<String, StreamSourceMetadata> metadataCache
            = CacheBuilder.newBuilder().concurrencyLevel(4).expireAfterWrite(5, TimeUnit.MINUTES).build();

    private final static int MAX_CHUNKS = 10000;
    private int writeChunkSize = Integer.parseInt(System.getProperty("gor.s3.write.chunksize", String.valueOf(1 << 26)));

    /**
     * Create source
     *
     * @param sourceReference contains oci url
     */
    public OCIObjectStorageSource(ObjectStorageAsync client, SourceReference sourceReference) throws MalformedURLException {
        this.client = client;
        this.sourceReference = sourceReference;
        this.url = OCIUrl.parse(sourceReference);
        this.namespace = url.getNamespace();
        this.bucket = url.getBucket();
        this.key = url.getPath();
    }

    @Override
    public InputStream open() {
        return open(null);
    }

    @Override
    public InputStream open(long start) {
        return open(RequestRange.fromFirstLength(start, getSourceMetadata().getLength()));
    }

    @Override
    public InputStream open(long start, long minLength) {
        return open(RequestRange.fromFirstLength(start, minLength));
    }

    @Override
    public OutputStream getOutputStream(boolean append) {
        if(append) throw new GorResourceException("OCI object store write not appendable",bucket+"/"+key);
        invalidateMeta();

        long maxFileSize = (long)writeChunkSize * (long)MAX_CHUNKS;

        try {
            StreamBroker broker = new StreamBroker();
            var request =
                    PutObjectRequest.builder()
                            .namespaceName(namespace)
                            .bucketName(bucket)
                            .objectName(key)
                            .putObjectBody(broker.getInputStream())
                            .build();

            Future<PutObjectResponse> future = client.putObject(request, null);
            broker.getOutputStream().setFuture(future);
            return  new LimitedOutputStream(broker.getOutputStream(), maxFileSize);
        } catch (Exception e) {
            throw new GorResourceException(getName(), getName(), e).retry();
        }
    }

    @Override
    public boolean supportsWriting() {
        return true;
    }

    private InputStream open(RequestRange range) {
        var requestBuilder =
                GetObjectRequest.builder()
                        .namespaceName(namespace)
                        .bucketName(bucket)
                        .objectName(key);

        if (range != null) {
            range = range.limitTo(getSourceMetadata().getLength());
            if (range.isEmpty()) return new ByteArrayInputStream(new byte[0]);
            requestBuilder.range(new Range(range.getFirst(), range.getLast()));
        }

        return openRequest(requestBuilder.build());
    }

    private InputStream openRequest(GetObjectRequest request) {
        try {
            Future<GetObjectResponse> fut = client.getObject(request, null);
            var response = fut.get();
            return response.getInputStream();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GorSystemException(e);
        } catch (Exception e) {
            Throwable ex = e;
            if (e instanceof ExecutionException || e instanceof UncheckedExecutionException) {
                ex = e.getCause();
            }
            throw new GorResourceException("Failed to open S3 object: " + sourceReference.getUrl(), sourceReference.getUrl(), ex).retry();
        }
    }

    @Override
    public String getName() {
        return sourceReference.getUrl();
    }

    private StreamSourceMetadata loadMetadata(String bucket, String key) {
        if (USE_META_CACHE) {
            return loadMetadataFromCache(bucket, key);
        } else {
            return createMetaData(bucket, key);
        }
    }

    private StreamSourceMetadata createMetaData(String bucket, String key) {
        try {
            HeadObjectRequest request =
                    HeadObjectRequest.builder()
                            .namespaceName(namespace)
                            .bucketName(bucket)
                            .objectName(key)
                            .build();
            HeadObjectResponse getResponse = client.headObject(request, null).get();
            return new StreamSourceMetadata(
                    this,
                    getName(),
                    getResponse.getLastModified().toInstant().toEpochMilli(),
                    getResponse.getContentLength(),
                    null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GorSystemException(e);
        } catch (Exception e) {
            Throwable ex = e;
            if (e instanceof ExecutionException || e instanceof UncheckedExecutionException) {
                ex = e.getCause();
            }
            throw new GorResourceException("Failed to load metadata for " + bucket + "/" + key, getName(), ex).retry();
        }
    }

    private StreamSourceMetadata loadMetadataFromCache(String bucket, String key) {
        try {
            return metadataCache.get(bucket + key, () -> createMetaData(bucket, key));
        } catch (ExecutionException | UncheckedExecutionException e) {
            var cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof GorException) {
                throw (GorException) cause;
            }
            throw new GorResourceException("Failed to load metadata from cache for " + bucket + "/" + key, getName(), cause).retry();
        }
    }

    @Override
    public StreamSourceMetadata getSourceMetadata() {
        if (meta == null) {
            meta = loadMetadata(bucket, key);
        }
        return meta;
    }

    @Override
    public SourceReference getSourceReference() {
        return sourceReference;
    }

    @Override
    public DataType getDataType() {
        return DataType.fromFileName(key);
    }

    @Override
    public boolean exists() {
        // Implemented by loading metadata (which has retry).
        return fileExists();
    }

    private boolean fileExists()  {
        try {
            // Already in cache, exists
            meta = loadMetadata(bucket, key);
            return true;
        } catch (GorResourceException e) {
            if (e.getCause() != null && (
                    e.getCause() instanceof NoSuchKeyException
            || e.getCause() instanceof BmcException && ((BmcException) e.getCause()).getStatusCode() == 404)) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void delete() {
        try {
            DeleteObjectRequest request =
                    DeleteObjectRequest.builder()
                            .namespaceName(namespace)
                            .bucketName(bucket)
                            .objectName(key)
                            .build();
            DeleteObjectResponse getResponse = client.deleteObject(request, null).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GorSystemException(e);
        } catch (Exception e) {
            Throwable ex = e;
            if (e instanceof ExecutionException || e instanceof UncheckedExecutionException) {
                ex = e.getCause();
            }
            throw new GorResourceException("Failed to delete " + getName(), getName(), e).retry();
        }

        invalidateMeta();
    }

    private void invalidateMeta() {
        meta = null;
        metadataCache.invalidate(bucket + key);
    }

    @Override
    public SourceType getSourceType() {
        return OCIObjectStorageSourceType.OCI_OBJECT_STORAGE;
    }

    @Override
    public GorIndexType useIndex() {
        return GorIndexType.CHROMINDEX;
    }

    @Override
    public void close() {
        // No resources to free
    }
}
