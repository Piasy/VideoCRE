#!/usr/bin/env bash

UPSTREAM=/Users/piasy/src/CameraApps/AppRTC-Android/libjingle_peerconnection/src/main/java/org/webrtc
DST=/Users/piasy/src/CameraApps/VideoSource/VideoSource/src/main/java/org/webrtc

cp /Users/piasy/src/CameraApps/AppRTC-Android/base_java/src/main/java/org/webrtc/Logging.java ${DST}/
cp /Users/piasy/src/CameraApps/AppRTC-Android/base_java/src/main/java/org/webrtc/Size.java ${DST}/
cp /Users/piasy/src/CameraApps/AppRTC-Android/base_java/src/main/java/org/webrtc/ThreadUtils.java ${DST}/

cp ${UPSTREAM}/BaseBitrateAdjuster.java ${DST}/
cp ${UPSTREAM}/BitrateAdjuster.java ${DST}/
cp ${UPSTREAM}/Camera1Capturer.java ${DST}/
cp ${UPSTREAM}/Camera1Enumerator.java ${DST}/
cp ${UPSTREAM}/Camera1Session.java ${DST}/
cp ${UPSTREAM}/Camera2Capturer.java ${DST}/
cp ${UPSTREAM}/Camera2Enumerator.java ${DST}/
cp ${UPSTREAM}/Camera2Session.java ${DST}/
cp ${UPSTREAM}/CameraCapturer.java ${DST}/
cp ${UPSTREAM}/CameraEnumerationAndroid.java ${DST}/
cp ${UPSTREAM}/CameraEnumerator.java ${DST}/
cp ${UPSTREAM}/CameraSession.java ${DST}/
cp ${UPSTREAM}/CameraVideoCapturer.java ${DST}/
cp ${UPSTREAM}/DynamicBitrateAdjuster.java ${DST}/
cp ${UPSTREAM}/EglBase.java ${DST}/
cp ${UPSTREAM}/EglBase10.java ${DST}/
cp ${UPSTREAM}/EglBase14.java ${DST}/
cp ${UPSTREAM}/EglRenderer.java ${DST}/
cp ${UPSTREAM}/EncodedImage.java ${DST}/
cp ${UPSTREAM}/FileVideoCapturer.java ${DST}/
cp ${UPSTREAM}/FramerateBitrateAdjuster.java ${DST}/
cp ${UPSTREAM}/GlRectDrawer.java ${DST}/
cp ${UPSTREAM}/GlShader.java ${DST}/
cp ${UPSTREAM}/GlTextureFrameBuffer.java ${DST}/
cp ${UPSTREAM}/GlUtil.java ${DST}/
cp ${UPSTREAM}/GlUtil.java ${DST}/
cp ${UPSTREAM}/HardwareVideoEncoder.java ${DST}/
cp ${UPSTREAM}/HardwareVideoEncoderFactory.java ${DST}/
cp ${UPSTREAM}/Histogram.java ${DST}/
cp ${UPSTREAM}/I420BufferImpl.java ${DST}/
cp ${UPSTREAM}/I420BufferImpl.java ${DST}/
cp ${UPSTREAM}/MediaCodecUtils.java ${DST}/
cp ${UPSTREAM}/MediaCodecVideoEncoder.java ${DST}/
cp ${UPSTREAM}/RendererCommon.java ${DST}/
cp ${UPSTREAM}/ScreenCapturerAndroid.java ${DST}/
cp ${UPSTREAM}/SurfaceTextureHelper.java ${DST}/
cp ${UPSTREAM}/SurfaceViewRenderer.java ${DST}/
cp ${UPSTREAM}/VideoCapturer.java ${DST}/
cp ${UPSTREAM}/VideoCodecInfo.java ${DST}/
cp ${UPSTREAM}/VideoCodecStatus.java ${DST}/
cp ${UPSTREAM}/VideoCodecType.java ${DST}/
cp ${UPSTREAM}/VideoEncoder.java ${DST}/
cp ${UPSTREAM}/VideoEncoderFactory.java ${DST}/
cp ${UPSTREAM}/VideoFrame.java ${DST}/
cp ${UPSTREAM}/YuvConverter.java ${DST}/
