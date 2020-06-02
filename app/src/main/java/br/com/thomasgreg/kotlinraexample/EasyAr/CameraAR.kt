package br.com.thomasgreg.kotlinraexample.EasyAr

import android.opengl.GLES20
import android.util.Log
import cn.easyar.*
import java.nio.ByteBuffer
import java.util.*

class CameraAR  {
    private var scheduler: DelayedCallbackScheduler? = null
    private var camera: CameraDevice? = null
    private var trackers: ArrayList<ImageTracker>? = null
    private var bgRenderer: BGRenderer? = null
    private var video_renderers: ArrayList<VideoRenderer>? = null
    private var current_video_renderer: VideoRenderer? = null
    private var tracked_target = 0
    private var active_target = 0
    private var video: ARVideo? = null

    private var throttler: InputFrameThrottler? = null
    private var feedbackFrameFork: FeedbackFrameFork? = null
    private var i2OAdapter: InputFrameToOutputFrameAdapter? = null
    private var inputFrameFork: InputFrameFork? = null
    private var join: OutputFrameJoin? = null
    private var oFrameBuffer: OutputFrameBuffer? = null
    private var i2FAdapter: InputFrameToFeedbackFrameAdapter? = null
    private var outputFrameFork: OutputFrameFork? = null
    private var previousInputFrameIndex = -1
    private var imageBuffer: ByteArray? = null

    constructor() {
        scheduler = DelayedCallbackScheduler()
        trackers = ArrayList()
    }

    private fun loadFromImage(tracker: ImageTracker, path: String, name: String) {

        val target = ImageTarget.createFromImageFile(path, 1, name, "", "", 1.0f)

        if (target == null) {
            Log.e("HelloAR", "target create failed or key is not correct")
            return
        }

        tracker.loadTarget(target,scheduler!!){
            target, status ->
                Log.i("HelloAR", String.format("load target (%b): %s (%d)", status, target.name(), target.runtimeID()))
        }

    }

    fun recreate_context() {
        if (active_target != 0) {
            video!!.onLost()
            video!!.dispose()
            video = null
            tracked_target = 0
            active_target = 0
        }
        if (bgRenderer != null) {
            bgRenderer!!.dispose()
            bgRenderer = null
        }
        if (video_renderers != null) {
            for (video_renderer in video_renderers!!) {
                video_renderer.dispose()
            }
            video_renderers = null
        }
        current_video_renderer = null
        previousInputFrameIndex = -1
        bgRenderer = BGRenderer()
        video_renderers = ArrayList()
        var k = 0
        while (k < 3) {
            val video_renderer = VideoRenderer()
            video_renderers!!.add(video_renderer)
            k += 1
        }
    }

    fun initialize() {
        recreate_context()
        camera = CameraDeviceSelector.createCameraDevice(CameraDevicePreference.PreferObjectSensing)
        throttler = InputFrameThrottler.create()
        inputFrameFork = InputFrameFork.create(2)
        join = OutputFrameJoin.create(2)
        oFrameBuffer = OutputFrameBuffer.create()
        i2OAdapter = InputFrameToOutputFrameAdapter.create()
        i2FAdapter = InputFrameToFeedbackFrameAdapter.create()
        outputFrameFork = OutputFrameFork.create(2)
        var status = true
        status = status and camera!!.openWithType(CameraDeviceType.Default)
        camera!!.setSize(Vec2I(1280, 720))
        camera!!.setFocusMode(CameraDeviceFocusMode.Continousauto)

        if (!status) {
            return
        }

        val tracker = ImageTracker.create()

        loadFromImage(tracker, "argame00.jpg", "argame")
        loadFromImage(tracker, "namecard.jpg", "namecard")
        loadFromImage(tracker, "idback.jpg", "idback")

        trackers!!.add(tracker)

        feedbackFrameFork = FeedbackFrameFork.create(trackers!!.size)
        camera!!.inputFrameSource().connect(throttler!!.input())
        throttler!!.output().connect(inputFrameFork!!.input())
        inputFrameFork!!.output(0).connect(i2OAdapter!!.input())
        i2OAdapter!!.output().connect(join!!.input(0))
        inputFrameFork!!.output(1).connect(i2FAdapter!!.input())
        i2FAdapter!!.output().connect(feedbackFrameFork!!.input())
        var k = 0
        for (_tracker in trackers!!) {
            feedbackFrameFork!!.output(k).connect(_tracker.feedbackFrameSink())
            _tracker.outputFrameSource().connect(join!!.input(k + 1))
            k++
        }
        join!!.output().connect(outputFrameFork!!.input())
        outputFrameFork!!.output(0).connect(oFrameBuffer!!.input())
        outputFrameFork!!.output(1).connect(i2FAdapter!!.sideInput())
        oFrameBuffer!!.signalOutput().connect(throttler!!.signalInput())
    }

    fun dispose() {
        if (video != null) {
            video!!.dispose()
            video = null
        }
        tracked_target = 0
        active_target = 0
        for (tracker in trackers!!) {
            tracker.dispose()
        }
        trackers!!.clear()
        if (video_renderers != null) {
            for (video_renderer in video_renderers!!) {
                video_renderer.dispose()
            }
            video_renderers = null
        }
        current_video_renderer = null
        if (bgRenderer != null) {
            bgRenderer = null
        }
        if (camera != null) {
            camera!!.dispose()
            camera = null
        }
        if (scheduler != null) {
            scheduler!!.dispose()
            scheduler = null
        }
    }

    fun start(): Boolean {
        var status = true
        status = if (camera != null) {
            status and camera!!.start()
        } else {
            false
        }
        for (tracker in trackers!!) {
            status = status and tracker.start()
        }
        return status
    }

    fun stop() {
        if (camera != null) {
            camera!!.stop()
        }
        for (tracker in trackers!!) {
            tracker.stop()
        }
    }

    fun render(width: Int, height: Int, screenRotation: Int) {
        while (scheduler!!.runOne()) {
        }
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val oframe = oFrameBuffer!!.peek() ?: return
        val iframe = oframe.inputFrame()
        if (iframe == null) {
            oframe.dispose()
            return
        }
        val cameraParameters = iframe.cameraParameters()
        if (cameraParameters == null) {
            oframe.dispose()
            iframe.dispose()
            return
        }
        val viewport_aspect_ratio = width.toFloat() / height.toFloat()
        val imageProjection = cameraParameters.imageProjection(viewport_aspect_ratio, screenRotation, true, false)
        val image = iframe.image()
        try {
            if (iframe.index() != previousInputFrameIndex) {
                val buffer = image.buffer()
                try {
                    if (imageBuffer == null || imageBuffer!!.size != buffer.size()) {
                        imageBuffer = ByteArray(buffer.size())
                    }
                    buffer.copyToByteArray(imageBuffer!!)
                    bgRenderer!!.upload(image.format(), image.width(), image.height(), ByteBuffer.wrap(imageBuffer))
                } finally {
                    buffer.dispose()
                }
                previousInputFrameIndex = iframe.index()
            }
            bgRenderer!!.render(imageProjection)
            val projectionMatrix = cameraParameters.projection(0.01f, 1000f, viewport_aspect_ratio, screenRotation, true, false)
            for (oResult in oframe.results()) {
                if (oResult is ImageTrackerResult) {
                    val targetInstances = oResult.targetInstances()
                    for (targetInstance in targetInstances) {
                        if (targetInstance.status() == TargetStatus.Tracked) {
                            val target = targetInstance.target()
                            val id = target!!.runtimeID()
                            if (active_target != 0 && active_target != id) {
                                video!!.onLost()
                                video!!.dispose()
                                video = null
                                tracked_target = 0
                                active_target = 0
                            }
                            if (tracked_target == 0) {
                                if (video == null && video_renderers!!.size > 0) {
                                    val target_name = target.name()
                                    if (target_name == "argame" && video_renderers!![0].texId() !== 0) {
                                        video = ARVideo()
                                        video!!.openVideoFile("video.mp4", video_renderers!![0].texId(), scheduler)
                                        current_video_renderer = video_renderers!![0]
                                    } else if (target_name == "namecard" && video_renderers!![1].texId() !== 0) {
                                        video = ARVideo()
                                        video!!.openTransparentVideoFile("transparentvideo.mp4", video_renderers!![1].texId(), scheduler)
                                        current_video_renderer = video_renderers!![1]
                                    } else if (target_name == "idback" && video_renderers!![2].texId() !== 0) {
                                        video = ARVideo()
                                        video!!.openStreamingVideo("https://sightpvideo-cdn.sightp.com/sdkvideo/EasyARSDKShow201520.mp4", video_renderers!![2].texId(), scheduler)
                                        current_video_renderer = video_renderers!![2]
                                    }
                                }
                                if (video != null) {
                                    video!!.onFound()
                                    tracked_target = id
                                    active_target = id
                                }
                            }
                            val imagetarget = if (target is ImageTarget) target else null
                            if (imagetarget != null) {
                                if (current_video_renderer != null) {
                                    video!!.update()
                                    val images = (target as ImageTarget?)!!.images()
                                    val targetImg = images[0]
                                    val targetScale = imagetarget.scale()
                                    val scale = Vec2F(targetScale, targetScale * targetImg.height() / targetImg.width())
                                    if (video!!.isRenderTextureAvailable()) {
                                        current_video_renderer!!.render(projectionMatrix, targetInstance.pose(), scale)
                                    }
                                }
                            }
                            target.dispose()
                        }
                        targetInstance.dispose()
                    }
                    if (targetInstances.size == 0) {
                        if (tracked_target != 0) {
                            video!!.onLost()
                            tracked_target = 0
                        }
                    }
                }
                oResult?.dispose()
            }
        } finally {
            iframe.dispose()
            oframe.dispose()
            cameraParameters?.dispose()
            image.dispose()
        }
    }

}