package org.gorpipe.gor.driver.providers.stream.sources;

import org.gorpipe.gor.driver.DataSource;
import org.gorpipe.gor.driver.meta.SourceType;
import org.gorpipe.gor.driver.providers.stream.sources.file.FileSource;
import org.gorpipe.gor.driver.providers.stream.sources.file.FileSourceType;
import org.gorpipe.gor.driver.providers.stream.sources.wrappers.RetryStreamSourceWrapper;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * Created by villi on 25/08/15.
 */
public class UTestFileSourceOnFileUrls extends CommonStreamTests {

    @Override
    protected String getDataName(File file) throws IOException {
        return "file://" + file.getCanonicalPath();
    }

    @Override
    protected StreamSource createSource(String name) {
        return new FileSource(name);
    }

    @Override
    protected String expectCanonical(StreamSource source, String name) {
        if (name.startsWith("file://")) return name;
        return "file://" + name;
    }

    @Override
    protected void verifyDriverDataSource(String name, DataSource fs) {
        Assert.assertEquals(RetryStreamSourceWrapper.class, fs.getClass());
        fs = ((RetryStreamSourceWrapper) fs).getWrapped();
        Assert.assertEquals(FileSource.class, fs.getClass());
    }

    @Override
    protected SourceType expectedSourcetype(StreamSource fs) {
        return FileSourceType.FILE;
    }

    @Override
    @Test
    public void testGetName() throws IOException {
        String name = getDataName(emptyFile);
        try (StreamSource fs = createSource(name)) {
            Assert.assertEquals(name, "file://" + fs.getName());
        }
    }

}
