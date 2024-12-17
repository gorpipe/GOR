package org.gorpipe.oci.driver;

import com.oracle.bmc.objectstorage.requests.HeadObjectRequest;
import gorsat.TestUtils;
import org.gorpipe.gor.driver.DataSource;
import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.driver.meta.SourceReferenceBuilder;
import org.gorpipe.gor.driver.meta.SourceType;
import org.gorpipe.gor.driver.providers.stream.sources.CommonStreamTests;
import org.gorpipe.gor.driver.providers.stream.sources.StreamSource;
import org.gorpipe.gor.driver.providers.stream.sources.wrappers.ExtendedRangeWrapper;
import org.gorpipe.gor.driver.providers.stream.sources.wrappers.RetryStreamSourceWrapper;
import org.gorpipe.gor.model.DriverBackedFileReader;
import org.gorpipe.test.IntegrationTests;
import org.gorpipe.utils.DriverUtils;
import org.junit.*;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Category(IntegrationTests.class)
public class ITestOCICommonStreamTests extends CommonStreamTests {

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

    @Override
    protected String getDataName(String name) {
        return String.format("oci://%s/csa_test_data/data_sets/gor_driver_testfiles/%s", bucketName,  name);
    }

    @Override
    protected String securityContext()  {
        return DriverUtils.ociSecurityContext(OCI_TENANT, OCI_USER, OCI_SIMPLE_PRIVATE_KEY, OCI_SIMPLE_FINGERPRINT);
    }

    @Override
    protected StreamSource createSource(String name) throws IOException {
        var sr = new SourceReference(name, securityContext(), null, null, null, false);
        return provider.resolveDataSource(sr);
    }

    @Override
    protected String expectCanonical(StreamSource source, String name) {
        return name;
    }

    @Override
    protected void verifyDriverDataSource(String name, DataSource fs) {
        Assert.assertEquals(ExtendedRangeWrapper.class, fs.getClass());
        fs = ((ExtendedRangeWrapper) fs).getWrapped();
        Assert.assertEquals(RetryStreamSourceWrapper.class, fs.getClass());
        fs = ((RetryStreamSourceWrapper) fs).getWrapped();
        Assert.assertEquals(OCIObjectStorageSource.class, fs.getClass());
    }

    @Override
    protected SourceType expectedSourcetype(StreamSource fs) {
        return OCIObjectStorageSourceType.OCI_OBJECT_STORAGE;
    }

    @Override
    protected long expectedTimeStamp(String s) throws IOException {
        try {
            return provider.getClient(securityContext(), getDataName(s))
                    .headObject(HeadObjectRequest.builder()
                            .namespaceName("id5mlxoq0dmt")
                            .bucketName(bucketName)
                            .objectName("csa_test_data/data_sets/gor_driver_testfiles/" + s)
                            .build(), null).get()
                    .getLastModified().toInstant().toEpochMilli();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected SourceReference mkSourceReference(String name) {
        return new SourceReferenceBuilder(name).securityContext(securityContext()).build();
    }

    @Test
    public void testOCIWrite() {
        TestUtils.runGorPipe(String.format("gor ../tests/data/gor/genes.gor | top 1 | write oci://%s/tmp/ociwrite/genes.gor", bucketName), false, securityContext());
    }

    @Ignore("Local file, also too large and slow to use always, no clean up")
    @Test
    public void testOCIWriteLargeFile() {
        long startTime = System.currentTimeMillis();
        TestUtils.runGorPipe(String.format("gorrows -p chr1:1-1000000000 | calc data 'Some dummy data to fatten the lines, boooooooooo' | write oci://%s/tmp/ocwrite/large.gor", bucketName));
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Time: " + duration/(1000) + " s");
    }

    @Ignore("Local file, also too large and slow to use always, no clean up")
    @Test
    public void testOCIWriteMoreThanMaxChunks() {
        System.setProperty("gor.s3.write.chunksize", String.valueOf(1 << 21));
        try {
            TestUtils.runGorPipe(String.format("gorrows -p chr1:1-1000000000 | calc data 'Some dummy data to fatten the lines, boooooooooo' | write oci://%s/tmp/ociwrite/large.gor", bucketName), false, securityContext());
            Assert.fail("Should have thrown exception");
        } catch (IllegalStateException e) {
            Assert.assertEquals("Output stream limit exceeded: 20971651072 > 20971520000", e.getMessage());
        }
    }

    @Ignore("Not supported yet, Too slow to always run")
    @Test
    public void testOCIWritePgorGord() throws IOException {
        String securityContext = securityContext();
        String randomId = UUID.randomUUID().toString();
        String dict = String.format("oci://%s/s3write/%s-genes.gord", bucketName, randomId);
        TestUtils.runGorPipe("pgor -split 2 ../tests/data/gor/genes.gor | top 2 | write " + dict, false, securityContext);
        String expected = TestUtils.runGorPipe("create x = pgor -split 2 ../tests/data/gor/genes.gor | top 2; gor [x] | select 1-4", false, securityContext);
        String result = TestUtils.runGorPipe("gor " + dict + " | select 1-4", false, securityContext);
        Assert.assertEquals(expected, result);
        DriverBackedFileReader fileReader = new DriverBackedFileReader(securityContext);
        fileReader.deleteDirectory(dict);
    }

    @Test
    public void testOCIWriteServerMode() {
        TestUtils.runGorPipe(String.format("gor ../tests/data/gor/genes.gor | top 1 | write oci://%s/tmp/ociwrite/genes.gor", bucketName),
                true, securityContext(), new String[] {"oci://"});
    }


    @Ignore("Not supported yet")
    @Test
    public void testOCIMeta() {
        var result = TestUtils.runGorPipe(String.format("meta oci://%s/tmp/ociwrite/genes.gor", bucketName), true, securityContext(), new String[] {"oci://"});

        Assert.assertFalse(result.isEmpty());
        Assert.assertTrue(result.contains("SOURCE\tTYPE\tOCI"));
    }

    @Ignore("Not supported yet")
    @Test
    public void testOCIMetaWithMetafile() {
        String securityContext = securityContext();
        TestUtils.runGorPipe(String.format("gor ../tests/data/gor/genes.gor | top 1 | write oci://%s/tmp/ociwrite/genes.gorz", bucketName), true, securityContext, new String[] {"oci://"});
        var result = TestUtils.runGorPipe(String.format("meta oci://%s/tmp/ociwrite//genes.gorz", bucketName), true, securityContext, new String[] {"oci://"});

        Assert.assertFalse(result.isEmpty());
        Assert.assertTrue(result.contains("SOURCE\tTYPE\tOCI"));
        Assert.assertTrue(result.contains("GOR\tMD5"));
        Assert.assertTrue(result.contains("GOR\tLINE_COUNT"));
    }
}
