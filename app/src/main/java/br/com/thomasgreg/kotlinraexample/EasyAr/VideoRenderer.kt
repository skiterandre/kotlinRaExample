package br.com.thomasgreg.kotlinraexample.EasyAr

import android.opengl.GLES20
import cn.easyar.Matrix44F
import cn.easyar.Vec2F
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLContext

class VideoRenderer {

    private var program_box = 0
    private var pos_coord_box = 0
    private var pos_tex_box = 0
    private var pos_trans_box = 0
    private var pos_proj_box = 0
    private var vbo_coord_box = 0
    private var vbo_tex_box = 0
    private var vbo_faces_box = 0
    private var texture_id = 0

    private var current_context: EGLContext? = null

    private val box_vert = """uniform mat4 trans;
                            uniform mat4 proj;
                            attribute vec4 coord;
                            attribute vec2 texcoord;
                            varying vec2 vtexcoord;
                            
                            void main(void)
                            {
                                vtexcoord = texcoord;
                                gl_Position = proj*trans*coord;
                            }
                            
                            """

    private val box_frag = """#ifdef GL_ES
                            precision highp float;
                            #endif
                            varying vec2 vtexcoord;
                            uniform sampler2D texture;
                            
                            void main(void)
                            {
                                gl_FragColor = texture2D(texture, vtexcoord);
                            }
                            
                            """

    private fun flatten(a: Array<FloatArray>): FloatArray? {
        var size = 0
        run {
            var k = 0
            while (k < a.size) {
                size += a[k].size
                k += 1
            }
        }
        val l = FloatArray(size)
        var offset = 0
        var k = 0
        while (k < a.size) {
            System.arraycopy(a[k], 0, l, offset, a[k].size)
            offset += a[k].size
            k += 1
        }
        return l
    }

    private fun flatten(a: Array<IntArray>): IntArray {
        var size = 0
        run {
            var k = 0
            while (k < a.size) {
                size += a[k].size
                k += 1
            }
        }
        val l = IntArray(size)
        var offset = 0
        var k = 0
        while (k < a.size) {
            System.arraycopy(a[k], 0, l, offset, a[k].size)
            offset += a[k].size
            k += 1
        }
        return l
    }

    private fun flatten(a: Array<ShortArray>): ShortArray? {
        var size = 0
        run {
            var k = 0
            while (k < a.size) {
                size += a[k].size
                k += 1
            }
        }
        val l = ShortArray(size)
        var offset = 0
        var k = 0
        while (k < a.size) {
            System.arraycopy(a[k], 0, l, offset, a[k].size)
            offset += a[k].size
            k += 1
        }
        return l
    }

    private fun flatten(a: Array<ByteArray>): ByteArray? {
        var size = 0
        run {
            var k = 0
            while (k < a.size) {
                size += a[k].size
                k += 1
            }
        }
        val l = ByteArray(size)
        var offset = 0
        var k = 0
        while (k < a.size) {
            System.arraycopy(a[k], 0, l, offset, a[k].size)
            offset += a[k].size
            k += 1
        }
        return l
    }

    private fun byteArrayFromIntArray(a: IntArray): ByteArray? {
        val l = ByteArray(a.size)
        var k = 0
        while (k < a.size) {
            l[k] = (a[k] and 0xFF).toByte()
            k += 1
        }
        return l
    }

    private fun generateOneBuffer(): Int {
        val buffer = intArrayOf(0)
        GLES20.glGenBuffers(1, buffer, 0)
        return buffer[0]
    }

    private fun generateOneTexture(): Int {
        val buffer = intArrayOf(0)
        GLES20.glGenTextures(1, buffer, 0)
        return buffer[0]
    }

    private fun getGLMatrix(m: Matrix44F): FloatArray? {
        val d = m.data
        return floatArrayOf(d[0], d[4], d[8], d[12], d[1], d[5], d[9], d[13], d[2], d[6], d[10], d[14], d[3], d[7], d[11], d[15])
    }

    constructor() {
        current_context = (EGLContext.getEGL() as EGL10).eglGetCurrentContext()
        program_box = GLES20.glCreateProgram()
        val vertShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
        GLES20.glShaderSource(vertShader, box_vert)
        GLES20.glCompileShader(vertShader)
        val fragShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
        GLES20.glShaderSource(fragShader, box_frag)
        GLES20.glCompileShader(fragShader)
        GLES20.glAttachShader(program_box, vertShader)
        GLES20.glAttachShader(program_box, fragShader)
        GLES20.glLinkProgram(program_box)
        GLES20.glUseProgram(program_box)
        GLES20.glDeleteShader(vertShader)
        GLES20.glDeleteShader(fragShader)
        pos_coord_box = GLES20.glGetAttribLocation(program_box, "coord")
        pos_tex_box = GLES20.glGetAttribLocation(program_box, "texcoord")
        pos_trans_box = GLES20.glGetUniformLocation(program_box, "trans")
        pos_proj_box = GLES20.glGetUniformLocation(program_box, "proj")
        vbo_coord_box = generateOneBuffer()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo_coord_box)
        val cube_vertices = arrayOf(floatArrayOf(1.0f / 2, 1.0f / 2, 0f), floatArrayOf(1.0f / 2, -1.0f / 2, 0f), floatArrayOf(-1.0f / 2, -1.0f / 2, 0f), floatArrayOf(-1.0f / 2, 1.0f / 2, 0f))
        val cube_vertices_buffer = FloatBuffer.wrap(flatten(cube_vertices))
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, cube_vertices_buffer.limit() * 4, cube_vertices_buffer, GLES20.GL_DYNAMIC_DRAW)
        vbo_tex_box = generateOneBuffer()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo_tex_box)
        val cube_vertex_texs = arrayOf(intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, 1), intArrayOf(0, 0))
        val cube_vertex_texs_buffer = ByteBuffer.wrap(byteArrayFromIntArray(flatten(cube_vertex_texs)))
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, cube_vertex_texs_buffer.limit(), cube_vertex_texs_buffer, GLES20.GL_STATIC_DRAW)
        vbo_faces_box = generateOneBuffer()
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, vbo_faces_box)
        val cube_faces = shortArrayOf(3, 2, 1, 0)
        val cube_faces_buffer = ShortBuffer.wrap(cube_faces)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, cube_faces_buffer.limit() * 2, cube_faces_buffer, GLES20.GL_STATIC_DRAW)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program_box, "texture"), 0)
        texture_id = generateOneTexture()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture_id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    fun dispose() {
        if ((EGLContext.getEGL() as EGL10).eglGetCurrentContext() == current_context) { //destroy resources unless the context has lost
            GLES20.glDeleteProgram(program_box)
            GLES20.glDeleteBuffers(1, intArrayOf(vbo_coord_box), 0)
            GLES20.glDeleteBuffers(1, intArrayOf(vbo_tex_box), 0)
            GLES20.glDeleteBuffers(1, intArrayOf(vbo_faces_box), 0)
            GLES20.glDeleteTextures(1, intArrayOf(texture_id), 0)
        }
    }

    fun render(projectionMatrix: Matrix44F, cameraview: Matrix44F, size: Vec2F) {
        val size0 = size.data[0]
        val size1 = size.data[1]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo_coord_box)
        val height = size0 / 1000
        val cube_vertices = arrayOf(floatArrayOf(size0 / 2, size1 / 2, 0f), floatArrayOf(size0 / 2, -size1 / 2, 0f), floatArrayOf(-size0 / 2, -size1 / 2, 0f), floatArrayOf(-size0 / 2, size1 / 2, 0f))
        val cube_vertices_buffer = FloatBuffer.wrap(flatten(cube_vertices))
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, cube_vertices_buffer.limit() * 4, cube_vertices_buffer, GLES20.GL_DYNAMIC_DRAW)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(program_box)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo_coord_box)
        GLES20.glEnableVertexAttribArray(pos_coord_box)
        GLES20.glVertexAttribPointer(pos_coord_box, 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo_tex_box)
        GLES20.glEnableVertexAttribArray(pos_tex_box)
        GLES20.glVertexAttribPointer(pos_tex_box, 2, GLES20.GL_UNSIGNED_BYTE, false, 0, 0)
        GLES20.glUniformMatrix4fv(pos_trans_box, 1, false, getGLMatrix(cameraview), 0)
        GLES20.glUniformMatrix4fv(pos_proj_box, 1, false, getGLMatrix(projectionMatrix), 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, vbo_faces_box)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture_id)
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_FAN, 4, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    fun texId(): Int {
        return texture_id
    }
}