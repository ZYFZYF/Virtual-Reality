package com.google.vr.sdk.applications.maze;

public class Box {
    private Point pos, size;

    public Box(float x, float y, float z, float x_size, float y_size, float z_size) {
        pos = new Point(x, y, z);
        size = new Point(x_size, y_size, z_size);
    }

    Box(Point _pos, Point _size) {
        pos = _pos;
        size = _size;
    }

    Point getPos() {
        return pos;
    }

    Point getSize() {
        return size;
    }

    void describe() {
        System.out.printf("This box's position is (%f, %f, %f) ans size is (%f, %f, %f)", pos.getX(), pos.getY(), pos.getZ(),
                size.getX(), size.getY(), size.getZ());
        System.out.println();
    }
}
