package com.chiptrack.app.share

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.chiptrack.app.R
import com.chiptrack.app.model.GameSession

object ShareHelper {
    fun shareSessionTable(fragment: Fragment, session: GameSession) {
        val context = fragment.requireContext()
        if (session.players.isEmpty()) {
            Toast.makeText(context, R.string.share_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val activity = fragment.requireActivity()
        Toast.makeText(context, R.string.share_generating, Toast.LENGTH_SHORT).show()

        ScoreboardImageExporter.capture(activity, session) { result ->
            result
                .onSuccess { uri -> launchShare(fragment, uri) }
                .onFailure {
                    Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun launchShare(fragment: Fragment, uri: Uri) {
        val context = fragment.requireContext()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.share_chooser_title))
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        fragment.startActivity(chooser)
    }
}
