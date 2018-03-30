package com.aliyun.demo.aliplayer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import com.alibaba.fastjson.JSON
import com.alivc.player.AliyunErrorCode
import com.aliyun.demo.aliplayer.entity.StsToken
import com.aliyun.vodplayer.media.AliyunVidSts
import com.aliyun.vodplayer.media.AliyunVodPlayer
import com.aliyun.vodplayer.media.IAliyunVodPlayer
import com.aliyun.vodplayerview.utils.NetWatchdog
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import java.net.HttpURLConnection
import java.net.URL


class MainActivity : AppCompatActivity() {
    private var netWatchdog: NetWatchdog? = null
    private var aliyunPlayer: AliyunVodPlayer? = null
    private var vidSts: AliyunVidSts = AliyunVidSts()
    private var lockListener: ScreenStatusController? = null
    private var httpURLConnection: HttpURLConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        verifyStoragePermissions(this)

        prepare.setOnClickListener {
            aliyunPlayer?.prepareAsync(vidSts)
        }

        btnPlay.setOnClickListener {
            player.visibility = View.VISIBLE
            aliyunPlayer?.start()
        }

        btnStop.setOnClickListener {
            aliyunPlayer?.stop()
            player.visibility = View.GONE
        }

        init.setOnClickListener {
            init()
            init.visibility = View.GONE
        }

        btnSts.setOnClickListener {
            getStsToken()
        }
    }
    override fun onResume() {
        super.onResume()
        //保存播放器的状态，供resume恢复使用。
        resumePlayerState()
    }
    override fun onStop() {
        super.onStop()
        //onStop中记录下来的状态，在这里恢复使用
        savePlayerState()
    }

    override fun onDestroy() {
        netWatchdog?.stopWatch()
        lockListener?.stopListen()
        aliyunPlayer?.stop()
        aliyunPlayer?.release()

        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updatePlayerViewMode()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_EXTERNAL_STORAGE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initVodPlayer()
        }

    }

    private fun updatePlayerViewMode() {
        if (player != null) {
            val orientation = resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                this.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                player.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

                //设置view的布局，宽高之类
                val aliVcVideoViewLayoutParams = player.layoutParams as LinearLayout.LayoutParams
                aliVcVideoViewLayoutParams.height = dp2px(this, 200f)
                aliVcVideoViewLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (!isStrangePhone()) {
                    this.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
                    player.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                }

                //设置view的布局，宽高
                val aliVcVideoViewLayoutParams = player.layoutParams as LinearLayout.LayoutParams
                aliVcVideoViewLayoutParams.height = dp2px(this, 200f)
                aliVcVideoViewLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }
    }

    private fun init() {
        initView()
        initVodPlayer()
        initNetWatch()
        initLockListener()

        toast("Init over.")

        init.visibility = View.GONE
    }

    private fun getStsToken() {
        launch(CommonPool) {
            try {
                val url = URL("http://sts.aliyun.com:8080/sts/get")
                httpURLConnection = url.openConnection() as HttpURLConnection
                httpURLConnection?.useCaches = false
                httpURLConnection?.connect()
                val stsToken = JSON.parseObject<StsToken>(httpURLConnection?.inputStream, StsToken::class.java)

                vidSts.vid = "6653d988858946b2ab6a506813166435"
                vidSts.acId = stsToken.accessKeyId
                vidSts.akSceret = stsToken.accessKeySecret
                vidSts.securityToken = stsToken.securityToken

                toast("获取 StsToken 成功！")
            } catch (e: Exception) {
                toast("获取 StsToken 失败！因为 ${e.message}")
                e.printStackTrace()
            } finally {
                httpURLConnection?.disconnect()
            }
        }
    }

    fun dp2px(context: Context, dpValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    /**
     * 监听锁屏状态
     */
    private fun initLockListener() {
        lockListener = ScreenStatusController(this)
        lockListener?.setScreenStatusListener(object : ScreenStatusController.ScreenStatusListener {
            override fun onScreenOff() {
                if (null != aliyunPlayer && aliyunPlayer!!.isPlaying) {
                    aliyunPlayer?.pause()
                }
            }

            override fun onScreenOn() {
            }

        })

        lockListener?.startListen()
    }

    /**
     * 初始化网络监听
     */
    private fun initNetWatch() {
        netWatchdog = NetWatchdog(this)
        netWatchdog?.setNetChangeListener(object : NetWatchdog.NetChangeListener {
            override fun onWifiTo4G() {
                if (null != aliyunPlayer && aliyunPlayer!!.isPlaying) {
                    aliyunPlayer?.pause()
                    toast("网络切换到 4G 已暂停播放")
                }
            }

            override fun on4GToWifi() {

            }

            override fun onNetDisconnected() {
                aliyunPlayer?.pause()
                toast("网络断开，已暂停播放")
            }
        })

        netWatchdog?.startWatch()
    }

    /**
     * View 设置
     */
    private fun initView() {
        player.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                aliyunPlayer?.setDisplay(holder)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                aliyunPlayer?.surfaceChanged()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.d("Demo", "surfaceDestroyed")
            }
        })
    }

    /**
     * 初始化播放器
     */
    private fun initVodPlayer() {
        aliyunPlayer = AliyunVodPlayer(this)
        val sdDir = Environment.getExternalStorageDirectory().absolutePath + "/sdcard/Download/"
        aliyunPlayer?.setPlayingCache(false, sdDir, 60 * 60 /*时长, s */, 300 /*大小，MB*/)
        aliyunPlayer?.setCirclePlay(false)

        aliyunPlayer?.setOnCircleStartListener({})
        aliyunPlayer?.setOnPreparedListener({
            toast("视频资源准备好了！")
            player.visibility = View.VISIBLE
            btnPlay.visibility = View.VISIBLE
            btnStop.visibility = View.VISIBLE
        })
        aliyunPlayer?.setOnFirstFrameStartListener({})
        aliyunPlayer?.setOnErrorListener({ p1: Int, p2: Int, p3: String ->
            {

            }
        })
        aliyunPlayer?.setOnCompletionListener({})
        aliyunPlayer?.setOnSeekCompleteListener({})
        aliyunPlayer?.setOnStoppedListner({})

        aliyunPlayer?.enableNativeLog()
    }

    fun onError(arg0: Int, arg1: Int, msg: String) {

        aliyunPlayer?.stop()

        if (arg0 == AliyunErrorCode.ALIVC_ERR_INVALID_INPUTFILE.code) {
            //当播放本地报错4003的时候，可能是文件地址不对，也有可能是没有权限。
            //如果是没有权限导致的，就做一个权限的错误提示。其他还是正常提示：
            val storagePermissionRet = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (storagePermissionRet != PackageManager.PERMISSION_GRANTED) {
                toast("NO PERMISSIONS")
                return
            }
        }

        toast("PLAY ERROR")

        aliyunPlayer?.prepareAsync(vidSts)
    }

    private val REQUEST_EXTERNAL_STORAGE = 1
    private val PERMISSIONS_STORAGE = arrayOf("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE")

    private fun verifyStoragePermissions(activity: Activity) {
        try {
            //检测是否有写的权限
            val permission = ActivityCompat.checkSelfPermission(activity, "android.permission.WRITE_EXTERNAL_STORAGE")
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    //用来记录前后台切换时的状态，以供恢复。
    private var mPlayerState: IAliyunVodPlayer.PlayerState? = null

    private fun resumePlayerState() {
        if (mPlayerState == IAliyunVodPlayer.PlayerState.Paused) {
            aliyunPlayer?.pause()
        } else if (mPlayerState == IAliyunVodPlayer.PlayerState.Started) {
            aliyunPlayer?.start()
        }
    }

    private fun savePlayerState() {
        mPlayerState = aliyunPlayer?.getPlayerState()
        if (null != aliyunPlayer && aliyunPlayer!!.isPlaying()) {
            //然后再暂停播放器
            aliyunPlayer?.pause()
        }
    }

    private fun isStrangePhone(): Boolean {
        return (Build.DEVICE.equals("mx5", ignoreCase = true)
                || Build.DEVICE.equals("Redmi Note2", ignoreCase = true)
                || Build.DEVICE.equals("Z00A_1", ignoreCase = true)
                || Build.DEVICE.equals("hwH60-L02", ignoreCase = true)
                || Build.DEVICE.equals("hermes", ignoreCase = true)
                || Build.DEVICE.equals("V4", ignoreCase = true) && Build.MANUFACTURER.equals("Meitu", ignoreCase = true))

    }
}


fun Activity.toast(message: CharSequence) {
    runOnUiThread {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

