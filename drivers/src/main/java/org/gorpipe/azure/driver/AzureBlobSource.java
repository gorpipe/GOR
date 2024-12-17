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

package org.gorpipe.azure.driver;

import java.net.HttpURLConnection;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobStorageException;
import org.gorpipe.exceptions.GorResourceException;
import org.gorpipe.exceptions.GorSystemException;
import org.gorpipe.exceptions.GorResourceException;
import org.gorpipe.gor.driver.meta.DataType;
import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.driver.meta.SourceReferenceBuilder;
import org.gorpipe.gor.driver.meta.SourceType;
import org.gorpipe.gor.driver.providers.stream.RequestRange;
import org.gorpipe.gor.driver.providers.stream.sources.StreamSource;
import org.gorpipe.gor.driver.providers.stream.sources.StreamSourceMetadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;

/**
 * Represents an object in Microsoft Azure.
 * Created by simmi on 10/09/15.
 */
public class AzureBlobSource implements StreamSource {
    private final SourceReference sourceReference;
    private final String bucket;
    private final String key;

    private BlobClient client = null;

    /**
     * Create source
     *
     * @param sourceReference azure url of the form az://bucket/objectpath
     */
    public AzureBlobSource(SourceReference sourceReference) {
        this.sourceReference = sourceReference;
        String[] parsed = AzureBlobHelper.parseUrl(this.sourceReference.getUrl());
        bucket = parsed[0];
        key = parsed[1];
    }

    /**
     * Create source
     *
     * @param bucket azure bucket name
     * @param key    Object key (path)
     */
    public AzureBlobSource(String bucket, String key, String subset) {
        this.bucket = bucket;
        this.key = key;
        this.sourceReference = new SourceReferenceBuilder(AzureBlobHelper.makeUrl(bucket, key))
                .build();
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

    private InputStream open(RequestRange range) {
        if (range!=null) {
            range = range.limitTo(getSourceMetadata().getLength());
            if (range.isEmpty()) return new ByteArrayInputStream(new byte[0]);

        }

        try {
            var client = createClient();
            if (range == null) {
                return client.openInputStream();
            } else {
                return client.openInputStream(
                        new BlobRange(range.getFirst(), range.getLast() - range.getFirst() + 1),
                        null);
            }
        } catch (Throwable t){
            handleExceptions(t);
        }

        return null;
    }

    private void handleExceptions(Throwable t) {
        var url = sourceReference.getUrl();
        if (t instanceof BlobStorageException bse) {
            handleBlobStorageException(bse);
        } else if (t instanceof URISyntaxException) {
            throw new GorResourceException("Invalid azure url", url, t);
        } else if (t instanceof InvalidKeyException) {
            throw new GorSystemException("Invalid azure key for url: " + url, t);
        } else if (t.getCause() instanceof IllegalArgumentException) {
            throw new GorSystemException("Invalid autorization key: " + url, t.getCause());
        } else if (t.getCause() instanceof UnknownHostException) {
            throw new GorSystemException("Unknown host: " + url, t.getCause());
        }
        else {
            throw new GorSystemException("Error reading file: " + url, t);
        }
    }

    private void handleBlobStorageException(BlobStorageException t) {
        var url = sourceReference.getUrl();
        if (t.getStatusCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
            throw new GorResourceException(
                    String.format("Bad request for resource. Detail: %s. Original message: %s", url, t.getMessage()),
                    url, t);
        } else if (t.getStatusCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
            throw new GorResourceException(
                    String.format("Unauthorized. Detail: %s. Original message: %s", url, t.getMessage()),
                    url, t);
        } else if (t.getStatusCode() == HttpURLConnection.HTTP_FORBIDDEN) {
            throw new GorResourceException(
                    String.format("Access Denied. Detail: %s. Original message: %s", url, t.getMessage()),
                    url, t);
        } else if (t.getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            throw new GorResourceException(
                    String.format("Not Found. Detail: %s. Original message: %s", url, t.getMessage()),
                    url, t);
        } else {
            throw new GorResourceException(
                    String.format("Resource error. Detail: %s. Original message: %s", url, t.getMessage()),
                    url, t);
        }
    }

    @Override
    public String getName() {
        return sourceReference.getUrl();
    }

    @Override
    public StreamSourceMetadata getSourceMetadata() {
        try {
            var client = createClient();
            var props = client.getProperties();
            long length = props.getBlobSize();
            long lastModified = props.getLastModified().toEpochSecond();
            var tag = props.getETag();

            return new StreamSourceMetadata(this, getName(), lastModified, length, tag);
        } catch (Throwable t) {
            handleExceptions(t);
        }

        return null;
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
            createClient();
        } catch (Throwable t) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public SourceType getSourceType() {
        return AzureSourceType.AZURE;
    }

    @Override
    public void close() {
        // No resources to free
    }

    private BlobClient createClient() {
        if (client == null) {
            client = AzureBlobHelper.getAzure(bucket, key);
        }

        return client;
    }
}
