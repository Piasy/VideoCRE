/*
 *  Copyright 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;
import com.github.piasy.videosource.MediaCodecCallback;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// Java-side of peerconnection_jni.cc:MediaCodecVideoEncoder.
// This class is an implementation detail of the Java PeerConnection API.
@TargetApi(19)
@SuppressWarnings("deprecation")
public class MediaCodecVideoEncoder {
  // This class is constructed, operated, and destroyed by its C++ incarnation,
  // so the class and its methods have non-public visibility.  The API this
  // class exposes aims to mimic the webrtc::VideoEncoder API as closely as
  // possibly to minimize the amount of translation work necessary.

  private static final String TAG = "MediaCodecVideoEncoder";

  // Tracks webrtc::VideoCodecType.
  public enum VideoCodecType { VIDEO_CODEC_VP8, VIDEO_CODEC_VP9, VIDEO_CODEC_H264 }

  private static final int MEDIA_CODEC_RELEASE_TIMEOUT_MS = 5000; // Timeout for codec releasing.
  private static final int DEQUEUE_TIMEOUT = 0; // Non-blocking, no wait.
  private static final int OUTPUT_THREAD_DEQUEUE_TIMEOUT_US = 3000; // 3 ms
  private static final int BITRATE_ADJUSTMENT_FPS = 30;
  private static final int MAXIMUM_INITIAL_FPS = 30;
  private static final double BITRATE_CORRECTION_SEC = 3.0;
  // Maximum bitrate correction scale - no more than 4 times.
  private static final double BITRATE_CORRECTION_MAX_SCALE = 4;
  // Amount of correction steps to reach correction maximum scale.
  private static final int BITRATE_CORRECTION_STEPS = 20;
  // Forced key frame interval - used to reduce color distortions on Qualcomm platform.
  private static final long QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_L_MS = 15000;
  private static final long QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS = 20000;
  private static final long QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_N_MS = 15000;

  // Active running encoder instance. Set in initEncode() (called from native code)
  // and reset to null in release() call.
  private static MediaCodecVideoEncoder runningInstance = null;
  private static MediaCodecVideoEncoderErrorCallback errorCallback = null;
  private static int codecErrors = 0;
  // List of disabled codec types - can be set from application.
  private static Set<String> hwEncoderDisabledTypes = new HashSet<String>();

  private Thread mediaCodecThread;
  private MediaCodec mediaCodec;
  private ByteBuffer[] outputBuffers;
  private EglBase eglBase;
  private int profile;
  private int width;
  private int height;
  private Surface inputSurface;
  private GlRectDrawer drawer;

  // Thread that delivers encoded frames to the user callback.
  private Thread outputThread;
  private MediaCodec.BufferInfo outputBufferInfo = new MediaCodec.BufferInfo();
  private ByteBuffer keyFrameData = ByteBuffer.allocateDirect(10240); // pre-allocate 10 KB
  private OutputBufferInfo outputFrame = new OutputBufferInfo();
  private MediaCodecCallback callback;
  // Whether the encoder is running.  Volatile so that the output thread can watch this value and
  // exit when the encoder stops.
  private volatile boolean running = false;

  private static final String VP8_MIME_TYPE = "video/x-vnd.on2.vp8";
  private static final String VP9_MIME_TYPE = "video/x-vnd.on2.vp9";
  private static final String H264_MIME_TYPE = "video/avc";

  private static final int VIDEO_AVCProfileHigh = 8;
  private static final int VIDEO_AVCLevel3 = 0x100;

  // Type of bitrate adjustment for video encoder.
  public enum BitrateAdjustmentType {
    // No adjustment - video encoder has no known bitrate problem.
    NO_ADJUSTMENT,
    // Framerate based bitrate adjustment is required - HW encoder does not use frame
    // timestamps to calculate frame bitrate budget and instead is relying on initial
    // fps configuration assuming that all frames are coming at fixed initial frame rate.
    FRAMERATE_ADJUSTMENT,
    // Dynamic bitrate adjustment is required - HW encoder used frame timestamps, but actual
    // bitrate deviates too much from the target value.
    DYNAMIC_ADJUSTMENT
  }

  // Should be in sync with webrtc::H264::Profile.
  public static enum H264Profile {
    CONSTRAINED_BASELINE(0),
    BASELINE(1),
    MAIN(2),
    CONSTRAINED_HIGH(3),
    HIGH(4);

    private final int value;

    H264Profile(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
  }

  // Class describing supported media codec properties.
  private static class MediaCodecProperties {
    public final String codecPrefix;
    // Minimum Android SDK required for this codec to be used.
    public final int minSdk;
    // Flag if encoder implementation does not use frame timestamps to calculate frame bitrate
    // budget and instead is relying on initial fps configuration assuming that all frames are
    // coming at fixed initial frame rate. Bitrate adjustment is required for this case.
    public final BitrateAdjustmentType bitrateAdjustmentType;

    MediaCodecProperties(
        String codecPrefix, int minSdk, BitrateAdjustmentType bitrateAdjustmentType) {
      this.codecPrefix = codecPrefix;
      this.minSdk = minSdk;
      this.bitrateAdjustmentType = bitrateAdjustmentType;
    }
  }

  // List of supported HW VP8 encoders.
  private static final MediaCodecProperties qcomVp8HwProperties = new MediaCodecProperties(
      "OMX.qcom.", Build.VERSION_CODES.KITKAT, BitrateAdjustmentType.NO_ADJUSTMENT);
  private static final MediaCodecProperties exynosVp8HwProperties = new MediaCodecProperties(
      "OMX.Exynos.", Build.VERSION_CODES.M, BitrateAdjustmentType.DYNAMIC_ADJUSTMENT);
  private static final MediaCodecProperties intelVp8HwProperties = new MediaCodecProperties(
      "OMX.Intel.", Build.VERSION_CODES.LOLLIPOP, BitrateAdjustmentType.NO_ADJUSTMENT);
  private static MediaCodecProperties[] vp8HwList() {
    final ArrayList<MediaCodecProperties> supported_codecs = new ArrayList<MediaCodecProperties>();
    supported_codecs.add(qcomVp8HwProperties);
    supported_codecs.add(exynosVp8HwProperties);
    supported_codecs.add(intelVp8HwProperties);
    return supported_codecs.toArray(new MediaCodecProperties[supported_codecs.size()]);
  }

  // List of supported HW VP9 encoders.
  private static final MediaCodecProperties qcomVp9HwProperties = new MediaCodecProperties(
      "OMX.qcom.", Build.VERSION_CODES.N, BitrateAdjustmentType.NO_ADJUSTMENT);
  private static final MediaCodecProperties exynosVp9HwProperties = new MediaCodecProperties(
      "OMX.Exynos.", Build.VERSION_CODES.N, BitrateAdjustmentType.FRAMERATE_ADJUSTMENT);
  private static final MediaCodecProperties[] vp9HwList =
      new MediaCodecProperties[] {qcomVp9HwProperties, exynosVp9HwProperties};

  // List of supported HW H.264 encoders.
  private static final MediaCodecProperties qcomH264HwProperties = new MediaCodecProperties(
      "OMX.qcom.", Build.VERSION_CODES.KITKAT, BitrateAdjustmentType.NO_ADJUSTMENT);
  private static final MediaCodecProperties exynosH264HwProperties = new MediaCodecProperties(
      "OMX.Exynos.", Build.VERSION_CODES.LOLLIPOP, BitrateAdjustmentType.FRAMERATE_ADJUSTMENT);
  private static final MediaCodecProperties[] h264HwList =
      new MediaCodecProperties[] {qcomH264HwProperties, exynosH264HwProperties};

  // List of supported HW H.264 high profile encoders.
  private static final MediaCodecProperties exynosH264HighProfileHwProperties =
      new MediaCodecProperties(
          "OMX.Exynos.", Build.VERSION_CODES.M, BitrateAdjustmentType.FRAMERATE_ADJUSTMENT);
  private static final MediaCodecProperties[] h264HighProfileHwList =
      new MediaCodecProperties[] {exynosH264HighProfileHwProperties};

  // List of devices with poor H.264 encoder quality.
  // HW H.264 encoder on below devices has poor bitrate control - actual
  // bitrates deviates a lot from the target value.
  private static final String[] H264_HW_EXCEPTION_MODELS =
      new String[] {"SAMSUNG-SGH-I337", "Nexus 7", "Nexus 4"};

  // Bitrate modes - should be in sync with OMX_VIDEO_CONTROLRATETYPE defined
  // in OMX_Video.h
  private static final int VIDEO_ControlRateConstant = 2;
  // NV12 color format supported by QCOM codec, but not declared in MediaCodec -
  // see /hardware/qcom/media/mm-core/inc/OMX_QCOMExtns.h
  private static final int COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m = 0x7FA30C04;
  // Allowable color formats supported by codec - in order of preference.
  private static final int[] supportedColorList = {CodecCapabilities.COLOR_FormatYUV420Planar,
      CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
      CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar,
      COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m};
  private static final int[] supportedSurfaceColorList = {CodecCapabilities.COLOR_FormatSurface};
  private VideoCodecType type;
  private int colorFormat; // Used by native code.

  // Variables used for dynamic bitrate adjustment.
  private BitrateAdjustmentType bitrateAdjustmentType = BitrateAdjustmentType.NO_ADJUSTMENT;
  private double bitrateAccumulator;
  private double bitrateAccumulatorMax;
  private double bitrateObservationTimeMs;
  private int bitrateAdjustmentScaleExp;
  private int targetBitrateBps;
  private int targetFps;

  // Interval in ms to force key frame generation. Used to reduce the time of color distortions
  // happened sometime when using Qualcomm video encoder.
  private long forcedKeyFrameMs;
  private long lastKeyFrameMs;

  // SPS and PPS NALs (Config frame) for H.264.
  private ByteBuffer configData = null;

  // MediaCodec error handler - invoked when critical error happens which may prevent
  // further use of media codec API. Now it means that one of media codec instances
  // is hanging and can no longer be used in the next call.
  public static interface MediaCodecVideoEncoderErrorCallback {
    void onMediaCodecVideoEncoderCriticalError(int codecErrors);
  }

  public static void setErrorCallback(MediaCodecVideoEncoderErrorCallback errorCallback) {
    Logging.d(TAG, "Set error callback");
    MediaCodecVideoEncoder.errorCallback = errorCallback;
  }

  // Functions to disable HW encoding - can be called from applications for platforms
  // which have known HW decoding problems.
  public static void disableVp8HwCodec() {
    Logging.w(TAG, "VP8 encoding is disabled by application.");
    hwEncoderDisabledTypes.add(VP8_MIME_TYPE);
  }

  public static void disableVp9HwCodec() {
    Logging.w(TAG, "VP9 encoding is disabled by application.");
    hwEncoderDisabledTypes.add(VP9_MIME_TYPE);
  }

  public static void disableH264HwCodec() {
    Logging.w(TAG, "H.264 encoding is disabled by application.");
    hwEncoderDisabledTypes.add(H264_MIME_TYPE);
  }

  // Functions to query if HW encoding is supported.
  public static boolean isVp8HwSupported() {
    return !hwEncoderDisabledTypes.contains(VP8_MIME_TYPE)
        && (findHwEncoder(VP8_MIME_TYPE, vp8HwList(), supportedColorList) != null);
  }

  public static EncoderProperties vp8HwEncoderProperties() {
    if (hwEncoderDisabledTypes.contains(VP8_MIME_TYPE)) {
      return null;
    } else {
      return findHwEncoder(VP8_MIME_TYPE, vp8HwList(), supportedColorList);
    }
  }

  public static boolean isVp9HwSupported() {
    return !hwEncoderDisabledTypes.contains(VP9_MIME_TYPE)
        && (findHwEncoder(VP9_MIME_TYPE, vp9HwList, supportedColorList) != null);
  }

  public static boolean isH264HwSupported() {
    return !hwEncoderDisabledTypes.contains(H264_MIME_TYPE)
        && (findHwEncoder(H264_MIME_TYPE, h264HwList, supportedColorList) != null);
  }

  public static boolean isH264HighProfileHwSupported() {
    return !hwEncoderDisabledTypes.contains(H264_MIME_TYPE)
        && (findHwEncoder(H264_MIME_TYPE, h264HighProfileHwList, supportedColorList) != null);
  }

  public static boolean isVp8HwSupportedUsingTextures() {
    return !hwEncoderDisabledTypes.contains(VP8_MIME_TYPE)
        && (findHwEncoder(VP8_MIME_TYPE, vp8HwList(), supportedSurfaceColorList) != null);
  }

  public static boolean isVp9HwSupportedUsingTextures() {
    return !hwEncoderDisabledTypes.contains(VP9_MIME_TYPE)
        && (findHwEncoder(VP9_MIME_TYPE, vp9HwList, supportedSurfaceColorList) != null);
  }

  public static boolean isH264HwSupportedUsingTextures() {
    return !hwEncoderDisabledTypes.contains(H264_MIME_TYPE)
        && (findHwEncoder(H264_MIME_TYPE, h264HwList, supportedSurfaceColorList) != null);
  }

  // Helper struct for findHwEncoder() below.
  public static class EncoderProperties {
    public EncoderProperties(
        String codecName, int colorFormat, BitrateAdjustmentType bitrateAdjustmentType) {
      this.codecName = codecName;
      this.colorFormat = colorFormat;
      this.bitrateAdjustmentType = bitrateAdjustmentType;
    }
    public final String codecName; // OpenMax component name for HW codec.
    public final int colorFormat; // Color format supported by codec.
    public final BitrateAdjustmentType bitrateAdjustmentType; // Bitrate adjustment type
  }

  private static EncoderProperties findHwEncoder(
      String mime, MediaCodecProperties[] supportedHwCodecProperties, int[] colorList) {
    // MediaCodec.setParameters is missing for JB and below, so bitrate
    // can not be adjusted dynamically.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
      return null;
    }

    // Check if device is in H.264 exception list.
    if (mime.equals(H264_MIME_TYPE)) {
      List<String> exceptionModels = Arrays.asList(H264_HW_EXCEPTION_MODELS);
      if (exceptionModels.contains(Build.MODEL)) {
        Logging.w(TAG, "Model: " + Build.MODEL + " has black listed H.264 encoder.");
        return null;
      }
    }

    for (int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
      MediaCodecInfo info = null;
      try {
        info = MediaCodecList.getCodecInfoAt(i);
      } catch (IllegalArgumentException e) {
        Logging.e(TAG, "Cannot retrieve encoder codec info", e);
      }
      if (info == null || !info.isEncoder()) {
        continue;
      }
      String name = null;
      for (String mimeType : info.getSupportedTypes()) {
        if (mimeType.equals(mime)) {
          name = info.getName();
          break;
        }
      }
      if (name == null) {
        continue; // No HW support in this codec; try the next one.
      }
      Logging.v(TAG, "Found candidate encoder " + name);

      // Check if this is supported HW encoder.
      boolean supportedCodec = false;
      BitrateAdjustmentType bitrateAdjustmentType = BitrateAdjustmentType.NO_ADJUSTMENT;
      for (MediaCodecProperties codecProperties : supportedHwCodecProperties) {
        if (name.startsWith(codecProperties.codecPrefix)) {
          if (Build.VERSION.SDK_INT < codecProperties.minSdk) {
            Logging.w(
                TAG, "Codec " + name + " is disabled due to SDK version " + Build.VERSION.SDK_INT);
            continue;
          }
          if (codecProperties.bitrateAdjustmentType != BitrateAdjustmentType.NO_ADJUSTMENT) {
            bitrateAdjustmentType = codecProperties.bitrateAdjustmentType;
            Logging.w(
                TAG, "Codec " + name + " requires bitrate adjustment: " + bitrateAdjustmentType);
          }
          supportedCodec = true;
          break;
        }
      }
      if (!supportedCodec) {
        continue;
      }

      // Check if HW codec supports known color format.
      CodecCapabilities capabilities;
      try {
        capabilities = info.getCapabilitiesForType(mime);
      } catch (IllegalArgumentException e) {
        Logging.e(TAG, "Cannot retrieve encoder capabilities", e);
        continue;
      }
      for (int colorFormat : capabilities.colorFormats) {
        Logging.v(TAG, "   Color: 0x" + Integer.toHexString(colorFormat));
      }

      for (int supportedColorFormat : colorList) {
        for (int codecColorFormat : capabilities.colorFormats) {
          if (codecColorFormat == supportedColorFormat) {
            // Found supported HW encoder.
            Logging.d(TAG, "Found target encoder for mime " + mime + " : " + name + ". Color: 0x"
                    + Integer.toHexString(codecColorFormat) + ". Bitrate adjustment: "
                    + bitrateAdjustmentType);
            return new EncoderProperties(name, codecColorFormat, bitrateAdjustmentType);
          }
        }
      }
    }
    return null; // No HW encoder.
  }

  private void checkOnMediaCodecThread() {
    if (mediaCodecThread.getId() != Thread.currentThread().getId()) {
      throw new RuntimeException("MediaCodecVideoEncoder previously operated on " + mediaCodecThread
          + " but is now called on " + Thread.currentThread());
    }
  }

  public static void printStackTrace() {
    if (runningInstance != null && runningInstance.mediaCodecThread != null) {
      StackTraceElement[] mediaCodecStackTraces = runningInstance.mediaCodecThread.getStackTrace();
      if (mediaCodecStackTraces.length > 0) {
        Logging.d(TAG, "MediaCodecVideoEncoder stacks trace:");
        for (StackTraceElement stackTrace : mediaCodecStackTraces) {
          Logging.d(TAG, stackTrace.toString());
        }
      }
    }
  }

  static MediaCodec createByCodecName(String codecName) {
    try {
      // In the L-SDK this call can throw IOException so in order to work in
      // both cases catch an exception.
      return MediaCodec.createByCodecName(codecName);
    } catch (Exception e) {
      return null;
    }
  }

  public boolean initEncode(VideoCodecType type, int profile, int width, int height, int kbps, int fps,
      EglBase.Context sharedContext, MediaCodecCallback callback) {
    final boolean useSurface = sharedContext != null;
    Logging.d(TAG,
        "Java initEncode: " + type + ". Profile: " + profile + " : " + width + " x " + height
            + ". @ " + kbps + " kbps. Fps: " + fps + ". Encode from texture : " + useSurface);

    this.profile = profile;
    this.width = width;
    this.height = height;
    this.callback = callback;
    if (mediaCodecThread != null) {
      throw new RuntimeException("Forgot to release()?");
    }
    EncoderProperties properties = null;
    String mime = null;
    int keyFrameIntervalSec = 0;
    boolean configureH264HighProfile = false;
    if (type == VideoCodecType.VIDEO_CODEC_VP8) {
      mime = VP8_MIME_TYPE;
      properties = findHwEncoder(
          VP8_MIME_TYPE, vp8HwList(), useSurface ? supportedSurfaceColorList : supportedColorList);
      keyFrameIntervalSec = 100;
    } else if (type == VideoCodecType.VIDEO_CODEC_VP9) {
      mime = VP9_MIME_TYPE;
      properties = findHwEncoder(
          VP9_MIME_TYPE, vp9HwList, useSurface ? supportedSurfaceColorList : supportedColorList);
      keyFrameIntervalSec = 100;
    } else if (type == VideoCodecType.VIDEO_CODEC_H264) {
      mime = H264_MIME_TYPE;
      properties = findHwEncoder(
          H264_MIME_TYPE, h264HwList, useSurface ? supportedSurfaceColorList : supportedColorList);
      if (profile == H264Profile.CONSTRAINED_HIGH.getValue()) {
        EncoderProperties h264HighProfileProperties = findHwEncoder(H264_MIME_TYPE,
            h264HighProfileHwList, useSurface ? supportedSurfaceColorList : supportedColorList);
        if (h264HighProfileProperties != null) {
          Logging.d(TAG, "High profile H.264 encoder supported.");
          configureH264HighProfile = true;
        } else {
          Logging.d(TAG, "High profile H.264 encoder requested, but not supported. Use baseline.");
        }
      }
      keyFrameIntervalSec = 2;
    }
    if (properties == null) {
      throw new RuntimeException("Can not find HW encoder for " + type);
    }
    runningInstance = this; // Encoder is now running and can be queried for stack traces.
    colorFormat = properties.colorFormat;
    bitrateAdjustmentType = properties.bitrateAdjustmentType;
    if (bitrateAdjustmentType == BitrateAdjustmentType.FRAMERATE_ADJUSTMENT) {
      fps = BITRATE_ADJUSTMENT_FPS;
    } else {
      fps = Math.min(fps, MAXIMUM_INITIAL_FPS);
    }

    forcedKeyFrameMs = 0;
    lastKeyFrameMs = -1;
    if (type == VideoCodecType.VIDEO_CODEC_VP8
        && properties.codecName.startsWith(qcomVp8HwProperties.codecPrefix)) {
      if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP
          || Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1) {
        forcedKeyFrameMs = QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_L_MS;
      } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
        forcedKeyFrameMs = QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS;
      } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
        forcedKeyFrameMs = QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_N_MS;
      }
    }

    Logging.d(TAG, "Color format: " + colorFormat + ". Bitrate adjustment: " + bitrateAdjustmentType
            + ". Key frame interval: " + forcedKeyFrameMs + " . Initial fps: " + fps);
    targetBitrateBps = 1000 * kbps;
    targetFps = fps;
    bitrateAccumulatorMax = targetBitrateBps / 8.0;
    bitrateAccumulator = 0;
    bitrateObservationTimeMs = 0;
    bitrateAdjustmentScaleExp = 0;

    mediaCodecThread = Thread.currentThread();
    try {
      MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
      format.setInteger(MediaFormat.KEY_BIT_RATE, targetBitrateBps);
      format.setInteger("bitrate-mode", VIDEO_ControlRateConstant);
      format.setInteger(MediaFormat.KEY_COLOR_FORMAT, properties.colorFormat);
      format.setInteger(MediaFormat.KEY_FRAME_RATE, targetFps);
      format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyFrameIntervalSec);
      if (configureH264HighProfile) {
        format.setInteger("profile", VIDEO_AVCProfileHigh);
        format.setInteger("level", VIDEO_AVCLevel3);
      }
      Logging.d(TAG, "  Format: " + format);
      mediaCodec = createByCodecName(properties.codecName);
      this.type = type;
      if (mediaCodec == null) {
        Logging.e(TAG, "Can not create media encoder");
        release();
        return false;
      }
      mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

      if (useSurface) {
        eglBase = EglBase.create(sharedContext, EglBase.CONFIG_RECORDABLE);
        // Create an input surface and keep a reference since we must release the surface when done.
        inputSurface = mediaCodec.createInputSurface();
        eglBase.createSurface(inputSurface);
        eglBase.makeCurrent();
        drawer = new GlRectDrawer();
      }
      mediaCodec.start();
      outputBuffers = mediaCodec.getOutputBuffers();
      Logging.d(TAG, "Output buffers: " + outputBuffers.length);

    } catch (IllegalStateException e) {
      Logging.e(TAG, "initEncode failed", e);
      release();
      return false;
    }

    running = true;
    outputThread = createOutputThread();
    outputThread.start();

    return true;
  }

  ByteBuffer[] getInputBuffers() {
    ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
    Logging.d(TAG, "Input buffers: " + inputBuffers.length);
    return inputBuffers;
  }

  void checkKeyFrameRequired(boolean requestedKeyFrame, long presentationTimestampUs) {
    long presentationTimestampMs = (presentationTimestampUs + 500) / 1000;
    if (lastKeyFrameMs < 0) {
      lastKeyFrameMs = presentationTimestampMs;
    }
    boolean forcedKeyFrame = false;
    if (!requestedKeyFrame && forcedKeyFrameMs > 0
        && presentationTimestampMs > lastKeyFrameMs + forcedKeyFrameMs) {
      forcedKeyFrame = true;
    }
    if (requestedKeyFrame || forcedKeyFrame) {
      // Ideally MediaCodec would honor BUFFER_FLAG_SYNC_FRAME so we could
      // indicate this in queueInputBuffer() below and guarantee _this_ frame
      // be encoded as a key frame, but sadly that flag is ignored.  Instead,
      // we request a key frame "soon".
      if (requestedKeyFrame) {
        Logging.d(TAG, "Sync frame request");
      } else {
        Logging.d(TAG, "Sync frame forced");
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        Bundle b = new Bundle();
        b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
        mediaCodec.setParameters(b);
      }
      lastKeyFrameMs = presentationTimestampMs;
    }
  }

  boolean encodeBuffer(
      boolean isKeyframe, int inputBuffer, int size, long presentationTimestampUs) {
    checkOnMediaCodecThread();
    try {
      checkKeyFrameRequired(isKeyframe, presentationTimestampUs);
      mediaCodec.queueInputBuffer(inputBuffer, 0, size, presentationTimestampUs, 0);
      return true;
    } catch (IllegalStateException e) {
      Logging.e(TAG, "encodeBuffer failed", e);
      return false;
    }
  }

  public boolean encodeTexture(boolean isKeyframe, int oesTextureId, float[] transformationMatrix,
      long presentationTimestampUs) {
    checkOnMediaCodecThread();
    try {
      checkKeyFrameRequired(isKeyframe, presentationTimestampUs);
      // makeCurrent once when create eglBase is enough
      //eglBase.makeCurrent();
      // TODO(perkj): glClear() shouldn't be necessary since every pixel is covered anyway,
      // but it's a workaround for bug webrtc:5147.
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
      drawer.drawOes(oesTextureId, transformationMatrix, width, height, 0, 0, width, height);
      eglBase.swapBuffers(TimeUnit.MICROSECONDS.toNanos(presentationTimestampUs));
      return true;
    } catch (RuntimeException e) {
      Logging.e(TAG, "encodeTexture failed", e);
      return false;
    }
  }

  public void release() {
    Logging.d(TAG, "Java releaseEncoder");
    checkOnMediaCodecThread();

    class CaughtException {
      Exception e;
    }
    final CaughtException caughtException = new CaughtException();
    boolean stopHung = false;

    running = false;
    ThreadUtils.joinUninterruptibly(outputThread);

    if (mediaCodec != null) {
      // Run Mediacodec stop() and release() on separate thread since sometime
      // Mediacodec.stop() may hang.
      final CountDownLatch releaseDone = new CountDownLatch(1);

      Runnable runMediaCodecRelease = new Runnable() {
        @Override
        public void run() {
          Logging.d(TAG, "Java releaseEncoder on release thread");
          try {
            mediaCodec.stop();
          } catch (Exception e) {
            Logging.e(TAG, "Media encoder stop failed", e);
          }
          try {
            mediaCodec.release();
          } catch (Exception e) {
            Logging.e(TAG, "Media encoder release failed", e);
            caughtException.e = e;
          }
          Logging.d(TAG, "Java releaseEncoder on release thread done");

          releaseDone.countDown();
        }
      };
      new Thread(runMediaCodecRelease).start();

      if (!ThreadUtils.awaitUninterruptibly(releaseDone, MEDIA_CODEC_RELEASE_TIMEOUT_MS)) {
        Logging.e(TAG, "Media encoder release timeout");
        stopHung = true;
      }

      mediaCodec = null;
    }

    mediaCodecThread = null;
    if (drawer != null) {
      drawer.release();
      drawer = null;
    }
    if (eglBase != null) {
      eglBase.release();
      eglBase = null;
    }
    if (inputSurface != null) {
      inputSurface.release();
      inputSurface = null;
    }
    runningInstance = null;

    if (stopHung) {
      codecErrors++;
      if (errorCallback != null) {
        Logging.e(TAG, "Invoke codec error callback. Errors: " + codecErrors);
        errorCallback.onMediaCodecVideoEncoderCriticalError(codecErrors);
      }
      throw new RuntimeException("Media encoder release timeout.");
    }

    // Re-throw any runtime exception caught inside the other thread. Since this is an invoke, add
    // stack trace for the waiting thread as well.
    if (caughtException.e != null) {
      final RuntimeException runtimeException = new RuntimeException(caughtException.e);
      runtimeException.setStackTrace(ThreadUtils.concatStackTraces(
          caughtException.e.getStackTrace(), runtimeException.getStackTrace()));
      throw runtimeException;
    }

    Logging.d(TAG, "Java releaseEncoder done");
  }

  private boolean setRates(int kbps, int frameRate) {
    checkOnMediaCodecThread();

    int codecBitrateBps = 1000 * kbps;
    if (bitrateAdjustmentType == BitrateAdjustmentType.DYNAMIC_ADJUSTMENT) {
      bitrateAccumulatorMax = codecBitrateBps / 8.0;
      if (targetBitrateBps > 0 && codecBitrateBps < targetBitrateBps) {
        // Rescale the accumulator level if the accumulator max decreases
        bitrateAccumulator = bitrateAccumulator * codecBitrateBps / targetBitrateBps;
      }
    }
    targetBitrateBps = codecBitrateBps;
    targetFps = frameRate;

    // Adjust actual encoder bitrate based on bitrate adjustment type.
    if (bitrateAdjustmentType == BitrateAdjustmentType.FRAMERATE_ADJUSTMENT && targetFps > 0) {
      codecBitrateBps = BITRATE_ADJUSTMENT_FPS * targetBitrateBps / targetFps;
      Logging.v(TAG,
          "setRates: " + kbps + " -> " + (codecBitrateBps / 1000) + " kbps. Fps: " + targetFps);
    } else if (bitrateAdjustmentType == BitrateAdjustmentType.DYNAMIC_ADJUSTMENT) {
      Logging.v(TAG, "setRates: " + kbps + " kbps. Fps: " + targetFps + ". ExpScale: "
              + bitrateAdjustmentScaleExp);
      if (bitrateAdjustmentScaleExp != 0) {
        codecBitrateBps = (int) (codecBitrateBps * getBitrateScale(bitrateAdjustmentScaleExp));
      }
    } else {
      Logging.v(TAG, "setRates: " + kbps + " kbps. Fps: " + targetFps);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      try {
        Bundle params = new Bundle();
        params.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, codecBitrateBps);
        mediaCodec.setParameters(params);
        return true;
      } catch (IllegalStateException e) {
        Logging.e(TAG, "setRates failed", e);
        return false;
      }
    } else {
      return false;
    }
  }

  // Dequeue an input buffer and return its index, -1 if no input buffer is
  // available, or -2 if the codec is no longer operative.
  int dequeueInputBuffer() {
    checkOnMediaCodecThread();
    try {
      return mediaCodec.dequeueInputBuffer(DEQUEUE_TIMEOUT);
    } catch (IllegalStateException e) {
      Logging.e(TAG, "dequeueIntputBuffer failed", e);
      return -2;
    }
  }

  // Helper struct for dequeueOutputBuffer() below.
  public static class OutputBufferInfo {
    private int index;
    private ByteBuffer buffer;
    private int size;
    private boolean isKeyFrame;
    private long presentationTimestampUs;

    private OutputBufferInfo fill(
        int index, ByteBuffer buffer, int size, boolean isKeyFrame, long presentationTimestampUs) {
      this.index = index;
      this.buffer = buffer;
      this.size = size;
      this.isKeyFrame = isKeyFrame;
      this.presentationTimestampUs = presentationTimestampUs;

      return this;
    }

    public int index() {
      return index;
    }

    public ByteBuffer buffer() {
      return buffer;
    }

    public int size() {
      return size;
    }

    public boolean isKeyFrame() {
      return isKeyFrame;
    }

    public long presentationTimestampUs() {
      return presentationTimestampUs;
    }
  }

  private Thread createOutputThread() {
    return new Thread() {
      @Override
      public void run() {
        while (running) {
          deliverEncodedImage();
        }
      }
    };
  }

  private void deliverEncodedImage() {
    try {
      int index = mediaCodec.dequeueOutputBuffer(outputBufferInfo,
              OUTPUT_THREAD_DEQUEUE_TIMEOUT_US);
      if (index < 0) {
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          callback.onOutputFormatChanged(mediaCodec, mediaCodec.getOutputFormat());
        }
        return;
      }

      ByteBuffer codecOutputBuffer = mediaCodec.getOutputBuffers()[index];
      codecOutputBuffer.position(outputBufferInfo.offset);
      codecOutputBuffer.limit(outputBufferInfo.offset + outputBufferInfo.size);

      if ((outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
        Logging.d(TAG, "Config frame generated. Offset: " + outputBufferInfo.offset
                       + ". Size: " + outputBufferInfo.size);
        configData = ByteBuffer.allocateDirect(outputBufferInfo.size);
        configData.put(codecOutputBuffer);
        // Log few SPS header bytes to check profile and level.
        String spsData = "";
        for (int i = 0; i < (outputBufferInfo.size < 8 ? outputBufferInfo.size : 8); i++) {
          spsData += Integer.toHexString(configData.get(i) & 0xff) + " ";
        }
        Logging.d(TAG, spsData);
      } else {
        reportEncodedFrame(outputBufferInfo.size);

        // Check key frame flag.
        boolean isKeyFrame = (outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_SYNC_FRAME) != 0;
        if (isKeyFrame && type == VideoCodecType.VIDEO_CODEC_H264) {
          // For H.264 key frame append SPS and PPS NALs at the start
          if (keyFrameData.capacity() < configData.capacity() + outputBufferInfo.size) {
            // allocate double size
            keyFrameData = ByteBuffer.allocateDirect(keyFrameData.capacity() * 2);
          }
          keyFrameData.position(0);
          configData.rewind();
          keyFrameData.put(configData);
          keyFrameData.put(codecOutputBuffer);
          keyFrameData.position(0);
          outputFrame.fill(index, keyFrameData, configData.capacity() + outputBufferInfo.size,
                  isKeyFrame, outputBufferInfo.presentationTimeUs);
          callback.onEncodedFrame(outputFrame, outputBufferInfo);
          releaseOutputBuffer(index);
        } else {
          outputFrame.fill(index, codecOutputBuffer, outputBufferInfo.size, isKeyFrame,
                  outputBufferInfo.presentationTimeUs);
          callback.onEncodedFrame(outputFrame, outputBufferInfo);
          releaseOutputBuffer(index);
        }
      }
    } catch (IllegalStateException e) {
      Logging.e(TAG, "deliverOutput failed", e);
    }
  }

  private double getBitrateScale(int bitrateAdjustmentScaleExp) {
    return Math.pow(BITRATE_CORRECTION_MAX_SCALE,
        (double) bitrateAdjustmentScaleExp / BITRATE_CORRECTION_STEPS);
  }

  private void reportEncodedFrame(int size) {
    if (targetFps == 0 || bitrateAdjustmentType != BitrateAdjustmentType.DYNAMIC_ADJUSTMENT) {
      return;
    }

    // Accumulate the difference between actial and expected frame sizes.
    double expectedBytesPerFrame = targetBitrateBps / (8.0 * targetFps);
    bitrateAccumulator += (size - expectedBytesPerFrame);
    bitrateObservationTimeMs += 1000.0 / targetFps;

    // Put a cap on the accumulator, i.e., don't let it grow beyond some level to avoid
    // using too old data for bitrate adjustment.
    double bitrateAccumulatorCap = BITRATE_CORRECTION_SEC * bitrateAccumulatorMax;
    bitrateAccumulator = Math.min(bitrateAccumulator, bitrateAccumulatorCap);
    bitrateAccumulator = Math.max(bitrateAccumulator, -bitrateAccumulatorCap);

    // Do bitrate adjustment every 3 seconds if actual encoder bitrate deviates too much
    // form the target value.
    if (bitrateObservationTimeMs > 1000 * BITRATE_CORRECTION_SEC) {
      Logging.d(TAG, "Acc: " + (int) bitrateAccumulator + ". Max: " + (int) bitrateAccumulatorMax
              + ". ExpScale: " + bitrateAdjustmentScaleExp);
      boolean bitrateAdjustmentScaleChanged = false;
      if (bitrateAccumulator > bitrateAccumulatorMax) {
        // Encoder generates too high bitrate - need to reduce the scale.
        int bitrateAdjustmentInc = (int) (bitrateAccumulator / bitrateAccumulatorMax + 0.5);
        bitrateAdjustmentScaleExp -= bitrateAdjustmentInc;
        bitrateAccumulator = bitrateAccumulatorMax;
        bitrateAdjustmentScaleChanged = true;
      } else if (bitrateAccumulator < -bitrateAccumulatorMax) {
        // Encoder generates too low bitrate - need to increase the scale.
        int bitrateAdjustmentInc = (int) (-bitrateAccumulator / bitrateAccumulatorMax + 0.5);
        bitrateAdjustmentScaleExp += bitrateAdjustmentInc;
        bitrateAccumulator = -bitrateAccumulatorMax;
        bitrateAdjustmentScaleChanged = true;
      }
      if (bitrateAdjustmentScaleChanged) {
        bitrateAdjustmentScaleExp = Math.min(bitrateAdjustmentScaleExp, BITRATE_CORRECTION_STEPS);
        bitrateAdjustmentScaleExp = Math.max(bitrateAdjustmentScaleExp, -BITRATE_CORRECTION_STEPS);
        Logging.d(TAG, "Adjusting bitrate scale to " + bitrateAdjustmentScaleExp + ". Value: "
                + getBitrateScale(bitrateAdjustmentScaleExp));
        setRates(targetBitrateBps / 1000, targetFps);
      }
      bitrateObservationTimeMs = 0;
    }
  }

  // Release a dequeued output buffer back to the codec for re-use.  Return
  // false if the codec is no longer operable.
  private boolean releaseOutputBuffer(int index) {
    try {
      mediaCodec.releaseOutputBuffer(index, false);
      return true;
    } catch (IllegalStateException e) {
      Logging.e(TAG, "releaseOutputBuffer failed", e);
      return false;
    }
  }
}
