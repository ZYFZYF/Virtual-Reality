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
import java.util.ArrayList;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;

/**
 * A Google VR sample application.
 *
 * <p>This app presents a scene consisting of a room and a floating object. When the user finds the
 * object, they can invoke the trigger action, and a new object will be randomly spawned. When in
 * Cardboard mode, the user must gaze at the object and use the Cardboard trigger button. When in
 * Daydream mode, the user can use the controller to position the cursor, and use the controller
 * buttons to invoke the trigger action.
 */
public class HelloVrActivity extends GvrActivity implements GvrView.StereoRenderer {
    private static final String TAG = "HelloVrActivity";

    private static final int MAZE_WIDTH = 8;
    private static final int MAZE_HEIGHT = 6;
    private static final float STEP_LENGTH = 0.01f;
    private static final boolean CONTRAOL_MOVE_ON_XY = false;

    private static final int TARGET_MESH_COUNT = 3;

    private static final float Z_NEAR = 0.01f;
    private static final float Z_FAR = 10.0f;

    // Convenience vector for extracting the position from a matrix via multiplication.
    private static final float[] POS_MATRIX_MULTIPLY_VEC = {0.0f, 0.0f, 0.0f, 1.0f};
    private static final float[] FORWARD_VEC = {0.0f, 0.0f, -1.0f, 1.f};

    private static final float MIN_TARGET_DISTANCE = 3.0f;
    private static final float MAX_TARGET_DISTANCE = 3.5f;

    private static final String OBJECT_SOUND_FILE = "audio/HelloVR_Loop.ogg";
    private static final String SUCCESS_SOUND_FILE = "audio/HelloVR_Activation.ogg";

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
    private boolean isMoving = false;
    private int objectProgram;

    private int objectPositionParam;
    private int objectUvParam;
    private int objectModelViewProjectionParam;

    private float targetDistance = MAX_TARGET_DISTANCE;

    private TexturedMesh room, floor;
    private Texture roomTex, floorTex;
    private ArrayList<TexturedMesh> targetObjectMeshes;
    private ArrayList<Texture> targetObjectNotSelectedTextures;
    private ArrayList<Texture> targetObjectSelectedTextures;
    private int curTargetObject;

    private Random random;

    private float[] targetPosition;
    private float[] camera;
    private float[] peoplePosition;
    private float[] targetTranslate;
    private float[] view;
    private float[] headView;
    private float[] modelViewProjection;
    private float[] modelView;
    private float[] perspective;

    private float[] modelTarget;
    private float[] modelRoom;
    private float[] modelFloor;
    private float[][][] modelHorizontalWall;
    private float[][][] modelVerticalWall;

    private float[] tempPosition;
    private float[] headRotation;
    private float[] headDirection;

    private GvrAudioEngine gvrAudioEngine;
    private volatile int sourceId = GvrAudioEngine.INVALID_ID;
    private volatile int successSourceId = GvrAudioEngine.INVALID_ID;

    private Maze maze;
    private CameraPosition cameraPosition;

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
        // Target object first appears directly in front of user.
        targetPosition = new float[]{0.0f, 0.0f, -MIN_TARGET_DISTANCE};
        peoplePosition = new float[]{0.0f, 0.0f, 0.0f};
        targetTranslate = new float[16];
        Matrix.setIdentityM(targetTranslate, 0);
        tempPosition = new float[4];
        headRotation = new float[4];
        headDirection = new float[4];
        modelTarget = new float[16];
        modelRoom = new float[16];
        headView = new float[16];
        maze = new Maze(MAZE_HEIGHT, MAZE_WIDTH);
        cameraPosition = new CameraPosition(maze.generateStartPoint(), maze.getWalls());
        modelHorizontalWall = new float[MAZE_HEIGHT + 1][MAZE_WIDTH][16];
        modelVerticalWall = new float[MAZE_HEIGHT][MAZE_WIDTH + 1][16];
        modelFloor = new float[16];
        Matrix.setIdentityM(modelFloor, 0);
        for (int i = 0; i < MAZE_HEIGHT + 1; i++) {
            for (int j = 0; j < MAZE_WIDTH; j++) {
                Box box = maze.getHorizontalWallPosition(i, j);
//                System.out.printf("Horizontal wall (%d, %d) is (%f, %f, %f) and size is (%f, %f, %f)", i, j,
//                        box.getPos().getX(), box.getPos().getY(), box.getPos().getZ(),
//                        box.getSize().getX(), box.getSize().getY(), box.getSize().getZ());
                Matrix.setIdentityM(modelHorizontalWall[i][j], 0);
                Matrix.translateM(modelHorizontalWall[i][j], 0, box.getPos().getX() + box.getSize().getX() * 0.5f, box.getPos().getY() + box.getSize().getY() * 0.5f, box.getPos().getZ() + box.getSize().getZ() * 0.5f);
                Matrix.scaleM(modelHorizontalWall[i][j], 0, box.getSize().getX(), box.getSize().getY(), box.getSize().getZ());
            }
        }

        for (int i = 0; i < MAZE_HEIGHT; i++) {
            for (int j = 0; j < MAZE_WIDTH + 1; j++) {
                Box box = maze.getVerticalWallPosition(i, j);
//                System.out.printf("Vertical wall (%d, %d) is (%f, %f, %f) and size is (%f, %f, %f)\n", i, j,
//                        box.getPos().getX(), box.getPos().getY(), box.getPos().getZ(),
//                        box.getSize().getX(), box.getSize().getY(), box.getSize().getZ());
                Matrix.setIdentityM(modelVerticalWall[i][j], 0);
                Matrix.translateM(modelVerticalWall[i][j], 0, box.getPos().getX() + box.getSize().getX() * 0.5f, box.getPos().getY() + box.getSize().getY() * 0.5f, box.getPos().getZ() + box.getSize().getZ() * 0.5f);
                Matrix.scaleM(modelVerticalWall[i][j], 0, box.getSize().getX(), box.getSize().getY(), box.getSize().getZ());
            }
        }

        for (int i = 0; i < MAZE_HEIGHT; i++) {
            for (int j = 0; j < MAZE_WIDTH; j++) {
                Box h = maze.getHorizontalWallPosition(i, j);
                System.out.printf("Horizontal wall (%d, %d) is (%f, %f, %f) and size is (%f, %f, %f)\n", i, j, h.getPos().getX(), h.getPos().getY(), h.getPos().getZ(), h.getSize().getX(), h.getSize().getY(), h.getSize().getZ());
                Box v = maze.getVerticalWallPosition(i, j);
                System.out.printf("Vertical wall (%d, %d) is (%f, %f, %f) and size is (%f, %f, %f)\n", i, j, v.getPos().getX(), v.getPos().getY(), v.getPos().getZ(), v.getSize().getX(), v.getSize().getY(), v.getSize().getZ());
            }
        }

        // Initialize 3D audio engine.
        gvrAudioEngine = new GvrAudioEngine(this, GvrAudioEngine.RenderingMode.BINAURAL_HIGH_QUALITY);

        random = new Random();
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

        Matrix.setIdentityM(modelRoom, 0);
        Matrix.translateM(modelRoom, 0, 0, FLOOR_HEIGHT, 0);

        // Avoid any delays during start-up due to decoding of sound files.
//        new Thread(
//                new Runnable() {
//                    @Override
//                    public void run() {
//                        // Start spatial audio playback of OBJECT_SOUND_FILE at the model position. The
//                        // returned sourceId handle is stored and allows for repositioning the sound object
//                        // whenever the target position changes.
//                        gvrAudioEngine.preloadSoundFile(HelloVrActivity.OBJECT_SOUND_FILE);
//                        sourceId = gvrAudioEngine.createSoundObject(HelloVrActivity.OBJECT_SOUND_FILE);
//                        gvrAudioEngine.setSoundObjectPosition(
//                                sourceId, targetPosition[0], targetPosition[1], targetPosition[2]);
//                        gvrAudioEngine.playSound(sourceId, true /* looped playback */);
//                        // Preload an unspatialized sound to be played on a successful trigger on the
//                        // target.
//                        gvrAudioEngine.preloadSoundFile(HelloVrActivity.SUCCESS_SOUND_FILE);
//                    }
//                })
//                .start();

        //updateTargetPosition();

        Util.checkGlError("onSurfaceCreated");

        try {
//            room = new TexturedMesh(this, "CubeRoom.obj", objectPositionParam, objectUvParam);
//            roomTex = new Texture(this, "CubeRoom_BakedDiffuse.png");

            room = new TexturedMesh(this, "cube.obj", objectPositionParam, objectUvParam);
            floor = new TexturedMesh(this, "floor.obj", objectPositionParam, objectUvParam);
            roomTex = new Texture(this, "wall3.png");
            floorTex = new Texture(this, "floor.png");
//            targetObjectMeshes = new ArrayList<>();
//            targetObjectNotSelectedTextures = new ArrayList<>();
//            targetObjectSelectedTextures = new ArrayList<>();
//            targetObjectMeshes.add(
//                    new TexturedMesh(this, "Icosahedron.obj", objectPositionParam, objectUvParam));
//            targetObjectNotSelectedTextures.add(new Texture(this, "Icosahedron_Blue_BakedDiffuse.png"));
//            targetObjectSelectedTextures.add(new Texture(this, "Icosahedron_Pink_BakedDiffuse.png"));
//            targetObjectMeshes.add(
//                    new TexturedMesh(this, "QuadSphere.obj", objectPositionParam, objectUvParam));
//            targetObjectNotSelectedTextures.add(new Texture(this, "QuadSphere_Blue_BakedDiffuse.png"));
//            targetObjectSelectedTextures.add(new Texture(this, "QuadSphere_Pink_BakedDiffuse.png"));
//            targetObjectMeshes.add(
//                    new TexturedMesh(this, "TriSphere.obj", objectPositionParam, objectUvParam));
//            targetObjectNotSelectedTextures.add(new Texture(this, "TriSphere_Blue_BakedDiffuse.png"));
//            targetObjectSelectedTextures.add(new Texture(this, "TriSphere_Pink_BakedDiffuse.png"));
        } catch (IOException e) {
            Log.e(TAG, "Unable to initialize objects", e);
        }
        // curTargetObject = random.nextInt(TARGET_MESH_COUNT);
    }

//    /**
//     * Updates the target object position.
//     */
//    private void updateTargetPosition() {
//        Matrix.setIdentityM(modelTarget, 0);
//        Matrix.translateM(modelTarget, 0, targetPosition[0], targetPosition[1], targetPosition[2]);
//
//        // Update the sound location to match it with the new target position.
//        if (sourceId != GvrAudioEngine.INVALID_ID) {
//            gvrAudioEngine.setSoundObjectPosition(
//                    sourceId, targetPosition[0], targetPosition[1], targetPosition[2]);
//        }
//        Util.checkGlError("updateTargetPosition");
//    }

    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {
        // System.out.println("QAQ");
        // Build the camera matrix and apply it to the ModelView.
        // System.out.printf("is moving = %d, peopleposition is (%f, %f, %f)\n", isMoving ? 1 : 0, peoplePosition[0], peoplePosition[1], peoplePosition[2]);
        // System.out.printf("head direction is (%f, %f, %f)\n", headDirection[0], headDirection[1], headDirection[2]);
        if (isMoving) {
            cameraPosition.move(headDirection[0] * STEP_LENGTH, headDirection[1] * STEP_LENGTH, headDirection[2] * STEP_LENGTH);
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
    }

    /**
     * Draws a frame for an eye.
     *
     * @param eye The eye to render. Includes all required transformations.
     */
    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        // The clear color doesn't matter here because it's completely obscured by
        // the room. However, the color buffer is still cleared because it may
        // improve performance.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Apply the eye transformation to the camera.
        Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, camera, 0);

        // Build the ModelView and ModelViewProjection matrices
        // for calculating the position of the target object.
        perspective = eye.getPerspective(Z_NEAR, Z_FAR);

//        Matrix.multiplyMM(modelView, 0, view, 0, modelTarget, 0);
//        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
//        drawTarget();

//        float[] translateCamera = new float[16];
//        Matrix.setIdentityM(translateCamera, 0);
//        Matrix.translateM(translateCamera, 0, -peoplePosition[0], -peoplePosition[1], -peoplePosition[2]);
//        float[] newView = new float[16];
//        Matrix.multiplyMM(newView, 0, view, 0, translateCamera, 0);
        cameraPosition.translateTarget(view, 0);

//        Matrix.multiplyMM(modelView, 0, view, 0, modelRoom, 0);
//        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
//        drawRoom();

        // Set modelView for the room, so it's drawn in the correct location
        for (int i = 0; i < MAZE_HEIGHT + 1; i++) {
            for (int j = 0; j < MAZE_WIDTH; j++) {
                if (maze.isHorizontalWall(i, j)) {
//                    Matrix.multiplyMM(modelView, 0, view, 0, modelHorizontalWall[i][j], 0);
//                    Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
//                    drawRoom();
                    drawObject(room, roomTex, modelHorizontalWall[i][j], 0);
                }
            }
        }

        for (int i = 0; i < MAZE_HEIGHT; i++) {
            for (int j = 0; j < MAZE_WIDTH + 1; j++) {
                if (maze.isVerticalWall(i, j)) {
//                    Matrix.multiplyMM(modelView, 0, view, 0, modelHorizontalWall[i][j], 0);
//                    Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
//                    drawRoom();
                    drawObject(room, roomTex, modelVerticalWall[i][j], 0);
                }
            }
        }
        drawObject(floor, floorTex, modelFloor, 0);

    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

//    /**
//     * Draw the target object.
//     */
//    private void drawTarget() {
//        GLES20.glUseProgram(objectProgram);
//        GLES20.glUniformMatrix4fv(objectModelViewProjectionParam, 1, false, modelViewProjection, 0);
////        if (isLookingAtTarget()) {
////            targetObjectSelectedTextures.get(curTargetObject).bind();
////        } else {
//        targetObjectNotSelectedTextures.get(curTargetObject).bind();
////        }
//        targetObjectMeshes.get(curTargetObject).draw();
//        Util.checkGlError("drawTarget");
//    }

    /**
     * Draw the room.
     */
    private void drawRoom() {
        GLES20.glUseProgram(objectProgram);
        GLES20.glUniformMatrix4fv(objectModelViewProjectionParam, 1, false, modelViewProjection, 0);
        roomTex.bind();
        room.draw();
        Util.checkGlError("drawRoom");
    }


    private void drawObject(TexturedMesh texturedMesh, Texture texture, float[] modelTarget, int offset) {
        Matrix.multiplyMM(modelView, 0, view, 0, modelTarget, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, offset);
        GLES20.glUseProgram(objectProgram);
        GLES20.glUniformMatrix4fv(objectModelViewProjectionParam, 1, false, modelViewProjection, 0);
        texture.bind();
        texturedMesh.draw();
        Util.checkGlError("drawObject");
    }

    /**
     * Called when the Cardboard trigger is pulled.
     */
    @Override
    public void onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger");

//        if (isLookingAtTarget()) {
//            successSourceId = gvrAudioEngine.createStereoSound(SUCCESS_SOUND_FILE);
//            gvrAudioEngine.playSound(successSourceId, false /* looping disabled */);
//            hideTarget();
//        }
        //triggerCnt++;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                //System.out.println("I get click start");
                isMoving = true;
                break;
            case MotionEvent.ACTION_UP:
                //System.out.println("I get click end");
                isMoving = false;
                break;
        }
        return super.dispatchTouchEvent(ev);
    }
//    /**
//     * Find a new random position for the target object.
//     */
//    private void hideTarget() {
//        float[] rotationMatrix = new float[16];
//        float[] posVec = new float[4];
//
//        // Matrix.setRotateM takes the angle in degrees, but Math.tan takes the angle in radians, so
//        // yaw is in degrees and pitch is in radians.
//        float yawDegrees = (random.nextFloat() - 0.5f) * 2.0f * MAX_YAW;
//        float pitchRadians = (float) Math.toRadians((random.nextFloat() - 0.5f) * 2.0f * MAX_PITCH);
//
//        Matrix.setRotateM(rotationMatrix, 0, yawDegrees, 0.0f, 1.0f, 0.0f);
//        targetDistance =
//                random.nextFloat() * (MAX_TARGET_DISTANCE - MIN_TARGET_DISTANCE) + MIN_TARGET_DISTANCE;
//        targetPosition = new float[]{0.0f, 0.0f, -targetDistance};
//        Matrix.setIdentityM(modelTarget, 0);
//        Matrix.translateM(modelTarget, 0, targetPosition[0], targetPosition[1], targetPosition[2]);
//        Matrix.multiplyMV(posVec, 0, rotationMatrix, 0, modelTarget, 12);
//
//        targetPosition[0] = posVec[0];
//        targetPosition[1] = (float) Math.tan(pitchRadians) * targetDistance;
//        targetPosition[2] = posVec[2];
//

//        updateTargetPosition();
//        curTargetObject = random.nextInt(TARGET_MESH_COUNT);
//    }
//
//    /**
//     * Check if user is looking at the target object by calculating where the object is in eye-space.
//     *
//     * @return true if the user is looking at the target object.
//     */
//    private boolean isLookingAtTarget() {
//        // Convert object space to camera space. Use the headView from onNewFrame.
//        Matrix.multiplyMM(modelView, 0, headView, 0, modelTarget, 0);
//        Matrix.multiplyMV(tempPosition, 0, modelView, 0, POS_MATRIX_MULTIPLY_VEC, 0);
//
//        float angle = Util.angleBetweenVectors(tempPosition, FORWARD_VEC);
//        return angle < ANGLE_LIMIT;
//    }
}
