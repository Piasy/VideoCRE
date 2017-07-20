package com.github.piasy.videosource.example;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import com.github.piasy.videosource.VideoConfig;
import com.github.piasy.videosource.VideoSink;
import com.github.piasy.videosource.VideoSource;
import com.github.piasy.videosource.webrtc.Camera1Enumerator;
import com.github.piasy.videosource.webrtc.Camera2Enumerator;
import com.github.piasy.videosource.webrtc.CameraEnumerator;
import com.github.piasy.videosource.webrtc.FileVideoCapturer;
import com.github.piasy.videosource.webrtc.Logging;
import com.github.piasy.videosource.webrtc.SurfaceViewRenderer;
import com.github.piasy.videosource.webrtc.VideoCapturer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class VideoActivity extends AppCompatActivity {
    private static final String TAG = "VideoActivity";

    private VideoSource mVideoSource;
    private List<SurfaceViewRenderer> mVideoViews = new ArrayList<>();
    private VideoSink mVideoSink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        SurfaceViewRenderer videoView1 = (SurfaceViewRenderer) findViewById(R.id.mVideoView1);
        mVideoViews.add(videoView1);
        SurfaceViewRenderer videoView2 = (SurfaceViewRenderer) findViewById(R.id.mVideoView2);
        mVideoViews.add(videoView2);
        SurfaceViewRenderer videoView3 = (SurfaceViewRenderer) findViewById(R.id.mVideoView3);
        mVideoViews.add(videoView3);

        mVideoSink = new VideoSink(videoView1, videoView2, videoView3);

        VideoConfig config = VideoConfig.builder()
                .videoWidth(1280)
                .videoHeight(720)
                .videoFps(30)
                .build();
        VideoCapturer capturer = createVideoCapturer(VideoSource.SOURCE_CAMERA1, null);

        mVideoSource = new VideoSource(getApplicationContext(), config, capturer, mVideoSink);

        for (SurfaceViewRenderer videoView : mVideoViews) {
            videoView.init(mVideoSource.getRootEglBase().getEglBaseContext(), null);
        }

        initView();
    }

    @Override
    protected void onStart() {
        super.onStart();

        mVideoSource.start();
    }

    @Override
    protected void onStop() {
        super.onStop();

        mVideoSource.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mVideoSource.destroy();
        for (SurfaceViewRenderer videoView : mVideoViews) {
            videoView.release();
        }
    }

    private void initView() {

        findViewById(R.id.mSwitch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                mVideoSource.switchCamera();
            }
        });

        final TextView tvRotateDegree = (TextView) findViewById(R.id.mTvRotateDegree);
        ((SeekBar) findViewById(R.id.mRotateSeek)).setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(final SeekBar seekBar, final int progress,
                            final boolean fromUser) {
                        float rotateDegree = 360.0f * progress / 100;
                        tvRotateDegree.setText(String.format("rotate: %.1f", rotateDegree));

                        mVideoSink.rotate(rotateDegree);
                    }

                    @Override
                    public void onStartTrackingTouch(final SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(final SeekBar seekBar) {
                    }
                });
        ((CheckBox) findViewById(R.id.mCbFlipH)).setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(final CompoundButton buttonView,
                            final boolean isChecked) {
                        mVideoSink.flipHorizontal(isChecked);
                    }
                });
        ((CheckBox) findViewById(R.id.mCbFlipV)).setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(final CompoundButton buttonView,
                            final boolean isChecked) {
                        mVideoSink.flipVertical(isChecked);
                    }
                });
    }

    private VideoCapturer createVideoCapturer(int source, String param) {
        switch (source) {
            case VideoSource.SOURCE_CAMERA1:
                return createCameraCapturer(new Camera1Enumerator(true));
            case VideoSource.SOURCE_CAMERA2:
                return createCameraCapturer(new Camera2Enumerator(this));
            case VideoSource.SOURCE_SCREEN:
                return null;
            case VideoSource.SOURCE_FILE:
                try {
                    return new FileVideoCapturer(param);
                } catch (IOException e) {
                    return null;
                }
            default:
                return null;
        }
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
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
