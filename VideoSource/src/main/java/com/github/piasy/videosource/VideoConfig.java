package com.github.piasy.videosource;

import com.google.auto.value.AutoValue;

/**
 * Created by Piasy{github.com/Piasy} on 20/07/2017.
 */

@AutoValue
public abstract class VideoConfig {

    public static Builder builder() {
        return new AutoValue_VideoConfig.Builder();
    }

    public abstract int videoWidth();

    public abstract int videoHeight();

    public abstract int videoFps();

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder videoWidth(int videoWidth);

        public abstract Builder videoHeight(int videoHeight);

        public abstract Builder videoFps(int videoFps);

        public abstract VideoConfig build();
    }
}
