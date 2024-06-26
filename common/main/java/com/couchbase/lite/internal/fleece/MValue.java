//
// Copyright (c) 2020 Couchbase, Inc.
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
package com.couchbase.lite.internal.fleece;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.MValueConverter;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * The Mutable Fleece implementations are different across the various platforms. Here's what
 * I can figure out (though I really have only rumors to go by).  I think that Jens did two
 * implementations, the second of which was an attempt to make life easier for the platforms.
 * Word has it that it did not succeed in doing that.  iOS still uses the first
 * implementation. Java tried to use the first implementation but, because of a problem with
 * running out of LocalRefs, the original developer for this platform (Java/Android) chose,
 * more or less, to port that first implementation into Java. I think that .NET did something
 * similar. As I understand it both Jim and Sandy tried to update .NET to use Jens' second
 * implementation somewhere in the 2.7 time-frame. They had, at most, partial success.
 * <p>
 * In 9/2020 (CBL-246), I tried to convert this code to use LiteCore's MutableFleece package
 * (that's Jens' second implementations). Both Jim and Jens warned me, without specifics,
 * that doing so might be more trouble than it was worth. Although the LiteCore
 * implementation of Mutable Fleece is relatively clear, this Java code is just plain
 * bizarre. It works, though. I don't think I have ever seen a problem that could be traced
 * to it. I've cleaned it up just bit but other than that, I'm leaving it alone.  I suggest
 * you do the same, unless something changes to make the benefit side of the C/B fraction
 * more interesting.
 * <p>
 * The regrettable upside-down dependency on MValueConverter provides access to package
 * visible symbols in com.couchbase.lite.
 * <p>
 * It worries me that this isn't thread safe... but, as I say, I've never seen it be a problem.
 * <p>
 * 3/2024 (CBL-5486): I've seen a problem!
 * If the parent, the object holding the Fleece reference, is closed, the Fleece object backing
 * all of the contained objects, is freed.
 */
public class MValue extends MValueConverter implements Encodable {

    //-------------------------------------------------------------------------
    // Static members
    //-------------------------------------------------------------------------

    static final MValue EMPTY = new MValue(null, null) {
        @Override
        public boolean isEmpty() { return true; }
    };

    //-------------------------------------------------------------------------
    // Instance members
    //-------------------------------------------------------------------------

    @Nullable
    private FLValue flValue;
    @Nullable
    private Object value;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    public MValue(@Nullable Object obj) { this(obj, null); }

    MValue(@Nullable FLValue val) { this(null, val); }

    private MValue(@Nullable Object obj, @Nullable FLValue val) {
        value = obj;
        this.flValue = val;
    }

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------

    @Override
    public void encodeTo(@NonNull FLEncoder enc) {
        if (isEmpty()) { throw new CouchbaseLiteError("MValue is empty."); }

        if (flValue != null) { enc.writeValue(flValue); }
        else if (value != null) { enc.writeValue(value); }
        else { enc.writeNull(); }
    }

    public boolean isEmpty() { return false; }

    public boolean isMutated() { return flValue == null; }

    @Nullable
    public FLValue getValue() { return flValue; }

    public void mutate() {
        Preconditions.assertNotNull(value, "Native object");
        flValue = null;
    }

    @Nullable
    public Object asNative(@Nullable MCollection parent) {
        if ((value != null) || (flValue == null)) { return value; }

        final NativeValue<?> val = toNative(this, parent);
        if (val.cacheIt) { value = val.nVal; }
        return val.nVal;
    }
}
