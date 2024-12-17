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

package org.gorpipe.gor.table.util;

import org.apache.commons.lang3.RandomStringUtils;
import org.gorpipe.exceptions.GorSystemException;
import org.gorpipe.gor.driver.DataSource;
import org.gorpipe.gor.driver.GorDriverFactory;
import org.gorpipe.gor.driver.meta.SourceReferenceBuilder;
import org.gorpipe.gor.driver.providers.rows.sources.sql.LegacyDbSourceType;
import org.gorpipe.gor.driver.providers.rows.sources.sql.SqlSourceType;
import org.gorpipe.gor.util.Util;
import org.gorpipe.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by gisli on 21/06/2017.
 */
public class PathUtils {

    private static final Logger log = LoggerFactory.getLogger(PathUtils.class);

    private PathUtils() {}

    /**
     * Get normalized absolute path
     *
     * @param path input path.  Relative paths are considered relative to the dicionary root.
     * @return normalized absolute path of {@code path}.
     */

    public static URI resolve(URI root, URI path) {
        if (path == null) {
            return null;
        }

        if (isAbsolutePath(path) || root == null || "".equals(root.toString())) {
            return normalize(path);
        }

        // The uri folder path must end with / for the resolve to work as Path resolve.
        root = markAsFolder(root);

        return normalize(root.resolve(path));
    }

    public static Path resolve(Path root, URI path) {
        return resolve(root, toPath(path));
    }

    public static URI resolve(URI root, String path) {
        return resolve(root, URI.create(path));
    }

    public static String resolve(String root, String path) {
        return root!=null && root.length()>0 ? resolve(toURIFolder(root), path).toString() : path;
    }

    public static Path resolve(Path root, Path path) {
        if (path == null) {
            return null;
        }

        if (path.isAbsolute()) {
            return normalize(path);
        }

        return normalize(root.resolve(path));
    }

    /**
     * Get relativize to table or absolute path.
     *
     * @param path the path to relativize.
     * @return relative to the root path if {@code path} is relative or starts with the table root path, otherwise absolute path is returned.
     * The path is also normalized.
     */
    public static Path relativize(Path root, Path path) {
        if (path == null) {
            return null;
        }

        Path norm = normalize(path);
        // Need to help path to do this right.
        return norm.startsWith(root) ? root.relativize(norm) : norm;
    }

    public static String relativize(String root, String path) {
        if (path == null) {
            return null;
        }

        if (Strings.isNullOrEmpty(root)) {
            return path;
        }

        String norm = normalize(path);
        String stripRoot = stripTrailingSlash(root);
        // Need to help path to do this right.
        return norm.startsWith(stripRoot) ? norm.substring(Math.min(stripRoot.length() + 1, norm.length())) : norm;
    }

    public static URI relativize(URI root, URI path) {
        if (path == null) {
            return null;
        }
        URI relURI = normalize(root.relativize(path));
        return relURI;
    }

    public static String relativize(URI root, String path) {
        if (path == null) {
            return null;
        }
        return relativize(root, URI.create(path)).toString();
    }

    public static boolean isAbsolutePath(String path) {
        return (path.length() > 0 && (path.charAt(0) == '/' || path.charAt(0) == '\\'))
                || path.contains("://")
                || path.contains("mem:")
                || (path.length() > 1 && path.charAt(1) == ':');
    }

    public static boolean isAbsolutePath(URI path) {
        return path.isAbsolute() || path.getPath().startsWith("/");
    }

    /*
     * Needed to add some URI helper methods because of limitations in how URI works.
     *
     * Some problems with URI:
     * 1. Paths.get(uri) fails if uri does not contain schema or if contains fragment, i.e. does not work
     *    properly for relative paths.   Also fails if the given schema does not have a FileSystem registered.
     * 2. path.toUri() transforms the path to absolute path, i.e. does not work for relative paths.  It also returns the
     *    paths as file:///abc/efg which is not consistent with how file URI should be represented (see 3)
     * 3. Some Uri functions (normalize and resolve) transform file:/// to file:/ (which is according ot the standard though),
     *    but we want file:/// so this is consistant between uris and this is also needed by other Nextcode code.
     * 4. Uri equal reqiuires exact match file:/path and file:///path are not equal???? At least there is no startsWith
     *    method so we need to transform to string or path to do that comparison.     8
     */

    /**
     * Normailze URI.  Handles scheme for files better than the uri.normalize() method.
     *
     * @param uri URI to normalize.
     * @return normalized URI.
     */
    public static URI normalize(URI uri) {
        return URI.create(normalize(uri.toString()));
    }

    public static String normalize(String uri) {
        return URI.create(fixFileSchema(convertSlashes(uri))).normalize().toString();
    }

    public static Path normalize(Path path) {
        return Paths.get(fixFileSchema(convertSlashes(path.toString()))).normalize();
    }

    /**
     * Convert URI to path.
     * Handles scheme better than the Paths.get(uri), and removes file: from the resulting Path.
     *
     * @param uri uri to convert.
     * @return Path object representing the URI.
     */
    public static Path toPath(URI uri) {
        return Paths.get(formatUri(uri));
    }

    public static Path toPath(String uri) {
        return Paths.get(formatUri(uri));
    }

    public static URI toURIFolder(Path path) {
        return toURIFolder(path.toString());
    }

    public static URI toURIFolder(String folder) {
        // The uri folder path must end with / for the resolve to work as Path resolve.
        folder = folder.endsWith("/") ? folder : folder + "/";

        return URI.create(normalize(folder));
    }

    /**
     * Format the uri as string.  Decodes and formats the URI.
     *
     * @param uri uri to format, must have valid file: format.
     * @return uri formatted as string.
     */
    public static String formatUri(URI uri) {
        return formatUri(uri.toString());
    }

    public static String formatUri(String uri) {
        return stripTrailingSlash(fixFileSchema(uri));
    }

    public static String stripTrailingSlash(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    public static String markAsFolder(String path) {
        if (!path.endsWith("/")) {
            return path + "/";
        }
        return path;
    }

    public static URI markAsFolder(URI path) {
        if (!path.toString().endsWith("/")) {
            return URI.create(path + "/");
        }
        return path;
    }

    public static URI toRealPath(URI uri) {
        if (isLocal(uri)) {
            try {
                return Path.of(uri.getPath()).toRealPath().toUri();
            } catch (IOException e) {
                // Ignore/
            }
        }

        return uri;
    }

    public static String getParent(String path) {
        String p = stripTrailingSlash(path);
        var idx = p.lastIndexOf("/");
        return idx >= 0 ? p.substring(0,idx) : "";
    }

    public static String getFileName(String path) {
        String p = stripTrailingSlash(path);
        var idx = p.lastIndexOf("/");
        return p.substring(idx+1);
    }

    public static URI getParent(URI uri) {
        return uri.getPath().endsWith("/") ? uri.resolve("..") : uri.resolve(".");
    }

    public static String fixFileSchema(String uri) {
        // TODO:  Should we search for file: or file:/ (and file:// or file:///).  Difference is are we doing this
        //        only for abs pahts or all paths.
        if (uri.startsWith("file:")) {
            if (uri.startsWith("file://")) {
                uri = uri.substring(7);
            } else {
                uri = uri.substring(5);
            }

            // Windows full path hack
            if (uri.length() > 3 && uri.charAt(2) == ':' && Util.isWindowsOS() ) {
                uri = uri.substring(1);
            }
        }

        return uri;
    }

    public static String fixDbSchema(String uri) {
        if (LegacyDbSourceType.DB.match(uri)) {
            return uri.replace(LegacyDbSourceType.ProtocolName, SqlSourceType.ProtocolName + "//");
        }

        return uri;
    }

    public static String convertSlashes(String path) {
        return path.replace('\\', '/');
    }

    public static boolean isLocal(URI path) {
        return path.getScheme() == null || path.getScheme().equals("file");
    }

    public static boolean isLocal(String path) {
        path = fixDbSchema(path);
        return !path.contains(":/") || path.startsWith("file");
    }

    public static long getLastModifiedTime(String fileName, String securityContext, String commonRoot) throws IOException {
        //TODO: This method should really take in SourceReference or better yet be removed and replaced with calls to datasource.getUniqueId
        DataSource ds = GorDriverFactory.fromConfig().getDataSource(new SourceReferenceBuilder(fileName).securityContext(securityContext).commonRoot(commonRoot).build());
        if (ds != null) {
            return ds.getSourceMetadata().getLastModified();
        } else {
            log.warn(String.format("Signature for %s is defaulting to currentTimeMillis (project root: %s)", fileName, commonRoot),
                    new GorSystemException("Stacktrace", null));
            return System.currentTimeMillis();
        }
    }

    public static String getCurrentAbsolutePath() {
        return Path.of("").toAbsolutePath().toString();
    }

    public static Path getTempFilePath(Path filePath) {
        var baseName = filePath.getFileName().toString();
        var dotIndex = baseName.indexOf('.');
        var base = dotIndex > 0 ? baseName.substring(0, dotIndex) : baseName;
        var ext = dotIndex > 0 ? baseName.substring(dotIndex) : "";

        var tempFileName = filePath.getParent().resolve(base
                + "-temp-" + RandomStringUtils.insecure().nextAlphanumeric(8) + ext);

        return tempFileName;
    }

}
