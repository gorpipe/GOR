package org.gorpipe.gor.driver.providers.db;

import com.nextcode.gor.driver.utils.DatabaseHelper;
import org.apache.commons.io.FileUtils;
import org.gorpipe.exceptions.GorDataException;
import org.gorpipe.exceptions.GorResourceException;
import org.gorpipe.gor.driver.DataSource;
import org.gorpipe.gor.driver.GorDriverFactory;
import org.gorpipe.gor.driver.meta.DataType;
import org.gorpipe.gor.driver.meta.SourceMetadata;
import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.driver.meta.SourceReferenceBuilder;
import org.gorpipe.gor.driver.providers.rows.sources.db.DbSource;
import org.gorpipe.gor.driver.providers.rows.sources.db.DbSourceType;
import org.gorpipe.gor.model.DbConnection;
import org.gorpipe.gor.model.GenomicIterator;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;

/**
 * Created by gisli on 02/03/15.
 */
public class UTestDbSource {

    private final static SourceReference defaultSourceReference = new SourceReferenceBuilder("db://rda:v_variant_annotations").securityContext("dbscope=project_id#int#10004|||extrastuff=other").build();

    protected DbSource createSource() {
        return new DbSource(defaultSourceReference);
    }

    protected DbSource createSource(String name, String securityContext) {
        return new DbSource(new SourceReferenceBuilder(name).securityContext(securityContext).build());
    }

    @BeforeClass
    public static void setup() throws IOException, ClassNotFoundException, SQLException {
        String[] paths = DatabaseHelper.createRdaDatabase();
        System.setProperty("gor.db.credentials", paths[2]);
        DbConnection.initInConsoleApp();
    }

    @Test
    public void testNonExistingLinkFile() {
        SourceReference sourceReference = new SourceReference("db://rda:rda.v_variant_annotation", null, "", null, null, false);
        try {
            GorDriverFactory.fromConfig().createIterator(sourceReference);
            Assert.fail();
        } catch (GorResourceException e) {
            //Test did not fail, not StackOverflowError, but rather FileNotFound
        } catch (IOException e) {
            //Test did not fail, not StackOverflowError, but rather FileNotFound
        }
    }

    @Test
    public void testMetadata() {
        DbSource ds = createSource();
        SourceMetadata meta = ds.getSourceMetadata();
        Assert.assertEquals("Canonical name", "db://rda:v_variant_annotations", meta.getCanonicalName());
        Assert.assertNotNull("Last modified", meta.getLastModified());
        Assert.assertNotNull("UniqueId", meta.getUniqueId());
        Assert.assertEquals("Source", ds, meta.getSource());
    }

    @Test
    public void testGetName() {
        DbSource ds = createSource();
        Assert.assertEquals("Name", "db://rda:v_variant_annotations", ds.getName());
    }

    @Test
    public void testGetSourceType() {
        DbSource ds = createSource();
        Assert.assertEquals("Source type", DbSourceType.DB, ds.getSourceType());
    }

    @Test
    public void testGetTimestamp() {
        DbSource ds = createSource();
        // TODO:  Do we have a better way of testing we are not getting current time as timestamp and not getting null.
        // We don't seem to have any tables on dev that have valid ora_rowscn (and are compatible with db://)
        // Assert.assertTrue("Last modified, to new", ds.getSourceMetadata().getLastModified().longValue() < System.currentTimeMillis() - 1000);
        Assert.assertTrue("Last modified, to old", ds.getSourceMetadata().getLastModified() > System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 365 * 10);
    }

    @Test
    public void testGetAttributes() throws IOException {
        DbSource ds = createSource();
        SourceMetadata md = ds.getSourceMetadata();
        Map<String, String> attr = md.attributes();
        Assert.assertEquals("GOR", attr.get("DataType"));
        Assert.assertEquals(DbSourceType.DB.getName(), attr.get("SourceType"));
        Assert.assertEquals(md.getCanonicalName(), attr.get("CanonicalName"));
        Assert.assertEquals(md.getUniqueId(), attr.get("UniqueId"));
    }

    @Test
    public void testExists() throws IOException {
        DbSource ds = createSource();
        Assert.assertTrue(ds.exists());
        ds.close();

        //Test table
        ds = createSource("db://rda:variant_annotations", "");
        Assert.assertTrue(ds.exists());
        ds.close();

        //Test view
        ds = createSource("db://rda:v_variant_annotations", "");
        Assert.assertTrue(ds.exists());
        ds.close();

        //Test with schema
        ds = createSource("db://rda:rda.v_variant_annotations", "");
        Assert.assertTrue(ds.exists());
        ds.close();

        Assert.assertThrows("Invalid db table name", GorResourceException.class,
                () -> createSource("db://rda:no_such_file", "")); ;
    }

    @Test
    public void testDriver() throws IOException {
        DataSource ds = GorDriverFactory.fromConfig().getDataSource(defaultSourceReference);

        Assert.assertNotNull("GorDriver", ds);
    }

    @Test
    public void testGetDataType() {
        DbSource ds = createSource();
        Assert.assertEquals(DataType.GOR, ds.getDataType());
    }

    @Test
    public void testReadDbThroughLinkFiles() throws IOException {
        Path linkPath = java.nio.file.Files.createTempFile("testLinkFiles_dbsource", DataType.LINK.suffix);
        linkPath.toFile().deleteOnExit();
        FileUtils.write(linkPath.toFile(), defaultSourceReference.getUrl() + "\n", (Charset) null);

        DataSource ds = GorDriverFactory.fromConfig().getDataSource(
                new SourceReferenceBuilder(linkPath.toAbsolutePath().toString()).build());

        Assert.assertNotNull("DataSource should have been created but is null", ds);
        Assert.assertEquals("DataSource is not of the correct type", DbSourceType.DB, ds.getSourceType());

        SourceMetadata sourceMetadata = ds.getSourceMetadata();
        Assert.assertNotNull("Last modified should not be null", sourceMetadata.getLastModified());
        Assert.assertEquals("Didn't get the original URL from the data source", defaultSourceReference.getUrl(),
                sourceMetadata.attributes().get("CanonicalName"));

        StringBuilder builder = new StringBuilder();
        GenomicIterator iterator = GorDriverFactory.fromConfig().createIterator(
                new SourceReferenceBuilder(linkPath.toAbsolutePath().toString()).securityContext("dbscope=project_id#int#10004").build());

        int lineCount = 5;
        addHeader(builder, iterator);
        addLines(builder, iterator, lineCount);
        iterator.close();

        Assert.assertEquals("Incorrect number of lines returned", lineCount + 1, builder.toString().split("\n").length);
    }

    @Test
    public void testDbScope() throws IOException {
        Path linkPath = java.nio.file.Files.createTempFile("testLinkFiles_dbsource", DataType.LINK.suffix);
        linkPath.toFile().deleteOnExit();
        FileUtils.write(linkPath.toFile(), defaultSourceReference.getUrl() + "\n", (Charset) null);

        DataSource ds = GorDriverFactory.fromConfig().getDataSource(
                new SourceReferenceBuilder(linkPath.toAbsolutePath().toString()).build());

        Assert.assertNotNull("DataSource should have been created but is null", ds);
        Assert.assertEquals("DataSource is not of the correct type", DbSourceType.DB, ds.getSourceType());

        SourceMetadata sourceMetadata = ds.getSourceMetadata();
        Assert.assertNotNull("Last modified should not be null", sourceMetadata.getLastModified());
        Assert.assertEquals("Didn't get the original URL from the data source", defaultSourceReference.getUrl(),
                sourceMetadata.attributes().get("CanonicalName"));


        GenomicIterator iterator = GorDriverFactory.fromConfig().createIterator(
                new SourceReferenceBuilder(linkPath.toAbsolutePath().toString()).securityContext("dbscope=project_id#int#10004").build());

        boolean seekValue = iterator.seek("1", 1);
        String[] header = iterator.getHeader().split("\t");
        Assert.assertArrayEquals(new String[]{"CHROMO", "POS", "PN", "FOO", "COMMENT"}, header);
        iterator.close();

        GenomicIterator iterator2 = GorDriverFactory.fromConfig().createIterator(
                new SourceReferenceBuilder(linkPath.toAbsolutePath().toString()).securityContext("dbscope=project_id#int#10004").build());
        StringBuilder builder = new StringBuilder();
        int lineCount = 5;
        addHeader(builder, iterator2);
        addLines(builder, iterator2, lineCount);
        iterator2.close();

        Assert.assertEquals("Incorrect number of lines returned", lineCount + 1, builder.toString().split("\n").length);
    }

    @Test
    public void testNoDbScope() throws IOException {
        Path linkPath = java.nio.file.Files.createTempFile("testLinkFiles_dbsource", DataType.LINK.suffix);
        linkPath.toFile().deleteOnExit();
        FileUtils.write(linkPath.toFile(), defaultSourceReference.getUrl() + "\n", (Charset) null);

        GenomicIterator iterator = GorDriverFactory.fromConfig().createIterator(
                new SourceReferenceBuilder(linkPath.toAbsolutePath().toString()).securityContext("dbscope=").build());
        String[] header = iterator.getHeader().split("\t");
        Assert.assertArrayEquals(new String[]{"PROJECT_ID", "CHROMO", "POS", "PN", "FOO", "COMMENT"}, header);
        iterator.close();
    }

    @Test(expected = GorDataException.class)
    public void testInValidbScope() throws IOException {
        Path linkPath = java.nio.file.Files.createTempFile("testLinkFiles_dbsource", DataType.LINK.suffix);
        linkPath.toFile().deleteOnExit();
        FileUtils.write(linkPath.toFile(), defaultSourceReference.getUrl() + "\n", (Charset) null);
        GenomicIterator iterator = GorDriverFactory.fromConfig().createIterator(
                new SourceReferenceBuilder(linkPath.toAbsolutePath().toString()).securityContext("dbscope=projectid#int#10004").build());
        iterator.close();
    }

    @Test
    public void testDuplicateDbScope() throws IOException {
        Path linkPath = java.nio.file.Files.createTempFile("testLinkFiles_dbsource", DataType.LINK.suffix);
        linkPath.toFile().deleteOnExit();
        FileUtils.write(linkPath.toFile(), defaultSourceReference.getUrl() + "\n", (Charset) null);

        GenomicIterator iterator = GorDriverFactory.fromConfig().createIterator(
                new SourceReferenceBuilder(linkPath.toAbsolutePath().toString()).securityContext("dbscope=project_id#int#10004,project_id#int#10004").build());
        String[] header = iterator.getHeader().split("\t");
        Assert.assertArrayEquals(new String[]{"CHROMO", "POS", "PN", "FOO", "COMMENT"}, header);
        iterator.close();
    }

    @Test
    public void testReadDbSource() throws IOException {
        GenomicIterator iterator = GorDriverFactory.fromConfig().createIterator(defaultSourceReference);
        int lineCount = 5;
        StringBuilder builder = new StringBuilder();

        addHeader(builder, iterator);
        addLines(builder, iterator, lineCount);
        iterator.close();
        Assert.assertEquals("Incorrect number of lines returned", lineCount + 1, builder.toString().split("\n").length);
    }

    @Test
    public void testSeek() throws IOException {
        GenomicIterator iterator = GorDriverFactory.fromConfig().createIterator(defaultSourceReference);
        Assert.assertTrue(iterator.seek("chr1", 1));
        Assert.assertFalse(iterator.seek("chr2", 1));
        Assert.assertFalse(iterator.seek("chr1", 7));
    }

    private void addHeader(StringBuilder builder, GenomicIterator iterator) {
        builder.append(iterator.getHeader());
        builder.append("\n");
    }

    private void addLines(StringBuilder builder, GenomicIterator iterator, Integer lineCount) {
        int lines = 0;
        while (iterator.hasNext() && (lineCount == null || lines < lineCount)) {
            builder.append(iterator.next().getAllCols());
            builder.append("\n");
            lines++;
        }
    }

    @Test
    public void testAccessToMultipleSources() throws SQLException, IOException, ClassNotFoundException {
        // Create avas database
        var avasPaths = DatabaseHelper.createAvasDatabase();
        DbConnection.systemConnections.install(new DbConnection("avas", "jdbc:derby:" + avasPaths[1], "avas", "beta3"));

        var dsRda = createSource("db://rda:variant_annotations", "dbscope=project_id#int#10004|||extrastuff=other");
        var dsAvas = createSource("db://avas:variant_annotations", "dbscope=project_id#int#10004|||extrastuff=other");

        Assert.assertTrue(dsRda.exists());
        Assert.assertTrue(dsAvas.exists());
    }
}
