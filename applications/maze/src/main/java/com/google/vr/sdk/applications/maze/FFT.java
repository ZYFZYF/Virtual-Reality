package com.google.vr.sdk.applications.maze;

import org.jtransforms.fft.FloatFFT_1D;

public class FFT {
    private static float[] multiply(float[] x, float[] y, int length) {
        FloatFFT_1D floatFFT_1D = new FloatFFT_1D(length);
        float[] new_x = new float[2 * length];
        for (int i = 0; i < length; i++) {
            if (i < x.length) {
                new_x[i * 2] = x[i];
            } else {
                new_x[i * 2] = 0;
            }
            new_x[i * 2 + 1] = 0;
        }
        x = new_x;

        float[] new_y = new float[2 * length];
        for (int i = 0; i < length; i++) {
            if (i < y.length) {
                new_y[i * 2] = y[i];
            } else {
                new_y[i * 2] = 0;
            }
            new_y[i * 2 + 1] = 0;
        }
        y = new_y;

        floatFFT_1D.complexForward(x);
        floatFFT_1D.complexForward(y);
        for (int i = 0; i < length; i++) {
            float rel = x[2 * i] * y[2 * i] - x[2 * i + 1] * y[2 * i + 1];
            float img = x[2 * i] * y[2 * i + 1] + x[2 * i + 1] * y[2 * i];
            x[2 * i] = rel;
            x[2 * i + 1] = img;
        }
        floatFFT_1D.complexInverse(x, true);
        float[] z = new float[length];
        for (int i = 0; i < length; i++) {
            z[i] = x[2 * i];
        }
        return z;
    }

    public static void main(String[] args) {
        float[] x = {1, 1, 1, 1, 1};
        float[] y = {1, 1, 1};
        float[] z = multiply(x, y, 10);
        for (int i = 0; i < z.length; i++) {
            System.out.println(z[i]);
        }
    }
}
