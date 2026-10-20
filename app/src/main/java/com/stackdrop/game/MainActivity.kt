package com.stackdrop.game

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.stackdrop.game.ads.AdsManager
import com.stackdrop.game.ads.ConsentManager
import com.stackdrop.game.game.GameEvent
import com.stackdrop.game.game.GameView
import com.stackdrop.game.online.OnlineServices
import com.stackdrop.game.platform.ActivityBridge

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private lateinit var online: OnlineServices
    private lateinit var ads: AdsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        online = OnlineServices(applicationContext)
        ads = AdsManager(this)
        val bridge = ActivityBridge(this, online, ads, ConsentManager(this))
        gameView = GameView(this, bridge, online)
        bridge.events = gameView::post
        online.events = gameView::post
        ads.events = gameView::post
        setContentView(gameView)

        onBackPressedDispatcher.addCallback(this) { gameView.post(GameEvent.Back) }

        WindowInsetsControllerCompat(window, gameView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        bridge.startAds()
    }

    override fun onResume() {
        super.onResume()
        gameView.onResumeGame()
        online.onResume()
    }

    override fun onPause() {
        gameView.onPauseGame()
        super.onPause()
    }

    override fun onDestroy() {
        gameView.release()
        online.close()
        ads.release()
        super.onDestroy()
    }
}
