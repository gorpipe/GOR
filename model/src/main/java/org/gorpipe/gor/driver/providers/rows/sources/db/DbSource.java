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

package org.gorpipe.gor.driver.providers.rows.sources.db;

import org.gorpipe.exceptions.GorResourceException;
import org.gorpipe.gor.driver.meta.DataType;
import org.gorpipe.gor.driver.meta.SourceMetadata;
import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.driver.meta.SourceType;
import org.gorpipe.gor.driver.providers.rows.RowIteratorSource;
import org.gorpipe.gor.model.DbConnection;
import org.gorpipe.gor.model.DbGenomicIterator;
import org.gorpipe.gor.model.GenomicIterator;
import org.gorpipe.gor.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Represents a data source accessed through a database.
 * <p>
 * Created by gisli on 26/02/16.
 */
public class DbSource extends RowIteratorSource {
    private static final Logger log = LoggerFactory.getLogger(DbSource.class);

    private final String databaseSource;
    private String chrColName = "chromo";
    private String posColName = "pos";
    private final String tableName;

    public DbSource(SourceReference sourceReference) {
        super(sourceReference);

        // Validate the url.
        URI parsedUri;
        try {
            parsedUri = new URI(this.sourceReference.getUrl());
        } catch (URISyntaxException e) {
            throw new GorResourceException("Invalid db source URI, Should be a valid uri", this.sourceReference.toString(), e);
        }
        String protocol = parsedUri.getScheme().toLowerCase();
        if (!protocol.equals("db")) {
            throw new GorResourceException("Invalid db source protocol, parsing " + this.sourceReference + " - expected protocol to be db", this.sourceReference.toString());
        }

        // Parse the url

        // The URI is not standard as it contains : and hence we need special parsing.
        String url = sourceReference.getUrl();
        final int colonIdx = url.indexOf(':', 6);
        if (colonIdx <= 0) {
            throw new GorResourceException("Invalid db source, expected data on the form db://source:rowset[(chr-col, pos-col)], but got " + url, url);
        }
        databaseSource = url.substring(5, colonIdx);
        final int parenthIdx = url.lastIndexOf('(');
        int rowsetend = url.length();
        if (parenthIdx > 0) {
            final String[] parts = StringUtil.splitToArray(url.substring(parenthIdx + 1, url.length() - 1), ',');
            chrColName = parts[0];
            posColName = parts[1];
            rowsetend = parenthIdx;
        }
        tableName = url.substring(colonIdx + 1, rowsetend);
        if (!exists()) {
            throw new GorResourceException("Database table not found: Unable to lookup table with name " + tableName + " for data source " + databaseSource, databaseSource);
        }
    }

    @Override
    public GenomicIterator open() {
        return this.open(null);
    }

    public GenomicIterator open(String filter) {
        List<DbScope> dbScopes = DbScope.parse(this.sourceReference.getSecurityContext());

        return new DbGenomicIterator(this.sourceReference.getLookup(), databaseSource, tableName, chrColName, posColName,
                dbScopes, sourceReference.securityContext);
    }


    public boolean supportsFiltering() {
        return false;
    }

    @Override
    public String getName() {
        return sourceReference.getUrl();
    }

    @Override
    public SourceType getSourceType() {
        return DbSourceType.DB;
    }

    @Override
    public DataType getDataType() {
        return DataType.GOR;
    }

    @Override
    public boolean exists() {
        if (tableName != null) {
            final DbConnection dbsource = DbConnection.systemConnections.lookup(databaseSource);
            if (dbsource == null) {
                log.warn("Database not found: {}", databaseSource);
                return false;
            }
            return dbsource.queryTableExists(tableName);
        }
        log.debug("Table queried does not exist tableName {}, databaseSource {}, sourceReference URL {}", tableName, databaseSource, sourceReference.url);
        return false;
    }

    @Override
    public void close() {
        // No action needed
    }

    @Override
    public boolean supportsLinks() {
        return false;
    }

    @Override
    public SourceMetadata getSourceMetadata() {
        long timestamp = System.currentTimeMillis();
        if (tableName != null) {
            final DbConnection dbsource = DbConnection.systemConnections.lookup(databaseSource);
            if (dbsource != null) {
                timestamp = dbsource.queryDefaultTableChange(tableName);
            }
        }
        return new SourceMetadata(this, sourceReference.getUrl(), timestamp, null);
    }

    @Override
    public SourceReference getSourceReference() {
        return sourceReference;
    }

    @Override
    public boolean isDirect() {
        return false;
    }
}
