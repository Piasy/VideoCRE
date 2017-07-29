package com.github.piasy.videocre;

import android.media.MediaCodec;
import android.media.MediaFormat;
import org.webrtc.MediaCodecVideoEncoder;

/**
 * Created by Piasy{github.com/Piasy} on 22/07/2017.
 */

public interface MediaCodecCallback {
    /**
     * must consume it synchronously.
     */
    void onEncodedFrame(MediaCodecVideoEncoder.OutputBufferInfo frame,
            MediaCodec.BufferInfo bufferInfo);

    void onOutputFormatChanged(MediaCodec codec, MediaFormat format);
}
