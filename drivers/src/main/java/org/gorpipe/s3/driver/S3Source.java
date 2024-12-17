/*
 *  BEGIN_COPYRIGHT
 *
 *  Copyright (C) 2011-2013 deCODE genetics Inc.
 *  Copyright (C) 2013-2019 WuXi NextCode Inc.
 *  All Rights Reserved.
 *
 *  GORpipe is free software: you can redistribute it and/or modify
 *  it under the terms of the AFFERO GNU General Public License as published by
 *  the Free Software Foundation.
 *
 *  GORpipe is distributed "AS-IS" AND WITHOUT ANY WARRANTY OF ANY KIND,
 *  INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 *  NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR PURPOSE. See
 *  the AFFERO GNU General Public License for the complete license terms.
 *
 *  You should have received a copy of the AFFERO GNU General Public License
 *  along with GORpipe.  If not, see <http://www.gnu.org/licenses/agpl-3.0.html>
 *
 *  END_COPYRIGHT
 */

package org.gorpipe.s3.driver;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.apache.commons.lang3.StringUtils;
import org.carlspring.cloud.storage.s3fs.*;
import org.gorpipe.base.security.Credentials;
import org.gorpipe.base.streams.LimitedOutputStream;
import org.gorpipe.exceptions.GorResourceException;
import org.gorpipe.gor.binsearch.GorIndexType;
import org.gorpipe.gor.driver.meta.DataType;
import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.driver.meta.SourceType;
import org.gorpipe.gor.driver.providers.stream.RequestRange;
import org.gorpipe.gor.driver.providers.stream.sources.StreamSource;
import org.gorpipe.gor.table.util.PathUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Represents an object in Amazon S3.
 * Created by villi on 22/08/15.
 */
public class S3Source implements StreamSource {
    private static final boolean USE_META_CACHE = true ;
    private final SourceReference sourceReference;
    private final String bucket;
    private final String key;
    private final S3Client client;
    private S3FileSystem s3fs;
    private S3SourceMetadata meta;
    private static final Cache<String, S3SourceMetadata> metadataCache = CacheBuilder.newBuilder().concurrencyLevel(4).expireAfterWrite(5, TimeUnit.MINUTES).build();

    private S3Path path;

    private final static int MAX_S3_CHUNKS = 10000;
    private int writeChunkSize = Integer.parseInt(System.getProperty("gor.s3.write.chunksize", String.valueOf(1 << 26)));

    /**
     * Create source
     *
     * @param sourceReference contains S3 url of the form s3://bucket/objectpath
     */
    public S3Source(S3Client client, SourceReference sourceReference) throws MalformedURLException {
        this(client, sourceReference, S3Url.parse(sourceReference));
    }

    S3Source(S3Client client, SourceReference sourceReference, S3Url url) {
        this.client = client;
        this.sourceReference = sourceReference;
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
        if(append) throw new GorResourceException("S3 write not appendable",bucket+"/"+key);
        invalidateMeta();

        long maxFileSize = (long)writeChunkSize * (long)MAX_S3_CHUNKS;
        try {
            return
                    new LimitedOutputStream(
                    getPath().getFileSystem().provider().newOutputStream(getPath(),
                            append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING),
                    maxFileSize);
        } catch (IOException e) {
            throw new GorResourceException(getName(), getName(), e).retry();
        }
    }

    @Override
    public boolean supportsWriting() {
        return true;
    }

    private InputStream open(RequestRange range) {
        var reqBuilder = GetObjectRequest.builder().bucket(bucket).key(key);
        if (range!=null) {
            range = range.limitTo(getSourceMetadata().getLength());
            if (range.isEmpty()) return new ByteArrayInputStream(new byte[0]);
            reqBuilder.range(BytesRange.startLengthtoRange(range.getFirst(), range.getLength()));
        }
        return openRequest(reqBuilder.build());
    }

    private InputStream openRequest(GetObjectRequest request) {
        try {
            return new AbortingInputStream(client.getObject(request), request);
        } catch (SdkClientException e) {
            throw new GorResourceException("Failed to open S3 object: " + sourceReference.getUrl(), getPath().toString(), e).retry();
        }
    }

    @Override
    public String getName() {
        return sourceReference.getUrl();
    }

    private S3SourceMetadata loadMetadata(String bucket, String key) {
        if (USE_META_CACHE) {
            return loadMetadataFromCache(bucket, key);
        } else {
            return createMetaData(bucket, key);
        }
    }

    private S3SourceMetadata createMetaData(String bucket, String key) {
        try {
            var objectMetaResponse = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return new S3SourceMetadata(this, objectMetaResponse, sourceReference.getLinkLastModified());
        } catch (SdkClientException e) {
            throw new GorResourceException("Failed to load metadata for " + bucket + "/" + key, getPath().toString(), e).retry();
        }
    }

    private S3SourceMetadata loadMetadataFromCache(String bucket, String key) {
        try {
            return metadataCache.get(bucket + key, () -> {
                // TODO:  If the object does not exists we don't cache.  This method will throw exception and the loader will exit.
                return createMetaData(bucket, key);
            });
        } catch (ExecutionException | UncheckedExecutionException e) {
            var cause = e.getCause() != null ? e.getCause() : e;
            throw new GorResourceException("Failed to load metadata from cache for " + bucket + "/" + key, getPath().toString(), cause).retry();
        }
    }

    @Override
    public S3SourceMetadata getSourceMetadata() {
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
        try {
            // Note: fileExists only handles dirs if the end with /. Therefor we fall back to the much slower Files.exists.
            return fileExists() || Files.exists(getPath());
        } catch (Exception e) {
            Credentials cred = S3ClientFileSystemProvider.getCredentials(sourceReference.getSecurityContext(), "s3", bucket);
            throw new GorResourceException(String.format("Exists failed for %s, region: %s, access key: %s, secret key: %s",
                    getName(), client.serviceClientConfiguration().region(),
                    cred != null ? cred.getOrDefault(Credentials.Attr.KEY, "No key in creds") : "No creds",
                    cred != null ? (!StringUtils.isEmpty(cred.getOrDefault(Credentials.Attr.KEY, "")) ? "Has secret" : "Empty secret")
                                 : "No creds"),
                    getName(), e).retry();
        }
    }

    private boolean fileExists()  {
        try {
            // Already in cache, exists
            loadMetadata(bucket, key);
            return true;
        } catch (GorResourceException e) {
            if (e.getCause() != null && e.getCause() instanceof NoSuchKeyException) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public String createDirectory(FileAttribute<?>... attrs) {
        try {
            // Files.createDirectory needs elevated access to list all buckets.
            S3Path s3Path = getPath();
            String directoryKey = s3Path.getKey().endsWith("/") ? s3Path.getKey() : s3Path.getKey() + "/";
            PutObjectRequest request = (PutObjectRequest)PutObjectRequest.builder().bucket(s3Path.getBucketName()).key(directoryKey).cacheControl(s3Path.getFileSystem().getRequestHeaderCacheControlProperty()).contentLength(0L).build();
            client.putObject(request, RequestBody.fromBytes(new byte[0]));
            return PathUtils.formatUri(getPath().toUri());
        } catch (Exception e) {
            throw new GorResourceException(e.getMessage(), getPath().toString(), e).retry();
        }
    }

    @Override
    public String createDirectories(FileAttribute<?>... attrs) {
        try {
            return PathUtils.formatUri(Files.createDirectories(getPath()).toUri());
        } catch (IOException e) {
            throw GorResourceException.fromIOException(e, getPath()).retry();
        }
    }

    @Override
    public boolean isDirectory() {
        return key.endsWith("/") || Files.isDirectory(getPath());
    }

    @Override
    public void delete() {
        try {
            Files.deleteIfExists(getPath());  // Use if exists for folders (that are not reported existing if empty)
        } catch (IOException e) {
            throw GorResourceException.fromIOException(e, getPath()).retry();
        }
        invalidateMeta();
    }

    @Override
    public void deleteDirectory() {
        // Implementation based on S3FileSystemProvider.delete with different logic for deleting the prefix.
        Preconditions.checkArgument(getPath() instanceof S3Path,
                "path must be an instance of %s", S3Path.class.getName());

        if (Files.notExists(getPath())){
            return;
        }

        if (!Files.isDirectory(getPath())){
            throw new GorResourceException("the path: " + getPath() + " is not a directory", getPath().toString());
        }

        deleteAllWithPrefix();

        invalidateMeta();
    }

    private void deleteAllWithPrefix() {
        ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder()
                .bucket(bucket)
                .prefix(key)
                .build();

        ListObjectsResponse objectListing = client.listObjects(listObjectsRequest);

        List<ObjectIdentifier> idsToDelete = new ArrayList<>();
        for (var object : objectListing.contents()) {
            idsToDelete.add(ObjectIdentifier.builder().key(object.key()).build());
        }

        if (!idsToDelete.isEmpty()) {
            DeleteObjectsRequest deleteObjectsRequest = DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(idsToDelete).build())
                    .build();
            client.deleteObjects(deleteObjectsRequest);
        }
    }

    private void invalidateMeta() {
        meta = null;
        metadataCache.invalidate(bucket + key);
    }

    public String copy(S3Source dest) throws IOException {
        Files.copy(getPath(), dest.getPath());
        return getName();
    }

    @Override
    public Stream<String> list() {
        try {
            return Files.list(getPath()).map(this::s3SubPathToUriString);
        } catch (Exception e) {
            Credentials cred = S3ClientFileSystemProvider.getCredentials(sourceReference.getSecurityContext(), "s3", bucket);
            throw new GorResourceException(String.format("List failed for %s, region: %s, access key: %s, secret key: %s",
                    getName(), client.serviceClientConfiguration().region(),
                    cred != null ? cred.getOrDefault(Credentials.Attr.KEY, "No key in creds") : "No creds",
                    cred != null ? (!StringUtils.isEmpty(cred.getOrDefault(Credentials.Attr.KEY, "")) ? "Has secret" : "Empty secret")
                            : "No creds"),
                    getName(), e).retry();
            //throw GorResourceException.fromIOException(e, getPath()).retry();
        }
    }

    @Override
    public Stream<String> walk() {
        try {
            return Files.walk(getPath()).map(this::s3SubPathToUriString);
        } catch (IOException e) {
            throw GorResourceException.fromIOException(e, getPath()).retry();
        }
    }

    /**
     * Convert Path that we now is sub-path of this, to URI.
     */
    protected String s3SubPathToUriString(Path p) {
        if (p instanceof S3Path) {
            return String.format("s3://%s/%s", ((S3Path) p).getBucketName(), ((S3Path) p).getKey());
        }
        return PathUtils.formatUri(p.toUri());
    }

    @Override
    public S3Path getPath() {
        if (path == null) {
            path = getS3Path(bucket, key, client);
        }
        return path;
    }

    public S3Path getS3Path(String bucket, String key, S3Client client) {
        if (s3fs == null) {
            // NOTE:  We keep the old style of creating our own client objects and then create filesystems using that client.
            //        Could also stop creating our own client and just passing all the necessary information to the filesystem
            //        using S3FileSystemProvider.PROPS_TO_OVERLOAD and S3FileSystemProvider.getFileSystem(URI, Map<String, ?>).
            //        Then we could extract the client from the filesystem.
            s3fs = S3ClientFileSystemProvider.getInstance().getFileSystem(client);
        }

        return s3fs.getPath( "/" + bucket, key);
    }

    @Override
    public SourceType getSourceType() {
        return S3SourceType.S3;
    }

    @Override
    public GorIndexType useIndex() {
        return GorIndexType.CHROMINDEX;
    }

    @Override
    public void close() {
        // No resources to free
    }

    public S3Client getClient() {
        return client;
    }
}
