package com.github.piasy.videosource;

import com.github.piasy.videosource.webrtc.Logging;
import com.github.piasy.videosource.webrtc.VideoCapturer;
import com.github.piasy.videosource.webrtc.VideoRenderer;

/**
 * Created by Piasy{github.com/Piasy} on 20/07/2017.
 */

public class VideoSink implements VideoCapturer.CapturerObserver {

    private static final String TAG = "VideoSink";

    private final VideoRenderer.Callbacks mCallbacks;
    private final MatrixHelper mMatrixHelper;

    private volatile boolean mFlipHorizontal;
    private volatile boolean mFlipVertical;
    private volatile float mRotateDegree;

    public VideoSink(final VideoRenderer.Callbacks callbacks) {
        mCallbacks = callbacks;
        mMatrixHelper = new MatrixHelper();
    }

    public void flipHorizontal(boolean flip) {
        mFlipHorizontal = flip;
    }

    public void flipVertical(boolean flip) {
        mFlipVertical = flip;
    }

    public void rotate(float rotateDegree) {
        mRotateDegree = rotateDegree;
    }

    @Override
    public void onCapturerStarted(final boolean success) {
        Logging.d(TAG, "onCapturerStarted " + success);
    }

    @Override
    public void onCapturerStopped() {
        Logging.d(TAG, "onCapturerStopped");
    }

    @Override
    public void onByteBufferFrameCaptured(final byte[] data, final int width, final int height,
            final int rotation, final long timestamp) {
    }

    @Override
    public void onTextureFrameCaptured(final int width, final int height, final int oesTextureId,
            final float[] transformMatrix, final int rotation, final long timestamp) {

        mMatrixHelper.flip(transformMatrix, mFlipHorizontal, mFlipVertical);
        mMatrixHelper.rotate(transformMatrix, mRotateDegree);

        VideoRenderer.I420Frame frame = new VideoRenderer.I420Frame(width, height, rotation,
                oesTextureId, transformMatrix, 0);
        mCallbacks.renderFrame(frame);
    }
}
