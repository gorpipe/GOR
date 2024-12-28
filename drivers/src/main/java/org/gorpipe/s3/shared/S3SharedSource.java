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

package org.gorpipe.s3.shared;

import com.google.common.base.Strings;
import org.gorpipe.gor.driver.meta.DataType;
import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.table.util.PathUtils;
import org.gorpipe.s3.driver.*;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.MalformedURLException;

import java.nio.file.Path;

/**
 * Represents an object in Amazon S3 (created from S3Shared source reference).
 */
public class S3SharedSource extends S3Source {

    // Project link file is a link file within the project that points to this source.
    private String projectLinkFile;
    private String projectLinkFileContent;
    private final String relativePath;
    private final S3SharedConfiguration s3SharedConfig;

    /**
     * Create source
     *
     */
    public S3SharedSource(S3Client client, S3AsyncClient asyncClient, SourceReference sourceReference,
                          String relativePath, S3SharedConfiguration s3SharedConfig) throws MalformedURLException {
        super(client, asyncClient, sourceReference);
        this.relativePath = relativePath;
        this.s3SharedConfig = s3SharedConfig;
    }

    public String getRelativePath() {
        return relativePath;
    }

    @Override
    public String getProjectLinkFile() {
        return projectLinkFile;
    }

    public void setProjectLinkFile(String projectLinkFile) {
        this.projectLinkFile = projectLinkFile;
    }

    @Override
    public String getProjectLinkFileContent() {
        return !Strings.isNullOrEmpty(projectLinkFileContent) ? projectLinkFileContent : getFullPath();
    }

    public void setProjectLinkFileContent(String projectLinkFileContent) {
        this.projectLinkFileContent = projectLinkFileContent;
    }

    @Override
    public boolean forceLink() {
        return projectLinkFile != null && !projectLinkFile.isEmpty();
    }

    @Override
    public String getAccessValidationPath() {
        if (getSourceReference().isCreatedFromLink()) {
            return getSourceReference().getOriginalSourceReference().getUrl();
        } else {
            return getRelativePath();
        }
    }

    @Override
    protected String s3SubPathToUriString(Path p) {
        // Need to change the s3 path to s3data path.

        Path subPath = getPath().relativize(p);
        String uri = PathUtils.resolve(getSourceReference().getParentSourceReference().getUrl(), subPath.toString());
        String updatedUri = removeExtraFolder(uri);
        return PathUtils.formatUri(updatedUri.toString());
    }

    private String removeExtraFolder(String path) {
        if (!path.toString().endsWith("/")) {
            String fileName = PathUtils.getFileName(path);
            int fileNameDotIndex = fileName.indexOf('.');
            String extraFolderCand = fileName.substring(0, fileNameDotIndex > 0 ? fileNameDotIndex : fileName.length());

            String parentPath = PathUtils.getParent(path);
            String parentParentPath = PathUtils.getParent(parentPath);
            if (!Strings.isNullOrEmpty(extraFolderCand) &&
                    extraFolderCand.equals(PathUtils.getFileName(parentPath)) &&
                    !Strings.isNullOrEmpty(parentParentPath)) {
                return PathUtils.resolve(parentParentPath, fileName);
            }
        }
        return path;
    }

    @Override
    public SourceReference getTopSourceReference() {
        // Shared source should be access though links, so find the first link (which should be the direct access link)
        SourceReference top = getSourceReference();
        while (top.getParentSourceReference() != null && !top.getParentSourceReference().getUrl().endsWith(DataType.LINK.suffix)) {
            top = top.getParentSourceReference();
        }
        return top;
    }
}
