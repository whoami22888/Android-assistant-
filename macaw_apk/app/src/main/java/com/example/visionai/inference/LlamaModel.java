package com.example.visionai.inference;

/** JNI API for SceneSense v1.3 AGPL native engine; see THIRD_PARTY.md. */
public final class LlamaModel {
    static { System.loadLibrary("visionai"); }
    public native long loadModel(String modelPath, String mmprojPath, int threads, int ctx);
    public native String runInference(long ptr, byte[] rgb, int width, int height, String prompt);
    public native String runVideoInference(long ptr, byte[][] frames, int[] widths, int[] heights, String prompt);
    public native void freeModel(long ptr);
}