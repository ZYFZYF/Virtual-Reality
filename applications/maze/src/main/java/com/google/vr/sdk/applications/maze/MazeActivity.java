/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.vr.sdk.applications.maze;

import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import com.google.vr.sdk.audio.GvrAudioEngine;
import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import java.io.IOException;
import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;

/**
 * A Google VR sample application.
 *
 * <p>This app presents a scene consisting of a wall and a floating object. When the user finds the
 * object, they can invoke the trigger action, and a new object will be randomly spawned. When in
 * Cardboard mode, the user must gaze at the object and use the Cardboard trigger button. When in
 * Daydream mode, the user can use the controller to position the cursor, and use the controller
 * buttons to invoke the trigger action.
 */
public class MazeActivity extends GvrActivity implements GvrView.StereoRenderer {
    private static final String TAG = "MazeActivity";

    private static final int MAZE_WIDTH = 8;
    private static final int MAZE_HEIGHT = 8;
    private static final float STEP_LENGTH = 0.01f;
    private static final long DOUBLE_CLICK_INTERVAL_LIMIT = 300;

    private static final float Z_NEAR = 0.01f;
    private static final float Z_FAR = 10.0f;

    private static final String OBJECT_SOUND_FILE = "audio/bgm64.ogg";
    private static final String BUILD_SUCCESS = "audio/build_success.mp3";
    private static final String BUILD_FAIL = "audio/build_fail2.mp3";
    private static final String FINAL_SUCCESS = "audio/final_success.mp3";
    private static final String SUCCESS_SOUND_FILE = "audio/build_fail.mp3";
    private static final String COLLIDE_WALL = "audio/wall.mp3";

    private static final float FLOOR_HEIGHT = -2.0f;

    private static final float ANGLE_LIMIT = 0.2f;

    // The maximum yaw and pitch of the target object, in degrees. After hiding the target, its
    // yaw will be within [-MAX_YAW, MAX_YAW] and pitch will be within [-MAX_PITCH, MAX_PITCH].
    private static final float MAX_YAW = 100.0f;
    private static final float MAX_PITCH = 25.0f;
    private static final String[] OBJECT_VERTEX_SHADER_CODE =
            new String[]{
                    "uniform mat4 u_MVP;",
                    "attribute vec4 a_Position;",
                    "attribute vec2 a_UV;",
                    "varying vec2 v_UV;",
                    "",
                    "void main() {",
                    "  v_UV = a_UV;",
                    "  gl_Position = u_MVP * a_Position;",
                    "}",
            };
    private static final String[] OBJECT_FRAGMENT_SHADER_CODE =
            new String[]{
                    "precision mediump float;",
                    "varying vec2 v_UV;",
                    "uniform sampler2D u_Texture;",
                    "",
                    "void main() {",
                    "  // The y coordinate of this sample's textures is reversed compared to",
                    "  // what OpenGL expects, so we invert the y coordinate.",
                    "  gl_FragColor = texture2D(u_Texture, vec2(v_UV.x, 1.0 - v_UV.y));",
                    "}",
            };
    private long lastClickTimeMillis = 0;
    private long lastCollideTimeMillis = 0;
    private boolean isMoving = false;
    private boolean success = false;
    private int objectProgram;

    private int objectPositionParam;
    private int objectUvParam;
    private int objectModelViewProjectionParam;

    private TexturedMesh wall, floor, square_xy, square_yz;
    private Texture wallTex, floorTex, yesTex, noTex, ceilTex;

    private float[] camera;
    private float[] view;
    private float[] headView;
    private float[] modelViewProjection;
    private float[] modelView;
    private float[] perspective;

    private float[] modelCeil;
    private float[] modelFloor;
    private float[][][] modelHorizontalWall;
    private float[][][] modelVerticalWall;
    private float[][][] modelHorizontalMark;
    private float[][][] modelVerticalMark;
    private MediaPlayer mPlayer, mNextPlayer;
    private int mPlayResId = R.raw.bgm1;

    private float[] headRotation;
    private float[] headDirection;

    private GvrAudioEngine gvrAudioEngine;
    private volatile int sourceId = GvrAudioEngine.INVALID_ID;
    private volatile int successSourceId = GvrAudioEngine.INVALID_ID;

    private Maze maze;
    private CameraPosition cameraPosition;

    private Vector<Plane> planes;

    /**
     * Sets the view to our GvrView and initializes the transformation matrices we will use
     * to render our scene.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initializeGvrView();

        camera = new float[16];
        view = new float[16];
        modelViewProjection = new float[16];
        modelView = new float[16];
        headRotation = new float[4];
        headDirection = new float[4];
        headView = new float[16];
        maze = new Maze(MAZE_HEIGHT, MAZE_WIDTH);
        cameraPosition = new CameraPosition(maze.generateStartPoint(), maze.getWalls());
        modelHorizontalWall = new float[MAZE_HEIGHT + 1][MAZE_WIDTH][16];
        modelVerticalWall = new float[MAZE_HEIGHT][MAZE_WIDTH + 1][16];
        modelHorizontalMark = new float[MAZE_HEIGHT + 1][MAZE_WIDTH][16];
        modelVerticalMark = new float[MAZE_HEIGHT][MAZE_WIDTH + 1][16];
        modelFloor = new float[16];
        Matrix.setIdentityM(modelFloor, 0);
        Matrix.scaleM(modelFloor, 0, 200, 1, 200);
        modelCeil = new float[16];
        Matrix.setIdentityM(modelCeil, 0);
        Point maxPoint = maze.getMaxPoint();
        Matrix.translateM(modelCeil, 0, maxPoint.getX() / 2, Maze.WALL_HEIGHT, maxPoint.getZ() / 2);
        Matrix.scaleM(modelCeil, 0, maxPoint.getX(), 0, maxPoint.getZ());
        for (int i = 0; i < MAZE_HEIGHT + 1; i++) {
            for (int j = 0; j < MAZE_WIDTH; j++) {
                Box box = maze.getHorizontalWallPosition(i, j);
                Matrix.setIdentityM(modelHorizontalWall[i][j], 0);
                Matrix.translateM(modelHorizontalWall[i][j], 0, box.getPos().getX() + box.getSize().getX() * 0.5f, box.getPos().getY() + box.getSize().getY() * 0.5f, box.getPos().getZ() + box.getSize().getZ() * 0.5f);
                Matrix.scaleM(modelHorizontalWall[i][j], 0, box.getSize().getX(), box.getSize().getY(), box.getSize().getZ());

                Matrix.setIdentityM(modelHorizontalMark[i][j], 0);
                Matrix.translateM(modelHorizontalMark[i][j], 0, box.getPos().getX() + box.getSize().getX() * 0.5f, box.getPos().getY() + box.getSize().getY() * 0.5f, box.getPos().getZ() + box.getSize().getZ() * 0.5f);
                Matrix.rotateM(modelHorizontalMark[i][j], 0, 0, 0, 0, 1);
                Matrix.scaleM(modelHorizontalMark[i][j], 0, box.getSize().getX(), box.getSize().getY(), 0);
            }
        }

        for (int i = 0; i < MAZE_HEIGHT; i++) {
            for (int j = 0; j < MAZE_WIDTH + 1; j++) {
                Box box = maze.getVerticalWallPosition(i, j);
                Matrix.setIdentityM(modelVerticalWall[i][j], 0);
                Matrix.translateM(modelVerticalWall[i][j], 0, box.getPos().getX() + box.getSize().getX() * 0.5f, box.getPos().getY() + box.getSize().getY() * 0.5f, box.getPos().getZ() + box.getSize().getZ() * 0.5f);
                Matrix.scaleM(modelVerticalWall[i][j], 0, box.getSize().getX(), box.getSize().getY(), box.getSize().getZ());

                Matrix.setIdentityM(modelVerticalMark[i][j], 0);
                Matrix.translateM(modelVerticalMark[i][j], 0, box.getPos().getX() + box.getSize().getX() * 0.5f, box.getPos().getY() + box.getSize().getY() * 0.5f, box.getPos().getZ() + box.getSize().getZ() * 0.5f);
                //Matrix.rotateM(modelVerticalMark[i][j], 0, 90, 0, 1, 0);
                Matrix.scaleM(modelVerticalMark[i][j], 0, 0, box.getSize().getY(), box.getSize().getZ());
            }
        }
        planes = new Vector<>();
        // Initialize 3D audio engine.
        gvrAudioEngine = new GvrAudioEngine(this, GvrAudioEngine.RenderingMode.BINAURAL_HIGH_QUALITY);

        testLoopPlayer();

    }

    public void testLoopPlayer() {
        mPlayer = MediaPlayer.create(this, mPlayResId);
        mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mPlayer.start();
            }
        });
        createNextMediaPlayer();
    }

    private void createNextMediaPlayer() {
        mNextPlayer = MediaPlayer.create(this, mPlayResId);
        mPlayer.setNextMediaPlayer(mNextPlayer);
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.release();
                mPlayer = mNextPlayer;
                createNextMediaPlayer();
            }
        });
    }

    private void initializeGvrView() {
        setContentView(R.layout.common_ui);

        GvrView gvrView = (GvrView) findViewById(R.id.gvr_view);
        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);

        gvrView.setRenderer(this);
        gvrView.setTransitionViewEnabled(true);

        // Enable Cardboard-trigger feedback with Daydream headsets. This is a simple way of supporting
        // Daydream controller input for basic interactions using the existing Cardboard trigger API.
        gvrView.enableCardboardTriggerEmulation();

        if (gvrView.setAsyncReprojectionEnabled(true)) {
            // Async reprojection decouples the app framerate from the display framerate,
            // allowing immersive interaction even at the throttled clockrates set by
            // sustained performance mode.
            AndroidCompat.setSustainedPerformanceMode(this, true);
        }

        setGvrView(gvrView);
    }

    @Override
    public void onPause() {
        gvrAudioEngine.pause();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        gvrAudioEngine.resume();
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged");
    }

    /**
     * Creates the buffers we use to store information about the 3D world.
     *
     * <p>OpenGL doesn't use Java arrays, but rather needs data in a format it can understand.
     * Hence we use ByteBuffers.
     *
     * @param config The EGL configuration used when creating the surface.
     */
    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        objectProgram = Util.compileProgram(OBJECT_VERTEX_SHADER_CODE, OBJECT_FRAGMENT_SHADER_CODE);

        objectPositionParam = GLES20.glGetAttribLocation(objectProgram, "a_Position");
        objectUvParam = GLES20.glGetAttribLocation(objectProgram, "a_UV");
        objectModelViewProjectionParam = GLES20.glGetUniformLocation(objectProgram, "u_MVP");

        Util.checkGlError("Object program params");

        // Avoid any delays during start-up due to decoding of sound files.
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        gvrAudioEngine.preloadSoundFile(MazeActivity.OBJECT_SOUND_FILE);
                        sourceId = gvrAudioEngine.createSoundObject(MazeActivity.OBJECT_SOUND_FILE);
                        gvrAudioEngine.setSoundObjectPosition(
                                sourceId, 0, 0, 0);
                        gvrAudioEngine.playSound(sourceId, true /* looped playback */);
                        gvrAudioEngine.preloadSoundFile(MazeActivity.SUCCESS_SOUND_FILE);
                    }
                })
                .start();

        //updateTargetPosition();

        Util.checkGlError("onSurfaceCreated");

        try {
            wall = new TexturedMesh(this, "cube.obj", objectPositionParam, objectUvParam);
            floor = new TexturedMesh(this, "floor.obj", objectPositionParam, objectUvParam);
            square_xy = new TexturedMesh(this, "square_xy.obj", objectPositionParam, objectUvParam);
            square_yz = new TexturedMesh(this, "square_yz.obj", objectPositionParam, objectUvParam);
            wallTex = new Texture(this, "wall4.png");
            floorTex = new Texture(this, "floor2.png");
            yesTex = new Texture(this, "tick.png");
            noTex = new Texture(this, "cross.png");
            ceilTex = new Texture(this, "ceil.png");
        } catch (IOException e) {
            Log.e(TAG, "Unable to initialize objects", e);
        }

        planes.add(maze.getEndPointPlane(yesTex));
    }

    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {
        if (isMoving && System.currentTimeMillis() - lastClickTimeMillis > DOUBLE_CLICK_INTERVAL_LIMIT) {
            if (!cameraPosition.move(headDirection[0] * STEP_LENGTH, headDirection[1] * STEP_LENGTH, headDirection[2] * STEP_LENGTH)) {
                long nowTime = System.currentTimeMillis();
                if (nowTime - lastCollideTimeMillis > 1000) {
                    successSourceId = gvrAudioEngine.createStereoSound(COLLIDE_WALL);
                    gvrAudioEngine.playSound(successSourceId, false /* looping disabled */);
                }
                lastCollideTimeMillis = nowTime;
//                Toast toast = Toast.makeText(getApplicationContext(), "您碰壁了！", Toast.LENGTH_SHORT);
//                toast.show();
            }
        }
        Matrix.setLookAtM(camera, 0, 0, 0, 0, 0.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f);

        headTransform.getHeadView(headView, 0);

        // Update the 3d audio engine with the most recent head rotation.
        headTransform.getQuaternion(headRotation, 0);
        headTransform.getForwardVector(headDirection, 0);
        gvrAudioEngine.setHeadRotation(
                headRotation[0], headRotation[1], headRotation[2], headRotation[3]);
        // Regular update call to GVR audio engine.
        gvrAudioEngine.update();

        Util.checkGlError("onNewFrame");

        checkSuccess();
    }

    void checkSuccess() {
        if (cameraPosition.getPos().getZ() < 0 && !success) {
            mPlayer.pause();
            successSourceId = gvrAudioEngine.createStereoSound(FINAL_SUCCESS);
            gvrAudioEngine.playSound(successSourceId, false /* looping disabled */);
            success = true;
        }
    }

    /**
     * Draws a frame for an eye.
     *
     * @param eye The eye to render. Includes all required transformations.
     */
    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, camera, 0);
        perspective = eye.getPerspective(Z_NEAR, Z_FAR);
        cameraPosition.translateTarget(view, 0);
        for (int i = 0; i < MAZE_HEIGHT + 1; i++) {
            for (int j = 0; j < MAZE_WIDTH; j++) {
                if (maze.isHorizontalWall(i, j)) {
                    drawObject(wall, wallTex, modelHorizontalWall[i][j], 0);
                }
            }
        }

        for (int i = 0; i < MAZE_HEIGHT; i++) {
            for (int j = 0; j < MAZE_WIDTH + 1; j++) {
                if (maze.isVerticalWall(i, j)) {
                    drawObject(wall, wallTex, modelVerticalWall[i][j], 0);
                }
            }
        }

        drawObject(floor, floorTex, modelFloor, 0);
        drawObject(floor, ceilTex, modelCeil, 0);

        for (Plane plane : planes) {
            drawPlane(plane);
        }

        for (int i = 0; i < MAZE_HEIGHT + 1; i++) {
            for (int j = 0; j < MAZE_WIDTH; j++) {
                if (maze.isHorizontalMark(i, j)) {
                    System.out.printf("build horizontal mark (%d, %d)\n", i, j);
                    drawObject(square_xy, noTex, modelHorizontalMark[i][j], 0);
                }
            }
        }

        for (int i = 0; i < MAZE_HEIGHT; i++) {
            for (int j = 0; j < MAZE_WIDTH + 1; j++) {
                if (maze.isVerticalMark(i, j)) {
                    System.out.printf("build vertical mark (%d, %d)\n", i, j);
                    drawObject(square_yz, noTex, modelVerticalMark[i][j], 0);
                }
            }
        }
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    private void drawObject(TexturedMesh texturedMesh, Texture texture, float[] modelTarget, int offset) {
        Matrix.multiplyMM(modelView, 0, view, 0, modelTarget, offset);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
        GLES20.glUseProgram(objectProgram);
        GLES20.glUniformMatrix4fv(objectModelViewProjectionParam, 1, false, modelViewProjection, 0);
        texture.bind();
        texturedMesh.draw();
        Util.checkGlError("drawObject");
    }

    private void drawPlane(Plane plane) {
        float[] modelTarget = new float[16];
        Matrix.setIdentityM(modelTarget, 0);
        Matrix.translateM(modelTarget, 0, plane.getCenter().getX(), plane.getCenter().getY(), plane.getCenter().getZ());
        Matrix.rotateM(modelTarget, 0, plane.getRotateDegree(), plane.getRotateDirection().getX(), plane.getRotateDirection().getY(), plane.getRotateDirection().getZ());
        Matrix.scaleM(modelTarget, 0, plane.getSize().getX(), plane.getSize().getY(), plane.getSize().getZ());
        Matrix.multiplyMM(modelView, 0, view, 0, modelTarget, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
        GLES20.glUseProgram(objectProgram);
        GLES20.glUniformMatrix4fv(objectModelViewProjectionParam, 1, false, modelViewProjection, 0);
        plane.getTexture().bind();
        square_xy.draw();
        Util.checkGlError("drawPlane");
    }

    /**
     * Called when the Cardboard trigger is pulled.
     */
    @Override
    public void onCardboardTrigger() {
        long nowTimeMillis = System.currentTimeMillis();
        if (nowTimeMillis - lastClickTimeMillis < DOUBLE_CLICK_INTERVAL_LIMIT) {
            createWarningPlane();
        }
        lastClickTimeMillis = nowTimeMillis;
    }

    private void createWarningPlane() {
        int r = cameraPosition.getNowRow();
        int c = cameraPosition.getNowCol();
        float dx = headDirection[0];
        float dz = headDirection[2];
        int ret = 0;
        if (Math.abs(dx) > Math.abs(dz)) {
            if (dx < 0) {
                ret = maze.updateVerticalMark(r, c);
            } else {
                ret = maze.updateVerticalMark(r, c + 1);
            }
        } else {
            if (dz < 0) {
                ret = maze.updateHorizontalMark(r, c);
            } else {
                ret = maze.updateHorizontalMark(r + 1, c);
            }
        }
        if (ret == 1) {
            successSourceId = gvrAudioEngine.createStereoSound(BUILD_SUCCESS);
            gvrAudioEngine.playSound(successSourceId, false /* looping disabled */);
        } else if (ret == 2) {
            successSourceId = gvrAudioEngine.createStereoSound(BUILD_FAIL);
            gvrAudioEngine.playSound(successSourceId, false /* looping disabled */);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isMoving = true;
                break;
            case MotionEvent.ACTION_UP:
                isMoving = false;
                break;
        }
        return super.dispatchTouchEvent(ev);
    }
}