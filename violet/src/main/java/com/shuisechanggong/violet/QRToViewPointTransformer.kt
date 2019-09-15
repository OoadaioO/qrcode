package com.shuisechanggong.violet

import android.graphics.Point
import android.graphics.PointF
import com.google.zxing.ResultPoint

/**
 *
 */

class QRToViewPointTransformer {


    fun transform(
        qrPoints: Array<ResultPoint>, isMirrorPreview: Boolean,
        orientation: Orientation,
        viewSize: Point, cameraPreviewSize: Point
    ): Array<PointF> {
        if (qrPoints.size == 0) {
            return Array<PointF>(0) { PointF() }
        }
        val transformedPoints = Array<PointF>(qrPoints.size) { PointF() }
        var index = 0
        for (qrPoint in qrPoints) {
            val transformedPoint = transform(
                qrPoint, isMirrorPreview, orientation, viewSize,
                cameraPreviewSize
            )
            transformedPoints[index] = transformedPoint
            index++
        }
        return transformedPoints
    }

    fun transform(
        qrPoint: ResultPoint, isMirrorPreview: Boolean, orientation: Orientation,
        viewSize: Point, cameraPreviewSize: Point
    ): PointF {
        val previewX = cameraPreviewSize.x.toFloat()
        val previewY = cameraPreviewSize.y.toFloat()

        var transformedPoint: PointF? = null
        val scaleX: Float
        val scaleY: Float

        if (orientation === Orientation.PORTRAIT) {
            scaleX = viewSize.x / previewY
            scaleY = viewSize.y / previewX
            transformedPoint = PointF((previewY - qrPoint.y) * scaleX, qrPoint.x * scaleY)
            if (isMirrorPreview) {
                transformedPoint.y = viewSize.y - transformedPoint.y
            }
        } else if (orientation === Orientation.LANDSCAPE) {
            scaleX = viewSize.x / previewX
            scaleY = viewSize.y / previewY
            transformedPoint = PointF(
                viewSize.x - qrPoint.x * scaleX,
                viewSize.y - qrPoint.y * scaleY
            )
            if (isMirrorPreview) {
                transformedPoint.x = viewSize.x - transformedPoint.x
            }
        }
        return transformedPoint ?: PointF(0f, 0f)
    }

}