package com.rhj.callback
/**
 * Created by liujunbin
 */

class CustomTipsUpdateCallback private constructor() {
    private var mCustomInfoUpdateListener: CustomTipsUpdateListener? = null

    private object CustomTipsUpdateCallbackHolder {
        val instance = CustomTipsUpdateCallback()
    }

    interface CustomTipsUpdateListener {
        fun updateCustomTips(showTips: Boolean)
    }

    fun setCountDownInfoUpdateListener(listener: CustomTipsUpdateListener?) {
        mCustomInfoUpdateListener = listener
    }

    fun updateCustomTips(showTips: Boolean) {
        if (mCustomInfoUpdateListener != null) {
            mCustomInfoUpdateListener!!.updateCustomTips(showTips)
        }
    }

    companion object {
        val instance: CustomTipsUpdateCallback
            get() = CustomTipsUpdateCallbackHolder.instance
    }
}