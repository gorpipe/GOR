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

package org.gorpipe.gor.driver.providers.rows.sources.sql;

import org.gorpipe.exceptions.GorResourceException;
import org.gorpipe.gor.driver.meta.DataType;
import org.gorpipe.gor.driver.meta.SourceMetadata;
import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.driver.meta.SourceType;
import org.gorpipe.gor.driver.providers.rows.RowIteratorSource;
import org.gorpipe.gor.driver.providers.rows.sources.db.DbScope;
import org.gorpipe.gor.model.DbConnection;
import org.gorpipe.gor.model.GenomicIterator;
import org.gorpipe.gor.model.StreamWrappedGenomicIterator;
import org.gorpipe.gor.table.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class SqlSource extends RowIteratorSource {
    private static final String PROJECT_ID = "project_id";
    private static final String ORGANIZATION_ID = "organization_id";
    private static final Logger log = LoggerFactory.getLogger(SqlSource.class);
    private SqlInfo sqlInfo;

    public SqlSource(SourceReference sourceReference) {
        super(sourceReference);

        // Validate sql statement
        if (!this.sourceReference.getUrl().toLowerCase().contains("select")) {
            throw new GorResourceException("Invalid sql source, expected data on the form sql://[sql statement], but got " + sourceReference.toString(), sourceReference.toString());
        }
    }

    @Override
    public GenomicIterator open() {
        var sql = getSqlfromSource();
        sqlInfo = getInfoFromSql(sql);

        String header = null;

        if (sqlInfo.hasHeader()) {
            header = "#" + String.join("\t", sqlInfo.columns());
        }

        Object[] constants = new Object[]{};
        var scopes = DbScope.parse(sourceReference.securityContext);
        if (!scopes.isEmpty()) {
            ArrayList<Object> constantsList = new ArrayList<>();
            for (var s : scopes) {
                if (s.getColumn().equalsIgnoreCase(PROJECT_ID)) {
                    constantsList.add(s.getValue());
                } else if (s.getColumn().equalsIgnoreCase(ORGANIZATION_ID)) {
                    constantsList.add(s.getValue());
                }
            }
            constants = constantsList.toArray();
        }

        var dataStream = DbConnection.getDBLinkStream(sqlInfo.statement(), constants, sqlInfo.database());
        return new StreamWrappedGenomicIterator(dataStream, header, true, true);
    }



    @Override
    public GenomicIterator open(String filter) {
        return null;
    }

    @Override
    public boolean supportsFiltering() {
        return false;
    }

    @Override
    public String getName() {
        return sourceReference.getUrl();
    }

    @Override
    public SourceType getSourceType() {
        return SqlSourceType.SQL;
    }

    @Override
    public DataType getDataType() {
        return DataType.fromFileName(sourceReference.getUrl());
    }

    @Override
    public boolean exists() {
        return true;
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

        if (sqlInfo == null) {
            var sql = getSqlfromSource();
            sqlInfo = getInfoFromSql(sql);
        }

        var table = sqlInfo.table();
        var database = sqlInfo.database();

        if (database == null) {
            database = DbConnection.DEFAULT_DBSOURCE;
        }

        if (table.length() > 0 && database.length() > 0) {
            final DbConnection dbsource = DbConnection.lookup(database);
            if (dbsource != null) {
                timestamp = dbsource.queryDefaultTableChange(table);
            }
        }
        return new SourceMetadata(this, sourceReference.getUrl(), timestamp, null, false);
    }

    @Override
    public SourceReference getSourceReference() {
        return sourceReference;
    }

    private String getSqlfromSource() {
        String resolvedUrl = PathUtils.fixDbSchema(sourceReference.getUrl());

        if (!SqlSourceType.SQL.match(resolvedUrl)) {
            throw new GorResourceException("SQLSource: content must start with a valid sql source type", resolvedUrl);
        }

        return resolvedUrl.substring(6);
    }

    private SqlInfo getInfoFromSql(String sql) {
        sqlInfo = SqlInfo.parse(sql);

        if (sqlInfo.columns().length == 0) {
            throw new GorResourceException("SQLSource: no columns specified", sql);
        }

        if (sqlInfo.table().length() == 0) {
            throw new GorResourceException("SQLSource: no table specified", sql);
        }

        return sqlInfo;
    }
}
