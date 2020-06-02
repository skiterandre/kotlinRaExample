package br.com.thomasgreg.kotlinraexample.EasyAr

import android.util.Log
import cn.easyar.*

class ARVideo {
    private var player: VideoPlayer? = null
    private var prepared = false
    private var found = false
    private var path: String? = null

    constructor(){
        player = VideoPlayer()
        prepared = false
        found = false
    }

    fun dispose() {
        player!!.close()
    }

    fun openVideoFile(path: String?, texid: Int, scheduler: DelayedCallbackScheduler?) {
        this.path = path
        player!!.setRenderTexture(TextureId.fromInt(texid))
        player!!.setVideoType(VideoType.Normal)
        player!!.open(path!!, StorageType.Assets, scheduler!!) { status -> setVideoStatus(status) }
    }

    fun openTransparentVideoFile(path: String?, texid: Int, scheduler: DelayedCallbackScheduler?) {
        this.path = path
        player!!.setRenderTexture(TextureId.fromInt(texid))
        player!!.setVideoType(VideoType.TransparentSideBySide)
        player!!.open(path!!, StorageType.Assets, scheduler!!) { status -> setVideoStatus(status) }
    }

    fun openStreamingVideo(url: String?, texid: Int, scheduler: DelayedCallbackScheduler?) {
        path = url
        player!!.setRenderTexture(TextureId.fromInt(texid))
        player!!.setVideoType(VideoType.Normal)
        player!!.open(url!!, StorageType.Absolute, scheduler!!) { status -> setVideoStatus(status) }
    }

    fun setVideoStatus(status: Int) {
        Log.i("HelloAR", "video: " + path + " (" + Integer.toString(status) + ")")
        if (status == VideoStatus.Ready) {
            prepared = true
            if (found) {
                player!!.play()
            }
        } else if (status == VideoStatus.Completed) {
            if (found) {
                player!!.play()
            }
        }
    }

    fun onFound() {
        found = true
        if (prepared) {
            player!!.play()
        }
    }

    fun onLost() {
        found = false
        if (prepared) {
            player!!.pause()
        }
    }

    fun isRenderTextureAvailable(): Boolean {
        return player!!.isRenderTextureAvailable
    }

    fun update() {
        player!!.updateFrame()
    }
}