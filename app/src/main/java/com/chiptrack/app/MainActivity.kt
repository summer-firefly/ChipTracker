package com.chiptrack.app

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.chiptrack.app.databinding.ActivityMainBinding
import com.chiptrack.app.model.SessionPhase
import com.chiptrack.app.share.macro.ShareMacroController
import com.chiptrack.app.ui.history.HistoryFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: GameSessionViewModel by viewModels()

    private val navController: NavController by lazy {
        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navHost.navController
    }

    /** 根页面：不显示返回箭头 */
    private val topLevelDestinations = setOf(
        R.id.setupFragment,
        R.id.tableFragment
    )

    override fun onResume() {
        super.onResume()
        // 从微信返回时结束宏录制并保存
        ShareMacroController.finishRecordingIfNeeded(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        if (savedInstanceState == null) {
            val startId = when (viewModel.session.value?.phase) {
                SessionPhase.PLAYING -> R.id.tableFragment
                SessionPhase.SETTLED -> R.id.settleFragment
                else -> R.id.setupFragment
            }
            if (startId != R.id.setupFragment) {
                val graph = navController.navInflater.inflate(R.navigation.nav_graph)
                graph.setStartDestination(startId)
                navController.graph = graph
            }
        }

        val appBarConfiguration = AppBarConfiguration(topLevelDestinations)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // 统一控制返回箭头：非根页面始终显示（含结算页作为 startDestination 的情况）
        navController.addOnDestinationChangedListener { _, destination, arguments ->
            val showUp = destination.id !in topLevelDestinations
            supportActionBar?.setDisplayHomeAsUpEnabled(showUp)
            supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)

            when (destination.id) {
                R.id.historyFragment -> {
                    val playerName = arguments
                        ?.getString(HistoryFragment.ARG_PLAYER_NAME)
                        ?.takeIf { it.isNotBlank() }
                    supportActionBar?.title = if (playerName != null) {
                        getString(R.string.history_player_title, playerName)
                    } else {
                        getString(R.string.history_global_title)
                    }
                }
                R.id.setupFragment ->
                    supportActionBar?.title = getString(R.string.app_name)
                else ->
                    supportActionBar?.title = destination.label
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return handleNavigateUp() || super.onSupportNavigateUp()
    }

    private fun handleNavigateUp(): Boolean {
        return when (navController.currentDestination?.id) {
            R.id.settleFragment -> {
                // 结算页返回：清空本局并回开局设置
                viewModel.newGame()
                navController.navigate(R.id.action_settle_to_setup)
                true
            }
            R.id.finalReturnFragment,
            R.id.historyFragment -> {
                navController.navigateUp()
            }
            else -> navController.navigateUp()
        }
    }
}
