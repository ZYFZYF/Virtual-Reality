package com.google.vr.sdk.applications.maze;

import java.util.Random;
import java.util.Vector;

public class Maze {
    private static final float WALL_WIDTH = 0.2f;
    private static final float PATH_WIDTH = 0.5f;
    private static final float WALL_HEIGHT = 1f;
    private static final float PEOPLE_HEIGHT = 0.7f;
    private final int n;
    private final int m;
    private final int[] dx = {1, 0};
    private final int[] dy = {0, 1};
    private boolean[][] isOpenHor;
    private boolean[][] isOpenVer;
    private int[] father;
    private Vector<Box> walls;
    private Random random;


    Maze(int _N, int _M) {
        n = _N;
        m = _M;
        isOpenHor = new boolean[n + 1][m];
        isOpenVer = new boolean[n][m + 1];
        father = new int[n * m];
        initMaze();
        random = new Random();
    }

    public static void main(String[] args) {
        Maze maze = new Maze(8, 8);
        for (int i = 0; i < 8 + 1; i++) {
            for (int j = 0; j < 8; j++) {
                System.out.printf("(%d, %d): %d ", i, j, maze.isHorizontalWall(i, j) ? 1 : 0);
                maze.getHorizontalWallPosition(i, j).describe();
            }
        }
    }

    private void generateWalls() {
        walls = new Vector<>();
        for (int i = 0; i < n + 1; i++) {
            for (int j = 0; j < m; j++) {
                if (isHorizontalWall(i, j)) {
                    walls.add(getHorizontalWallPosition(i, j));
                }
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m + 1; j++) {
                if (isVerticalWall(i, j)) {
                    walls.add(getVerticalWallPosition(i, j));
                }
            }
        }
    }

    Vector<Box> getWalls() {
        return walls;
    }

    Box getHorizontalWallPosition(int r, int c) {
        Point pos = new Point(c * (WALL_WIDTH + PATH_WIDTH) + WALL_WIDTH, 0, r * (WALL_WIDTH + PATH_WIDTH));
        Point size = new Point(PATH_WIDTH + WALL_WIDTH, WALL_HEIGHT, WALL_WIDTH);
        //一般的墙都自动补上右边的空格，如果这个空格没有被左边的补上，就只能由右边的来补上了
        if (c == 0 || !isHorizontalWall(r, c - 1)) {
            pos.addX(-WALL_WIDTH);
            size.addX(WALL_WIDTH);
        }
        return new Box(pos, size);
    }


    Box getVerticalWallPosition(int r, int c) {
        Point pos = new Point(c * (WALL_WIDTH + PATH_WIDTH), 0, r * (WALL_WIDTH + PATH_WIDTH) + WALL_WIDTH);
        Point size = new Point(WALL_WIDTH, WALL_HEIGHT, PATH_WIDTH);
        // 如果是两面竖着的墙而两边没有横墙会留空隙，这里把它补上
        if ((r + 1 == n || (r + 1 < n && isVerticalWall(r + 1, c)) &&
                (c == 0 || (c > 0 && !isHorizontalWall(r + 1, c - 1))) &&
                (c == m || (c < m && !isHorizontalWall(r + 1, c))))) {
            size.addZ(WALL_WIDTH);
        }
        return new Box(pos, size);
    }

    boolean isHorizontalWall(int r, int c) {
        if (r < 0 || r > n || c < 0 || c >= m) {
            return false;
        } else {
            return !isOpenHor[r][c];
        }
    }

    boolean isVerticalWall(int r, int c) {
        if (r < 0 || r >= n || c < 0 || c > m) {
            return false;
        } else {
            return !isOpenVer[r][c];
        }
    }

    private int getFather(int i) {
        return father[i] == i ? i : getFather(father[i]);
    }

    private int getFather(int r, int c) {
        return getFather(r * m + c);
    }

    private void initMaze() {
        for (int i = 0; i < n * m; i++) {
            father[i] = i;
        }
        for (int i = 0; i < n + 1; i++) {
            for (int j = 0; j < m; j++) {
                isOpenHor[i][j] = false;
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m + 1; j++) {
                isOpenVer[i][j] = false;
            }
        }
        int connectedBlock = n * m;
        Random random = new Random();
        while (connectedBlock != 1) {
            int r = random.nextInt(n);
            int c = random.nextInt(m);
            if (r == n - 1 && c == m - 1) {
                continue;
            }
            int p, q;
            while (true) {
                int x = random.nextInt(2);
                p = r + dx[x];
                q = c + dy[x];
                if (p < 0 || p >= n || q < 0 || q >= m) {
                    continue;
                }
                if (getFather(r, c) != getFather(p, q)) {
                    father[getFather(r, c)] = getFather(p, q);
                    connectedBlock--;
                    if (x == 0) {
                        isOpenHor[r + 1][c] = true;
                    } else {
                        isOpenVer[r][c + 1] = true;
                    }
                }
                break;
            }
        }
        isOpenHor[0][m / 2] = true;
        describe();
        generateWalls();
    }

    private void describe() {
        System.out.println("Generated maze is:");
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                System.out.print(' ');
                System.out.print(isOpenHor[i][j] ? ' ' : '-');
            }
            System.out.println();
            for (int j = 0; j <= m; j++) {
                System.out.print(isOpenVer[i][j] ? ' ' : '|');
                System.out.print(' ');
            }
            System.out.println();
        }
        for (int j = 0; j < m; j++) {
            System.out.print(' ');
            System.out.print(isOpenHor[n][j] ? ' ' : '-');
        }
    }

    Point generateStartPoint() {
        int r = random.nextInt(n - 1) + 1;
        int c = random.nextInt(m - 1) + 1;
        System.out.printf("Generate grid is (%d, %d) and pos is (%f, %f, %f)\n", r, c, r * (WALL_WIDTH + PATH_WIDTH) + WALL_WIDTH + 0.5f * PATH_WIDTH, PEOPLE_HEIGHT, c * (WALL_WIDTH + PATH_WIDTH) + WALL_WIDTH + 0.5F * PATH_WIDTH);
        return new Point(r * (WALL_WIDTH + PATH_WIDTH) + WALL_WIDTH + 0.5f * PATH_WIDTH, PEOPLE_HEIGHT, c * (WALL_WIDTH + PATH_WIDTH) + WALL_WIDTH + 0.5F * PATH_WIDTH);
    }
}
