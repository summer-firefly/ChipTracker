package com.chiptrack.app.ui.settle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.chiptrack.app.GameSessionViewModel
import com.chiptrack.app.R
import com.chiptrack.app.databinding.FragmentSettleBinding
import com.chiptrack.app.model.Player
import com.chiptrack.app.model.SessionPhase
import com.chiptrack.app.share.ShareHelper
import com.chiptrack.app.ui.ProfitFormat
import com.chiptrack.app.ui.history.HistoryFragment
import com.chiptrack.app.ui.navigateOnce
import kotlin.math.abs

class SettleFragment : Fragment() {
    private var _binding: FragmentSettleBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GameSessionViewModel by activityViewModels()
    private lateinit var adapter: SettlePlayersAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SettlePlayersAdapter { openPlayerHistory(it) }
        binding.settleList.layoutManager = LinearLayoutManager(requireContext())
        binding.settleList.adapter = adapter

        binding.backButton.setOnClickListener { goHome() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    goHome()
                }
            }
        )

        binding.historyButton.setOnClickListener {
            findNavController().navigate(
                R.id.action_settle_to_history,
                HistoryFragment.args()
            )
        }
        binding.shareButton.setOnClickListener {
            val session = viewModel.session.value ?: return@setOnClickListener
            ShareHelper.shareSessionTable(this, session)
        }
        binding.newGameButton.setOnClickListener {
            goHome()
        }

        viewModel.session.observe(viewLifecycleOwner) { session ->
            if (session.phase != SessionPhase.SETTLED) return@observe

            val ranked = session.players.sortedByDescending { it.profit }
            adapter.submitList(ranked)

            binding.reconcileTakenText.text =
                getString(R.string.settle_reconcile_taken, session.totalTakenSum)
            binding.reconcileReturnedText.text =
                getString(R.string.settle_reconcile_returned, session.totalReturnedSum)

            val gap = session.reconcileGap
            when {
                gap == 0 -> {
                    binding.reconcileGapText.text = getString(R.string.settle_reconcile_ok)
                    binding.reconcileGapText.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.profit_positive)
                    )
                }
                gap > 0 -> {
                    binding.reconcileGapText.text =
                        getString(R.string.settle_reconcile_over, gap)
                    binding.reconcileGapText.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.btn_remove_points)
                    )
                }
                else -> {
                    binding.reconcileGapText.text =
                        getString(R.string.settle_reconcile_under, abs(gap))
                    binding.reconcileGapText.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.profit_negative)
                    )
                }
            }

            binding.totalProfitText.text = getString(
                R.string.settle_total,
                ProfitFormat.text(requireContext(), session.totalProfit)
            )
            binding.totalProfitText.setTextColor(
                ProfitFormat.color(requireContext(), session.totalProfit)
            )
        }
    }

    private fun goHome() {
        viewModel.newGame()
        navigateOnce(R.id.action_settle_to_setup)
    }

    private fun openPlayerHistory(player: Player) {
        findNavController().navigate(
            R.id.action_settle_to_history,
            HistoryFragment.args(player.id, player.name)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
