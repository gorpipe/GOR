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

package org.gorpipe.gor.driver.providers.rows.sources.mem;

import org.gorpipe.gor.driver.meta.DataType;
import org.gorpipe.gor.driver.meta.SourceMetadata;
import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.driver.meta.SourceType;
import org.gorpipe.gor.driver.providers.rows.RowIteratorSource;
import org.gorpipe.gor.model.GenomicIterator;
import org.gorpipe.gor.util.Util;

/**
 * Represents a data source accessed through file system.
 * <p>
 * Created by villi on 22/08/15.
 */
public class MemSource extends RowIteratorSource {

    /**
     * Name of file.  This should be the full path to the file.
     */
    public MemSource(SourceReference sourceReference) {
        super(sourceReference);
    }

    @Override
    public GenomicIterator open() {
        return open(null);
    }

    @Override
    public GenomicIterator open(String filter) {
        return new MemGenomicIterator(sourceReference.getLookup(), 4000);
    }

    @Override
    public boolean supportsFiltering() {
        return true;
    }

    @Override
    public SourceMetadata getSourceMetadata() {
        return new SourceMetadata(this, getName(), 0L, Util.md5(getName()));
    }

    @Override
    public String getName() {
        return sourceReference.getUrl();
    }

    @Override
    public SourceType getSourceType() {
        return MemSourceType.MEM;
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
    public SourceReference getSourceReference() {
        return sourceReference;
    }

    @Override
    public void close() {
    }
}
