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

package org.gorpipe.gor.driver;

import org.apache.commons.io.FileUtils;
import org.gorpipe.exceptions.GorResourceException;
import org.gorpipe.gor.driver.meta.DataType;
import org.gorpipe.gor.driver.meta.IndexableSourceReference;
import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.model.GenomicIterator;
import org.gorpipe.gor.util.DataUtil;
import org.gorpipe.test.utils.FileTestUtils;
import org.junit.*;
import org.nanohttpd.protocols.http.TestFileHttpServer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by villi on 28/08/15.
 */
public class UTestGorDriver {

    private static int port = -1;
    private static TestFileHttpServer server;
    private static GorDriver gorDriver;
    private static File workDir;

    @BeforeClass
    public static void setUp() throws Exception {
        server = new TestFileHttpServer(46993);
        port = server.getPort();
        gorDriver = GorDriverFactory.fromConfig();
        workDir = Paths.get("../tests/data").toFile().getCanonicalFile();
        createTempFiles();

    }

    private static void createTempFiles() throws IOException {
        FileTestUtils.createTempFile(workDir, DataUtil.toLinkFile("relative_link_to_lines_2000", DataType.TXT), "lines_2000.txt");
        FileTestUtils.createTempFile(workDir, DataUtil.toLinkFile("absolute_link_to_dummy", DataType.GOR), "http://127.0.0.1:46993/dummy.gor");
        FileTestUtils.createTempFile(workDir, DataUtil.toLinkFile("relative_link_to_subdir", DataType.TXT), "subdir/lines_1000.txt");
        FileTestUtils.createTempFile(workDir, DataUtil.toFile("link_to_nowhere", DataType.LINK), "http://127.0.0.1:46993/no_such_file.txt");

        File subdir = Paths.get("../tests/data/subdir").toFile();
        subdir.deleteOnExit();

        FileTestUtils.createLinesFile(subdir, 1000);
        FileTestUtils.createLinesFile(workDir, 2000);
    }


    @Test
    public void gorDataSource() throws IOException {
        SourceReference sourceReference = new SourceReference( DataUtil.toFile( "../tests/data/gor/dbsnp_test", DataType.GOR));
        GenomicIterator iterator = gorDriver.createIterator(sourceReference);
        Assert.assertNotNull(iterator);
    }

    @Test
    public void gorzDataSource() throws IOException {
        SourceReference sourceReference = new SourceReference(DataUtil.toFile("../tests/data/gor/dbsnp_test", DataType.GORZ));
        GenomicIterator iterator = gorDriver.createIterator(sourceReference);
        Assert.assertNotNull(iterator);
    }

    @Test
    public void gorgzDataSource() throws IOException {
        SourceReference sourceReference = new SourceReference(DataUtil.toFile("../tests/data/gor/dbsnp_test", DataType.GORGZ));
        GenomicIterator iterator = gorDriver.createIterator(sourceReference);
        Assert.assertNotNull(iterator);
    }

    @Test
    public void norDataSource() throws IOException {
        SourceReference sourceReference = new SourceReference(DataUtil.toFile("../tests/data/nor/simple", DataType.NOR));
        GenomicIterator iterator = gorDriver.createIterator(sourceReference);
        Assert.assertNotNull(iterator);
    }

    @Test
    public void norzDataSource() throws IOException {
        SourceReference sourceReference = new SourceReference(DataUtil.toFile("../tests/data/nor/simple", DataType.NORZ));
        GenomicIterator iterator = gorDriver.createIterator(sourceReference);
        Assert.assertNotNull(iterator);
    }

    @Test
    public void vcfDataSource() throws IOException {
        SourceReference sourceReference = new SourceReference(DataUtil.toFile("../tests/data/external/samtools/test", DataType.VCF));
        GenomicIterator iterator = gorDriver.createIterator(sourceReference);
        Assert.assertNotNull(iterator);
    }

    @Test
    public void vcfgzDataSource() throws IOException {
        SourceReference sourceReference = new SourceReference(DataUtil.toFile("../tests/data/external/samtools/test", DataType.VCFGZ));
        GenomicIterator iterator = gorDriver.createIterator(sourceReference);
        Assert.assertNotNull(iterator);
    }

    @Test
    public void bamDataSource() throws IOException {
        SourceReference sourceReference = new SourceReference(DataUtil.toFile("../tests/data/external/samtools/noindex", DataType.BAM));
        GenomicIterator iterator = gorDriver.createIterator(sourceReference);
        Assert.assertNotNull(iterator);
    }

    @Test
    public void cramDataSource() throws IOException {
        SourceReference sourceReference = new SourceReference(DataUtil.toFile("../tests/data/external/samtools/cram_query_sorted", DataType.CRAM));
        GenomicIterator iterator = gorDriver.createIterator(sourceReference);
        Assert.assertNotNull(iterator);
    }

    @Test
    public void bgenDataSource() throws IOException {
        SourceReference sourceReference = new SourceReference(DataUtil.toFile("../tests/data/external/bgen/testfile1_chr1", DataType.BGEN));
        GenomicIterator iterator = gorDriver.createIterator(sourceReference);
        Assert.assertNotNull(iterator);
    }

    @Test
    public void parquetDataSource() throws IOException {
        SourceReference sourceReference = new SourceReference(DataUtil.toFile("../tests/data/parquet/dbsnp_test", DataType.PARQUET));
        GenomicIterator iterator = gorDriver.createIterator(sourceReference);
        Assert.assertNotNull(iterator);
    }

    @Test
    public void testRelativeLinkFiles() {
        DataSource source = gorDriver.getDataSource(new SourceReference("http://127.0.0.1:" + port + DataUtil.toLinkFile("/relative_link_to_lines_2000", DataType.TXT)));
        Assert.assertEquals("http://127.0.0.1:"+port+"/lines_2000.txt", source.getName());
    }

    @Test
    public void testAutoDiscovery() throws IOException {
        DataSource source = gorDriver.getDataSource(new SourceReference("http://127.0.0.1:" + port + DataUtil.toFile("/absolute_link_to_dummy", DataType.GOR)));
        Assert.assertEquals("http://127.0.0.1:" + port + DataUtil.toFile("/dummy", DataType.GOR), source.getName());
    }

    @Test
    public void testRelativeLinkAutoDiscovery() throws IOException {
        DataSource source = gorDriver.getDataSource(new SourceReference("http://127.0.0.1:" + port + DataUtil.toFile("/relative_link_to_lines_2000", DataType.TXT)));
        Assert.assertEquals("http://127.0.0.1:"+port+"/lines_2000.txt", source.getName());
    }

    @Test
    public void testRelativeSubdir() throws IOException {
        DataSource source = gorDriver.getDataSource(new SourceReference("http://127.0.0.1:" + port + DataUtil.toLinkFile( "/relative_link_to_subdir", DataType.TXT)));
        Assert.assertEquals("http://127.0.0.1:"+port+"/subdir/lines_1000.txt", source.getName());
    }

    @Test
    public void testAbsoluteLink() throws IOException {
        DataSource source = gorDriver.getDataSource(new SourceReference("http://127.0.0.1:" + port + DataUtil.toLinkFile("/absolute_link_to_dummy", DataType.GOR)));
        Assert.assertEquals("http://127.0.0.1:" + port + DataUtil.toFile("/dummy", DataType.GOR), source.getName());
    }

    @Ignore("In the process of updating the driver to handle db://")
    @Test
    public void testDbLinks() throws IOException {
        // GOR-66: DB links are treated as file sources.
        // This test should be modified or removed once we support db links through new driver.
        Assert.assertNull(gorDriver.getDataSource(new SourceReference("db:/rda:voff")));
    }

    @Test
    public void testBrokenLink() throws IOException {
        DataSource source = gorDriver.getDataSource(new SourceReference("http://127.0.0.1:" + port + DataUtil.toFile("/link_to_nowhere", DataType.LINK)));
        Assert.assertFalse("source should not exists", source.exists());
    }

    @Test
    public void testIndexedSourceReference() throws IOException {
        DataSource source = gorDriver.getDataSource(new IndexableSourceReference(DataUtil.toFile("../tests/data/gor/genes", DataType.GOR), "foo", "bar", null, null, null));

        Assert.assertTrue("source should exists", source.exists());
        Assert.assertTrue(source.getSourceReference() instanceof IndexableSourceReference);

        testIndexableSourceReference(source);
    }

    private void testIndexableSourceReference(DataSource source) {
        IndexableSourceReference sourceReference = (IndexableSourceReference) source.getSourceReference();
        Assert.assertEquals("foo", sourceReference.getIndexSource());
        Assert.assertEquals("bar", sourceReference.getReferenceSource());
    }

    @Test
    public void testIndexedSourceReferenceWithLinkFile() throws IOException {
        // Create link file
        File dataFile = new File("../tests/data/gor/genes.gor");
        File linkFile = Files.createTempFile("genes", DataUtil.toLinkFile("", DataType.GOR)).toFile();
        linkFile.deleteOnExit();
        FileUtils.writeStringToFile(linkFile, dataFile.getAbsolutePath(), Charset.defaultCharset());

        // Create indexable source references
        DataSource source = gorDriver.getDataSource(new IndexableSourceReference(linkFile.getAbsolutePath(), "foo", "bar", null, null, null));
        Assert.assertTrue("source should exists", source.exists());

        // test that the data source source reference is of the correct type and includes the index and reference file
        testIndexableSourceReference(source);
    }

    @Test
    public void testIndexedSourceReferenceWithLinkFileAndNoLinkExtension() throws IOException {
        // Create link file
        File dataFile = new File(DataUtil.toFile("../tests/data/gor/genes", DataType.GOR));
        File linkFile = Files.createTempFile("genes", DataUtil.toLinkFile("", DataType.GOR)).toFile();
        linkFile.deleteOnExit();
        FileUtils.writeStringToFile(linkFile, dataFile.getAbsolutePath(), Charset.defaultCharset());

        // Create indexable source references
        DataSource source = gorDriver.getDataSource(new IndexableSourceReference(linkFile.getAbsolutePath().replace(DataType.LINK.suffix, ""), "foo", "bar", null, null, null));
        Assert.assertTrue("source should exists", source.exists());

        // test that the data source source reference is of the correct type and includes the index and reference file
        testIndexableSourceReference(source);
    }

    @AfterClass
    public static void tearDown() {
        server.stop();
        server = null;
    }

}
