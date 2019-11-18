package com.google.vr.sdk.applications.maze;

import android.opengl.Matrix;

import java.util.Vector;

public class CameraPosition {
    private float MIN_DISTANCE_BETWEEN_PEOPLE_AND_WALL = 0.2f;
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
        System.out.println(obstacles.size());
        for (Box box : obstacles) {
            box.describe();
        }
    }


    boolean move(float x, float y, float z) {
        y = 0;//限制人的高度无法改变
        Point point = new Point(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
        if (!collisionDetect(point)) {
            //System.out.printf("Move (%f, %f, %f) successed and now is (%f, %f, %f)\n", x, y, z, point.getX(), point.getY(), point.getZ());
            pos.setX(point.getX());
            pos.setY(point.getY());
            pos.setZ(point.getZ());
            Matrix.translateM(translate, 0, -x, -y, -z);
            return true;
        } else {
            return false;
        }
    }

    public Point getPos() {
        return pos;
    }

    void translateTarget(float[] m, int offset) {
        Matrix.multiplyMM(temp, 0, m, offset, translate, 0);
        for (int i = 0; i < 16; i++) {
            m[i] = temp[i];
        }
    }

    private boolean collisionDetect(Point p) {
        for (Box box : obstacles) {
            if (p.getX() > box.getPos().getX() - MIN_DISTANCE_BETWEEN_PEOPLE_AND_WALL &&
                    p.getX() < box.getPos().getX() + box.getSize().getX() + MIN_DISTANCE_BETWEEN_PEOPLE_AND_WALL &&
                    p.getZ() > box.getPos().getZ() - MIN_DISTANCE_BETWEEN_PEOPLE_AND_WALL &&
                    p.getZ() < box.getPos().getZ() + box.getSize().getZ() + MIN_DISTANCE_BETWEEN_PEOPLE_AND_WALL) {
                return true;
            }
        }
        return false;
    }

    int getNowRow() {
        return (int) Math.floor(pos.getZ() / (Maze.WALL_WIDTH + Maze.PATH_WIDTH));
    }

    int getNowCol() {
        return (int) Math.floor(pos.getX() / (Maze.WALL_WIDTH + Maze.PATH_WIDTH));

    }
}
