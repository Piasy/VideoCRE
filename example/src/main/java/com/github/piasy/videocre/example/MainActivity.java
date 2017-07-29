package com.github.piasy.videocre.example;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import com.github.piasy.videocre.VideoSource;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public static int sVideoSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        onClick(R.id.mCamera1, R.id.mCamera2);
    }

    private void onClick(int... ids) {
        for (int id : ids) {
            findViewById(id).setOnClickListener(this);
        }
    }

    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            case R.id.mCamera1:
                sVideoSource = VideoSource.SOURCE_CAMERA1;
                break;
            case R.id.mCamera2:
                sVideoSource = VideoSource.SOURCE_CAMERA2;
                break;
            default:
                return;
        }

        startActivity(new Intent(this, VideoActivity.class));
    }
}
