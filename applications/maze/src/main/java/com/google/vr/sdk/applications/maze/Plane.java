package com.google.vr.sdk.applications.maze;

class Plane {
    private Texture texture;
    private Point center, size, rotateDirection;
    private float rotateDegree;

    Plane(Point center, Point size, Point rotateDirection, float rotateDegree, Texture texture) {
        this.center = center;
        this.size = size;
        this.rotateDirection = rotateDirection;
        this.rotateDegree = rotateDegree;
        this.texture = texture;
    }

    float getRotateDegree() {
        return rotateDegree;
    }

    Texture getTexture() {
        return texture;
    }

    Point getCenter() {
        return center;
    }

    Point getSize() {
        return size;
    }

    Point getRotateDirection() {
        return rotateDirection;
    }
}
