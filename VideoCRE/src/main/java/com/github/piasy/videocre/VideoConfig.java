package com.github.piasy.videocre;

import com.google.auto.value.AutoValue;

/**
 * Created by Piasy{github.com/Piasy} on 20/07/2017.
 */

@AutoValue
public abstract class VideoConfig {

    public static Builder builder() {
        return new AutoValue_VideoConfig.Builder();
    }

    public abstract int previewWidth();

    public abstract int previewHeight();

    public abstract int outputWidth();

    public abstract int outputHeight();

    public abstract int fps();

    public abstract int outputBitrate();

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder previewWidth(int previewWidth);

        public abstract Builder previewHeight(int previewHeight);

        public abstract Builder outputWidth(int outputWidth);

        public abstract Builder outputHeight(int outputHeight);

        public abstract Builder fps(int fps);

        public abstract Builder outputBitrate(int outputBitrate);

        public abstract VideoConfig build();
    }
}
