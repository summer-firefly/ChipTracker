package com.chiptrack.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import com.chiptrack.app.databinding.ActivityMainBinding
import com.chiptrack.app.model.SessionPhase

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val navController by lazy {
        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navHost.navController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        if (savedInstanceState == null) {
            val viewModel = androidx.lifecycle.ViewModelProvider(
                this,
                androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )[GameSessionViewModel::class.java]
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
        setupActionBarWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
