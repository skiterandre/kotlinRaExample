package br.com.thomasgreg.kotlinraexample.EasyAr

import android.opengl.GLES20
import android.util.Log
import cn.easyar.Matrix44F
import cn.easyar.PixelFormat
import cn.easyar.Vec2I
import cn.easyar.Vec4F
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLContext

class BGRenderer {
    private val videobackground_vert = """attribute vec4 coord;
attribute vec2 texCoord;
varying vec2 texc;

void main(void)
{
    gl_Position = coord;
    texc = texCoord;
}

"""
    private val videobackground_bgr_frag = """#ifdef GL_ES
precision mediump float;
#endif
uniform sampler2D texture;
varying vec2 texc;

void main(void)
{
    gl_FragColor = texture2D(texture, texc).bgra;
}

"""
    private val videobackground_rgb_frag = """#ifdef GL_ES
precision mediump float;
#endif
uniform sampler2D texture;
varying vec2 texc;

void main(void)
{
    gl_FragColor = texture2D(texture, texc);
}

"""
    private val videobackground_yuv_i420_yv12_frag = """#ifdef GL_ES
precision highp float;
#endif
uniform sampler2D texture;
uniform sampler2D u_texture;
uniform sampler2D v_texture;
varying vec2 texc;

void main(void)
{
    float cb = texture2D(u_texture, texc).r - 0.5;
    float cr = texture2D(v_texture, texc).r - 0.5;
    vec3 ycbcr = vec3(texture2D(texture, texc).r, cb, cr);
    vec3 rgb = mat3(1, 1, 1,
        0, -0.344, 1.772,
        1.402, -0.714, 0) * ycbcr;
    gl_FragColor = vec4(rgb, 1.0);
}

"""
    private val videobackground_yuv_nv12_frag = """#ifdef GL_ES
precision highp float;
#endif
uniform sampler2D texture;
uniform sampler2D uv_texture;
varying vec2 texc;

void main(void)
{
    vec2 cbcr = texture2D(uv_texture, texc).ra - vec2(0.5, 0.5);
    vec3 ycbcr = vec3(texture2D(texture, texc).r, cbcr);
    vec3 rgb = mat3(1.0, 1.0, 1.0,
        0.0, -0.344, 1.772,
        1.402, -0.714, 0.0) * ycbcr;
    gl_FragColor = vec4(rgb, 1.0);
}

"""
    private val videobackground_yuv_nv21_frag = """#ifdef GL_ES
precision highp float;
#endif
uniform sampler2D texture;
uniform sampler2D uv_texture;
varying vec2 texc;

void main(void)
{
    vec2 cbcr = texture2D(uv_texture, texc).ar - vec2(0.5, 0.5);
    vec3 ycbcr = vec3(texture2D(texture, texc).r, cbcr);
    vec3 rgb = mat3(1, 1, 1,
        0, -0.344, 1.772,
        1.402, -0.714, 0.0) * ycbcr;
    gl_FragColor = vec4(rgb, 1.0);
}

"""

    private val yuv_back = byteArrayOf(
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
            127, 127,
            127, 127,
            127, 127,
            127, 127)

    internal enum class FrameShader {
        RGB, YUV
    }

    private var background_shader_ = FrameShader.RGB

    private var initialized_ = false
    private var current_context_: EGLContext? = null
    private var background_program_ = 0
    private val background_texture_id_ = IntBuffer.allocate(1)
    private val background_texture_uv_id_ = IntBuffer.allocate(1)
    private val background_texture_u_id_ = IntBuffer.allocate(1)
    private val background_texture_v_id_ = IntBuffer.allocate(1)
    private var background_coord_location_ = -1
    private var background_texture_location_ = -1
    private val background_coord_vbo_ = IntBuffer.allocate(1)
    private val background_texture_vbo_ = IntBuffer.allocate(1)
    private val background_texture_fbo_ = IntBuffer.allocate(1)

    private var current_format_ = PixelFormat.Unknown
    private var current_image_size_: Vec2I? = null

    private fun mul(mat: Matrix44F, vec: Vec4F): Vec4F {
        val `val` = Vec4F(0F, 0F, 0F, 0F)
        for (i in 0..3) {
            for (k in 0..3) {
                `val`.data[i] += mat.data[i * 4 + k] * vec.data[k]
            }
        }
        return `val`
    }

    fun dispose() {
        finalize(current_format_)
    }

    fun upload(format: Int, width: Int, height: Int, buffer: ByteBuffer) {

        val bak_tex = IntBuffer.allocate(1)
        val bak_program = IntBuffer.allocate(1)
        val bak_active_tex = IntBuffer.allocate(1)
        val bak_tex_1 = IntBuffer.allocate(1)
        val bak_tex_2 = IntBuffer.allocate(1)

        GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, bak_program)
        GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE, bak_active_tex)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, bak_tex)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, bak_tex_1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, bak_tex_2)

        try {
            if (current_format_ != format) {
                finalize(current_format_)
                if (!initialize(format)) {
                    return
                }
                current_format_ = format
            }
            current_image_size_ = Vec2I(width, height)
            when (background_shader_) {
                FrameShader.RGB -> {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, background_texture_id_[0])
                    retrieveFrame(format, width, height, buffer, 0)
                }
                FrameShader.YUV -> {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, background_texture_id_[0])
                    retrieveFrame(format, width, height, buffer, 0)
                    if (format == PixelFormat.YUV_NV21 || format == PixelFormat.YUV_NV12) {
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, background_texture_uv_id_[0])
                        retrieveFrame(format, width, height, buffer, 1)
                    } else {
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, background_texture_u_id_[0])
                        retrieveFrame(format, width, height, buffer, 1)
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, background_texture_v_id_[0])
                        retrieveFrame(format, width, height, buffer, 2)
                    }
                }
            }
        } finally {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bak_tex[0])
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bak_tex_1[0])
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bak_tex_2[0])
            GLES20.glActiveTexture(bak_active_tex[0])
            GLES20.glUseProgram(bak_program[0])
        }
    }

    fun render(imageProjection: Matrix44F) {

        val bak_blend = IntBuffer.allocate(1)
        val bak_depth = IntBuffer.allocate(1)
        val bak_fbo = IntBuffer.allocate(1)
        val bak_tex = IntBuffer.allocate(1)
        val bak_arr_buf = IntBuffer.allocate(1)
        val bak_ele_arr_buf = IntBuffer.allocate(1)
        val bak_cull = IntBuffer.allocate(1)
        val bak_program = IntBuffer.allocate(1)
        val bak_active_tex = IntBuffer.allocate(1)
        val bak_tex_1 = IntBuffer.allocate(1)
        val bak_tex_2 = IntBuffer.allocate(1)
        val bak_viewport = IntBuffer.allocate(4)

        GLES20.glGetIntegerv(GLES20.GL_BLEND, bak_blend)
        GLES20.glGetIntegerv(GLES20.GL_DEPTH_TEST, bak_depth)
        GLES20.glGetIntegerv(GLES20.GL_CULL_FACE, bak_cull)
        GLES20.glGetIntegerv(GLES20.GL_ARRAY_BUFFER_BINDING, bak_arr_buf)
        GLES20.glGetIntegerv(GLES20.GL_ELEMENT_ARRAY_BUFFER_BINDING, bak_ele_arr_buf)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, bak_fbo)
        bak_viewport.position(0)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, bak_viewport)
        GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, bak_program)
        GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE, bak_active_tex)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, bak_tex)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, bak_tex_1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, bak_tex_2)

        val va = intArrayOf(-1, -1)

        val bak_va_binding = arrayOf(IntBuffer.allocate(1), IntBuffer.allocate(1))
        val bak_va_enable = arrayOf(IntBuffer.allocate(1), IntBuffer.allocate(1))
        val bak_va_size = arrayOf(IntBuffer.allocate(1), IntBuffer.allocate(1))
        val bak_va_stride = arrayOf(IntBuffer.allocate(1), IntBuffer.allocate(1))
        val bak_va_type = arrayOf(IntBuffer.allocate(1), IntBuffer.allocate(1))
        val bak_va_norm = arrayOf(IntBuffer.allocate(1), IntBuffer.allocate(1))
        val bak_va_pointer = arrayOf(IntBuffer.allocate(1), IntBuffer.allocate(1))

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

        va[0] = background_coord_location_
        va[1] = background_texture_location_

        for (i in 0..1) {
            if (va[i] == -1) continue
            GLES20.glGetVertexAttribiv(va[i], GLES20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING, bak_va_binding[i])
            GLES20.glGetVertexAttribiv(va[i], GLES20.GL_VERTEX_ATTRIB_ARRAY_ENABLED, bak_va_enable[i])
            GLES20.glGetVertexAttribiv(va[i], GLES20.GL_VERTEX_ATTRIB_ARRAY_SIZE, bak_va_size[i])
            GLES20.glGetVertexAttribiv(va[i], GLES20.GL_VERTEX_ATTRIB_ARRAY_STRIDE, bak_va_stride[i])
            GLES20.glGetVertexAttribiv(va[i], GLES20.GL_VERTEX_ATTRIB_ARRAY_TYPE, bak_va_type[i])
            GLES20.glGetVertexAttribiv(va[i], GLES20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED, bak_va_norm[i])
            //GLES20.glGetVertexAttribPointerv(va[i], GLES20.GL_VERTEX_ATTRIB_ARRAY_POINTER,bak_va_pointer[i]);
        }
        try {
            GLES20.glUseProgram(background_program_)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, background_coord_vbo_[0])

            GLES20.glEnableVertexAttribArray(background_coord_location_)
            GLES20.glVertexAttribPointer(background_coord_location_, 3, GLES20.GL_FLOAT, false, 0, 0)

            val vertices = floatArrayOf(
                    -1.0f, -1.0f, 0f,
                    1.0f, -1.0f, 0f,
                    1.0f, 1.0f, 0f,
                    -1.0f, 1.0f, 0f)

            var v0_v4f = Vec4F(vertices[0], vertices[1], vertices[2], 1.0f)
            var v1_v4f = Vec4F(vertices[3], vertices[4], vertices[5], 1.0f)
            var v2_v4f = Vec4F(vertices[6], vertices[7], vertices[8], 1.0f)
            var v3_v4f = Vec4F(vertices[9], vertices[10], vertices[11], 1.0f)

            v0_v4f = mul(imageProjection, v0_v4f)
            v1_v4f = mul(imageProjection, v1_v4f)
            v2_v4f = mul(imageProjection, v2_v4f)
            v3_v4f = mul(imageProjection, v3_v4f)

            val v4f_array = arrayOf(v0_v4f, v1_v4f, v2_v4f, v3_v4f)

            var i = 0
            while (i < 4) {
                var k = 0
                while (k < 3) {
                    vertices[i * 3 + k] = v4f_array[i].data[k]
                    k += 1
                }
                i += 1
            }

            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 4 * 12, FloatBuffer.wrap(vertices), GLES20.GL_DYNAMIC_DRAW)

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, background_texture_vbo_[0])
            GLES20.glEnableVertexAttribArray(background_texture_location_)
            GLES20.glVertexAttribPointer(background_texture_location_, 2, GLES20.GL_FLOAT, false, 0, 0)

            when (background_shader_) {
                FrameShader.RGB -> {

                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, background_texture_id_[0])

                }
                FrameShader.YUV -> {

                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, background_texture_id_[0])

                    if (current_format_ == PixelFormat.YUV_NV21 || current_format_ == PixelFormat.YUV_NV12) {
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, background_texture_uv_id_[0])
                    } else {
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, background_texture_u_id_[0])

                        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, background_texture_v_id_[0])
                    }
                }
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
        } finally {
            if (bak_blend[0] != 0) GLES20.glEnable(GLES20.GL_BLEND)
            if (bak_depth[0] != 0) GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            if (bak_cull[0] != 0) GLES20.glEnable(GLES20.GL_CULL_FACE)

            for (i in 0..1) {
                if (bak_va_binding[i][0] == 0) continue
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bak_va_binding[i][0])
                if (bak_va_enable[i][0] != 0) GLES20.glEnableVertexAttribArray(va[i]) else GLES20.glDisableVertexAttribArray(va[i])
                //bak_va_pointer[i].position(0);
                //GLES20.glVertexAttribPointer(va[i], bak_va_size[i].get(0),bak_va_type[i].get(0), bak_va_norm[i].get(0)==0? false:true, bak_va_stride[i].get(0), bak_va_pointer[i].get(0));
            }

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bak_arr_buf[0])
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, bak_ele_arr_buf[0])
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bak_fbo[0])
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bak_tex[0])
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bak_tex_1[0])
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bak_tex_2[0])
            GLES20.glActiveTexture(bak_active_tex[0])
            GLES20.glViewport(bak_viewport[0], bak_viewport[1], bak_viewport[2], bak_viewport[3])
            GLES20.glUseProgram(bak_program[0])

        }
    }

    private var uv_buffer: ByteArray? = null

    private fun retrieveFrame(format: Int, width: Int, height: Int, buffer: ByteBuffer, retrieve_count: Int) {

        if (retrieve_count == 0) {
            if (width % 4 != 0) {
                if (width % 2 != 0) {
                    GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
                } else {
                    GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 2)
                }
            }

            when (background_shader_) {
                FrameShader.RGB -> when (format) {
                    PixelFormat.Unknown -> GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                    PixelFormat.Gray -> GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width, height, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, buffer)
                    PixelFormat.BGR888 -> GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, width, height, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, buffer)
                    PixelFormat.RGB888 -> GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, width, height, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, buffer)
                    PixelFormat.RGBA8888 -> GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
                    PixelFormat.BGRA8888 -> GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
                    else -> GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                }
                FrameShader.YUV ->
                    if (format == PixelFormat.YUV_NV21 || format == PixelFormat.YUV_NV12 ||
                            format == PixelFormat.YUV_I420 || format == PixelFormat.YUV_YV12) {
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width, height, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, buffer)
                } else {
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, 4, 4, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, ByteBuffer.wrap(yuv_back))
                }
                else -> {
                }
            }
        } else if (retrieve_count == 1 || retrieve_count == 2) {

            if (background_shader_ != FrameShader.YUV) {
                return
            }

            if (width % 8 != 0) {
                if (width % 4 != 0) {
                    GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
                } else {
                    GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 2)
                }
            }

            val type: Int
            val offset: Int
            val size: Int

            if (format == PixelFormat.YUV_NV21 || format == PixelFormat.YUV_NV12) {
                type = GLES20.GL_LUMINANCE_ALPHA
                offset = width * height
                size = width * height / 2
            } else if (format == PixelFormat.YUV_I420) {
                type = GLES20.GL_LUMINANCE
                offset = if (retrieve_count == 1) { //U
                    width * height
                } else if (retrieve_count == 2) { //V
                    width * height * 5 / 4
                } else {
                    throw IllegalStateException()
                }
                size = width * height / 4
            } else if (format == PixelFormat.YUV_YV12) {
                type = GLES20.GL_LUMINANCE
                offset = if (retrieve_count == 1) { //U
                    width * height * 5 / 4
                } else if (retrieve_count == 2) { //V
                    width * height
                } else {
                    throw IllegalStateException()
                }
                size = width * height / 4
            } else {
                throw IllegalStateException()
            }

            if (uv_buffer == null || uv_buffer!!.size != size) {
                uv_buffer = ByteArray(size)
            }

            buffer.position(offset)
            buffer[uv_buffer]
            buffer.position(0)
            //Android bug: GLES20.glTexImage2D crash with buffer with offset, https://issuetracker.google.com/issues/136535675
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, type, width / 2, height / 2, 0, type, GLES20.GL_UNSIGNED_BYTE, ByteBuffer.wrap(uv_buffer))
        }
    }

    private fun initialize(format: Int): Boolean {
        if (format == PixelFormat.Unknown) {
            return false
        }
        current_context_ = (EGLContext.getEGL() as EGL10).eglGetCurrentContext()
        background_program_ = GLES20.glCreateProgram()
        val vertShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
        GLES20.glShaderSource(vertShader, videobackground_vert)
        GLES20.glCompileShader(vertShader)
        val fragShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
        when (format) {
            PixelFormat.Gray, PixelFormat.RGB888, PixelFormat.RGBA8888 -> {
                background_shader_ = FrameShader.RGB
                GLES20.glShaderSource(fragShader, videobackground_rgb_frag)
            }
            PixelFormat.BGR888, PixelFormat.BGRA8888 -> {
                background_shader_ = FrameShader.RGB
                GLES20.glShaderSource(fragShader, videobackground_bgr_frag)
            }
            PixelFormat.YUV_NV21 -> {
                background_shader_ = FrameShader.YUV
                GLES20.glShaderSource(fragShader, videobackground_yuv_nv21_frag)
            }
            PixelFormat.YUV_NV12 -> {
                background_shader_ = FrameShader.YUV
                GLES20.glShaderSource(fragShader, videobackground_yuv_nv12_frag)
            }
            PixelFormat.YUV_I420, PixelFormat.YUV_YV12 -> {
                background_shader_ = FrameShader.YUV
                GLES20.glShaderSource(fragShader, videobackground_yuv_i420_yv12_frag)
            }
            else -> {
            }
        }
        GLES20.glCompileShader(fragShader)
        GLES20.glAttachShader(background_program_, vertShader)
        GLES20.glAttachShader(background_program_, fragShader)
        val compileSuccess_0 = IntBuffer.allocate(1)
        GLES20.glGetShaderiv(vertShader, GLES20.GL_COMPILE_STATUS, compileSuccess_0)
        if (compileSuccess_0[0] == GLES20.GL_FALSE) {
            val messages = GLES20.glGetShaderInfoLog(vertShader)
            Log.v("[easyar]", "vertshader error $messages")
        }
        val compileSuccess_1 = IntBuffer.allocate(1)
        GLES20.glGetShaderiv(fragShader, GLES20.GL_COMPILE_STATUS, compileSuccess_1)
        if (compileSuccess_1[0] == GLES20.GL_FALSE) {
            val messages = GLES20.glGetShaderInfoLog(fragShader)
            Log.v("[easyar]", "vertshader error $messages")
        }
        GLES20.glLinkProgram(background_program_)
        GLES20.glDeleteShader(vertShader)
        GLES20.glDeleteShader(fragShader)
        val linkstatus = IntBuffer.allocate(1)
        GLES20.glGetProgramiv(background_program_, GLES20.GL_LINK_STATUS, linkstatus)
        GLES20.glUseProgram(background_program_)
        background_coord_location_ = GLES20.glGetAttribLocation(background_program_, "coord")
        background_texture_location_ = GLES20.glGetAttribLocation(background_program_, "texCoord")
        GLES20.glDeleteShader(vertShader)
        GLES20.glDeleteShader(fragShader)
        GLES20.glGenBuffers(1, background_coord_vbo_)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, background_coord_vbo_[0])
        val coord = floatArrayOf(-1.0f, -1.0f, 0f, 1.0f, -1.0f, 0f, 1.0f, 1.0f, 0f, -1.0f, 1.0f, 0f)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 12 * 4, FloatBuffer.wrap(coord), GLES20.GL_DYNAMIC_DRAW)
        GLES20.glGenBuffers(1, background_texture_vbo_)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, background_texture_vbo_[0])
        val texcoord = floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f) //input texture data is Y-inverted
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 8 * 4, FloatBuffer.wrap(texcoord), GLES20.GL_STATIC_DRAW)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(background_program_, "texture"), 0)
        GLES20.glGenTextures(1, background_texture_id_)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, background_texture_id_[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        when (background_shader_) {
            FrameShader.RGB -> {
            }
            FrameShader.YUV -> if (format == PixelFormat.YUV_NV21 || format == PixelFormat.YUV_NV12) {
                GLES20.glUniform1i(GLES20.glGetUniformLocation(background_program_, "uv_texture"), 1)
                GLES20.glGenTextures(1, background_texture_uv_id_)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, background_texture_uv_id_[0])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            } else {
                GLES20.glUniform1i(GLES20.glGetUniformLocation(background_program_, "u_texture"), 1)
                GLES20.glGenTextures(1, background_texture_u_id_)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, background_texture_u_id_[0])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(background_program_, "v_texture"), 2)
                GLES20.glGenTextures(1, background_texture_v_id_)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, background_texture_v_id_[0])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            }
            else -> {
            }
        }
        GLES20.glGenFramebuffers(1, background_texture_fbo_)
        initialized_ = true
        return true
    }

    private fun finalize(format: Int) {
        if (!initialized_) {
            return
        }
        if ((EGLContext.getEGL() as EGL10).eglGetCurrentContext() == current_context_) { //destroy resources unless the context has lost
            GLES20.glDeleteProgram(background_program_)
            GLES20.glDeleteBuffers(1, background_coord_vbo_)
            GLES20.glDeleteBuffers(1, background_texture_vbo_)
            GLES20.glDeleteFramebuffers(1, background_texture_fbo_)
            GLES20.glDeleteTextures(1, background_texture_id_)
            when (background_shader_) {
                FrameShader.RGB -> {
                }
                FrameShader.YUV -> if (format == PixelFormat.YUV_NV21 || format == PixelFormat.YUV_NV12) {
                    GLES20.glDeleteTextures(1, background_texture_uv_id_)
                } else {
                    GLES20.glDeleteTextures(1, background_texture_u_id_)
                    GLES20.glDeleteTextures(1, background_texture_v_id_)
                }
                else -> {
                }
            }
        }
        initialized_ = false
    }
}