package com.couchbase.lite;

public class Meta {
    //---------------------------------------------
    // Inner public Class
    //---------------------------------------------
    public static class MetaExpression extends Expression.PropertyExpression {
        MetaExpression(String property) {
            super(property);
        }
    }

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    Meta() {
    }

    //---------------------------------------------
    // API - public methods
    //------------------------------------------
    public MetaExpression getId() {
        return new MetaExpression("_id");
    }

    public MetaExpression getSequence() {
        return new MetaExpression("_sequence");
    }
}