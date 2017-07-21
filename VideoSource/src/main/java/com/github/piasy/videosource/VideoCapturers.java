package com.github.piasy.videosource;

import android.content.Context;
import java.io.IOException;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.FileVideoCapturer;
import org.webrtc.Logging;
import org.webrtc.VideoCapturer;

/**
 * Created by Piasy{github.com/Piasy} on 21/07/2017.
 */

public final class VideoCapturers {
    private static final String TAG = "VideoCapturers";

    public static VideoCapturer createCamera1Capturer(boolean captureToTexture) {
        return createCameraCapturer(new Camera1Enumerator(captureToTexture));
    }

    public static VideoCapturer createCamera2Capturer(Context context) {
        return createCameraCapturer(new Camera2Enumerator(context));
    }

    public static VideoCapturer createFileVideoCapturer(String path) {
        try {
            return new FileVideoCapturer(path);
        } catch (IOException e) {
            return null;
        }
    }

    private static VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }
}
