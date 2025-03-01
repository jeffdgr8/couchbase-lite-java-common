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
package com.couchbase.lite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.internal.utils.Internal;


/**
 * This is an internal interface and not part of the public API.
 */
// You look at this and the ArrayInterface and you see that they
// are, obviously, the same and should be a single interface with a
// generic key type. Just try it...
// Result extends both interfaces.  You can't extend
// two interfaces with the same erasure.  Curses!!!
@Internal("This interface is not part of the public API")
public interface DictionaryInterface extends JFleeceCollectionInterface {
    boolean contains(@NonNull String key);

    // Return a COPY of all keys
    @NonNull
    List<String> getKeys();

    boolean getBoolean(@NonNull String key);

    int getInt(@NonNull String key);

    long getLong(@NonNull String key);

    float getFloat(@NonNull String key);

    double getDouble(@NonNull String key);

    @Nullable
    Number getNumber(@NonNull String key);

    @Nullable
    String getString(@NonNull String key);

    @Nullable
    Date getDate(@NonNull String key);

    @Nullable
    Blob getBlob(@NonNull String key);

    @Nullable
    ArrayInterface getArray(@NonNull String key);

    @Nullable
    DictionaryInterface getDictionary(@NonNull String key);

    @Nullable
    Object getValue(@NonNull String key);

    @Nullable
    <T> T getValue(@NonNull Class<T> klass, @NonNull String key);

    @NonNull
    Map<String, Object> toMap();
}
