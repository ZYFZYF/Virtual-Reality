package com.google.vr.sdk.applications.maze;

import java.util.Random;

class Point {
    private float x, y, z;

    Point(float x, float y, float z) {
        super();
        this.x = x;
        this.y = y;
        this.z = z;
    }

    static Point getRandomNormal() {
        Random random = new Random();
        while (true) {
            float x = (float) (random.nextInt(10001) - 5000) / 5000;
            float y = (float) (random.nextInt(10001) - 5000) / 5000;
            float z = (float) (random.nextInt(10001) - 5000) / 5000;
            float r = (float) Math.sqrt(x * x + y * y + z * z);
            if (r < 1) {
                return new Point(x / r, y / r, z / r);
            }
        }
    }

    public static void main(String[] args) {
        getRandomNormal().describe();
        getRandomNormal().describe();
        getRandomNormal().describe();
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

    void describe() {
        System.out.printf("This point is at (%f, %f, %f)\n", x, y, z);
    }

    float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    void normalize() {
        float len = length();
        x /= len;
        y /= len;
        z /= len;
    }
}
