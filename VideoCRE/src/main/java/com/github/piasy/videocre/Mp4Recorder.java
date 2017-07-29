package com.github.piasy.videocre;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import java.io.File;
import java.io.IOException;
import org.webrtc.Logging;
import org.webrtc.MediaCodecVideoEncoder;

/**
 * Created by Piasy{github.com/Piasy} on 22/07/2017.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class Mp4Recorder implements MediaCodecCallback {
    private static final String TAG = "Mp4Recorder";

    private final MediaMuxer mMediaMuxer;

    private int mTrackIndex;
    private boolean mMuxerStarted;

    public Mp4Recorder(final File outputFile) throws IOException {
        mMediaMuxer = new MediaMuxer(outputFile.getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    @Override
    public void onEncodedFrame(final MediaCodecVideoEncoder.OutputBufferInfo frame,
            final MediaCodec.BufferInfo bufferInfo) {
        boolean configFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
        if (!configFrame) {
            mMediaMuxer.writeSampleData(mTrackIndex, frame.buffer(), bufferInfo);
        }
    }

    @Override
    public void onOutputFormatChanged(final MediaCodec codec, final MediaFormat format) {
        if (mMuxerStarted) {
            throw new RuntimeException("format changed twice");
        }

        String name = format.getString(MediaFormat.KEY_MIME);
        int width = format.getInteger(MediaFormat.KEY_WIDTH);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT);

        Logging.d(TAG, "onOutputFormatChanged " + name + " " + width + "x" + height);

        mTrackIndex = mMediaMuxer.addTrack(format);
        mMediaMuxer.start();
        mMuxerStarted = true;
    }

    public void stop() {
        mMediaMuxer.stop();
        mMediaMuxer.release();
    }
}
