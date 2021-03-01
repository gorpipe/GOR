/*
 *  BEGIN_COPYRIGHT
 *
 *  Copyright (C) 2011-2013 deCODE genetics Inc.
 *  Copyright (C) 2013-2021 WuXi NextCode Inc.
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

package org.gorpipe.gor.driver.providers;

import org.gorpipe.gor.driver.DataSource;
import org.gorpipe.gor.driver.meta.DataType;

import java.io.IOException;
import java.util.List;

/**
 * Represents a Source file.
 * <p>
 * Created by villi on 23/08/15.
 */
public interface SourceFile {

    String getName() throws IOException;

    DataSource getFileSource();

    DataSource getIndexSource();

    DataSource getReferenceSource();

    void setIndexSource(DataSource index);

    void setReferenceSource(DataSource reference);

    List<String> possibleIndexNames() throws IOException;

    boolean supportsIndex();
    boolean supportsReference();

    String getReferenceFileName();

    DataType getType() throws IOException;
}
