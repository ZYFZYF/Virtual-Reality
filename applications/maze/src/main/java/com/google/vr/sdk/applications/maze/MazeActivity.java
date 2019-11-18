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

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Random;

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
    private static final int FRAME_SAMPLES = 500;
    private static final int TOTAL_SAMPLES = 120000;
    private static final int SAMPLE_RATE = 22050;
    private static final int CONVOLVE_SIZE = 100;
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
    private static int MAZE_WIDTH = 4;
    private static int MAZE_HEIGHT = 4;
    private long lastClickTimeMillis = 0;
    private long lastCollideTimeMillis = 0;
    private boolean isMoving = false;
    private boolean success = false;
    private int objectProgram;
    private int objectPositionParam;
    private int objectUvParam;
    private int objectModelViewProjectionParam;
    private int mosquitoDirectionPeriod = 100;
    private int mosquitoDircetionCount = 0;
    private TexturedMesh wall, floor, mosquito;
    private Texture wallTex, floorTex, ceilTex, mosquitoTex;
    private float[] camera;
    private float[] view;
    private float[] headView;
    private float[] modelViewProjection;
    private float[] modelView;
    private float[] perspective;
    private float[] modelFloor;
    private float[] modelCeil;
    private float[] modelMosquito;
    private float[][][] modelHorizontalWall;
    private float[][][] modelVerticalWall;
    private float[][][] hrirL;
    private float[][][] hrirR;
    private float[] mosquitoL;
    private float[] mosquitoR;
    private AudioTrack audioTrack;
    private int currentSample;
    private float[] headRotation;
    private float[] headDirection;
    private GvrAudioEngine gvrAudioEngine;
    private volatile int sourceId = GvrAudioEngine.INVALID_ID;
    private volatile int successSourceId = GvrAudioEngine.INVALID_ID;
    private Maze maze;
    private CameraPosition cameraPosition;
    private MosquitoPosition mosquitoPosition;

    /**
     * Sets the view to our GvrView and initializes the transformation matrices we will use
     * to render our scene.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initializeGvrView();
        initGame();
        initAudio();
    }

    private void initGame() {
        if (success) {
            Random random = new Random();
            MAZE_HEIGHT += random.nextInt(2) + 1;
            MAZE_WIDTH += random.nextInt(2) + 1;
        }
        success = false;
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
        modelFloor = new float[16];
        Matrix.setIdentityM(modelFloor, 0);
        Matrix.scaleM(modelFloor, 0, 200, 1, 200);
        modelCeil = new float[16];
        Matrix.setIdentityM(modelCeil, 0);
        Point maxPoint = maze.getMaxPoint();
        Matrix.translateM(modelCeil, 0, maxPoint.getX() / 2, Maze.WALL_HEIGHT, maxPoint.getZ() / 2);
        Matrix.scaleM(modelCeil, 0, maxPoint.getX(), 0, maxPoint.getZ());
        modelMosquito = new float[16];
        Point temp = maze.generateStartPoint();
        temp.setY(temp.getY() - 0.15f);
        mosquitoPosition = new MosquitoPosition(temp, maze.getWalls());
        for (int i = 0; i < MAZE_HEIGHT + 1; i++) {
            for (int j = 0; j < MAZE_WIDTH; j++) {
                Box box = maze.getHorizontalWallPosition(i, j);
                Matrix.setIdentityM(modelHorizontalWall[i][j], 0);
                Matrix.translateM(modelHorizontalWall[i][j], 0, box.getPos().getX() + box.getSize().getX() * 0.5f, box.getPos().getY() + box.getSize().getY() * 0.5f, box.getPos().getZ() + box.getSize().getZ() * 0.5f);
                Matrix.scaleM(modelHorizontalWall[i][j], 0, box.getSize().getX(), box.getSize().getY(), box.getSize().getZ());
            }
        }

        for (int i = 0; i < MAZE_HEIGHT; i++) {
            for (int j = 0; j < MAZE_WIDTH + 1; j++) {
                Box box = maze.getVerticalWallPosition(i, j);
                Matrix.setIdentityM(modelVerticalWall[i][j], 0);
                Matrix.translateM(modelVerticalWall[i][j], 0, box.getPos().getX() + box.getSize().getX() * 0.5f, box.getPos().getY() + box.getSize().getY() * 0.5f, box.getPos().getZ() + box.getSize().getZ() * 0.5f);
                Matrix.scaleM(modelVerticalWall[i][j], 0, box.getSize().getX(), box.getSize().getY(), box.getSize().getZ());
            }
        }

        // Initialize 3D audio engine.
        gvrAudioEngine = new GvrAudioEngine(this, GvrAudioEngine.RenderingMode.BINAURAL_HIGH_QUALITY);

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

    private void initAudio() {
        //现在播放到的采样点
        currentSample = 0;
        //读取hrir数据和mosquito数据
        hrirL = new float[25][50][100];
        hrirR = new float[25][50][100];
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(getResources().openRawResource(R.raw.hrir_l)));
            String[] strNums = bufferedReader.readLine().split("\\s");
            for (int k = 0, cnt = 0; k < 100; k++) {
                for (int j = 0; j < 50; j++) {
                    for (int i = 0; i < 25; i++, cnt++) {
                        hrirL[i][j][k] = Float.parseFloat(strNums[cnt]);
                    }
                }
            }
            bufferedReader = new BufferedReader(new InputStreamReader(getResources().openRawResource(R.raw.hrir_r)));
            strNums = bufferedReader.readLine().split("\\s");
            for (int k = 0, cnt = 0; k < 100; k++) {
                for (int j = 0; j < 50; j++) {
                    for (int i = 0; i < 25; i++, cnt++) {
                        hrirR[i][j][k] = Float.parseFloat(strNums[cnt]);
                    }
                }
            }
            bufferedReader = new BufferedReader(new InputStreamReader(getResources().openRawResource(R.raw.mosquito_l)));
            strNums = bufferedReader.readLine().split("\\s");
            mosquitoL = new float[strNums.length];
            for (int i = 0; i < strNums.length; i++) {
                mosquitoL[i] = Float.parseFloat(strNums[i]);
            }

            bufferedReader = new BufferedReader(new InputStreamReader(getResources().openRawResource(R.raw.mosquito_r)));
            strNums = bufferedReader.readLine().split("\\s");
            mosquitoR = new float[strNums.length];
            for (int i = 0; i < strNums.length; i++) {
                mosquitoR[i] = Float.parseFloat(strNums[i]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT, minBufferSize * 4 * 2, AudioTrack.MODE_STREAM);
    }

    private void playAudio(float[] left, float[] right) {
        assert (left.length == right.length);
        float[] audio = new float[left.length + right.length];
        for (int i = 0; i < left.length; i++) {
            audio[i * 2] = left[i];
            audio[i * 2 + 1] = right[i];
        }
        audioTrack.write(audio, 0, left.length * 2, AudioTrack.WRITE_NON_BLOCKING);
        audioTrack.play();
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
            mosquito = new TexturedMesh(this, "mosquito.obj", objectPositionParam, objectUvParam);

            wallTex = new Texture(this, "wall4.png");
            floorTex = new Texture(this, "floor2.png");
            ceilTex = new Texture(this, "ceil.png");
            mosquitoTex = new Texture(this, "black.png");
        } catch (IOException e) {
            Log.e(TAG, "Unable to initialize objects", e);
        }
    }

    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {
        if (++mosquitoDircetionCount == mosquitoDirectionPeriod) {
            mosquitoPosition.move(Point.getRandomNormal());
            mosquitoDircetionCount = 0;
            mosquitoDirectionPeriod = new Random().nextInt(150) + 1;
        } else {
            mosquitoPosition.move();
        }
        //System.out.println("new frame is " + System.currentTimeMillis());
        if (isMoving && System.currentTimeMillis() - lastClickTimeMillis > DOUBLE_CLICK_INTERVAL_LIMIT) {
            if (!cameraPosition.move(headDirection[0] * STEP_LENGTH, headDirection[1] * STEP_LENGTH, headDirection[2] * STEP_LENGTH)) {
                long nowTime = System.currentTimeMillis();
                if (nowTime - lastCollideTimeMillis > 1000) {
                    successSourceId = gvrAudioEngine.createStereoSound(COLLIDE_WALL);
                    //gvrAudioEngine.playSound(successSourceId, false /* looping disabled */);
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

        //播放音频
        Point headPosition = cameraPosition.getPos();
        float[] mosquitoModelPosition = new float[]{mosquitoPosition.getPos().getX() - headPosition.getX(), mosquitoPosition.getPos().getY() - headPosition.getY(), mosquitoPosition.getPos().getZ() - headPosition.getZ(), 1};
        float[] mosquitoViewPosition = new float[4];
        Matrix.multiplyMV(mosquitoViewPosition, 0, headView, 0, mosquitoModelPosition, 0);
        float[] sphere = new float[3];
        Util.convertRectangleToSphere(mosquitoViewPosition, sphere);
        //System.out.println("azi is " + sphere[2] + " and ele is " + sphere[1]);
        int azi_index = Util.getNearestAzimuthIndex(sphere[2]);
        int ele_index = Util.getNearestElevationIndex(sphere[1]);
        //确定了hrir的位置之后进行卷积计算来准备音频
        float[] result_l = new float[FRAME_SAMPLES];
        float[] result_r = new float[FRAME_SAMPLES];
        int targetSample = Math.min(TOTAL_SAMPLES - 2 * CONVOLVE_SIZE, currentSample + FRAME_SAMPLES);
        for (int start = currentSample; start < targetSample; start += CONVOLVE_SIZE) {
            //System.out.println("start is " + start + " and to " + (start + CONVOLVE_SIZE));
            float[] audio_l = Arrays.copyOfRange(mosquitoL, start, start + CONVOLVE_SIZE * 2);
            float[] audio_r = Arrays.copyOfRange(mosquitoR, start, start + CONVOLVE_SIZE * 2);
            float[] hrir_l = Arrays.copyOf(hrirL[azi_index][ele_index], CONVOLVE_SIZE);
            float[] hrir_r = Arrays.copyOf(hrirR[azi_index][ele_index], CONVOLVE_SIZE);
            //fft算法
            //float[] convove_result_l = Convolve.FFT(audio_l, hrir_l, CONVOLVE_SIZE * 2);
            //float[] convove_result_r = Convolve.FFT(audio_r, hrir_r, CONVOLVE_SIZE * 2);
            //暴力算法
            float[] convove_result_l = Convolve.bruteForce(audio_l, hrir_l, CONVOLVE_SIZE);
            float[] convove_result_r = Convolve.bruteForce(audio_r, hrir_r, CONVOLVE_SIZE);
            //不卷积算法
//            float[] convove_result_l = audio_l.clone();
//            float[] convove_result_r = audio_r.clone();
            System.arraycopy(convove_result_l, 0, result_l, start - currentSample, CONVOLVE_SIZE);
            System.arraycopy(convove_result_r, 0, result_r, start - currentSample, CONVOLVE_SIZE);
        }
        //播放准备好的音频
        //距离衰减
        float distance = mosquitoPosition.getPos().getDistance(headPosition);
        //audioTrack.setVolume(1.0f / (1 + (float) Math.log(1 + distance)));
        audioTrack.setVolume((float) Math.exp(-distance));
        //直接播放片段
//        result_l = Arrays.copyOfRange(mosquitoL, currentSample, targetSample);
//        result_r = Arrays.copyOfRange(mosquitoR, currentSample, targetSample);
        playAudio(result_l, result_r);
        currentSample = targetSample == TOTAL_SAMPLES - 2 * CONVOLVE_SIZE ? 0 : targetSample;
    }

    private void checkSuccess() {
        if (cameraPosition.getPos().getZ() < 0 && !success) {
            successSourceId = gvrAudioEngine.createStereoSound(FINAL_SUCCESS);
            gvrAudioEngine.playSound(successSourceId, false /* looping disabled */);
            success = true;
            try {
                Thread.sleep(4000);
            } catch (java.lang.InterruptedException e) {
                Log.e(TAG, "Interupted by someone", e);
            }
            initGame();
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
        Point prevPos = mosquitoPosition.getPrevPos();
        Point nowPos = mosquitoPosition.getPos();
        Matrix.setLookAtM(modelMosquito, 0, prevPos.getX(), prevPos.getY(), prevPos.getZ(),
                nowPos.getX(), nowPos.getY(), nowPos.getZ(),
                0, 1, 0);
        Matrix.invertM(modelMosquito, 0, modelMosquito, 0);
        Matrix.rotateM(modelMosquito, 0, 270, 1, 0, 0);
        Matrix.rotateM(modelMosquito, 0, 180, 0, 0, 1);
        Matrix.scaleM(modelMosquito, 0, 0.006f, 0.006f, 0.006f);
        drawObject(mosquito, mosquitoTex, modelMosquito, 0);
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

    /**
     * Called when the Cardboard trigger is pulled.
     */
    @Override
    public void onCardboardTrigger() {
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
