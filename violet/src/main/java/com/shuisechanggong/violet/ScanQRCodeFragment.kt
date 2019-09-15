package com.shuisechanggong.violet

import android.graphics.PointF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment

/**
 *
 */

typealias OnQRCodeResultListener = (String?) -> Unit


class ScanQRCodeFragment : Fragment() {

    var onQRCodeResultListener: OnQRCodeResultListener? = null

    private var barCodeView: BarCodeReaderView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = FrameLayout(inflater.context)

        barCodeView = BarCodeReaderView(inflater.context)
        rootView.addView(barCodeView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        barCodeView?.setBackCamera()
        barCodeView?.setAutofocusInterval(2000L)
        barCodeView?.setOnQRCodeReadListener(object : BarCodeReaderView.OnQRCodeReadListener {
            override fun onQRCodeRead(text: String, points: Array<PointF>) {
                onQRCodeResultListener?.invoke(text)
            }

        })
        return rootView
    }


    override fun onResume() {
        super.onResume()
        barCodeView?.startCamera()
    }

    override fun onPause() {
        super.onPause()
        barCodeView?.stopCamera()
    }
}