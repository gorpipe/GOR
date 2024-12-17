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

import org.gorpipe.gor.driver.providers.stream.sources.StreamSourceMetadata;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

public class S3SourceMetadata extends StreamSourceMetadata {

    private final HeadObjectResponse omd;

    public S3SourceMetadata(S3Source source, HeadObjectResponse md, Long linkLastModified) {
        super(source, source.getName(), md.lastModified().toEpochMilli(), linkLastModified, md.contentLength(), null);
        this.omd = md;
    }

    public S3SourceMetadata(S3Source source, HeadObjectResponse md) {
        super(source, source.getName(), md.lastModified().toEpochMilli(), md.contentLength(), null);
        this.omd = md;
    }

    @Override
    public Map<String, String> attributes() throws IOException {
        Map<String, String> map = super.attributes();
        map.put("S3.ContentMD5", omd.checksumSHA256());
        map.put("S3.ContentType", omd.contentType());
        map.put("S3.ContentEncoding", omd.contentEncoding());
        map.put("S3.ETag", omd.eTag());
        map.put("S3.VersionId", omd.versionId());
        map.put("S3.InstanceLength", String.valueOf(omd.contentLength()));
        Date exp = omd.expires() != null ? new Date( omd.expires().toEpochMilli()) : null;
        if (exp != null) {
            DateFormat df = DateFormat.getTimeInstance();
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            map.put("S3.ExpirationTime", df.format(exp));
        }
        return map;
    }
}
