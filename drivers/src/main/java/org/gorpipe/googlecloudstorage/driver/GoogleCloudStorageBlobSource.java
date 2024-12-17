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

package org.gorpipe.googlecloudstorage.driver;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.Lists;
import org.gorpipe.base.config.ConfigManager;
import org.gorpipe.exceptions.GorResourceException;
import org.gorpipe.gor.driver.meta.DataType;
import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.driver.meta.SourceReferenceBuilder;
import org.gorpipe.gor.driver.meta.SourceType;
import org.gorpipe.gor.driver.providers.stream.RequestRange;
import org.gorpipe.gor.driver.providers.stream.sources.StreamSource;
import org.gorpipe.gor.driver.providers.stream.sources.StreamSourceMetadata;
import org.gorpipe.gor.servers.GorConfig;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;

/**
 * Represents an object in Google Cloud Storage.
 * Created by simmi on 20/02/18.
 */
public class GoogleCloudStorageBlobSource implements StreamSource {
    private final SourceReference sourceReference;
    private final String bucket;
    private final String key;
    private String json;
    Blob cb;
    private InputStream is;

    /**
     * Create source
     *
     * @param sourceReference Google Cloud Storage url of the form gs://bucket/objectpath
     */
    public GoogleCloudStorageBlobSource(SourceReference sourceReference) {
        this(sourceReference, null);
    }

    /**
     * Create source
     *
     * @param sourceReference Google Cloud Storage url of the form gs://bucket/objectpath
     */
    public GoogleCloudStorageBlobSource(SourceReference sourceReference, String json) {
        this.sourceReference = sourceReference;
        String[] parsed = GoogleCloudStorageBlobHelper.parseUrl(this.sourceReference.getUrl());
        bucket = parsed[0];
        key = parsed[1];
        this.json = json;
        init();
    }

    /**
     * Create source
     *
     * @param bucket Google Cloud Storage bucket name
     * @param key    Object key (path)
     */
    public GoogleCloudStorageBlobSource(String bucket, String key) {
        this.bucket = bucket;
        this.key = key;
        this.sourceReference = new SourceReferenceBuilder(GoogleCloudStorageBlobHelper.makeUrl(bucket, key)).build();
        init();
    }

    private void init() {
        Storage storage;

        GorConfig gorConfig = ConfigManager.getPrefixConfig("gor", GorConfig.class);
        String googleApplicationCredentials = gorConfig.googleApplicationCredentials();
        if( json == null && googleApplicationCredentials != null && googleApplicationCredentials.length() > 0 ) {
            json = googleApplicationCredentials;
        }

        if( json == null ) {
            storage = GoogleCloudStorageBlobHelper.getGoogleCloudStorage();
        } else {
            GoogleCredentials credentials;
            try {
                credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(json.getBytes())).createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
            } catch (IOException e) {
                throw new RuntimeException("Unable to read credentials from stream", e);
            }
            storage = StorageOptions.newBuilder().setCredentials(credentials).build().getService();
        }

        cb = storage.get(bucket, key);
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
        ReadChannel rc = cb.reader();
        if( range != null ) {
            try {
                rc.seek(range.getFirst());
            } catch (IOException e) {
                throw GorResourceException.fromIOException(e, getPath());
            }
        }
        close();
        is = Channels.newInputStream(rc);
        return is;
    }

    @Override
    public String getName() {
        return sourceReference.getUrl();
    }

    @Override
    public StreamSourceMetadata getSourceMetadata() {
        long length = cb.getSize();
        long lastModified = cb.getCreateTimeOffsetDateTime().toInstant().toEpochMilli();
        return new StreamSourceMetadata(this, getName(), lastModified, length, cb.getEtag());
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
        return cb != null && cb.exists();
    }

    @Override
    public SourceType getSourceType() {
        return GoogleCloudStorageSourceType.GOOGLE_CLOUD_STORAGE_SOURCE_TYPE;
    }

    @Override
    public void close() {
        try {
            if (is != null) is.close();
        } catch (IOException e) {
            throw GorResourceException.fromIOException(e, getPath());
        }
    }
}
