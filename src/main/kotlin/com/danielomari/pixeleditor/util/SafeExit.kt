package com.danielomari.pixeleditor.util

import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/**
 * Action listener for the Exit button. Confirms before quitting and offers to
 * save first if there are unsaved changes.
 *
 * (Kotlin: interoperates with the Java [Save] helper and is instantiated from
 * the Java menu/keybinding code as `new SafeExit()`.)
 */
class SafeExit : ActionListener {

    override fun actionPerformed(e: ActionEvent) {
        val confirmQuit = JOptionPane.showConfirmDialog(
            null,
            "<html><b>Are you sure you want to exit the application?</b></html>",
            "Confirm exit",
            JOptionPane.YES_NO_OPTION,
        )
        if (confirmQuit != JOptionPane.YES_OPTION) return

        // Nothing to save: quit straight away.
        if (Save.getHasSavedRecently()) {
            exitProcess(0)
        }

        val confirmSave = JOptionPane.showConfirmDialog(
            null,
            "<html><b>Do you want to save your work before exiting?</b></html>",
            "Confirm save",
            JOptionPane.YES_NO_OPTION,
        )
        if (confirmSave == JOptionPane.YES_OPTION) {
            Save.saveImage()
        }
        exitProcess(0)
    }
}
