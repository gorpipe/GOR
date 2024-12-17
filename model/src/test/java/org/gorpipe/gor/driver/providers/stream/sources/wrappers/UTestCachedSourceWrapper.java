package org.gorpipe.gor.driver.providers.stream.sources.wrappers;

import org.gorpipe.gor.driver.meta.SourceType;
import org.gorpipe.gor.driver.providers.stream.FileCache;
import org.gorpipe.gor.driver.providers.stream.StreamUtils;
import org.gorpipe.gor.driver.providers.stream.sources.StreamSource;
import org.gorpipe.gor.driver.providers.stream.sources.UTestHttpSource;
import org.gorpipe.gor.driver.providers.stream.sources.file.FileSource;
import org.gorpipe.gor.driver.providers.stream.sources.file.FileSourceType;
import org.gorpipe.test.utils.FileTestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by villi on 26/08/15.
 */
public class UTestCachedSourceWrapper extends UTestHttpSource {

    private FileCache fileCache;

    @Override
    protected CachedSourceWrapper createSource(String name) throws IOException {
        StreamSource toWrap = super.createSource(name);
        return new CachedSourceWrapper(fileCache, toWrap);
    }

    @Override
    protected SourceType expectedSourcetype(StreamSource fs) {
        return FileSourceType.FILE;
    }

    @Override
    protected SourceType expectedAttributeSourcetype(StreamSource fs) {
        return super.expectedSourcetype(fs);
    }

    public long[] getSeeds() {
        long[] arr = {System.nanoTime()};
        return arr;
    }

    @org.junit.Before
    public void setup() throws IOException {
        fileCache = new FileCache("/tmp/test_cache", 100000);
        File dir = new File("/tmp/test_cache");
        if (dir.exists()) {
            FileTestUtils.deleteFolder(dir.toPath());
        }
    }

    @Test
    public void testRemoteSource() throws IOException {
        CachedSourceWrapper source = createSource(getDataName(lines1000File));
        Assert.assertFalse(new File(cachedFilePath(source)).exists());
        Assert.assertTrue(source.getWrapped().getSourceType().isRemote());

        try (InputStream str = source.open()) {
            Assert.assertTrue(str instanceof FileSource.FileSourceStream);
            Assert.assertTrue(new File(cachedFilePath(source)).exists());
        }

        String orgData;
        try (FileInputStream f = new FileInputStream(lines1000File.getCanonicalPath())) {
            orgData = StreamUtils.readString(f, 5000);
        }
        String cachedData;
        try (FileInputStream f = new FileInputStream(cachedFilePath(source))) {
            cachedData = StreamUtils.readString(f, 5000);
        }
        Assert.assertEquals(orgData, cachedData);

    }


    @Test
    public void testLocalSource() throws IOException {
        FileSource fs = new FileSource(lines1000File.getCanonicalPath());

        CachedSourceWrapper source = new CachedSourceWrapper(fileCache, fs);
        Assert.assertFalse(new File(cachedFilePath(source)).exists());

        try (InputStream str = source.open()) {
            Assert.assertTrue(str instanceof FileSource.FileSourceStream);
            Assert.assertFalse(new File(cachedFilePath(source)).exists());
        }
    }

    @Override
    public void testGetName() throws IOException {
        String name = getDataName(emptyFile);
        StreamSource fs = createSource(name);
        Assert.assertTrue(fs.getName().startsWith("/tmp/test_cache"));
    }

    private String cachedFilePath(CachedSourceWrapper source) throws IOException {
        return fileCache.cachedFilePath(source.getSourceMetadata().getUniqueId());
    }
}
