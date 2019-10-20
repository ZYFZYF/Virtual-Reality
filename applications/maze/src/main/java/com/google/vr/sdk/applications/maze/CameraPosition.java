package com.google.vr.sdk.applications.maze;

import android.opengl.Matrix;

import java.util.Vector;

public class CameraPosition {
    private Vector<Box> obstacles;
    private float[] translate;
    private Point pos;
    private float[] temp;

    CameraPosition(Point startPos, Vector<Box> _obstacles) {
        pos = new Point(startPos.getX(), startPos.getY(), startPos.getZ());
        translate = new float[16];
        temp = new float[16];
        Matrix.setIdentityM(translate, 0);
        Matrix.translateM(translate, 0, -pos.getX(), -pos.getY(), -pos.getZ());
        obstacles = new Vector<>();
        obstacles.addAll(_obstacles);
    }


    void move(float x, float y, float z) {
        y = 0;//限制人的高度无法改变
        Point point = new Point(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
        if (!collisionDetect(point)) {
            pos.setX(point.getX());
            pos.setY(point.getY());
            pos.setZ(point.getZ());
            Matrix.translateM(translate, 0, -x, -y, -z);
        }
    }

    public Point getPos() {
        return pos;
    }

    void translateTarget(float[] m, int offset) {
        //Util.drawMatrix("pre m is ", m, offset);
        Matrix.multiplyMM(temp, 0, m, offset, translate, 0);
        for (int i = 0; i < 16; i++) {
            m[i] = temp[i];
        }
        //Util.drawMatrix("translate matrix is :", translate, 0);
        //Util.drawMatrix("nxt m is ", m, offset);

    }

    private boolean collisionDetect(Point p) {
        return false;
    }
}
