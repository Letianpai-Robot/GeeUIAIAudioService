package com.demon.fmodsound

import java.lang.Exception

/**
 * @author DeMon
 * Created on 2020/12/31.
 * E-mail 757454343@qq.com
 * Desc:
 */

object FmodSound {
    //音效的类型

    init {
        System.loadLibrary("fmodL")
        System.loadLibrary("fmod")
        System.loadLibrary("FmodSound")
    }

    external fun playTts(url: String, listener: IPlaySoundListener)
    interface IPlaySoundListener {
        //成功
        fun onFinish()
    }

}