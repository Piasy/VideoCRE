package com.github.piasy.videosource;

import android.media.MediaCodec;
import android.media.MediaFormat;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.webrtc.EglBase;
import org.webrtc.MediaCodecVideoEncoder;
import org.webrtc.VideoRenderer;

/**
 * Created by Piasy{github.com/Piasy} on 21/07/2017.
 */

public class HwAvcEncoder implements VideoRenderer.Callbacks, MediaCodecCallback {

    private final ExecutorService mMediaCodecService;
    private final MediaCodecVideoEncoder mVideoEncoder;
    private final List<MediaCodecCallback> mMediaCodecCallbacks;

    public HwAvcEncoder(final MediaCodecCallback... callbacks) {
        mMediaCodecService = Executors.newSingleThreadExecutor();
        mVideoEncoder = new MediaCodecVideoEncoder();
        mMediaCodecCallbacks = Arrays.asList(callbacks);
    }

    public void start(final EglBase eglBase) {
        mMediaCodecService.execute(new Runnable() {
            @Override
            public void run() {
                mVideoEncoder.initEncode(MediaCodecVideoEncoder.VideoCodecType.VIDEO_CODEC_H264,
                        MediaCodecVideoEncoder.H264Profile.CONSTRAINED_BASELINE.getValue(),
                        360, 640, 500, 60, eglBase.getEglBaseContext(), HwAvcEncoder.this);
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

    @Override
    public void onEncodedFrame(final MediaCodecVideoEncoder.OutputBufferInfo frame,
            final MediaCodec.BufferInfo bufferInfo) {
        for (MediaCodecCallback callback : mMediaCodecCallbacks) {
            callback.onEncodedFrame(frame, bufferInfo);
        }
    }

    @Override
    public void onOutputFormatChanged(final MediaCodec codec, final MediaFormat format) {
        for (MediaCodecCallback callback : mMediaCodecCallbacks) {
            callback.onOutputFormatChanged(codec, format);
        }
    }
}
