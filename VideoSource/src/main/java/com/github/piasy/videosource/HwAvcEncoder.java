package com.github.piasy.videosource;

import org.webrtc.EglBase;
import org.webrtc.MediaCodecVideoEncoder;
import org.webrtc.VideoRenderer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Piasy{github.com/Piasy} on 21/07/2017.
 */

public class HwAvcEncoder implements VideoRenderer.Callbacks {

    private final ExecutorService mMediaCodecService;
    private final MediaCodecVideoEncoder mVideoEncoder;
    private final EncodedFrameObserver mEncodedFrameObserver;

    public HwAvcEncoder(final EncodedFrameObserver encodedFrameObserver) {
        mMediaCodecService = Executors.newSingleThreadExecutor();
        mVideoEncoder = new MediaCodecVideoEncoder();
        mEncodedFrameObserver = encodedFrameObserver;
    }

    public void start(final EglBase eglBase) {
        mMediaCodecService.execute(new Runnable() {
            @Override
            public void run() {
                mVideoEncoder.initEncode(MediaCodecVideoEncoder.VideoCodecType.VIDEO_CODEC_H264,
                        MediaCodecVideoEncoder.H264Profile.CONSTRAINED_BASELINE.getValue(),
                        640, 360, 500, 60, eglBase.getEglBaseContext());
            }
        });
    }

    @Override
    public void renderFrame(final VideoRenderer.I420Frame frame) {
        mMediaCodecService.execute(new Runnable() {
            @Override
            public void run() {
                mVideoEncoder.encodeTexture(false, frame.textureId, frame.samplingMatrix,
                        frame.timestamp / 1000);

                MediaCodecVideoEncoder.OutputBufferInfo bufferInfo
                        = mVideoEncoder.dequeueOutputBuffer();
                if (bufferInfo != null) {
                    mEncodedFrameObserver.onEncodedFrame(bufferInfo);
                    mVideoEncoder.releaseOutputBuffer(bufferInfo.index);
                }
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
