package com.gis.utils;

public enum GeometryEnum {
    Line("LineString"),
    Polygon("Polygon");

    GeometryEnum(String type) {
        this.type = type;
    }

    private String type;

    public String getType() {
        return type;
    }
}
