package br.com.thomasgreg.kotlinraexample.EasyAr

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import cn.easyar.Engine
import javax.microedition.khronos.egl.*
import javax.microedition.khronos.opengles.GL10

class GLView(context: Context) : GLSurfaceView(context){

    var lock:Any = Any()
    var finishing:Boolean = false
    var initialized:Boolean = false
    var widthInitCameraAr = 1
    var HeightInitCameraAr = 1

    var cameraAr:CameraAR? = null



    init{
        setEGLContextFactory(ContextFactory())
        setEGLWindowSurfaceFactory(WindowSurfaceFactory{
            onSurfaceDestroyed()
        })
        setEGLConfigChooser(ConfigChooser())

        setRenderer(object : Renderer {
            override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
                if (!initialized) {
                    initialized = true
                    cameraAr = CameraAR()
                    cameraAr?.initialize()
                } else {
                    cameraAr?.recreate_context()
                }
                cameraAr?.start()
            }

            override fun onSurfaceChanged(gl: GL10, w: Int, h: Int) {
                widthInitCameraAr = w
                HeightInitCameraAr = h
            }

            override fun onDrawFrame(gl: GL10) {
                if (!initialized) {
                    return
                }
                cameraAr?.render(width, height, GetScreenRotation())
            }
        })

    }

    private fun GetScreenRotation(): Int {
        val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        val orientation: Int
        orientation = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return orientation
    }

    private fun getActivity(): Activity? {
        var context = context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

    fun onSurfaceDestroyed() {
        var b: Boolean
        synchronized(lock) { b = finishing }
        if (initialized && b) {
            initialized = false
            cameraAr?.stop()
            cameraAr?.dispose()
            cameraAr = null
        }
    }

    override fun onResume() {
        super.onResume()
        Engine.onResume()
    }

    override fun onPause() {
        val a: Activity? = getActivity()
        if (a != null) {
            val b = a.isFinishing
            synchronized(lock) { finishing = b }
        }
        Engine.onPause()
        super.onPause()
    }

    private class ContextFactory : EGLContextFactory {
        override fun createContext(egl: EGL10, display: EGLDisplay, eglConfig: EGLConfig): EGLContext {
            val context: EGLContext
            val attrib = intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE)
            context = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attrib)
            return context
        }

        override fun destroyContext(egl: EGL10, display: EGLDisplay, context: EGLContext) {
            egl.eglDestroyContext(display, context)
        }

        companion object {
            private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
        }
    }

    private class WindowSurfaceFactory(var delegate:() -> Unit) : EGLWindowSurfaceFactory {

        override fun createWindowSurface(egl: EGL10, display: EGLDisplay, config: EGLConfig, nativeWindow: Any): EGLSurface {
            var result: EGLSurface? = null
            try {
                result = egl.eglCreateWindowSurface(display, config, nativeWindow, null)
            } catch (e: IllegalArgumentException) {
                // This exception indicates that the surface flinger surface
                // is not valid. This can happen if the surface flinger surface has
                // been torn down, but the application has not yet been
                // notified via SurfaceHolder.Callback.surfaceDestroyed.
                // In theory the application should be notified first,
                // but in practice sometimes it is not. See b/4588890
                Log.e("GLSurfaceView", "eglCreateWindowSurface", e)
            }
            return result!!
        }

        override fun destroySurface(egl: EGL10, display: EGLDisplay, surface: EGLSurface) {
            delegate()
            egl.eglDestroySurface(display, surface)
        }
    }

    private class ConfigChooser : EGLConfigChooser {

        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val EGL_OPENGL_ES2_BIT = 0x0004

            val attrib = intArrayOf(EGL10.EGL_RED_SIZE, 4, EGL10.EGL_GREEN_SIZE, 4, EGL10.EGL_BLUE_SIZE, 4,
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL10.EGL_NONE)

            val num_config = IntArray(1)
            egl.eglChooseConfig(display, attrib, null, 0, num_config)

            val numConfigs = num_config[0]
            require(numConfigs > 0) { "fail to choose EGL configs" }

            val configs = arrayOfNulls<EGLConfig>(numConfigs)
            egl.eglChooseConfig(display, attrib, configs, numConfigs, num_config)

            for (config in configs) {
                val `val` = IntArray(1)
                var r = 0
                var g = 0
                var b = 0
                var a = 0
                var d = 0
                if (egl.eglGetConfigAttrib(display, config, EGL10.EGL_DEPTH_SIZE, `val`)) d = `val`[0]
                if (d < 16) continue
                if (egl.eglGetConfigAttrib(display, config, EGL10.EGL_RED_SIZE, `val`)) r = `val`[0]
                if (egl.eglGetConfigAttrib(display, config, EGL10.EGL_GREEN_SIZE, `val`)) g = `val`[0]
                if (egl.eglGetConfigAttrib(display, config, EGL10.EGL_BLUE_SIZE, `val`)) b = `val`[0]
                if (egl.eglGetConfigAttrib(display, config, EGL10.EGL_ALPHA_SIZE, `val`)) a = `val`[0]
                if (r == 8 && g == 8 && b == 8 && a == 0)
                    return config!!
            }

            return configs[0]!!
        }
    }

}