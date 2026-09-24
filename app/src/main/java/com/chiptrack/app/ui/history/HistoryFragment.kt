package com.chiptrack.app.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.chiptrack.app.GameSessionViewModel
import com.chiptrack.app.R
import com.chiptrack.app.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GameSessionViewModel by activityViewModels()
    private lateinit var adapter: ChipRecordsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val playerId = arguments?.getString(ARG_PLAYER_ID)?.takeIf { it.isNotBlank() }
        adapter = ChipRecordsAdapter(showPlayerName = playerId == null)
        binding.recordsList.layoutManager = LinearLayoutManager(requireContext())
        binding.recordsList.adapter = adapter

        binding.subtitleText.text = if (playerId == null) {
            getString(R.string.history_global_subtitle)
        } else {
            getString(R.string.history_player_subtitle)
        }

        viewModel.session.observe(viewLifecycleOwner) { session ->
            val list = if (playerId == null) session.recordsNewestFirst()
            else session.recordsFor(playerId)
            adapter.submitList(list)
            val empty = list.isEmpty()
            binding.emptyText.visibility = if (empty) View.VISIBLE else View.GONE
            binding.recordsList.visibility = if (empty) View.GONE else View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_PLAYER_ID = "playerId"
        const val ARG_PLAYER_NAME = "playerName"

        fun args(playerId: String? = null, playerName: String? = null): Bundle =
            Bundle().apply {
                putString(ARG_PLAYER_ID, playerId.orEmpty())
                putString(ARG_PLAYER_NAME, playerName.orEmpty())
            }
    }
}
