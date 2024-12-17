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

package org.gorpipe.gor.driver.meta;

import org.gorpipe.gor.model.ChromoLookup;

/**
 * Created by sigmar on 20/05/16.
 */
public class IndexableSourceReference extends SourceReference {
    private final String indexSource;
    private final String referenceSource;

    public IndexableSourceReference(String url, String indexSource, String referenceSource, String securityContext, String commonRoot, ChromoLookup lookup) {
        super(url, securityContext, commonRoot, lookup, null, false);

        this.indexSource = indexSource;
        this.referenceSource = referenceSource;
    }

    public IndexableSourceReference(String url, IndexableSourceReference parentSourceReference) {
        this(url, parentSourceReference, null);
    }

    public IndexableSourceReference(String url, IndexableSourceReference parentSourceReference, String linkSubPath) {
        super(url, parentSourceReference, linkSubPath);

        this.indexSource = parentSourceReference.getIndexSource();
        this.referenceSource = parentSourceReference.getReferenceSource();
    }

    public String getIndexSource() {
        return indexSource;
    }

    public String getReferenceSource() {
        return referenceSource;
    }
}
