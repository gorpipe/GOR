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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gorpipe.exceptions.GorSystemException;
import org.gorpipe.gor.model.ChromoLookup;
import org.gorpipe.gor.model.DefaultChromoLookup;

import java.io.IOException;
import java.util.Objects;

/**
 * Source reference - url and its context.
 * <p>
 * Created by gisli on 29/02/16.
 */
public class SourceReference {

    public final String url;
    public final String securityContext;
    public final String commonRoot; // TODO: This should be removed?
    public final boolean writeSource;
    @JsonIgnore
    ChromoLookup lookup;
    private final String linkSubPath;
    private boolean isCreatedFromLink = false;
    private Long linkLastModified = null;
    private SourceReference parentSourceReference;
    private boolean isFallback;

    // TODO: evaluate whether the securityContext, lookup and columns should actually be a part of this class.
    // - should the context come in at request time?
    // - should the chromo lookup be retrieved from somewhere instead of pushed into this object?
    // - common root and security context are not used in all driver types, shouldn't this rather be a hash map?
    // - should the context hash map be stored as a part of this class or should it enter the chain at some other point?

    public SourceReference(String url, String securityContext, String commonRoot, ChromoLookup lookup,
                           String linkSubPath, boolean writeSource, boolean isFallback) {
        this.url = url;
        // Pick up default security context here - it's not propagated from GorOptions if this is a sub query.
        if (securityContext == null) {
            this.securityContext = System.getProperty("gor.security.context");
        } else {
            this.securityContext = securityContext;
        }
        this.commonRoot = commonRoot;
        this.lookup = lookup != null ? lookup : new DefaultChromoLookup();
        this.linkSubPath = linkSubPath;
        this.writeSource = writeSource;
        this.isFallback = isFallback;
    }

    public SourceReference(String url, String securityContext, String commonRoot, ChromoLookup lookup,
                           String linkSubPath, boolean writeSource) {
        this(url, securityContext, commonRoot, lookup, linkSubPath, writeSource, true);
    }

    /**
     * @param url url for the source.
     */
    public SourceReference(String url) {
        this(url, null, null, null, null, false);
    }

    /**
     * @param url                   url for the source.
     * @param parentSourceReference parent source reference to copy unupplied context from.
     */
    public SourceReference(String url, SourceReference parentSourceReference) {
        this(url, parentSourceReference, null);
    }

    /**
     * @param url                   url for the source.
     * @param parentSourceReference parent source reference to copy unupplied context from.
     */
    public SourceReference(String url, SourceReference parentSourceReference, String linkSubPath) {
        this(url, parentSourceReference, linkSubPath, parentSourceReference.getSecurityContext());
    }

    /**
     * @param url                   url for the source.
     * @param parentSourceReference parent source reference to copy unupplied context from.
     */
    public SourceReference(String url, SourceReference parentSourceReference, String linkSubPath, String securityContext) {
        this(url, securityContext, parentSourceReference.getCommonRoot(),
                parentSourceReference.getLookup(), linkSubPath,
                parentSourceReference.isWriteSource());
        if (this.parentSourceReference == null) {
            this.parentSourceReference = parentSourceReference;
        }
    }

    @JsonCreator
    public SourceReference(@JsonProperty("url") String url, @JsonProperty("securityContext") String securityContext,
                           @JsonProperty("commonRoot") String commonRoot) {
        this(url, securityContext, commonRoot, null, null, false);
    }

    public String getUrl() {
        return url;
    }

    public String getLinkSubPath() {
        return linkSubPath;
    }

    public String getSecurityContext() {
        return securityContext;
    }

    public String getCommonRoot() {
        return commonRoot;
    }

    public ChromoLookup getLookup() {
        return lookup;
    }

    public boolean isWriteSource() {
        return writeSource;
    }

    public void setLookup(ChromoLookup lookup) {
        this.lookup = lookup;
    }

    public int[] getColumns() {
        return null;
    }

    public boolean isCreatedFromLink() {
        return isCreatedFromLink;
    }

    public void setCreatedFromLink(boolean createdFromLink) {
        isCreatedFromLink = createdFromLink;
    }

    public Long getLinkLastModified() {
        return linkLastModified;
    }

    public void setLinkLastModified(Long linkLastModified) {
        this.linkLastModified = linkLastModified;
    }

    public SourceReference getParentSourceReference() {
        return parentSourceReference;
    }

    public SourceReference getOriginalSourceReference() {
        return parentSourceReference != null ? parentSourceReference.getOriginalSourceReference() : this;
    }

    public boolean isFallback() {
        return isFallback;
    }

    /**
     * Get top level source reference that is not a link.
     * If this is a link reference then self is returned.
     */
    public SourceReference getTopSourceReference() {
        SourceReference top = this;
        while (top.getParentSourceReference() != null
                && !top.getParentSourceReference().getUrl().endsWith(DataType.LINK.suffix)) {
            top = top.getParentSourceReference();
        }
        return top;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceReference)) return false;

        SourceReference that = (SourceReference) o;

        return Objects.equals(url, that.url)
                && Objects.equals(securityContext, that.securityContext)
                && Objects.equals(commonRoot, that.commonRoot)
                && Objects.equals(lookup, that.lookup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, securityContext, commonRoot, lookup);
    }

    @Override
    public String toString() {
        return "SourceContext{" +
                "url='" + url + '\'' +
                ", securityContext='" + securityContext + '\'' +
                ", commonRoot='" + commonRoot + '\'' +
                ", lookup=" + lookup +
                '}';
    }

    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new GorSystemException("Unable to convert SourceReference to JSON. Content:\n" + this.toString(), e);
        }
    }

    public static SourceReference fromJson(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(json, SourceReference.class);
        } catch (IOException e) {
            throw new GorSystemException("Unable to convert JSON to SourceReference. Content:\n" + json, e);
        }
    }

    /**
     * Builder for the SourceReference, use builder copy constructor to allow copying fields from parent SourceReference.
     */
    public static class Builder {
        private final String url;
        private String securityContext;
        private String commonRoot;
        private ChromoLookup lookup;
        private int[] columns;
        private String linkSubPath;

        public Builder(String url) {
            this.url = url;
        }

        public Builder(String url, SourceReference parentSourceReference) {
            this.url = url;
            // Don't copy objects, we want to share the instance with the parent.
            this.securityContext = parentSourceReference.securityContext;
            this.commonRoot = parentSourceReference.commonRoot;
            this.lookup = parentSourceReference.lookup;
            this.linkSubPath = parentSourceReference.linkSubPath;
        }

        public SourceReference build() {
            return new SourceReference(url, securityContext, commonRoot, lookup, linkSubPath, false);
        }

        public Builder securityContext(String securityContext) {
            this.securityContext = securityContext;
            return this;
        }

        public Builder commonRoot(String commonRoot) {
            this.commonRoot = commonRoot;
            return this;
        }

        public Builder lookup(ChromoLookup lookup) {
            this.lookup = lookup;
            return this;
        }
    }
}
