package com.stackdrop.game

import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.stackdrop.game.ads.AdsManager
import com.stackdrop.game.ads.ConsentManager
import com.stackdrop.game.billing.BillingManager
import com.stackdrop.game.data.SaveData
import com.stackdrop.game.game.GameEvent
import com.stackdrop.game.game.GameView
import com.stackdrop.game.online.OnlineServices
import com.stackdrop.game.platform.ActivityBridge
import com.stackdrop.game.platform.LauncherIcon

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private lateinit var online: OnlineServices
    private lateinit var ads: AdsManager
    private lateinit var billing: BillingManager

    /** UI-thread copy of the premium flag; the game keeps its own through PremiumChanged events. */
    private var premium = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // The last known answer from Play, so a premium player never sees the banner strip flash.
        premium = SaveData(this).premium

        online = OnlineServices(applicationContext)
        ads = AdsManager(this).also { it.premium = premium }
        billing = BillingManager(this, billingListener)
        val bridge = ActivityBridge(this, online, ads, ConsentManager(this), billing)
        gameView = GameView(this, bridge, online)
        bridge.events = gameView::post
        online.events = gameView::post
        ads.events = gameView::post

        // The game fills the screen above a strip reserved for the banner.
        val bannerSlot = FrameLayout(this).apply { setBackgroundColor(BANNER_STRIP) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(gameView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bannerSlot, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0))
        }
        setContentView(root)
        ads.attachBannerSlot(bannerSlot)

        onBackPressedDispatcher.addCallback(this) { gameView.post(GameEvent.Back) }

        WindowInsetsControllerCompat(window, gameView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        bridge.startAds()
        billing.start()
    }

    private val billingListener = object : BillingManager.Listener {
        override fun onPremiumChanged(owned: Boolean) {
            premium = owned
            ads.premium = owned
            gameView.post(GameEvent.PremiumChanged(owned))
        }

        override fun onPrice(price: String?) = gameView.post(GameEvent.PremiumPrice(price))

        override fun onMessage(message: String) = gameView.post(GameEvent.Toast(message))
    }

    override fun onResume() {
        super.onResume()
        gameView.onResumeGame()
        online.onResume()
        ads.resume()
    }

    override fun onPause() {
        ads.pause()
        gameView.onPauseGame()
        super.onPause()
    }

    override fun onStop() {
        // Swapping the launcher icon can restart the app on some launchers, so it waits until the
        // player has left.
        LauncherIcon.apply(this, premium)
        super.onStop()
    }

    override fun onDestroy() {
        gameView.release()
        online.close()
        ads.release()
        billing.end()
        super.onDestroy()
    }

    private companion object {
        const val BANNER_STRIP = 0xFF0E0C18.toInt()
    }
}
