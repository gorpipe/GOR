package org.gorpipe.oci.driver;

import gorsat.TestUtils;
import org.gorpipe.exceptions.GorException;
import org.gorpipe.gor.driver.DataSource;
import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.driver.meta.SourceReferenceBuilder;
import org.gorpipe.gor.driver.meta.SourceType;
import org.gorpipe.gor.driver.providers.stream.sources.StreamSource;
import org.gorpipe.gor.driver.providers.stream.sources.wrappers.ExtendedRangeWrapper;
import org.gorpipe.gor.driver.providers.stream.sources.wrappers.RetryStreamSourceWrapper;
import org.gorpipe.gor.model.GenomicIterator;
import org.gorpipe.s3.driver.S3Source;
import org.gorpipe.s3.driver.S3SourceType;
import org.gorpipe.test.IntegrationTests;
import org.gorpipe.utils.DriverUtils;
import org.junit.*;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.Properties;

@Category(IntegrationTests.class)
public class ITestOCISource {

    private String bucketName = "gdb_gor_test_data_dev";

    private static String OCI_TENANT;
    private static String OCI_USER;

    private static String OCI_SIMPLE_PRIVATE_KEY;
    private static String OCI_SIMPLE_FINGERPRINT;

    @Rule
    public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();

    private OCIObjectStorageSourceProvider provider = new OCIObjectStorageSourceProvider();


    @BeforeClass
    static public void setUpClass() {
        Properties props = DriverUtils.getDriverProperties();
        OCI_TENANT = props.getProperty("OCI_TENANT");
        OCI_USER = props.getProperty("OCI_USER");
        OCI_SIMPLE_PRIVATE_KEY = props.getProperty("OCI_SIMPLE_PRIVATE_KEY");
        OCI_SIMPLE_FINGERPRINT = props.getProperty("OCI_SIMPLE_FINGERPRINT");
    }


    protected String getDataName(String name) {
        return String.format("oci://%s/csa_test_data/data_sets/gor_driver_testfiles/%s", bucketName,  name);
    }

    protected String securityContext()  {
        return DriverUtils.ociSecurityContext(OCI_TENANT, OCI_USER, OCI_SIMPLE_PRIVATE_KEY, OCI_SIMPLE_FINGERPRINT);
    }

    protected StreamSource createSource(String name) throws IOException {
        var sr = new SourceReference(name, securityContext(), null, null, null, false);
        return provider.resolveDataSource(sr);
    }

    protected String expectCanonical(StreamSource source, String name) {
        return name;
    }

    protected void verifyDriverDataSource(String name, DataSource fs) {
        Assert.assertEquals(ExtendedRangeWrapper.class, fs.getClass());
        fs = ((ExtendedRangeWrapper) fs).getWrapped();
        Assert.assertEquals(RetryStreamSourceWrapper.class, fs.getClass());
        fs = ((RetryStreamSourceWrapper) fs).getWrapped();
        Assert.assertEquals(S3Source.class, fs.getClass());
    }

    protected SourceType expectedSourcetype(StreamSource fs) {
        return S3SourceType.S3;
    }

//    @Test
//    public void testFial() {
//        Assert.fail("Not implemented");
//    }

    @Test
    public void testOCIWriteServerMode() {
        TestUtils.runGorPipe(String.format("gor ../tests/data/gor/genes.gor | top 1 | write oci://%s/tmp/ociwrite/genes.gor", bucketName),
                true, securityContext(), new String[] {"oci://"});
    }

    @Test
    public void testWithoutContext() {
        String source = getDataName("derived/raw_bam_to_gor/BVL_INDEX_SLC52A2.bam.gor");

        SourceReference ref = new SourceReferenceBuilder(source).build();

        try {
            DataSource ds = org.gorpipe.gor.driver.utils.TestUtils.gorDriver.getDataSource(ref);
            Assert.fail("Should not be able to query OCI without security context");
        } catch (GorException io) {
            // Expected
        }
        try {
            GenomicIterator iterator = org.gorpipe.gor.driver.utils.TestUtils.gorDriver.createIterator(ref);
            StringBuilder builder = new StringBuilder();

            org.gorpipe.gor.driver.utils.TestUtils.addHeader(builder, iterator);
            org.gorpipe.gor.driver.utils.TestUtils.addLines(builder, iterator, 5);
            Assert.fail("Should not be able to read data without security context");
        } catch (IOException | GorException io) {
            // Expected
        }
    }

}
