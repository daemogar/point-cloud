package edu.southern.pointcloud.scanning

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Renders the ARCore camera feed as a full-screen background using an
 * OES external texture (GL_TEXTURE_EXTERNAL_OES).
 *
 * Usage from the GL thread:
 *   1. Call [onSurfaceCreated] — returns the OES texture ID to pass to
 *      `Session.setCameraTextureName()`.
 *   2. Call [draw] each frame before any overlay rendering.
 */
class CameraPreviewRenderer {

    var textureId  = -1
        private set
    private var program    = -1
    private var quadVbo    = -1
    private var posHandle  = -1
    private var texHandle  = -1
    private var texUniform = -1

    var surfaceTexture: SurfaceTexture? = null
        private set

    // Full-screen quad: position (x,y) + texcoord (u,v) — 4 floats per vertex
    private val QUAD = floatArrayOf(
        -1f, -1f,  0f, 1f,
         1f, -1f,  1f, 1f,
        -1f,  1f,  0f, 0f,
         1f,  1f,  1f, 0f
    )

    private val VERTEX_SHADER = """
        attribute vec4 a_Pos;
        attribute vec2 a_UV;
        varying vec2 v_UV;
        void main() { gl_Position = a_Pos; v_UV = a_UV; }
    """.trimIndent()

    private val FRAGMENT_SHADER = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 v_UV;
        uniform samplerExternalOES u_Tex;
        void main() { gl_FragColor = texture2D(u_Tex, v_UV); }
    """.trimIndent()

    /** Must be called on the GL thread. Returns the OES texture ID. */
    fun onSurfaceCreated(): Int {
        // Guard against double-init (e.g. surface recreated after pause)
        if (textureId != -1) return textureId

        // OES texture for ARCore camera feed
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        surfaceTexture = SurfaceTexture(textureId)

        // Shader program
        program    = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        posHandle  = GLES20.glGetAttribLocation(program, "a_Pos")
        texHandle  = GLES20.glGetAttribLocation(program, "a_UV")
        texUniform = GLES20.glGetUniformLocation(program, "u_Tex")

        // VBO
        val vbos = IntArray(1)
        GLES20.glGenBuffers(1, vbos, 0)
        quadVbo = vbos[0]
        val buf = ByteBuffer.allocateDirect(QUAD.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(QUAD).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, QUAD.size * 4, buf, GLES20.GL_STATIC_DRAW)

        return textureId
    }

    /** Draw the camera feed. Call each frame before any 3-D overlay. */
    fun draw() {
        surfaceTexture?.updateTexImage() ?: return
        if (program == -1) return

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(program)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)

        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 16, 0)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 16, 8)
        GLES20.glEnableVertexAttribArray(texHandle)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(texUniform, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun createProgram(vert: String, frag: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vert)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, frag)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, v)
            GLES20.glAttachShader(it, f)
            GLES20.glLinkProgram(it)
        }
    }

    private fun compile(type: Int, src: String): Int =
        GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src)
            GLES20.glCompileShader(it)
            val status = IntArray(1)
            GLES20.glGetShaderiv(it, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e("CameraRenderer", "Shader compile error: ${GLES20.glGetShaderInfoLog(it)}")
            }
        }

    /** Call from ScanActivity.onPause() to free GPU resources. */
    fun release() {
        surfaceTexture?.release()
        surfaceTexture = null
        if (textureId != -1) { GLES20.glDeleteTextures(1, intArrayOf(textureId), 0); textureId = -1 }
        if (quadVbo  != -1) { GLES20.glDeleteBuffers(1, intArrayOf(quadVbo), 0);    quadVbo  = -1 }
        if (program  != -1) { GLES20.glDeleteProgram(program);                       program  = -1 }
    }
}
