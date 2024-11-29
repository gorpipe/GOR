package org.gorpipe.gor.util;

import org.apache.commons.lang3.RandomStringUtils;
import org.gorpipe.exceptions.GorDataException;
import org.gorpipe.gor.driver.meta.DataType;

import java.nio.file.Files;

public class DataUtil {

    public static final String TEMPTEMPFILE = "temptempfile";

    public static boolean isMem(String file) {
        return DataType.isOfType(file, DataType.MEM);
    }

    public static boolean isGord(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.GORD);
    }

    public static boolean isGor(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.GOR);
    }

    public static boolean isGorz(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.GORZ);
    }

    public static boolean isNord(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.NORD);
    }

    public static boolean isNor(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.NOR);
    }

    public static boolean isNorz(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.NORZ);
    }

    public static boolean isNorSource(String file) {
        return isNorz(file)
                || isTsv(file)
                || isNor(file);
    }

    public static boolean isDictionary(String file) {
        return isGord(file) || isNord(file) ;
    }

    public static boolean isTxt(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.TXT);
    }

    public static boolean isTsv(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.TSV);
    }

    public static boolean isAnyCsv(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.CSV)
                || DataType.isOfTypeOrLinksToType(file, DataType.CSVGZ);
    }

    public static boolean isLink(String file) {
        return DataType.isOfType(file, DataType.LINK);
    }

    public static boolean isMeta(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.META);
    }

    public static boolean isRScript(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.R);
    }

    public static boolean isShellScript(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.SH);
    }

    public static boolean isPythonScript(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.PY);
    }

    public static boolean isYml(String file) {
        // yml files can have params, so we can have .yml(), .yml:, .yml?..
        return DataType.isOfTypeOrLinksToType(file, DataType.YML) ||
                DataType.containsTypeWithCallingSuffix(file, DataType.YML);
    }

    public static boolean isGorq(String file) {
        // gorq files can have params, so we can have .gorq(), .gorq:, .gorq?..
        return DataType.containsType(file, DataType.GORQ);
    }

    public static boolean isParquet(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.PARQUET);
    }

    public static boolean isBam(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.BAM);
    }

    public static boolean isCram(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.CRAM);
    }

    public static boolean isBgen(String file) { return DataType.isOfTypeOrLinksToType(file, DataType.BGEN); }

    public static boolean isAnyVcf(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.VCF)
                || DataType.isOfTypeOrLinksToType(file, DataType.VCFGZ)
                || DataType.isOfTypeOrLinksToType(file, DataType.VCFBGZ);
    }

    public static boolean isGZip(String file) {
        return DataType.isOfTypeOrLinksToType(file, DataType.GZ);
    }

    public static String toFile(String name, DataType type) {
        return name + type.suffix;
    }

    public static String toLinkFile(String name, DataType type) {
        return name + type.suffix + DataType.LINK.suffix;
    }

    public static String toTempTempFile(String file) {
        var type = DataType.fromFileName(file);

        if (type != null) {
            return file.replace(type.suffix,  "." + RandomStringUtils.random(8, true, true) + "." + TEMPTEMPFILE + type.suffix);
        }

        throw new GorDataException(String.format("Data type does not exist for file: %s", file));
    }

    public static boolean isTempTempFile(String file) {
        return file.contains(TEMPTEMPFILE);
    }
}
