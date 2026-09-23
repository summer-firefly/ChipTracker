package com.chiptrack.app.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.chiptrack.app.GameSessionViewModel
import com.chiptrack.app.R
import com.chiptrack.app.databinding.FragmentSetupBinding
import com.chiptrack.app.databinding.ItemSetupPlayerBinding
import com.chiptrack.app.model.SessionPhase
import com.chiptrack.app.ui.navigateOnce
import com.google.android.material.textfield.TextInputEditText

class SetupFragment : Fragment() {
    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GameSessionViewModel by activityViewModels()
    private val nameInputs = mutableListOf<TextInputEditText>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateEmptyHint()
        binding.addPlayerButton.setOnClickListener { addPlayerRow() }
        binding.startButton.setOnClickListener { startGame() }

        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.consumeMessage()
            }
        }
        viewModel.session.observe(viewLifecycleOwner) { session ->
            when (session.phase) {
                SessionPhase.PLAYING -> navigateOnce(R.id.action_setup_to_table)
                SessionPhase.SETTLED -> navigateOnce(R.id.action_setup_to_settle)
                SessionPhase.SETUP -> Unit
            }
        }
    }

    private fun addPlayerRow(prefill: String = "") {
        val rowBinding = ItemSetupPlayerBinding.inflate(
            layoutInflater,
            binding.playerNamesContainer,
            false
        )
        if (prefill.isNotBlank()) {
            rowBinding.nameInput.setText(prefill)
        }
        rowBinding.removeButton.setOnClickListener {
            binding.playerNamesContainer.removeView(rowBinding.root)
            nameInputs.remove(rowBinding.nameInput)
            updateEmptyHint()
        }
        binding.playerNamesContainer.addView(rowBinding.root)
        nameInputs += rowBinding.nameInput
        updateEmptyHint()
        rowBinding.nameInput.requestFocus()
        scrollRowIntoView(rowBinding.root)
    }

    private fun scrollRowIntoView(target: View) {
        binding.setupScroll.post {
            val childOffset = IntArray(2)
            val scrollOffset = IntArray(2)
            target.getLocationOnScreen(childOffset)
            binding.setupScroll.getLocationOnScreen(scrollOffset)
            val y = childOffset[1] - scrollOffset[1] + binding.setupScroll.scrollY - 24
            binding.setupScroll.smoothScrollTo(0, y.coerceAtLeast(0))
        }
    }

    private fun updateEmptyHint() {
        binding.emptyHint.visibility =
            if (nameInputs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun startGame() {
        val buyIn = binding.buyInInput.text?.toString()?.toIntOrNull()
        if (buyIn == null || buyIn <= 0) {
            Toast.makeText(requireContext(), R.string.error_invalid_amount, Toast.LENGTH_SHORT).show()
            return
        }
        val names = nameInputs.map { it.text?.toString()?.trim().orEmpty() }
        if (names.isEmpty()) {
            Toast.makeText(requireContext(), R.string.error_need_players, Toast.LENGTH_SHORT).show()
            return
        }
        if (names.any { it.isBlank() }) {
            Toast.makeText(requireContext(), R.string.error_empty_name, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.startGame(names, buyIn)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        nameInputs.clear()
    }
}
