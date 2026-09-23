package com.chiptrack.app.ui.settle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.chiptrack.app.GameSessionViewModel
import com.chiptrack.app.R
import com.chiptrack.app.databinding.FragmentFinalReturnBinding
import com.chiptrack.app.model.SessionPhase
import com.chiptrack.app.ui.navigateOnce

class FinalReturnFragment : Fragment() {
    private var _binding: FragmentFinalReturnBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GameSessionViewModel by activityViewModels()
    private lateinit var adapter: FinalReturnAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFinalReturnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = FinalReturnAdapter()
        binding.returnList.layoutManager = LinearLayoutManager(requireContext())
        binding.returnList.adapter = adapter

        binding.confirmButton.setOnClickListener { confirmSettle() }

        viewModel.session.observe(viewLifecycleOwner) { session ->
            when (session.phase) {
                SessionPhase.SETTLED -> navigateOnce(R.id.action_final_return_to_settle)
                SessionPhase.SETUP -> navigateOnce(R.id.action_final_return_to_setup)
                SessionPhase.PLAYING -> {
                    // 已退出玩家应已通过「退还」结清，终局只填在桌玩家
                    adapter.submitList(session.activePlayers)
                    val exitedHint = if (session.exitedPlayers.isEmpty()) {
                        getString(R.string.final_return_subtitle)
                    } else {
                        getString(
                            R.string.final_return_subtitle_with_exited,
                            session.exitedPlayers.size
                        )
                    }
                    binding.subtitleText.text = exitedHint
                }
            }
        }
    }

    private fun confirmSettle() {
        val activePlayers = viewModel.session.value?.activePlayers.orEmpty()
        val amounts = mutableMapOf<String, Int>()
        activePlayers.forEach { player ->
            val raw = adapter.draftOf(player.id).trim()
            val amount = if (raw.isEmpty()) 0 else raw.toIntOrNull()
            if (amount == null || amount < 0) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_invalid_amount) + "（${player.name}）",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            amounts[player.id] = amount
        }
        viewModel.finalizeReturnsAndSettle(amounts)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
