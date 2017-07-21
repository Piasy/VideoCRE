package com.github.piasy.videosource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.webrtc.EglBase;
import org.webrtc.EncodedImage;
import org.webrtc.HardwareVideoEncoderFactory;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCodecInfo;
import org.webrtc.VideoEncoder;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoFrame;
import org.webrtc.VideoRenderer;

/**
 * Created by Piasy{github.com/Piasy} on 21/07/2017.
 */

public class HwAvcEncoderV2 implements VideoRenderer.Callbacks {
    private static final String TAG = "HwAvcEncoderV2";

    private static final EncodedImage.FrameType[] KEY_FRAME = new EncodedImage.FrameType[] {
            EncodedImage.FrameType.VideoFrameKey
    };
    private static final EncodedImage.FrameType[] DELTA_FRAME = new EncodedImage.FrameType[] {
            EncodedImage.FrameType.VideoFrameDelta
    };
    private static final VideoEncoder.EncodeInfo KEY_FRAME_INFO = new VideoEncoder.EncodeInfo(
            KEY_FRAME);
    private static final VideoEncoder.EncodeInfo DELTA_FRAME_INFO = new VideoEncoder.EncodeInfo(
            DELTA_FRAME);

    private final ExecutorService mMediaCodecService;
    private final SurfaceTextureHelper mSurfaceTextureHelper;
    private final VideoEncoder mVideoEncoder;
    private final VideoEncoder.Callback mEncoderOutputCallback;

    public HwAvcEncoderV2(final EglBase eglBase, final SurfaceTextureHelper surfaceTextureHelper,
            final VideoEncoder.Callback callback) {
        mSurfaceTextureHelper = surfaceTextureHelper;
        mMediaCodecService = Executors.newSingleThreadExecutor();
        mVideoEncoder = createH264HwEncoder(eglBase);
        mEncoderOutputCallback = callback;
    }

    private static VideoEncoder createH264HwEncoder(final EglBase eglBase) {
        VideoEncoderFactory factory = new HardwareVideoEncoderFactory(eglBase.getEglBaseContext(),
                false, true);
        VideoCodecInfo[] codecs = factory.getSupportedCodecs();
        return factory.createEncoder(codecs[codecs.length - 1]);
    }

    public void start() {
        mMediaCodecService.execute(new Runnable() {
            @Override
            public void run() {
                VideoEncoder.Settings settings = new VideoEncoder.Settings(2, 640, 360, 500, 30);
                mVideoEncoder.initEncode(settings, mEncoderOutputCallback);
            }
        });
    }

    @Override
    public void renderFrame(final VideoRenderer.I420Frame frame) {
        mMediaCodecService.execute(new Runnable() {
            @Override
            public void run() {
                // TODO: 21/07/2017 how to create OesTextureBuffer better?
                VideoFrame.Buffer buffer = mSurfaceTextureHelper.createTextureBuffer(frame.width,
                        frame.height, frame.samplingMatrix);
                VideoFrame videoFrame = new VideoFrame(buffer, frame.rotationDegree,
                        frame.timestamp,
                        RendererCommon.convertMatrixToAndroidGraphicsMatrix(frame.samplingMatrix));
                mVideoEncoder.encode(videoFrame, DELTA_FRAME_INFO);
            }
        });
    }

    public void destroy() {
        mMediaCodecService.execute(new Runnable() {
            @Override
            public void run() {
                mVideoEncoder.release();
                mMediaCodecService.shutdown();
            }
        });
    }
}
