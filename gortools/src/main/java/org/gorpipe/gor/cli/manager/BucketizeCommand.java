/*
 *  BEGIN_COPYRIGHT
 *
 *  Copyright (C) 2011-2013 deCODE genetics Inc.
 *  Copyright (C) 2013-2021 WuXi NextCode Inc.
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

package org.gorpipe.gor.cli.manager;

import org.gorpipe.gor.manager.BucketManager;
import org.gorpipe.gor.manager.TableManager;
import picocli.CommandLine;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@CommandLine.Command(name = "bucketize",
        aliases = {"b"},
        description="Bucketize dictionary/table.",
        header="Bucketize dictionary/table.")
public class BucketizeCommand extends CommandBucketizeOptions implements Runnable{

    @CommandLine.Option(names = {"-d", "--bucket_dirs"},
            split = ",",
            description = "Directories to put the bucket files in, either absolute path or relative to the table dir.  " +
                    "The directories must exists and be writable.  Values are specified as comma separated list.  " +
                    "Dafault: .<table name>.buckets")
    private List<String> bucketDirs = new ArrayList<>();

    @CommandLine.Option(names = {"--max_bucket_count"},
            description = "Maximum number of buckets created in this call to bucketize.  No limit if less than 0. Default: " + BucketManager.DEFAULT_MAX_BUCKET_COUNT)
    private int maxBucketCount = BucketManager.DEFAULT_MAX_BUCKET_COUNT;

    @Override
    public void run() {
        TableManager tm = TableManager.newBuilder().minBucketSize(this.minBucketSize).bucketSize(this.bucketSize)
                .useHistory(!nohistory).lockTimeout(Duration.ofSeconds(lockTimeout))
                .build();
        tm.bucketize(dictionaryFile.toPath(), this.bucketPackLevel, this.workers, this.maxBucketCount, bucketDirs.stream().map(b -> Paths.get(b)).collect(Collectors.toList()));
    }
}
