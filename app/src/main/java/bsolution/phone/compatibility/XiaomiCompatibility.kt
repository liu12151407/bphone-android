/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package bsolution.phone.compatibility

import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import bsolution.phone.LinphoneApplication.Companion.coreContext
import bsolution.phone.LinphoneApplication.Companion.corePreferences
import bsolution.phone.R
import bsolution.phone.contact.getThumbnailUri
import bsolution.phone.notifications.Notifiable
import bsolution.phone.notifications.NotificationsManager
import bsolution.phone.utils.ImageUtils
import bsolution.phone.utils.LinphoneUtils
import org.linphone.core.Call

@TargetApi(26)
class XiaomiCompatibility {
    companion object {
        fun createIncomingCallNotification(
            context: Context,
            call: Call,
            notifiable: Notifiable,
            pendingIntent: PendingIntent,
            notificationsManager: NotificationsManager
        ): Notification {
            val contact = coreContext.contactsManager.findContactByAddress(call.remoteAddress)
            val roundPicture = ImageUtils.getRoundBitmapFromUri(context, contact?.getThumbnailUri())
            val displayName = contact?.name ?: LinphoneUtils.getDisplayName(call.remoteAddress)
            val address = LinphoneUtils.getDisplayableAddress(call.remoteAddress)

            val builder = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_incoming_call_id))
                .addPerson(notificationsManager.getPerson(contact, displayName, roundPicture))
                .setSmallIcon(R.drawable.topbar_call_notification)
                .setLargeIcon(roundPicture ?: BitmapFactory.decodeResource(context.resources, R.drawable.avatar))
                .setContentTitle(displayName)
                .setContentText(address)
                .setSubText(context.getString(R.string.incoming_call_notification_title))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .setShowWhen(true)
                .setOngoing(true)
                .setColor(ContextCompat.getColor(context, R.color.primary_color))
                .setFullScreenIntent(pendingIntent, true)
                .addAction(notificationsManager.getCallDeclineAction(notifiable))
                .addAction(notificationsManager.getCallAnswerAction(notifiable))

            if (!corePreferences.preventInterfaceFromShowingUp) {
                builder.setContentIntent(pendingIntent)
            }

            return builder.build()
        }
    }
}
