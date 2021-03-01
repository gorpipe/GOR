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

package org.gorpipe.gor.manager;

import org.gorpipe.gor.table.BaseTable;
import org.gorpipe.gor.table.BucketableTableEntry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Helper class to create bucket files from bucket descriptions.
 */
public interface BucketCreator<T extends BucketableTableEntry> {

    /**
     * Create bucket files.
     *
     * @param table           table to create bucket for.  Should not change while running this could be a
     *                        copy of the original table.
     * @param bucketsToCreate map with bucket name to table entries, representing the buckets to be created.
     * @param absBucketDir    absolute path to the bucket dir, where bucket files should be put.  It temp folders are to be used they should
     *                        be created in this dir (for fast file move).
     */
    void createBuckets(BaseTable<T> table, Map<Path, List<T>> bucketsToCreate,
                              Path absBucketDir) throws IOException;

}
