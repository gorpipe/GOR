package org.gorpipe.oci.driver;

import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.driver.providers.stream.sources.CommonFilesTests;
import org.gorpipe.gor.driver.providers.stream.sources.StreamSource;
import org.gorpipe.test.IntegrationTests;
import org.gorpipe.utils.DriverUtils;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.Properties;

@Ignore("Not yet supported")
@Category(IntegrationTests.class)
public class ITestOCICommonFilesTests extends CommonFilesTests {

    private String bucketName = "gdb_gor_test_data_dev";

    private static String OCI_TENANT;
    private static String OCI_USER;

    private static String OCI_SIMPLE_PRIVATE_KEY;
    private static String OCI_SIMPLE_FINGERPRINT;


    @Rule
    public final ProvideSystemProperty gorSecurityContext
            = new ProvideSystemProperty("gor.security.context", securityContext());

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();

    private OCIObjectStorageSourceProvider provider = new OCIObjectStorageSourceProvider();

    public ITestOCICommonFilesTests() throws IOException {
    }

    @BeforeClass
    static public void setUpClass() {
        Properties props = DriverUtils.getDriverProperties();
        OCI_TENANT = props.getProperty("OCI_TENANT");
        OCI_USER = props.getProperty("OCI_USER");
        OCI_SIMPLE_PRIVATE_KEY = props.getProperty("OCI_SIMPLE_PRIVATE_KEY");
        OCI_SIMPLE_FINGERPRINT = props.getProperty("OCI_SIMPLE_FINGERPRINT");
    }

    @Override
    protected String securityContext()  {
        return DriverUtils.ociSecurityContext(OCI_TENANT, OCI_USER, OCI_SIMPLE_PRIVATE_KEY, OCI_SIMPLE_FINGERPRINT);
    }

    @Override
    protected String getDataName(String name) {
        return String.format("oci://%s/csa_test_data/data_sets/bvl_min_gor/%s", bucketName, name);
    }

    @Override
    protected StreamSource createSource(String name) throws IOException {
        var sr = new SourceReference(name, securityContext(), null, null, null, false);
        return provider.resolveDataSource(sr);
    }
}
