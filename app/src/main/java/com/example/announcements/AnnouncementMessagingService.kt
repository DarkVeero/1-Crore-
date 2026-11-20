package com.example.announcements

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking

class AnnouncementMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AnnouncementNotificationHelper.ensureSubscribedToAnnouncementsTopic(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val parsedItem = AnnouncementParser.parseFromFcmData(
            messageId = message.messageId,
            notificationTitle = message.notification?.title,
            notificationBody = message.notification?.body,
            data = message.data
        ) ?: return

        runBlocking {
            AnnouncementNotificationHelper.handleIncomingFcmAnnouncement(
                context = applicationContext,
                item = parsedItem
            )
        }
    }
}
