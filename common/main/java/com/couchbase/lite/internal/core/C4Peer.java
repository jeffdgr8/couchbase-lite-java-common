//
// Copyright (c) 2023 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.internal.core;

import androidx.annotation.CallSuper;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.exec.CBLExecutor;
import com.couchbase.lite.internal.exec.Cleaner;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


public abstract class C4Peer implements AutoCloseable {
    /**
     * WARNING:
     * Be very careful with the implementations of this class!
     * Anything to which a PeerCleaner holds a reference (even implicitly, as a lambda)
     * will be visible until its clean() method is called  If the reference is to the object
     * that the Cleaner is supposed to clean up, that object will be permanently visible
     * and never eligible for collection: a permanent memory leak
     */
    public interface PeerCleaner {
        void dispose(long peer);
    }

    private static class PeerHolder implements Cleaner.Cleanable {
        @Nullable
        private final PeerCleaner cleaner;
        private final long id;

        @GuardedBy("name")
        private long peer;

        // Instrumentation
        @NonNull
        private final String name;
        private final long createdAt;
        @NonNull
        private final AtomicReference<Exception> lifecycle;

        PeerHolder(@NonNull String name, long peer, @Nullable PeerCleaner cleaner) {
            this.id = peer;
            this.cleaner = cleaner;

            this.peer = peer;

            this.name = name;
            this.createdAt = System.currentTimeMillis();
            this.lifecycle
                = new AtomicReference<>((!CouchbaseLiteInternal.debugging()) ? null : new Exception("Created at:"));
        }

        /**
         * Cleanup: Release the native peer.
         */
        @SuppressFBWarnings("JLM_JSR166_UTILCONCURRENT_MONITORENTER")
        public final void clean(boolean finalizing) {
            final C4Peer.PeerCleaner localCleaner = cleaner;
            if (localCleaner != null) {
                // All use of the peer is protected by the PeerLock.  If some other thread is still
                // using the peer it must also be holding its lock.  When we get the lock, we set
                // the peer to 0, so no other thread will be able to use it and queue the disposer,
                // which, at that point, can safely use the peer without a lock.
                final Object lock = getPeerLock();
                CORE_CLEANER.execute(() -> {
                    final long peerRef;
                    synchronized (lock) {
                        peerRef = peer;
                        if (peerRef == 0) { return; }
                        peer = 0L;
                    }
                    localCleaner.dispose(peerRef);
                });
            }

            final Exception origin = lifecycle.get();
            if (origin == null) { return; }
            // Debug instrumentation: done only if CouchbaseLiteInternal.debugging is true

            if (!finalizing) {
                lifecycle.set(new Exception("Closed at:", origin));
                return;
            }

            // Keep this at the debug level and only do it if debugging.  It frightens the children.
            // Apparently this call is bizarrely expensive: just don't do it unless you really need it.
            // Definitely don't do it on the cleaner thread
            CORE_CLEANER.execute(() -> Log.d(LogDomain.DATABASE, "Peer %s not explicitly closed", createdAt, name));
        }

        // It would be disastrous if there are two objects that plan on cleaning up the same peer
        // PeerHolders are equal if they hold the same peer

        @Override
        public int hashCode() { return (int) id; }

        @Override
        public boolean equals(@Nullable Object o) {
            return (this == o) || ((o instanceof PeerHolder) && (((PeerHolder) o).id == id));
        }

        @NonNull
        @Override
        public String toString() { return "PeerHolder{" + Long.toHexString(peer) + " @" + createdAt + "}"; }

        @NonNull
        Object getPeerLock() { return name; }

        // Log an attempt to use a closed peer.
        private void logBadCall() {
            Log.w(LogDomain.DATABASE, "Operation on closed native peer", new Exception("Used at:", lifecycle.get()));
        }
    }

    @VisibleForTesting
    static final Cleaner CLEANER = new Cleaner("c4peer");
    @VisibleForTesting
    static final CBLExecutor CORE_CLEANER = new CBLExecutor("CoreCleaner", 3, 3, new LinkedBlockingQueue<>());


    @NonNull
    private final String name;
    @NonNull
    private final PeerHolder peerHolder;
    @NonNull
    final Cleaner.Cleanable cleaner;

    protected C4Peer(long peer, @Nullable PeerCleaner cleaner) {
        this.name = getClass().getSimpleName() + ClassUtils.objId(this);

        this.peerHolder = new PeerHolder(name, Preconditions.assertNotZero(peer, "peer"), cleaner);

        // WARNING:
        // Anything to which the peerHolder holds a reference will be reachable until the cleaner is run
        this.cleaner = CLEANER.register(this, peerHolder);
    }

    @NonNull
    @Override
    public String toString() { return name; }

    /**
     * Explicit close: use the cleaner to clean up
     * the Cleaner, and, exactly once, call dispose.
     */
    @Override
    @CallSuper
    public void close() { cleaner.clean(false); }

    protected final <E extends Exception> void voidWithPeerOrWarn(@NonNull Fn.ConsumerThrows<Long, E> fn) throws E {
        synchronized (getPeerLock()) {
            final long peer = peerHolder.peer;
            if (peer != 0) {
                fn.accept(peer);
                return;
            }

            peerHolder.logBadCall();
        }
    }

    protected final <E extends Exception> void voidWithPeerOrThrow(@NonNull Fn.ConsumerThrows<Long, E> fn)
        throws E {
        synchronized (getPeerLock()) {
            final long peer = peerHolder.peer;
            if (peer != 0) {
                fn.accept(peer);
                return;
            }
        }

        peerHolder.logBadCall();
        throw new CouchbaseLiteError("Closed peer");
    }

    @Nullable
    protected final <R, E extends Exception> R withPeerOrNull(@NonNull Fn.NullableFunctionThrows<Long, R, E> fn)
        throws E {
        synchronized (getPeerLock()) {
            final long peer = peerHolder.peer;
            if (peer != 0) { return fn.apply(peer); }

            peerHolder.logBadCall();
            return null;
        }
    }

    @NonNull
    protected final <R, E extends Exception> R withPeerOrDefault(
        @NonNull R def,
        @NonNull Fn.NullableFunctionThrows<Long, R, E> fn)
        throws E {
        synchronized (getPeerLock()) {
            final long peer = peerHolder.peer;
            if (peer != 0) {
                final R val = fn.apply(peer);
                return (val != null) ? val : def;
            }
        }

        peerHolder.logBadCall();
        return def;
    }

    @NonNull
    protected final <R, E extends Exception> R withPeerOrThrow(
        @NonNull Fn.NonNullFunctionThrows<Long, R, E> fn)
        throws E {
        synchronized (getPeerLock()) {
            final long peer = peerHolder.peer;
            if (peer != 0) { return fn.apply(peer); }
        }

        peerHolder.logBadCall();
        throw new IllegalStateException("Closed peer");
    }

    /**
     * Get this object's lock.
     * Enables subclasses to assure atomicity with this class' internal behavior.
     * You *know* you gotta be careful with this, right?
     *
     * @return the lock used by this object
     */
    @NonNull
    protected final Object getPeerLock() { return peerHolder.getPeerLock(); }
}