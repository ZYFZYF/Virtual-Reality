package com.google.vr.sdk.applications.maze;

import android.opengl.Matrix;

import java.util.Vector;

public class MosquitoPosition {
    private static final float MOSQUITO_SPEED = 0.001f;
    private float MIN_DISTANCE_BETWEEN_MOSQUITO_AND_WALL = 0.1f;
    private Vector<Box> obstacles;
    private Point pos, prevPos, direction;

    MosquitoPosition(Point startPos, Vector<Box> _obstacles) {
        pos = new Point(startPos.getX(), startPos.getY(), startPos.getZ());
        obstacles = new Vector<>();
        obstacles.addAll(_obstacles);
        direction = new Point(0, 0, -1);
        prevPos = new Point(0, 0, 0);
    }


    void move(Point movePos) {
        float rate = 5.0f;
        direction.addX(movePos.getX() / rate);
        direction.addY(movePos.getY() / rate);
        direction.addZ(movePos.getZ() / rate);
        direction.normalize();
        move();
    }

    void move() {
        prevPos.setX(pos.getX());
        prevPos.setY(pos.getY());
        prevPos.setZ(pos.getZ());
        Point point = new Point(pos.getX() + direction.getX() * MOSQUITO_SPEED, pos.getY() + direction.getY() * MOSQUITO_SPEED, pos.getZ() + direction.getZ() * MOSQUITO_SPEED);
        //如果不合法那么我们反射，只把导致碰撞的那一维取反
        int ret = collisionDetect(point);
        if (point.getY() - MIN_DISTANCE_BETWEEN_MOSQUITO_AND_WALL < 0 || point.getY() + MIN_DISTANCE_BETWEEN_MOSQUITO_AND_WALL > Maze.WALL_HEIGHT) {
            direction.setY(-direction.getY());
        } else if (ret == 1) {

            direction.setX(-direction.getX());

        } else if (ret == 2) {
            direction.setZ(-direction.getZ());
        }
        pos.addX(direction.getX() * MOSQUITO_SPEED);
        pos.addY(direction.getY() * MOSQUITO_SPEED);
        pos.addZ(direction.getZ() * MOSQUITO_SPEED);
        System.out.printf("now mosquito position is (%f, %f, %f)\n", pos.getX(), pos.getY(), pos.getZ());
    }

    public Point getPos() {
        return pos;
    }

    public Point getPrevPos() {
        return prevPos;
    }

    private int collisionDetect(Point p) {

        for (Box box : obstacles) {
            if ((p.getX() > box.getPos().getX() - MIN_DISTANCE_BETWEEN_MOSQUITO_AND_WALL &&
                    p.getX() < box.getPos().getX() + box.getSize().getX() + MIN_DISTANCE_BETWEEN_MOSQUITO_AND_WALL) &&
                    (p.getZ() > box.getPos().getZ() - MIN_DISTANCE_BETWEEN_MOSQUITO_AND_WALL &&
                            p.getZ() < box.getPos().getZ() + box.getSize().getZ() + MIN_DISTANCE_BETWEEN_MOSQUITO_AND_WALL)) {
                if (box.getSize().getX() < box.getSize().getZ()) {
                    return 1;
                } else {
                    return 2;
                }
            }
        }
        return 0;
    }
}
