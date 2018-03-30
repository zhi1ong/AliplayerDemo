package com.aliyun.demo.aliplayer

import com.aliyun.vodplayer.media.IAliyunVodPlayer
import java.lang.ref.WeakReference

/**
 * 错误处理
 *
 * @author zhilong <a href="mailto:zhilong.lzl@alibaba-inc.com">Contact me.</a>
 * @version 1.0
 * @since 2018/3/19 下午4:23
 */
open class PlayErrorListener(skinActivity:MainActivity): IAliyunVodPlayer.OnErrorListener {

    private val activityWeakReference: WeakReference<MainActivity> = WeakReference(skinActivity)

    override fun onError(arg0: Int, arg1: Int, msg: String) {
        val activity = activityWeakReference.get()
        if (activity != null) {
            activity.onError(arg0, arg1, msg)
        }
    }
}