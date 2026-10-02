// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray.core;

/** SNI has activation calls, not mouse click counts. Each pair toggles at most once. */
public final class ActivationPolicy {
    private long last=Long.MIN_VALUE;
    public synchronized boolean accept(boolean single, long now, long interval) {
        if(single) { last=Long.MIN_VALUE;return true; }
        if(last!=Long.MIN_VALUE && now>=last && now-last<=interval) {last=Long.MIN_VALUE;return true;}
        last=now;return false;
    }
}
