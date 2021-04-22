//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public final class JSONUtils {
    private JSONUtils() { }

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        protected synchronized SimpleDateFormat initialValue() {
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            return sdf;
        }
    };

    public static JSONObject toJSON(Map<?, ?> map) throws JSONException {
        if (map == null) { return null; }

        final JSONObject json = new JSONObject();
        for (Map.Entry<?, ?> entry: map.entrySet()) { json.put(entry.getKey().toString(), toJSON(entry.getValue())); }
        return json;
    }

    public static JSONArray toJSON(List<?> list) throws JSONException {
        if (list == null) { return null; }

        final JSONArray json = new JSONArray();
        for (Object value: list) { json.put(toJSON(value)); }

        return json;
    }

    public static String toJSONString(Date date) { return DATE_FORMAT.get().format(date); }

    public static Object toJSON(Object val) throws JSONException {
        if (val instanceof Map<?, ?>) { return toJSON((Map<?, ?>) val); }
        if (val instanceof List<?>) { return toJSON((List<?>) val); }
        if (val == null) { return JSONObject.NULL; }
        return val;
    }

    public static Map<String, Object> fromJSON(JSONObject json) throws JSONException {
        if (json == null) { return null; }

        final Map<String, Object> result = new HashMap<>();
        final Iterator<String> itr = json.keys();
        while (itr.hasNext()) {
            final String key = itr.next();
            result.put(key, fromJSON(json.get(key)));
        }

        return result;
    }

    public static List<Object> fromJSON(JSONArray json) throws JSONException {
        if (json == null) { return null; }

        final List<Object> result = new ArrayList<>();
        for (int i = 0; i < json.length(); i++) { result.add(fromJSON(json.get(i))); }

        return result;
    }

    private static Object fromJSON(Object value) throws JSONException {
        if (value instanceof JSONObject) { return fromJSON((JSONObject) value); }
        if (value instanceof JSONArray) { return fromJSON((JSONArray) value); }
        if (value == JSONObject.NULL) { return null; }
        return value;
    }

    public static Date toDate(String json) {
        if (json == null) { return null; }

        try { return DATE_FORMAT.get().parse(json); }
        catch (ParseException ignore) { }

        return null;
    }
}