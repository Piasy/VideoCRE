package com.github.piasy.videosource;

import android.media.MediaCodec;
import android.media.MediaFormat;
import org.webrtc.MediaCodecVideoEncoder;

/**
 * Created by Piasy{github.com/Piasy} on 22/07/2017.
 */

public interface MediaCodecCallback {
    void onEncodedFrame(MediaCodecVideoEncoder.OutputBufferInfo frame);

    void onOutputFormatChanged(MediaCodec codec, MediaFormat format);
}
