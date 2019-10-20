package com.google.vr.sdk.applications.maze;

class Point {
    private float x, y, z;

    Point(float x, float y, float z) {
        super();
        this.x = x;
        this.y = y;
        this.z = z;
    }

    float getX() {
        return x;
    }

    void setX(float x) {
        this.x = x;
    }

    float getY() {
        return y;
    }

    void setY(float y) {
        this.y = y;
    }

    float getZ() {
        return z;
    }

    void setZ(float z) {
        this.z = z;
    }

    void addX(float x) {
        this.x += x;
    }

    void addY(float y) {
        this.y += y;
    }

    void addZ(float z) {
        this.z += z;
    }

    float getDistance(Point p) {
        return (x - p.x) * (x - p.x) + (y - p.y) * (y - p.y) + (z - p.z) * (z - p.z);
    }
}
