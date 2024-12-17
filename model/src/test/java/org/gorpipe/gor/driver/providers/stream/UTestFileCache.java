package org.gorpipe.gor.driver.providers.stream;

import org.gorpipe.gor.driver.providers.stream.sources.file.FileSource;
import org.gorpipe.gor.driver.utils.TestUtils;
import org.gorpipe.test.SlowTests;
import org.gorpipe.test.utils.FileTestUtils;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Created by villi on 09/10/15.
 */
public class UTestFileCache {

    protected static File dummyFile;

    @Rule
    public TemporaryFolder workDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File dir = new File("/tmp/my_cache");
        if (dir.exists()) {
            FileTestUtils.deleteFolder(dir.toPath());
        }
        dummyFile = FileTestUtils.createDummyGorFile(workDir.getRoot());
    }

    @Test
    public void testGetNonExisting() throws IOException {
        FileCache cache = new FileCache("/tmp/my_cache", 100);
        Assert.assertNull(cache.get("Some id"));
    }

    @Ignore // Fails when run inside docker container (GOR-498)
    @Test
    public void testStoreUnwritableCacheDir() throws IOException {
        Path tmpdir = Files.createTempDirectory("doesnotexist");
        tmpdir.toFile().setReadOnly();
        FileCache cache = new FileCache(tmpdir.toString(), 100);
        String sourceName = dummyFile.getCanonicalPath();
        FileSource source = new FileSource(sourceName);
        File stored = cache.store("ABCD", source);
        Assert.assertNull(stored);
    }

    @Test
    public void testStoreAndRetrieve() throws IOException {
        FileCache cache = new FileCache("/tmp/my_cache", 100);
        String sourceName = dummyFile.getCanonicalPath();
        FileSource source = new FileSource(sourceName);
        File stored = cache.store("ABCD", source);
        Assert.assertTrue(stored.getPath().startsWith("/tmp/my_cache"));

        Assert.assertEquals(TestUtils.readFile(new File(sourceName)), TestUtils.readFile(stored));

        File stored2 = cache.get("ABCD");
        Assert.assertEquals(TestUtils.readFile(new File(sourceName)), TestUtils.readFile(stored2));
    }

    @Category(SlowTests.class)
    @Test
    public void testSweep() throws Exception {
        FileCache cache = new FileCache("/tmp/my_cache", 100);
        Assert.assertEquals(0, cache.getCachedFiles().length);

        String lines1000Path = FileTestUtils.createLinesFile(workDir.getRoot(),1000).getCanonicalPath();

        // Fill cache, first two small files, then large.
        // Sweep should delete all
        cache.store("1", new FileSource(dummyFile.getCanonicalPath()));
        cache.store("2", new FileSource(dummyFile.getCanonicalPath()));
        Thread.sleep(1001);

        cache.store("3", new FileSource(lines1000Path));
        Assert.assertEquals(3, cache.getCachedFiles().length);
        cache.sweep();

        File[] cached = cache.getCachedFiles();
        Assert.assertEquals(0, cached.length);

        // Reverse it - should only delete large file
        // Sweep should delete all
        cache.store("3", new FileSource(lines1000Path));
        Thread.sleep(1001);

        cache.store("1", new FileSource(dummyFile.getCanonicalPath()));
        cache.store("2", new FileSource(dummyFile.getCanonicalPath()));
        Assert.assertEquals(3, cache.getCachedFiles().length);
        cache.sweep();

        cached = cache.getCachedFiles();
        Assert.assertEquals(2, cached.length);
        Assert.assertNotNull(cache.get("1"));
        Assert.assertNotNull(cache.get("2"));
        Assert.assertNull(cache.get("3"));
    }

    @Category(SlowTests.class)
    @Test
    public void testStampUpdate() throws Exception {
        FileCache cache = new FileCache("/tmp/my_cache", 100);
        File stored = cache.store("1", new FileSource(dummyFile.getCanonicalPath()));
        long stamp1 = stored.lastModified();
        Thread.sleep(1001);
        File stored2 = cache.store("2", new FileSource(dummyFile.getCanonicalPath()));
        long stamp2 = stored2.lastModified();

        Assert.assertTrue(stamp2 > stamp1);

        Thread.sleep(1001);
        File stored22 = cache.get("2");
        Assert.assertTrue(stored22.lastModified() > stamp2);
        Thread.sleep(1001);
        File stored12 = cache.get("1");
        Assert.assertTrue(stored12.lastModified() > stamp2);

        Assert.assertTrue(stored12.lastModified() > stored22.lastModified());
    }

}
