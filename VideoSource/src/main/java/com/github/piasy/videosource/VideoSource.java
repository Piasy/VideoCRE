package com.github.piasy.videosource;

import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.Logging;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;

/**
 * Created by Piasy{github.com/Piasy} on 20/07/2017.
 */

public class VideoSource {
    public static final int SOURCE_CAMERA1 = 1;
    public static final int SOURCE_CAMERA2 = 2;
    public static final int SOURCE_SCREEN = 3;
    public static final int SOURCE_FILE = 4;

    private static final String TAG = "VideoSource";
    private static final String VIDEO_CAPTURER_THREAD_NAME = "VideoCapturerThread";

    private final Context mAppContext;
    private final EglBase mEglBase;
    private final ExecutorService mExecutor;

    private VideoCapturer mVideoCapturer;
    private SurfaceTextureHelper mSurfaceTextureHelper;
    private VideoConfig mVideoConfig;
    private boolean mVideoCapturerStopped;

    public VideoSource(final Context appContext, final VideoConfig config,
            final VideoCapturer capturer, final VideoCapturer.CapturerObserver capturerObserver) {
        mAppContext = appContext;
        mEglBase = EglBase.create();
        mExecutor = Executors.newSingleThreadExecutor();

        mVideoConfig = config;
        final EglBase.Context eglContext = mEglBase.getEglBaseContext();
        mSurfaceTextureHelper = SurfaceTextureHelper.create(VIDEO_CAPTURER_THREAD_NAME, eglContext);
        mVideoCapturer = capturer;
        mVideoCapturer.initialize(mSurfaceTextureHelper, mAppContext,
                new VideoCapturer.CapturerObserver() {
                    @Override
                    public void onCapturerStarted(final boolean success) {
                        capturerObserver.onCapturerStarted(success);
                    }

                    @Override
                    public void onCapturerStopped() {
                        capturerObserver.onCapturerStopped();
                    }

                    @Override
                    public void onByteBufferFrameCaptured(final byte[] data, final int width,
                            final int height, final int rotation, final long timestamp) {
                        capturerObserver.onByteBufferFrameCaptured(data, width, height, rotation,
                                timestamp);
                    }

                    @Override
                    public void onTextureFrameCaptured(final int width, final int height,
                            final int oesTextureId, final float[] transformMatrix,
                            final int rotation, final long timestamp) {
                        capturerObserver.onTextureFrameCaptured(width, height, oesTextureId,
                                transformMatrix, rotation, timestamp);
                        mSurfaceTextureHelper.returnTextureFrame();
                    }
                });
    }

    public void start() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mVideoCapturer.startCapture(mVideoConfig.previewWidth(),
                        mVideoConfig.previewHeight(), mVideoConfig.fps());
                mVideoCapturerStopped = false;
            }
        });
    }

    public void switchCamera() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mVideoCapturer instanceof CameraVideoCapturer) {
                    ((CameraVideoCapturer) mVideoCapturer).switchCamera(null);
                }
            }
        });
    }

    public void stop() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mVideoCapturer != null && !mVideoCapturerStopped) {
                    Logging.d(TAG, "Stop video source.");
                    try {
                        mVideoCapturer.stopCapture();
                    } catch (InterruptedException e) {
                    }
                    mVideoCapturerStopped = true;
                }
            }
        });
    }

    public EglBase getRootEglBase() {
        return mEglBase;
    }

    public void destroy() {
        mVideoCapturer.dispose();
        mSurfaceTextureHelper.dispose();
        mEglBase.release();
        mExecutor.shutdown();
    }
}
