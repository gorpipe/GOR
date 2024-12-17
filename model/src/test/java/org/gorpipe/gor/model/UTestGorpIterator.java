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
import org.apache.commons.io.FileUtils;
import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.driver.providers.stream.sources.file.FileSource;
import org.gorpipe.gor.model.GenomicIterator;
import org.gorpipe.gor.model.GorpIterator;
import org.gorpipe.gor.model.SourceRef;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileReader;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class UTestGorpIterator {
    private static File tempDir;
    private static String tempDirPath;
    private static List<SourceRef> srs;
    private static String gorpPath;
    private static final String[] chrs = Arrays.stream(new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "X", "Y", "M"})
            .map(s -> "chr" + s).sorted().toArray(String[]::new);
    private static final Map<String, Integer> chrToId;

    static {
        chrToId = new HashMap<>();
        for (int i = 0; i < chrs.length; ++i) {
            chrToId.put(chrs[i], i);
        }
    }

    @BeforeClass
    public static void setup() throws IOException {
        tempDir = Files.createTempDirectory("utestgorpiterator").toFile();
        tempDirPath = tempDir.getAbsolutePath();
        srs = writeFiles();
        gorpPath = writeGorp(srs, "genes_dict.gorp");
    }

    private static String writeGorp(List<SourceRef> srs, String path) throws IOException {
        final String absPath = new File(tempDir, path).getAbsolutePath();
        final BufferedWriter bw = new BufferedWriter(new FileWriter(absPath));
        for (final SourceRef sr : srs) {
            bw.write(sr.file + "\t1\t" + sr.startChr + "\t" + sr.startPos + "\t" + sr.stopChr + "\t" + sr.stopPos + "\n");
        }
        bw.close();
        return absPath;
    }

    private static List<SourceRef> writeFiles() throws IOException {
        final List<SourceRef> srs = new ArrayList<>();
        final Iterator<String> rows = new BufferedReader(new FileReader("../tests/data/gor/genes.gor")).lines().iterator();
        BufferedWriter bw1 = null;
        BufferedWriter bw2 = null;
        String currentChr = "";
        final String header = rows.next();
        while (rows.hasNext()) {
            final String row = rows.next();
            final String chr = row.substring(0, row.indexOf('\t'));
            if (!currentChr.equals(chr)) {
                currentChr = chr;
                if (bw1 != null && bw2 != null) bw1.close();
                final String relPath = "genes_part_" + srs.size() + ".gor";
                final String absPath = new File(tempDirPath, relPath).getAbsolutePath();
                final int chrIdx = chrToId.get(chr);
                srs.add(new SourceRef(relPath, null, null, null,
                        chrs[chrIdx], 1, chrs[Math.min(chrIdx + 1, chrs.length - 1)], 1000_000_000,
                        null, null, false, "", tempDirPath));
                if (bw1 == null) {
                    bw1 = new BufferedWriter(new FileWriter(absPath));
                    bw1.write(header + "\tsource\n");
                } else {
                    if (bw2 != null) bw1 = bw2;
                    bw2 = new BufferedWriter(new FileWriter(absPath));
                    bw2.write(header + "\tsource\n");
                }
            }
            if (bw1 != null) {
                bw1.write(row + "\t" + (bw2 == null ? srs.size() - 1 : srs.size() - 2) + "\n");
            }
            if (bw2 != null) {
                bw2.write(row + "\t" + (srs.size() - 1) + "\n");
            }
        }
        if (bw1 != null) bw1.close();
        if (bw2 != null) bw2.close();
        return srs;
    }

    @AfterClass
    public static void tearDown() throws IOException {
        FileUtils.deleteDirectory(tempDir);
    }

    @Test
    public void test_stream() {
        final GenomicIterator git = new GorpIterator(new FileSource(new SourceReference(gorpPath, null, tempDirPath, null, null, false))).filter(r -> r.isProgress);
        for (String chr : chrs) {
            Assert.assertTrue(git.hasNext());
            Assert.assertTrue(git.next().toString().startsWith(chr));
        }
        Assert.assertFalse(git.hasNext());
    }

    @Test
    public void test_seek() {
        final GenomicIterator git = new GorpIterator(new FileSource(new SourceReference(gorpPath, null, tempDirPath, null, null, false)));
        Assert.assertTrue(git.seek("chrM", 576));
        Assert.assertTrue(git.hasNext());
        Assert.assertEquals("chrM\t576\t647\tJ01415.2\t21", git.next().toString());
        Assert.assertTrue(git.hasNext());
        Assert.assertEquals("chrM\t576\t647\tJ01415.2\t22", git.next().toString());

        Assert.assertTrue(git.seek("chr13",32_889_610));
        Assert.assertTrue(git.hasNext());
        Assert.assertEquals("chr13\t32889610\t32973805\tBRCA2\t3", git.next().toString());
        Assert.assertTrue(git.hasNext());
        Assert.assertEquals("chr13\t32889610\t32973805\tBRCA2\t4", git.next().toString());
    }

    @Test
    public void test_seekBeyondEnd() {
        final GenomicIterator git = new GorpIterator(new FileSource(new SourceReference(gorpPath, null, tempDirPath, null, null, false)));
        Assert.assertFalse(git.seek("chrZ", 0));
    }

    @Test
    public void test_seekBeyondEndAndThenAtBeginning() {
        final GenomicIterator git = new GorpIterator(new FileSource(new SourceReference(gorpPath, null, tempDirPath, null, null, false)));
        Assert.assertFalse(git.seek("chrZ", 0));
        Assert.assertTrue(git.seek("chr1", 0));
        Assert.assertTrue(git.hasNext());
        Assert.assertEquals("chr1\t1\tprogress", git.next().toString());
        Assert.assertTrue(git.hasNext());
        Assert.assertEquals("chr1\t11868\t14412\tDDX11L1\t0", git.next().toString());
    }

    @Test
    public void test_filterProgressRows() {
        final GenomicIterator git = new GorpIterator(new FileSource(new SourceReference(gorpPath, null, tempDirPath, null, null, false)))
                .filter(r -> !r.isProgress);
        while (git.hasNext()) {
            Assert.assertFalse(git.next().isProgress);
        }
    }

    @Test
    public void testGorp() {
        var result = TestUtils.runGorPipe("gor "+gorpPath+" | group chrom -count | top 1");
        Assert.assertEquals("Chrom\tbpStart\tbpStop\tallCount\n" +
                "chr1\t0\t250000000\t4747\n", result);
    }
}
