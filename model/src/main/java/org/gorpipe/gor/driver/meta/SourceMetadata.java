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

package org.gorpipe.gor.driver.meta;

import org.gorpipe.gor.driver.DataSource;
import org.gorpipe.gor.util.Util;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Source metadata (name, timestamps etc).
 * Created by villi on 22/08/15.
 */
public class SourceMetadata {
    private final String canonicalName;
    private final Long lastModified;
    private final Long linkLastModified;
    private final String uniqueId;
    private final DataSource source;

    /**
     * @param canonicalName Canonical name of this source
     * @param lastModified  Last modified - see getLastModified()
     * @param linkLastModified  Link Last modified
     * @param uniqueId      See uniqueId. If this is null, it will be generated from canonicalName and lastModified
     */
    public SourceMetadata(DataSource source, String canonicalName, Long lastModified, Long linkLastModified, String uniqueId) {
        this.source = source;
        this.canonicalName = canonicalName;
        this.lastModified = lastModified;
        this.linkLastModified = linkLastModified;
        if (uniqueId == null && canonicalName != null) {
            this.uniqueId = Util.md5(canonicalName + (lastModified != null ? ":" + lastModified : ""));
        } else {
            this.uniqueId = uniqueId;
        }
    }

    /**
     * @param canonicalName Canonical name of this source
     * @param lastModified  Last modified - see getLastModified()
     * @param uniqueId      See uniqueId. If this is null, it will be generated from canonicalName and lastModified
     */
    public SourceMetadata(DataSource source, String canonicalName, Long lastModified, String uniqueId) {
        this(source, canonicalName, lastModified, lastModified, uniqueId);
    }

    /**
     * Get last modified timestamp (number of milliseconds since January 1, 1970, 00:00:00 GMT)
     * This can be null if timestamp cannot be determined.
     */
    public Long getLastModified() {
        return lastModified;
    }

    public Long getLinkLastModified() {
        return linkLastModified;
    }

    /**
     * Get full canonical name of file. This should include the URL if it is an external resource.
     * It should fully expand all links, symbolic links and link files so two references to the same source data
     * should (if possible) always give the same canonical name.
     */
    public String getCanonicalName() {
        return canonicalName;
    }

    /**
     * Get unique identifier of this source. It should be guaranteed that two sources that hold different data will
     * return different unique identifiers. If the source data changed, this identifier must change.
     * If this cannot be guaranteed, the method should return null
     * <p>
     * If possible it returns an md5 sum as an identifier for the file. Indeed the md5
     * sum is not absolutely unique but for our using purposes it is good enough.
     *
     * @return Unique identifier or null
     */
    public String getUniqueId() {
        return uniqueId;
    }

    public DataSource getSource() {
        return source;
    }

    /**
     * Get meta attributes as key/value pairs
     */
    public Map<String, String> attributes() throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Name", source.getName());
        map.put("SourceType", source.getSourceType().getName());
        DataType dataType = source.getDataType();
        if (dataType != null) {
            map.put("DataType", dataType.name());
        } else {
            map.put("DataType", "UNKNOWN");
        }
        map.put("CanonicalName", getCanonicalName());
        map.put("UniqueId", getUniqueId());
        Long modified = getLastModified();
        map.put("LastModified", "" + modified);
        if (modified != null) {
            DateFormat df = DateFormat.getDateTimeInstance();
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            map.put("LastModifiedUtc", df.format(new Date(modified)));
        }
        return map;
    }
}
