package com.github.piasy.videosource;

import org.webrtc.MediaCodecVideoEncoder;

/**
 * Created by Piasy{github.com/Piasy} on 21/07/2017.
 */

public interface EncodedFrameObserver {
    void onEncodedFrame(final MediaCodecVideoEncoder.OutputBufferInfo bufferInfo);
}
