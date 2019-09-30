package com.yee.bigdata.utils;

import java.util.Collection;
import java.util.Collections;

/**
 * An object whose RAM usage can be computed.
 *
 * @lucene.internal
 */
public interface Accountable {

    /**
     * Return the memory usage of this object in bytes. Negative values are illegal.
     */
    long ramBytesUsed();

    /**
     * Returns nested resources of this class.
     * The result should be a point-in-time snapshot (to avoid race conditions).
     * @see Accountables
     */
    default Collection<Accountable> getChildResources() {
        return Collections.emptyList();
    }

}