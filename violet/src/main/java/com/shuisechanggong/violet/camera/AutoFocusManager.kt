/*
 * Copyright (C) 2012 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * -- Class modifications
 *
 * Copyright 2016 David Lázaro Esparcia.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shuisechanggong.violet.camera

import android.annotation.SuppressLint
import android.hardware.Camera
import android.os.AsyncTask
import com.shuisechanggong.violet.SimpleLog

import java.util.ArrayList
import java.util.concurrent.RejectedExecutionException

internal class AutoFocusManager(private val camera: Camera) : Camera.AutoFocusCallback {
    private var autofocusIntervalMs = DEFAULT_AUTO_FOCUS_INTERVAL_MS

    private var stopped: Boolean = false
    private var focusing: Boolean = false
    private val useAutoFocus: Boolean
    private var outstandingTask: AsyncTask<*, *, *>? = null

    init {
        val currentFocusMode = camera.parameters.focusMode
        useAutoFocus = FOCUS_MODES_CALLING_AF.contains(currentFocusMode)
        SimpleLog.i(
            TAG, "Current focus mode '"
                    + currentFocusMode
                    + "'; use auto focus? "
                    + useAutoFocus
        )
        start()
    }

    @Synchronized
    override fun onAutoFocus(success: Boolean, theCamera: Camera) {
        focusing = false
        autoFocusAgainLater()
    }

    fun setAutofocusInterval(autofocusIntervalMs: Long) {
        if (autofocusIntervalMs <= 0) {
            throw IllegalArgumentException("AutoFocusInterval must be greater than 0.")
        }
        this.autofocusIntervalMs = autofocusIntervalMs
    }

    @SuppressLint("NewApi")
    @Synchronized
    private fun autoFocusAgainLater() {
        if (!stopped && outstandingTask == null) {
            val newTask = AutoFocusTask()
            try {
                newTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                outstandingTask = newTask
            } catch (ree: RejectedExecutionException) {
                SimpleLog.w(TAG, "Could not request auto focus", ree)
            }

        }
    }

    @Synchronized
    fun start() {
        if (useAutoFocus) {
            outstandingTask = null
            if (!stopped && !focusing) {
                try {
                    camera.autoFocus(this)
                    focusing = true
                } catch (re: RuntimeException) {
                    // Have heard RuntimeException reported in Android 4.0.x+; continue?
                    SimpleLog.w(TAG, "Unexpected exception while focusing", re)
                    // Try again later to keep cycle going
                    autoFocusAgainLater()
                }

            }
        }
    }

    @Synchronized
    private fun cancelOutstandingTask() {
        if (outstandingTask != null) {
            if (outstandingTask!!.status != AsyncTask.Status.FINISHED) {
                outstandingTask!!.cancel(true)
            }
            outstandingTask = null
        }
    }

    @Synchronized
    fun stop() {
        stopped = true
        if (useAutoFocus) {
            cancelOutstandingTask()
            // Doesn't hurt to call this even if not focusing
            try {
                camera.cancelAutoFocus()
            } catch (re: RuntimeException) {
                // Have heard RuntimeException reported in Android 4.0.x+; continue?
                SimpleLog.w(TAG, "Unexpected exception while cancelling focusing", re)
            }

        }
    }

    private inner class AutoFocusTask : AsyncTask<Any, Any, Any>() {
        override fun doInBackground(vararg voids: Any): Any? {
            try {
                Thread.sleep(autofocusIntervalMs)
            } catch (e: InterruptedException) {
                // continue
            }

            start()
            return null
        }
    }

    companion object {

        private val TAG = AutoFocusManager::class.java.simpleName

        val DEFAULT_AUTO_FOCUS_INTERVAL_MS = 5000L
        private val FOCUS_MODES_CALLING_AF: MutableCollection<String>

        init {
            FOCUS_MODES_CALLING_AF = ArrayList(2)
            FOCUS_MODES_CALLING_AF.add(Camera.Parameters.FOCUS_MODE_AUTO)
            FOCUS_MODES_CALLING_AF.add(Camera.Parameters.FOCUS_MODE_MACRO)
        }
    }
}
