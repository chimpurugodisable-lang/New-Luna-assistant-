package com.luna.assistant

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.MediaStore
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

data class ActionResult(val reply: Reply, val arg: String = "")

/** Carries out a command and says what happened. */
class Actions(private val ctx: Context) {

    fun run(cmd: Command): ActionResult = try {
        when (cmd) {
            is Command.OpenApp -> openApp(cmd.name)
            is Command.Call -> call(cmd.name)
            is Command.SendText -> sendText(cmd.name, cmd.message)
            is Command.NeedMessage -> ActionResult(Reply.NEED_MESSAGE, cmd.name)
            Command.Camera -> openCamera()
            Command.Unknown -> ActionResult(Reply.NOT_UNDERSTOOD)
        }
    } catch (e: SecurityException) {
        ActionResult(Reply.NO_PERMISSION)
    } catch (e: Exception) {
        ActionResult(Reply.FAILED)
    }

    private fun openApp(spoken: String): ActionResult {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { it.second != ctx.packageName }

        val want = TextNorm.squash(spoken)
        if (want.isEmpty()) return ActionResult(Reply.NOT_UNDERSTOOD)
        val match = bestMatch(apps, want) { TextNorm.squash(it.first) }
            ?: return ActionResult(Reply.NO_APP, spoken)

        val intent = pm.getLaunchIntentForPackage(match.second)
            ?: return ActionResult(Reply.NO_APP, spoken)
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ActionResult(Reply.OPENING, match.first)
    }

    private fun call(name: String): ActionResult {
        if (!granted(Manifest.permission.READ_CONTACTS) || !granted(Manifest.permission.CALL_PHONE)) {
            return ActionResult(Reply.NO_PERMISSION)
        }
        val contact = findContact(name) ?: return ActionResult(Reply.NO_CONTACT, name)
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(contact.second)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        return ActionResult(Reply.CALLING, contact.first)
    }

    private fun sendText(name: String, message: String): ActionResult {
        if (!granted(Manifest.permission.READ_CONTACTS) || !granted(Manifest.permission.SEND_SMS)) {
            return ActionResult(Reply.NO_PERMISSION)
        }
        val contact = findContact(name) ?: return ActionResult(Reply.NO_CONTACT, name)
        val sms: SmsManager =
            if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
        val parts = sms.divideMessage(message)
        sms.sendMultipartTextMessage(contact.second, null, parts, null, null)
        return ActionResult(Reply.SENT, contact.first)
    }

    private fun openCamera(): ActionResult {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            ActionResult(Reply.CAMERA)
        } catch (e: ActivityNotFoundException) {
            ActionResult(Reply.NO_APP, "camera")
        }
    }

    /** Returns (display name, phone number) of the closest contact. */
    private fun findContact(spoken: String): Pair<String, String>? {
        val want = TextNorm.squash(spoken)
        if (want.isEmpty()) return null
        val all = mutableListOf<Pair<String, String>>()
        ctx.contentResolver.query(
            Phone.CONTENT_URI, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                val num = c.getString(1) ?: continue
                all += n to num
            }
        }
        return bestMatch(all, want) { TextNorm.squash(it.first) }
    }

    /** Exact name first, then names that start with what was said, then names that contain it. */
    private fun <T> bestMatch(items: List<T>, want: String, key: (T) -> String): T? {
        val keyed = items.map { it to key(it) }.filter { it.second.isNotEmpty() }
        return keyed.firstOrNull { it.second == want }?.first
            ?: keyed.filter { it.second.startsWith(want) }.minByOrNull { it.second.length }?.first
            ?: keyed.filter { it.second.contains(want) }.minByOrNull { it.second.length }?.first
            ?: keyed.filter { want.contains(it.second) && it.second.length >= 3 }
                .maxByOrNull { it.second.length }?.first
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
}
