package com.shuisechanggong.violet

/**
 *
 */


import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import android.graphics.PointF
import android.hardware.Camera
import android.hardware.Camera.getCameraInfo
import android.os.AsyncTask
import android.os.Build
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.shuisechanggong.violet.camera.CameraManager
import java.io.IOException
import java.lang.ref.WeakReference

class BarCodeReaderView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs), SurfaceHolder.Callback, Camera.PreviewCallback {

    private var mOnQRCodeReadListener: OnQRCodeReadListener? = null

    private var mQRCodeReader: QRCodeReader? = null
    private var mPreviewWidth: Int = 0
    private var mPreviewHeight: Int = 0
    private var mCameraManager: CameraManager? = null
    private var mQrDecodingEnabled = true
    private var decodeFrameTask: DecodeFrameTask? = null
    private var decodeHints: Map<DecodeHintType, Any>? = null

    /**
     * Fix for the camera Sensor on some devices (ex.: Nexus 5x)
     */
    private// compensate the mirror
    // back-facing
    val cameraDisplayOrientation: Int
        get() {

            val info = Camera.CameraInfo()
            getCameraInfo(mCameraManager!!.previewCameraId, info)
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val rotation = windowManager.defaultDisplay.rotation
            var degrees = 0
            when (rotation) {
                Surface.ROTATION_0 -> degrees = 0
                Surface.ROTATION_90 -> degrees = 90
                Surface.ROTATION_180 -> degrees = 180
                Surface.ROTATION_270 -> degrees = 270
                else -> {
                }
            }

            var result: Int
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360
                result = (360 - result) % 360
            } else {
                result = (info.orientation - degrees + 360) % 360
            }
            return result
        }

    interface OnQRCodeReadListener {

        fun onQRCodeRead(text: String, points: Array<PointF>)
    }

    init {

        if (!isInEditMode) {

            if (checkCameraHardware()) {
                mCameraManager = CameraManager(getContext())
                mCameraManager!!.setPreviewCallback(this)
                holder.addCallback(this)
                setBackCamera()
            } else {
                throw RuntimeException("Error: Camera not found")
            }
        }
    }

    /**
     * Set the callback to return decoding result
     *
     * @param onQRCodeReadListener the listener
     */
    fun setOnQRCodeReadListener(onQRCodeReadListener: OnQRCodeReadListener) {
        mOnQRCodeReadListener = onQRCodeReadListener
    }

    /**
     * Enable/disable logging, false by default
     *
     * @param enabled logging enabled/disabled.
     */
    fun setLoggingEnabled(enabled: Boolean) {
        SimpleLog.setLoggingEnabled(enabled)
    }

    /**
     * Set QR decoding enabled/disabled.
     * default value is true
     *
     * @param qrDecodingEnabled decoding enabled/disabled.
     */
    fun setQRDecodingEnabled(qrDecodingEnabled: Boolean) {
        this.mQrDecodingEnabled = qrDecodingEnabled
    }

    /**
     * Set QR hints required for decoding
     *
     * @param decodeHints hints for decoding qrcode
     */
    fun setDecodeHints(decodeHints: Map<DecodeHintType, Any>) {
        this.decodeHints = decodeHints
    }

    /**
     * Starts camera preview and decoding
     */
    fun startCamera() {
        mCameraManager!!.startPreview()
    }

    /**
     * Stop camera preview and decoding
     */
    fun stopCamera() {
        mCameraManager!!.stopPreview()
    }

    /**
     * Set Camera autofocus interval value
     * default value is 5000 ms.
     *
     * @param autofocusIntervalInMs autofocus interval value
     */
    fun setAutofocusInterval(autofocusIntervalInMs: Long) {
        if (mCameraManager != null) {
            mCameraManager!!.setAutofocusInterval(autofocusIntervalInMs)
        }
    }

    /**
     * Trigger an auto focus
     */
    fun forceAutoFocus() {
        if (mCameraManager != null) {
            mCameraManager!!.forceAutoFocus()
        }
    }

    /**
     * Set Torch enabled/disabled.
     * default value is false
     *
     * @param enabled torch enabled/disabled.
     */
    fun setTorchEnabled(enabled: Boolean) {
        if (mCameraManager != null) {
            mCameraManager!!.setTorchEnabled(enabled)
        }
    }

    /**
     * Allows user to specify the camera ID, rather than determine
     * it automatically based on available cameras and their orientation.
     *
     * @param cameraId camera ID of the camera to use. A negative value means "no preference".
     */
    fun setPreviewCameraId(cameraId: Int) {
        mCameraManager!!.previewCameraId = cameraId
    }

    /**
     * Camera preview from device back camera
     */
    fun setBackCamera() {
        setPreviewCameraId(Camera.CameraInfo.CAMERA_FACING_BACK)
    }

    /**
     * Camera preview from device front camera
     */
    fun setFrontCamera() {
        setPreviewCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT)
    }

    public override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        if (decodeFrameTask != null) {
            decodeFrameTask!!.cancel(true)
            decodeFrameTask = null
        }
    }

    /****************************************************
     * SurfaceHolder.Callback,Camera.PreviewCallback
     */

    override fun surfaceCreated(holder: SurfaceHolder) {
        SimpleLog.d(TAG, "surfaceCreated")

        try {
            // Indicate camera, our View dimensions
            mCameraManager!!.openDriver(holder, this.width, this.height)
        } catch (e: IOException) {
            SimpleLog.w(TAG, "Can not openDriver: " + e.message)
            mCameraManager!!.closeDriver()
        } catch (e: RuntimeException) {
            SimpleLog.w(TAG, "Can not openDriver: " + e.message)
            mCameraManager!!.closeDriver()
        }

        try {
            mQRCodeReader = QRCodeReader()
            mCameraManager!!.startPreview()
        } catch (e: Exception) {
            SimpleLog.e(TAG, "Exception: " + e.message)
            mCameraManager!!.closeDriver()
        }

    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        SimpleLog.d(TAG, "surfaceChanged")

        if (holder.surface == null) {
            SimpleLog.e(TAG, "Error: preview surface does not exist")
            return
        }

        if (mCameraManager!!.previewSize == null) {
            SimpleLog.e(TAG, "Error: preview size does not exist")
            return
        }

        mPreviewWidth = mCameraManager!!.previewSize!!.x
        mPreviewHeight = mCameraManager!!.previewSize!!.y

        mCameraManager!!.stopPreview()

        // Fix the camera sensor rotation
        mCameraManager!!.setPreviewCallback(this)
        mCameraManager!!.setDisplayOrientation(cameraDisplayOrientation)

        mCameraManager!!.startPreview()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        SimpleLog.d(TAG, "surfaceDestroyed")

        mCameraManager!!.setPreviewCallback(null!!)
        mCameraManager!!.stopPreview()
        mCameraManager!!.closeDriver()
    }

    // Called when camera take a frame
    override fun onPreviewFrame(data: ByteArray, camera: Camera) {
        if (!mQrDecodingEnabled || decodeFrameTask != null && (decodeFrameTask!!.status == AsyncTask.Status.RUNNING || decodeFrameTask!!.status == AsyncTask.Status.PENDING)) {
            return
        }

        decodeFrameTask = DecodeFrameTask(this, decodeHints)
        decodeFrameTask!!.execute(data)
    }

    /**
     * Check if this device has a camera
     */
    private fun checkCameraHardware(): Boolean {
        return if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            true
        } else if (context.packageManager
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
        ) {
            // this device has a front camera
            true
        } else {
            // this device has any camera
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_CAMERA_ANY
            )
        }
    }

    private class DecodeFrameTask internal constructor(view: BarCodeReaderView, hints: Map<DecodeHintType, Any>?) :
        AsyncTask<ByteArray, Void, Result>() {

        private val viewRef: WeakReference<BarCodeReaderView>
        private val hintsRef: WeakReference<Map<DecodeHintType, Any>?>
        private val qrToViewPointTransformer = QRToViewPointTransformer()

        init {
            viewRef = WeakReference(view)
            hintsRef = WeakReference(hints)
        }

        override fun doInBackground(vararg params: ByteArray): Result? {
            val view = viewRef.get() ?: return null

            val source = view.mCameraManager!!.buildLuminanceSource(
                params[0], view.mPreviewWidth,
                view.mPreviewHeight
            )

            val hybBin = HybridBinarizer(source)
            val bitmap = BinaryBitmap(hybBin)

            try {
                return view.mQRCodeReader!!.decode(bitmap, hintsRef.get())
            } catch (e: ChecksumException) {
                SimpleLog.d(TAG, "ChecksumException", e)
            } catch (e: NotFoundException) {
                SimpleLog.d(TAG, "No QR Code found")
            } catch (e: FormatException) {
                SimpleLog.d(TAG, "FormatException", e)
            } finally {
                view.mQRCodeReader!!.reset()
            }

            return null
        }

        override fun onPostExecute(result: Result?) {
            super.onPostExecute(result)

            val view = viewRef.get()

            // Notify we found a QRCode
            if (view != null && result != null && view.mOnQRCodeReadListener != null) {
                // Transform resultPoints to View coordinates
                val transformedPoints = transformToViewCoordinates(view, result.resultPoints)
                view.mOnQRCodeReadListener!!.onQRCodeRead(result.text, transformedPoints)
            }
        }

        /**
         * Transform result to surfaceView coordinates
         *
         *
         * This method is needed because coordinates are given in landscape camera coordinates when
         * device is in portrait mode and different coordinates otherwise.
         *
         * @return a new PointF array with transformed points
         */
        private fun transformToViewCoordinates(
            view: BarCodeReaderView,
            resultPoints: Array<ResultPoint>
        ): Array<PointF> {
            val orientationDegrees = view.cameraDisplayOrientation
            val orientation = if (orientationDegrees == 90 || orientationDegrees == 270)
                Orientation.PORTRAIT
            else
                Orientation.LANDSCAPE
            val viewSize = Point(view.width, view.height)
            val cameraPreviewSize = view.mCameraManager!!.previewSize
            val isMirrorCamera = view.mCameraManager!!.previewCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT

            return qrToViewPointTransformer.transform(
                resultPoints, isMirrorCamera, orientation,
                viewSize, cameraPreviewSize!!
            )
        }
    }

    companion object {

        private val TAG = BarCodeReaderView::class.java.name
    }

}
