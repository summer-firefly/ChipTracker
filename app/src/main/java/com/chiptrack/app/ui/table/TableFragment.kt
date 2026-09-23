package com.chiptrack.app.ui.table

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
import com.chiptrack.app.databinding.FragmentTableBinding
import com.chiptrack.app.model.Player
import com.chiptrack.app.model.SessionPhase
import com.chiptrack.app.share.ShareHelper
import com.chiptrack.app.ui.history.HistoryFragment
import com.chiptrack.app.ui.navigateOnce
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class TableFragment : Fragment() {
    private var _binding: FragmentTableBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GameSessionViewModel by activityViewModels()
    private lateinit var adapter: TablePlayersAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTableBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = TablePlayersAdapter(
            onTake = { showPointsDialog(it, isTake = true) },
            onReturn = { showPointsDialog(it, isTake = false) },
            onExit = { confirmExit(it) },
            onHistory = { openPlayerHistory(it) }
        )
        binding.playersList.layoutManager = LinearLayoutManager(requireContext())
        binding.playersList.adapter = adapter

        binding.addPlayerButton.setOnClickListener { showAddPlayerDialog() }
        binding.historyButton.setOnClickListener {
            findNavController().navigate(R.id.action_table_to_history, HistoryFragment.args())
        }
        binding.settleButton.setOnClickListener {
            findNavController().navigate(R.id.action_table_to_final_return)
        }
        binding.shareButton.setOnClickListener {
            val session = viewModel.session.value ?: return@setOnClickListener
            ShareHelper.shareSessionTable(this, session)
        }

        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.consumeMessage()
            }
        }
        viewModel.session.observe(viewLifecycleOwner) { session ->
            when (session.phase) {
                SessionPhase.SETUP -> navigateOnce(R.id.action_table_to_setup)
                SessionPhase.SETTLED -> navigateOnce(R.id.action_table_to_settle)
                SessionPhase.PLAYING -> {
                    binding.playerCountText.text =
                        getString(R.string.table_player_count, session.activePlayers.size)
                    binding.takenSumText.text = session.totalTakenSum.toString()
                    binding.returnedSumText.text = session.totalReturnedSum.toString()
                    adapter.submitList(buildRows(session.activePlayers, session.exitedPlayers))
                }
            }
        }
    }

    private fun openPlayerHistory(player: Player) {
        findNavController().navigate(
            R.id.action_table_to_history,
            HistoryFragment.args(player.id, player.name)
        )
    }

    private fun buildRows(active: List<Player>, exited: List<Player>): List<TableRow> {
        val rows = mutableListOf<TableRow>()
        if (active.isNotEmpty()) {
            rows += TableRow.Header(R.string.section_active)
            rows += active.map { TableRow.PlayerRow(it) }
        }
        if (exited.isNotEmpty()) {
            rows += TableRow.Header(R.string.section_exited)
            rows += exited.map { TableRow.PlayerRow(it) }
        }
        return rows
    }

    private fun showPointsDialog(player: Player, isTake: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_points_adjust, null)
        val quick1000 = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.quick1000Button)
        val quick2000 = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.quick2000Button)
        val customInput = dialogView.findViewById<TextInputEditText>(R.id.customPointsInput)

        fun applyPoints(amount: Int) {
            if (amount <= 0) {
                Toast.makeText(requireContext(), R.string.error_invalid_amount, Toast.LENGTH_SHORT).show()
                return
            }
            if (isTake) viewModel.take(player.id, amount)
            else viewModel.returnPoints(player.id, amount)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (isTake) R.string.dialog_amount_title_buy_in
                else R.string.dialog_amount_title_cash_out
            )
            .setMessage(player.name)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val amount = customInput.text?.toString()?.toIntOrNull()
                if (amount == null) {
                    Toast.makeText(requireContext(), R.string.error_invalid_amount, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                applyPoints(amount)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        quick1000.setOnClickListener {
            applyPoints(1000)
            dialog.dismiss()
        }
        quick2000.setOnClickListener {
            applyPoints(2000)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun confirmExit(player: Player) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_exit_title)
            .setMessage(getString(R.string.dialog_exit_message, player.name))
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                viewModel.exitPlayer(player.id)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.dialog_exit_toast, player.name),
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showAddPlayerDialog() {
        val defaultBuyIn = viewModel.session.value?.defaultBuyIn ?: 1000
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.dialog_add_name_hint)
        }
        val nameEdit = TextInputEditText(nameLayout.context).apply { setSingleLine() }
        nameLayout.addView(nameEdit)

        val buyInLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.dialog_add_buy_in_hint, defaultBuyIn)
        }
        val buyInEdit = TextInputEditText(buyInLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(defaultBuyIn.toString())
        }
        buyInLayout.addView(buyInEdit)

        container.addView(nameLayout)
        container.addView(buyInLayout)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_add_player_title)
            .setView(container)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val name = nameEdit.text?.toString()?.trim().orEmpty()
                val buyIn = buyInEdit.text?.toString()?.toIntOrNull() ?: defaultBuyIn
                if (name.isBlank()) {
                    Toast.makeText(requireContext(), R.string.error_empty_name, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (buyIn <= 0) {
                    Toast.makeText(requireContext(), R.string.error_invalid_amount, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.addPlayer(name, buyIn)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
