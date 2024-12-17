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

package org.gorpipe.gor.driver.providers.stream.sources;

import org.gorpipe.gor.driver.meta.SourceMetadata;

import java.io.IOException;
import java.util.Map;

/**
 * Metadata for stream sources.
 *
 * @author villi
 */
public class StreamSourceMetadata extends SourceMetadata {
    private final Long length;

    public StreamSourceMetadata(StreamSource source, String canonicalName, Long lastModified, Long linkLastModified,
                                Long length, String uniqueId) {
        super(source, canonicalName, lastModified, linkLastModified, uniqueId);
        this.length = length;
    }

    public StreamSourceMetadata(StreamSource source, String canonicalName, Long lastModified,
                                Long length, String uniqueId) {
        super(source, canonicalName, lastModified, uniqueId);
        this.length = length;
    }

    /**
     * Get length in bytes.
     * If length cannot be determined, this can be null.
     * Such a source would probably not be seekable.
     */
    public Long getLength() {
        return length;
    }

    @Override
    public StreamSource getSource() {
        return (StreamSource) super.getSource();
    }

    @Override
    public Map<String, String> attributes() throws IOException {
        Map<String, String> map = super.attributes();
        map.put("ByteLength", getLength().toString());
        return map;
    }

    /**
     * Get source url of index file.
     *
     * @return source url or null if unknown or not applicable
     */
    public String getIndexFileUrl() {
        return null;
    }


    /**
     * Get url for this datasource - using path representation if available.
     * E.g. if the canonical url uses id's without filenames but
     * there is an alternative representation available.
     *
     * @return
     */
    public String getNamedUrl() throws IOException {
        return getSource().getName();
    }
}
