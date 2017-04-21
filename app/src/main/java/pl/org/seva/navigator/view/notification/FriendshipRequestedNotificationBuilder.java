package pl.org.seva.navigator.view.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.support.v7.app.NotificationCompat;

import pl.org.seva.navigator.R;

public final class FriendshipRequestedNotificationBuilder  {

    private Context context;
    private PendingIntent yesPi;
    private PendingIntent noPi;
    private String message;

    public FriendshipRequestedNotificationBuilder(Context context) {
        this.context = context;
    }

    public FriendshipRequestedNotificationBuilder setYesPendingIntent(PendingIntent yesPi) {
        this.yesPi = yesPi;
        return this;
    }

    public FriendshipRequestedNotificationBuilder setMessage(String message) {
        this.message = message;
        return this;
    }

    public FriendshipRequestedNotificationBuilder setNoPendingIntent(PendingIntent noPi) {
        this.noPi = noPi;
        return this;
    }

    public Notification build() {
        // http://stackoverflow.com/questions/6357450/android-multiline-notifications-notifications-with-longer-text#22964072
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(context.getString(R.string.app_name));
        bigTextStyle.bigText(message);

        // http://stackoverflow.com/questions/11883534/how-to-dismiss-notification-after-action-has-been-clicked#11884313
        return new NotificationCompat.Builder(context)
                .setStyle(bigTextStyle)
                .setContentText(context.getText(R.string.friendship_confirmation_short))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(false)
                .addAction(R.drawable.ic_close_black_24dp, context.getString(android.R.string.no), noPi)
                .addAction(R.drawable.ic_check_black_24dp, context.getString(android.R.string.yes), yesPi)
                .build();
    }
}
