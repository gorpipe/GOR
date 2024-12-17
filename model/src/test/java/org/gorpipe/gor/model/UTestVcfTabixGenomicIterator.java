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

package org.gorpipe.gor.model;

import gorsat.TestUtils;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.index.tabix.TabixIndex;
import htsjdk.variant.vcf.VCFCodec;
import org.gorpipe.gor.driver.meta.DataType;
import org.gorpipe.gor.driver.providers.stream.sources.file.FileSource;
import org.gorpipe.gor.util.DataUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UTestVcfTabixGenomicIterator {
    Path tabixIndexPath;
    TabixIndex ti;
    FileSource fs;
    FileSource fi;
    ChromoLookup cl;
    String ipath = DataUtil.toFile(DataUtil.toFile( "../tests/data/external/samtools/testTabixIndex", DataType.VCFGZ), DataType.TBI);

    @Before
    public void init() throws IOException {
        tabixIndexPath = Paths.get(ipath);
        cl = new DefaultChromoLookup();
        String path = DataUtil.toFile( "../tests/data/external/samtools/testTabixIndex", DataType.VCFGZ);
        fs = new FileSource(path);
        ti = IndexFactory.createTabixIndex(new File(path), new VCFCodec(), null);
        ti.write(tabixIndexPath);
        fi = new FileSource(ipath);
    }

    @Test
    public void test() {
        final String query = "gor -p chr2 ../tests/data/external/samtools/testTabixIndex.vcf.gz";
        final String results = TestUtils.runGorPipe(query);
        System.err.println(results);
    }

    @Test
    public void testVcfTabixHasNext() throws IOException {
        try(VcfGzTabixGenomicIterator vcfit = new VcfGzTabixGenomicIterator(cl, fs, fi)) {
            Assert.assertTrue("Tabix indexed vcf file with no lines", vcfit.hasNext());
        }
    }

    @Test
    public void testVcfTabixNext() throws IOException {
        try (VcfGzTabixGenomicIterator vcfit = new VcfGzTabixGenomicIterator(cl, fs, fi)) {
            vcfit.hasNext();
            Assert.assertEquals("Wrong line from tabix indexed vcf file", "chr1\t327\t.\tT\tC\t666.18\tGATK_STANDARD;HARD_TO_VALIDATE\tAB=0.74;AC=3;AF=0.50;AN=6;DB=0;DP=936;Dels=0.00;HRun=3;MQ=34.66;MQ0=728;QD=0.71;SB=-268.74;set=filteredInBoth\tGT:DP:GQ\t1/0:10:62.65\t1/0:37:99.00\t1/0:53:99.00", vcfit.next().toString());
        }
    }

    @Test
    public void testVcfTabixAll() throws IOException {
        try (VcfGzTabixGenomicIterator vcfit = new VcfGzTabixGenomicIterator(cl, fs, fi)) {
            StringBuilder sb = new StringBuilder();
            vcfit.forEachRemaining(sb::append);
            String results = sb.substring(sb.lastIndexOf("chr"));
            Assert.assertEquals("Wrong line from tabix indexed vcf file", "chrY\t327\t.\tT\tC\t666.18\tGATK_STANDARD;HARD_TO_VALIDATE\tAB=0.74;AC=3;AF=0.50;AN=6;DB=0;DP=936;Dels=0.00;HRun=3;MQ=34.66;MQ0=728;QD=0.71;SB=-268.74;set=filteredInBoth\tGT:DP:GQ\t1/0:10:62.65\t1/0:37:99.00\t1/0:53:99.00", results);
        }
    }

    @Test
    public void testVcfTabixGetHeader() throws IOException {
        try (VcfGzTabixGenomicIterator vcfit = new VcfGzTabixGenomicIterator(cl, fs, fi)) {
            Assert.assertEquals("Wrong header from tabix indexed vcf file", "CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tNA19238\tNA19239\tNA19240", String.join("\t", vcfit.getHeader()));
        }
    }

    @Test
    public void testVcfTabixSeek() throws IOException {
        try (VcfGzTabixGenomicIterator vcfit = new VcfGzTabixGenomicIterator(cl, fs, fi)) {
            Assert.assertTrue("Tabix indexed vcf file seek failed", vcfit.seek("chr1", 327));
        }
    }

    @After
    public void cleanup() {
        try {
            Files.delete(tabixIndexPath);
        } catch(Exception e) {
            // dont care
        }
    }
}
