package org.proyecto.ricardo.p2ptrain;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.MessageContext;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.SessionListener;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.SignalEmitter;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.annotation.BusSignalHandler;

public class AllJoynService extends Service implements Observer {
    private static final String TAG = "chat.AllJoynService";

    /**
     * We don't chat the bindery to communiate between any client and this
     * service so we return null.
     */
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind()");
        return null;
    }

    /**
     * Our onCreate() method is called by the Android application framework
     * when the service is first created.  We spin up a background thread
     * to handle any long-lived requests (pretty much all AllJoyn calls that
     * involve communication with remote processes) that need to be done and
     * insinuate ourselves into the custom_list of observers of the model so we can
     * get event notifications.
     */
    public void onCreate() {
        Log.i(TAG, "onCreate()");
        startBusThread();
        mTrainApplication = (TrainApplication)getApplication();
        mTrainApplication.addObserver(this);

        CharSequence title = "P2PTrain";
        CharSequence message = "Red P2P conectada";
        Intent intent = new Intent(this, TabWidget.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        Notification notification = new Notification(R.drawable.icon, null, System.currentTimeMillis());
        notification.setLatestEventInfo(this, title, message, pendingIntent);
        notification.flags |= Notification.DEFAULT_SOUND | Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;

        Log.i(TAG, "onCreate(): startForeground()");
        startForeground(NOTIFICATION_ID, notification);

        /*
         * We have an AllJoyn handler thread running at this time, so take
         * advantage of the fact to get connected to the bus and start finding
         * remote channel instances in the background while the rest of the app
         * is starting up.
         */
        mBackgroundHandler.connect();
        mBackgroundHandler.startDiscovery();
    }

    private static final int NOTIFICATION_ID = 0xdefaced;

    /**
     * Our onDestroy() is called by the Android appliation framework when it
     * decides that our Service is no longer needed.  We tell our background
     * thread to exit andremove ourselves from the custom_list of observers of the
     * model.
     */
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        mBackgroundHandler.cancelDiscovery();
        mBackgroundHandler.disconnect();
        stopBusThread();
        mTrainApplication.deleteObserver(this);
    }

    /**
     * The method onStartCommand() is called by the Android application
     * framework when a client explicitly starts a Service by calling
     * startService().  We expect that the only place this is going to be done
     * is when the Android Application class for our application is created.
     * The Appliation class provides our model in the sense of the MVC
     * application we really are.
     *
     * We return START_STICKY to enable us to be explicity started and stopped
     * which means that our Service will essentially run "forever" (or until
     * Android decides that we should die for resource management issues) since
     * our Application class is left running as long as the process is left running.
     */
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand()");
        return START_STICKY;
    }

    /**
     * A reference to a descendent of the Android Application class that is
     * acting as the Model of our MVC-based application.
     */
    private TrainApplication mTrainApplication = null;

    /**
     * This is the event handler for the Observable/Observed design pattern.
     * Whenever an interesting event happens in our appliation, the Model (the
     * source of the event) notifies registered observers, resulting in this
     * method being called since we registered as an Observer in onCreate().
     *
     * This method will be called in the context of the Model, which is, in
     * turn the context of an event source.  This will either be the single
     * Android application framework thread if the source is one of the
     * Activities of the application or the Service.  It could also be in the
     * context of the Service background thread.  Since the Android Application
     * framework is a fundamentally single threaded thing, we avoid multithread
     * issues and deadlocks by immediately getting this event into a separate
     * execution in the context of the Service message pump.
     *
     * We do this by taking the event from the calling component and queueing
     * it onto a "handler" in our Service and returning to the caller.  When
     * the calling componenet finishes what ever caused the event notification,
     * we expect the Android application framework to notice our pending
     * message and run our handler in the context of the single application
     * thread.
     *
     * In reality, both events are executed in the context of the single
     * Android thread.
     */
    public synchronized void update(Observable o, Object arg) {
        Log.i(TAG, "update(" + arg + ")");
        String qualifier = (String)arg;

        if (qualifier.equals(TrainApplication.APPLICATION_QUIT_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_APPLICATION_QUIT_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(TrainApplication.CHAT_JOIN_CHANNEL_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_USE_JOIN_CHANNEL_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(TrainApplication.CHAT_LEAVE_CHANNEL_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_USE_LEAVE_CHANNEL_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(TrainApplication.HOST_INIT_CHANNEL_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_HOST_INIT_CHANNEL_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(TrainApplication.HOST_START_CHANNEL_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_HOST_START_CHANNEL_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(TrainApplication.HOST_STOP_CHANNEL_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_HOST_STOP_CHANNEL_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(TrainApplication.OUTBOUND_CHANGED_EVENT)) {

            Message message = mHandler.obtainMessage(HANDLE_OUTBOUND_CHANGED_EVENT);
            mHandler.sendMessage(message);
        }
    }

    /**
     * This is the Android Service message handler.  It runs in the context of the
     * tab_main Android Service thread, which is also shared with Activities since
     * Android is a fundamentally single-threaded system.
     *
     * The important thing for us is to note that this thread cannot be blocked for
     * a significant amount of time or we risk the dreaded "force close" message.
     * We can run relatively short-lived operations here, but we need to run our
     * distributed system calls in a background thread.
     *
     * This handler serves translates from UI-related events into AllJoyn events
     * and decides whether functions can be handled in the context of the
     * Android tab_main thread or if they must be dispatched to a background thread
     * which can take as much time as needed to accomplish a task.
     */
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HANDLE_APPLICATION_QUIT_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): APPLICATION_QUIT_EVENT");
                    mBackgroundHandler.leaveSession();
                    mBackgroundHandler.cancelAdvertise();
                    mBackgroundHandler.unbindSession();
                    mBackgroundHandler.releaseName();
                    mBackgroundHandler.exit();
                    stopSelf();
                }
                break;
            case HANDLE_USE_JOIN_CHANNEL_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): USE_JOIN_CHANNEL_EVENT");
                    mBackgroundHandler.joinSession();
                }
                break;
            case HANDLE_USE_LEAVE_CHANNEL_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): USE_LEAVE_CHANNEL_EVENT");
                    mBackgroundHandler.leaveSession();
                }
                break;
            case HANDLE_HOST_INIT_CHANNEL_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): HOST_INIT_CHANNEL_EVENT");
                }
                break;
            case HANDLE_HOST_START_CHANNEL_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): HOST_START_CHANNEL_EVENT");
                    mBackgroundHandler.requestName();
                    mBackgroundHandler.bindSession();
                    mBackgroundHandler.advertise();
                }
                break;
            case HANDLE_HOST_STOP_CHANNEL_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): HOST_STOP_CHANNEL_EVENT");
                    mBackgroundHandler.cancelAdvertise();
                    mBackgroundHandler.unbindSession();
                    mBackgroundHandler.releaseName();
                }
                break;
            case HANDLE_OUTBOUND_CHANGED_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): OUTBOUND_CHANGED_EVENT");
                    mBackgroundHandler.sendMessages();
                }
                break;

            default:
                break;
            }
        }
    };

    /**
     * Value for the HANDLE_APPLICATION_QUIT_EVENT case observer notification handler.
     */
    private static final int HANDLE_APPLICATION_QUIT_EVENT = 0;

    /**
     * Value for the HANDLE_USE_JOIN_CHANNEL_EVENT case observer notification handler.
     */
    private static final int HANDLE_USE_JOIN_CHANNEL_EVENT = 1;

    /**
     * Value for the HANDLE_USE_LEAVE_CHANNEL_EVENT case observer notification handler.
     */
    private static final int HANDLE_USE_LEAVE_CHANNEL_EVENT = 2;

    /**
     * Value for the HANDLE_HOST_INIT_CHANNEL_EVENT case observer notification handler.
     */
    private static final int HANDLE_HOST_INIT_CHANNEL_EVENT = 3;

    /**
     * Value for the HANDLE_HOST_START_CHANNEL_EVENT case observer notification handler.
     */
    private static final int HANDLE_HOST_START_CHANNEL_EVENT = 4;

    /**
     * Value for the HANDLE_HOST_STOP_CHANNEL_EVENT case observer notification handler.
     */
    private static final int HANDLE_HOST_STOP_CHANNEL_EVENT = 5;

    /**
     * Value for the HANDLE_OUTBOUND_CHANGED_EVENT case observer notification handler.
     */
    private static final int HANDLE_OUTBOUND_CHANGED_EVENT = 6;

    /**
     * Enumeration of the states of the AllJoyn bus attachment.  This
     * lets us make a note to ourselves regarding where we are in the process
     * of preparing and tearing down the fundamental connection to the AllJoyn
     * bus.
     *
     * This should really be a more private think, but for the sample we want
     * to show the user the states we are running through.  Because we are
     * really making a data hiding exception, and because we trust ourselves,
     * we don't go to any effort to prevent the UI from changing our state out
     * from under us.
     *
     * There are separate variables describing the states of the client
     * ("chat") and service ("host") pieces.
     */
    public static enum BusAttachmentState {
        DISCONNECTED,    /** The bus attachment is not connected to the AllJoyn bus */
        CONNECTED,        /** The  bus attachment is connected to the AllJoyn bus */
        DISCOVERING        /** The bus attachment is discovering remote attachments hosting chat channels */
    }

    /**
     * The state of the AllJoyn bus attachment.
     */
    private BusAttachmentState mBusAttachmentState = BusAttachmentState.DISCONNECTED;

    /**
     * Enumeration of the states of a hosted chat channel.  This lets us make a
     * note to ourselves regarding where we are in the process of preparing
     * and tearing down the AllJoyn pieces responsible for providing the chat
     * service.  In order to be out of the IDLE state, the BusAttachment state
     * must be at least CONNECTED.
     */
    public static enum HostChannelState {
        IDLE,            /** There is no hosted chat channel */
        NAMED,            /** The well-known name for the channel has been successfully acquired */
        BOUND,            /** A session port has been bound for the channel */
        ADVERTISED,        /** The bus attachment has advertised itself as hosting an chat channel */
        CONNECTED       /** At least one remote device has connected to a session on the channel */
    }

    /**
     * The state of the AllJoyn components responsible for hosting an chat channel.
     */
    private HostChannelState mHostChannelState = HostChannelState.IDLE;

    /**
     * Enumeration of the states of a hosted chat channel.  This lets us make a
     * note to ourselves regarding where we are in the process of preparing
     * and tearing down the AllJoyn pieces responsible for providing the chat
     * service.  In order to be out of the IDLE state, the BusAttachment state
     * must be at least CONNECTED.
     */
    public static enum ChatChannelState {
        IDLE,            /** There is no used chat channel */
        JOINED,            /** The session for the channel has been successfully joined */
    }

    /**
     * The state of the AllJoyn components responsible for hosting an chat channel.
     */
    private ChatChannelState mChatChannelState = ChatChannelState.IDLE;

    /**
     * This is the AllJoyn background thread handler class.  AllJoyn is a
     * distributed system and must therefore make calls to other devices over
     * networks.  These calls may take arbitrary amounts of time.  The Android
     * application framework is fundamentally single-threaded and so the tab_main
     * Service thread that is executing in our component is the same thread as
     * the ones which appear to be executing the user interface code in the
     * other Activities.  We cannot block this thread while waiting for a
     * network to respond, so we need to run our calls in the context of a
     * background thread.  This is the class that provides that background
     * thread implementation.
     *
     * When we need to do some possibly long-lived task, we just pass a message
     * to an object implementing this class telling it what needs to be done.
     * There are two tab_main parts to this class:  an external API and the actual
     * handler.  In order to make life easier for callers, we provide API
     * methods to deal with the actual message passing, and then when the
     * handler thread is executing the desired method, it calls out to an
     * implementation in the enclosing class.  For example, in order to perform
     * a connect() operation in the background, the enclosing class calls
     * BackgroundHandler.connect(); and the result is that the enclosing class
     * method doConnect() will be called in the context of the background
     * thread.
     */
    private final class BackgroundHandler extends Handler {
        public BackgroundHandler(Looper looper) {
            super(looper);
        }

        /**
         * Exit the background handler thread.  This will be the last message
         * executed by an instance of the handler.
         */
        public void exit() {
            Log.i(TAG, "mBackgroundHandler.exit()");
            Message msg = mBackgroundHandler.obtainMessage(EXIT);
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * Connect the application to the Alljoyn bus attachment.  We expect
         * this method to be called in the context of the tab_main Service thread.
         * All this method does is to dispatch a corresponding method in the
         * context of the service worker thread.
         */
        public void connect() {
            Log.i(TAG, "mBackgroundHandler.connect()");
            Message msg = mBackgroundHandler.obtainMessage(CONNECT);
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * Disonnect the application from the Alljoyn bus attachment.  We
         * expect this method to be called in the context of the tab_main Service
         * thread.  All this method does is to dispatch a corresponding method
         * in the context of the service worker thread.
         */
        public void disconnect() {
            Log.i(TAG, "mBackgroundHandler.disconnect()");
            Message msg = mBackgroundHandler.obtainMessage(DISCONNECT);
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * Start discovering remote instances of the application.  We expect
         * this method to be called in the context of the tab_main Service thread.
         * All this method does is to dispatch a corresponding method in the
         * context of the service worker thread.
         */
        public void startDiscovery() {
            Log.i(TAG, "mBackgroundHandler.startDiscovery()");
            Message msg = mBackgroundHandler.obtainMessage(START_DISCOVERY);
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * Stop discovering remote instances of the application.  We expect
         * this method to be called in the context of the tab_main Service thread.
         * All this method does is to dispatch a corresponding method in the
         * context of the service worker thread.
         */
        public void cancelDiscovery() {
            Log.i(TAG, "mBackgroundHandler.stopDiscovery()");
            Message msg = mBackgroundHandler.obtainMessage(CANCEL_DISCOVERY);
            mBackgroundHandler.sendMessage(msg);
        }

        public void requestName() {
            Log.i(TAG, "mBackgroundHandler.requestName()");
            Message msg = mBackgroundHandler.obtainMessage(REQUEST_NAME);
            mBackgroundHandler.sendMessage(msg);
        }

        public void releaseName() {
            Log.i(TAG, "mBackgroundHandler.releaseName()");
            Message msg = mBackgroundHandler.obtainMessage(RELEASE_NAME);
            mBackgroundHandler.sendMessage(msg);
        }

        public void bindSession() {
            Log.i(TAG, "mBackgroundHandler.bindSession()");
            Message msg = mBackgroundHandler.obtainMessage(BIND_SESSION);
            mBackgroundHandler.sendMessage(msg);
        }

        public void unbindSession() {
            Log.i(TAG, "mBackgroundHandler.unbindSession()");
            Message msg = mBackgroundHandler.obtainMessage(UNBIND_SESSION);
            mBackgroundHandler.sendMessage(msg);
        }

        public void advertise() {
            Log.i(TAG, "mBackgroundHandler.advertise()");
            Message msg = mBackgroundHandler.obtainMessage(ADVERTISE);
            mBackgroundHandler.sendMessage(msg);
        }

        public void cancelAdvertise() {
            Log.i(TAG, "mBackgroundHandler.cancelAdvertise()");
            Message msg = mBackgroundHandler.obtainMessage(CANCEL_ADVERTISE);
            mBackgroundHandler.sendMessage(msg);
        }

        public void joinSession() {
            Log.i(TAG, "mBackgroundHandler.joinSession()");
            Message msg = mBackgroundHandler.obtainMessage(JOIN_SESSION);
            mBackgroundHandler.sendMessage(msg);
        }

        public void leaveSession() {
            Log.i(TAG, "mBackgroundHandler.leaveSession()");
            Message msg = mBackgroundHandler.obtainMessage(LEAVE_SESSION);
            mBackgroundHandler.sendMessage(msg);
        }

        public void sendMessages() {
            Log.i(TAG, "mBackgroundHandler.sendMessages()");
            Message msg = mBackgroundHandler.obtainMessage(SEND_MESSAGES);
            mBackgroundHandler.sendMessage(msg);
        }


        /**
         * The message handler for the worker thread that handles background
         * tasks for the AllJoyn bus.
         */
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case CONNECT:
                doConnect();
                break;
            case DISCONNECT:
                doDisconnect();
                break;
            case START_DISCOVERY:
                doStartDiscovery();
                break;
            case CANCEL_DISCOVERY:
                doStopDiscovery();
                break;
            case REQUEST_NAME:
                doRequestName();
                break;
            case RELEASE_NAME:
                doReleaseName();
                break;
            case BIND_SESSION:
                doBindSession();
                break;
            case UNBIND_SESSION:
                doUnbindSession();
                break;
            case ADVERTISE:
                doAdvertise();
                break;
            case CANCEL_ADVERTISE:
                doCancelAdvertise();
                break;
            case JOIN_SESSION:
                doJoinSession();
                break;
            case LEAVE_SESSION:
                doLeaveSession();
                break;
            case SEND_MESSAGES:
                doSendMessages();
                break;
            case EXIT:
                getLooper().quit();
                break;
            default:
                break;
            }
        }
    }

    private static final int EXIT = 1;
    private static final int CONNECT = 2;
    private static final int DISCONNECT = 3;
    private static final int START_DISCOVERY = 4;
    private static final int CANCEL_DISCOVERY = 5;
    private static final int REQUEST_NAME = 6;
    private static final int RELEASE_NAME = 7;
    private static final int BIND_SESSION = 8;
    private static final int UNBIND_SESSION = 9;
    private static final int ADVERTISE = 10;
    private static final int CANCEL_ADVERTISE = 11;
    private static final int JOIN_SESSION = 12;
    private static final int LEAVE_SESSION = 13;
    private static final int SEND_MESSAGES = 14;

    /**
     * The instance of the AllJoyn background thread handler.  It is created
     * when Android decides the Service is needed and is called from the
     * onCreate() method.  When Android decides our Service is no longer
     * needed, it will call onDestroy(), which spins down the thread.
     */
    private BackgroundHandler mBackgroundHandler = null;

    /**
     * Since basically our whole reason for being is to spin up a thread to
     * handle long-lived remote operations, we provide thsi method to do so.
     */
    private void startBusThread() {
        HandlerThread busThread = new HandlerThread("BackgroundHandler");
        busThread.start();
        mBackgroundHandler = new BackgroundHandler(busThread.getLooper());
    }

    /**
     * When Android decides that our Service is no longer needed, we need to
     * tear down the thread that is servicing our long-lived remote operations.
     * This method does so.
     */
    private void stopBusThread() {
        mBackgroundHandler.exit();
    }

    /**
     * The bus attachment is the object that provides AllJoyn services to Java
     * clients.  Pretty much all communiation with AllJoyn is going to go through
     * this obejct.
     */
    private BusAttachment mBus  = new BusAttachment(TrainApplication.PACKAGE_NAME, BusAttachment.RemoteMessage.Receive);

    /**
     * The well-known name prefix which all bus attachments hosting a channel
     * will chat.  The NAME_PREFIX and the channel name are composed to give
     * the well-known name a hosting bus attachment will request and
     * advertise.
     */
    private static final String NAME_PREFIX = "org.proyecto.ricardo.p2ptrain";

    /**
     * The well-known session port used as the contact port for the chat service.
     */
    private static final short CONTACT_PORT = 27;

    /**
     * The object path used to identify the service "location" in the bus
     * attachment.
     */
    private static final String OBJECT_PATH = "/p2pTrainService";

    /**
     * The TrainBusListener is a class that listens to the AllJoyn bus for
     * notifications corresponding to the existence of events happening out on
     * the bus.  We provide one implementation of our listener to the bus
     * attachment during the connect().
     */
    private class TrainBusListener extends BusListener {
        /**
         * This method is called when AllJoyn discovers a remote attachment
         * that is hosting an chat channel.  We expect that since we only
         * do a findAdvertisedName looking for instances of the chat
         * well-known name prefix we will only find names that we know to
         * be interesting.  When we find a remote application that is
         * hosting a channel, we add its channel name it to the custom_list of
         * available channels selectable by the user.
         *
         * In the class documentation for the BusListener note that it is a
         * requirement for this method to be multithread safe.  This is
         * accomplished by the chat of a monitor on the TrainApplication as
         * exemplified by the synchronized attribute of the addFoundChannel
         * method there.
         */
        public void foundAdvertisedName(String name, short transport, String namePrefix) {
            Log.i(TAG, "mBusListener.foundAdvertisedName(" + name + ")");
            TrainApplication application = (TrainApplication)getApplication();
            application.addFoundChannel(name);
        }

        /**
         * This method is called when AllJoyn decides that a remote bus
         * attachment that is hosting an chat channel is no longer available.
         * When we lose a remote application that is hosting a channel, we
         * remote its name from the custom_list of available channels selectable
         * by the user.
         *
         * In the class documentation for the BusListener note that it is a
         * requirement for this method to be multithread safe.  This is
         * accomplished by the chat of a monitor on the TrainApplication as
         * exemplified by the synchronized attribute of the removeFoundChannel
         * method there.
         */
        public void lostAdvertisedName(String name, short transport, String namePrefix) {
            Log.i(TAG, "mBusListener.lostAdvertisedName(" + name + ")");
            TrainApplication application = (TrainApplication)getApplication();
            application.removeFoundChannel(name);
        }
    }

    /**
     * An instance of an AllJoyn bus listener that knows what to do with
     * foundAdvertisedName and lostAdvertisedName notifications.  Although
     * we often chat the anonymous class idiom when talking to AllJoyn, the
     * bus listener works slightly differently and it is better to chat an
     * explicitly declared class in this case.
     */
    private TrainBusListener mBusListener = new TrainBusListener();

    /**
     * Implementation of the functionality related to connecting our app
     * to the AllJoyn bus.  We expect that this method will only be called in
     * the context of the AllJoyn bus handler thread; and while we are in the
     * DISCONNECTED state.
     */
    private void doConnect() {
        Log.i(TAG, "doConnect()");
        org.alljoyn.bus.alljoyn.DaemonInit.PrepareDaemon(getApplicationContext());
        assert(mBusAttachmentState == BusAttachmentState.DISCONNECTED);
        mBus.useOSLogging(true);
        mBus.setDebugLevel("ALLJOYN_JAVA", 7);
        mBus.registerBusListener(mBusListener);

        /*
         * To make a service available to other AllJoyn peers, first
         * register a BusObject with the BusAttachment at a specific
         * object path.  Our service is implemented by the TrainService
         * BusObject found at the "/p2pTrainService" object path.
         */
        Status status = mBus.registerBusObject(mTrainService, OBJECT_PATH);
        if (Status.OK != status) {
            mTrainApplication.alljoynError(TrainApplication.Module.HOST, "Incapaz de registrar el bus de chat: (" + status + ")");
            return;
        }

        status = mBus.connect();
        if (status != Status.OK) {
            mTrainApplication.alljoynError(TrainApplication.Module.GENERAL, "Incapaz de conectar al bus: (" + status + ")");
            return;
        }

        status = mBus.registerSignalHandlers(this);
        if (status != Status.OK) {
            mTrainApplication.alljoynError(TrainApplication.Module.GENERAL, "Incapaz de registrar manejadores de señales: (" + status + ")");
            return;
        }

        mBusAttachmentState = BusAttachmentState.CONNECTED;
    }

    /**
     * Implementation of the functionality related to disconnecting our app
     * from the AllJoyn bus.  We expect that this method will only be called
     * in the context of the AllJoyn bus handler thread.  We expect that this
     * method will only be called in the context of the AllJoyn bus handler
     * thread; and while we are in the CONNECTED state.
     */
    private boolean doDisconnect() {
        Log.i(TAG, "doDisonnect()");
        assert(mBusAttachmentState == BusAttachmentState.CONNECTED);
        mBus.unregisterBusListener(mBusListener);
        mBus.disconnect();
        mBusAttachmentState = BusAttachmentState.DISCONNECTED;
        return true;
    }

    /**
     * Implementation of the functionality related to discovering remote apps
     * which are hosting chat channels.  We expect that this method will only
     * be called in the context of the AllJoyn bus handler thread; and while
     * we are in the CONNECTED state.  Since this is a core bit of functionalty
     * for the "chat" side of the app, we always do this at startup.
     */
    private void doStartDiscovery() {
        Log.i(TAG, "doStartDiscovery()");
        assert(mBusAttachmentState == BusAttachmentState.CONNECTED);
        Status status = mBus.findAdvertisedName(NAME_PREFIX);
        if (status == Status.OK) {
            mBusAttachmentState = BusAttachmentState.DISCOVERING;
            return;
        } else {
            mTrainApplication.alljoynError(TrainApplication.Module.USE, "Incapaz de empezar a encontrar nombres anunciados: (" + status + ")");
            return;
        }
    }

    /**
     * Implementation of the functionality related to stopping discovery of
     * remote apps which are hosting chat channels.
     */
    private void doStopDiscovery() {
        Log.i(TAG, "doStopDiscovery()");
        assert(mBusAttachmentState == BusAttachmentState.CONNECTED);
        mBus.cancelFindAdvertisedName(NAME_PREFIX);
        mBusAttachmentState = BusAttachmentState.CONNECTED;
    }

    /**
     * Implementation of the functionality related to requesting a well-known
     * name from an AllJoyn bus attachment.
     */
    private void doRequestName() {
        Log.i(TAG, "doRequestName()");

        /*
         * In order to request a name, the bus attachment must at least be
         * connected.
         */
        int stateRelation = mBusAttachmentState.compareTo(BusAttachmentState.DISCONNECTED);
        assert (stateRelation >= 0);

        /*
         * We depend on the user interface and model to work together to not
         * get this process started until a valid name is set in the channel name.
         */
        String wellKnownName = NAME_PREFIX + "." + mTrainApplication.hostGetChannelName();
        Status status = mBus.requestName(wellKnownName, BusAttachment.ALLJOYN_REQUESTNAME_FLAG_DO_NOT_QUEUE);
        if (status == Status.OK) {
            mHostChannelState = HostChannelState.NAMED;
            mTrainApplication.hostSetChannelState(mHostChannelState);
        } else {
            mTrainApplication.alljoynError(TrainApplication.Module.USE, "Incapaz de adquirir id: (" + status + ")");
        }
    }

    /**
     * Implementation of the functionality related to releasing a well-known
     * name from an AllJoyn bus attachment.
     */
    private void doReleaseName() {
        Log.i(TAG, "doReleaseName()");

        /*
         * In order to release a name, the bus attachment must at least be
         * connected.
         */
        int stateRelation = mBusAttachmentState.compareTo(BusAttachmentState.DISCONNECTED);
        assert (stateRelation >= 0);
        assert(mBusAttachmentState == BusAttachmentState.CONNECTED || mBusAttachmentState == BusAttachmentState.DISCOVERING);

        /*
         * We need to progress monotonically down the hosted channel states
         * for sanity.
         */
        assert(mHostChannelState == HostChannelState.NAMED);

        /*
         * We depend on the user interface and model to work together to not
         * change the name out from under us while we are running.
         */
        String wellKnownName = NAME_PREFIX + "." + mTrainApplication.hostGetChannelName();

        /*
         * There's not a lot we can do if the bus attachment refuses to release
         * the name.  It is not a fatal error, though, if it doesn't.  This is
         * because bus attachments can have multiple names.
         */
        mBus.releaseName(wellKnownName);
        mHostChannelState = HostChannelState.IDLE;
        mTrainApplication.hostSetChannelState(mHostChannelState);
    }

    /**
     * Implementation of the functionality related to binding a session port
     * to an AllJoyn bus attachment.
     */
    private void doBindSession() {
        Log.i(TAG, "doBindSession()");

        Mutable.ShortValue contactPort = new Mutable.ShortValue(CONTACT_PORT);
        SessionOpts sessionOpts = new SessionOpts(SessionOpts.TRAFFIC_MESSAGES, true, SessionOpts.PROXIMITY_ANY, SessionOpts.TRANSPORT_ANY);

        Status status = mBus.bindSessionPort(contactPort, sessionOpts, new SessionPortListener() {
            /**
             * This method is called when a client tries to join the session
             * we have bound.  It asks us if we want to accept the client into
             * our session.
             *
             * In the class documentation for the SessionPortListener note that
             * it is a requirement for this method to be multithread safe.
             * Since we never access any shared state, this requirement is met.
             */
            public boolean acceptSessionJoiner(short sessionPort, String joiner, SessionOpts sessionOpts) {
                Log.i(TAG, "SessionPortListener.acceptSessionJoiner(" + sessionPort + ", " + joiner + ", " + sessionOpts.toString() + ")");

                /*
                 * Accept anyone who can get our contact port correct.
                 */
                if (sessionPort == CONTACT_PORT) {
                    return true;
                }
                return false;
            }

            /**
             * If we return true in acceptSessionJoiner, we admit a new client
             * into our session.  The session does not really exist until a
             * client joins, at which time the session is created and a session
             * ID is assigned.  This method communicates to us that this event
             * has happened, and provides the new session ID for us to chat.
             *
             * In the class documentation for the SessionPortListener note that
             * it is a requirement for this method to be multithread safe.
             * Since we never access any shared state, this requirement is met.
             *
             * See comments in joinSession for why the hosted chat interface is
             * created here.
             */
            public void sessionJoined(short sessionPort, int id, String joiner) {
                Log.i(TAG, "SessionPortListener.sessionJoined(" + sessionPort + ", " + id + ", " + joiner + ")");
                mHostSessionId = id;
                SignalEmitter emitter = new SignalEmitter(mTrainService, id, SignalEmitter.GlobalBroadcast.Off);
                mHostTrainInterface = emitter.getInterface(TrainInterface.class);
            }
        });

        if (status == Status.OK) {
            mHostChannelState = HostChannelState.BOUND;
            mTrainApplication.hostSetChannelState(mHostChannelState);
        } else {
            mTrainApplication.alljoynError(TrainApplication.Module.HOST, "Incapaz de conectar al puerto de sesión: (" + status + ")");
            return;
        }
    }

    /**
     * Implementation of the functionality related to un-binding a session port
     * from an AllJoyn bus attachment.
     */
    private void doUnbindSession() {
        Log.i(TAG, "doUnbindSession()");

        /*
         * There's not a lot we can do if the bus attachment refuses to unbind
         * our port.
         */
        mBus.unbindSessionPort(CONTACT_PORT);
        mHostTrainInterface = null;
        mHostChannelState = HostChannelState.NAMED;
        mTrainApplication.hostSetChannelState(mHostChannelState);
    }

    /**
     * The session identifier of the "host" session that the application
     * provides for remote devices.  Set to -1 if not connected.
     */
    int mHostSessionId = -1;

    /**
     * A flag indicating that the application has joined a chat channel that
     * it is hosting.  See the long comment in doJoinSession() for a
     * description of this rather non-intuitively complicated case.
     */
    boolean mJoinedToSelf = false;

    /**
     * This is the interface over which the chat messages will be preview_squat in
     * the case where the application is joined to a chat channel hosted
     * by the application.  See the long comment in doJoinSession() for a
     * description of this rather non-intuitively complicated case.
     */
    TrainInterface mHostTrainInterface = null;

    /**
     * Implementation of the functionality related to advertising a service on
     * an AllJoyn bus attachment.
     */
    private void doAdvertise() {
        Log.i(TAG, "doAdvertise()");

        /*
         * We depend on the user interface and model to work together to not
         * change the name out from under us while we are running.
         */
        String wellKnownName = NAME_PREFIX + "." + mTrainApplication.hostGetChannelName();
        Status status = mBus.advertiseName(wellKnownName, SessionOpts.TRANSPORT_ANY);

        if (status == Status.OK) {
            mHostChannelState = HostChannelState.ADVERTISED;
            mTrainApplication.hostSetChannelState(mHostChannelState);
        } else {
            mTrainApplication.alljoynError(TrainApplication.Module.HOST, "Incapaz de anunciar id: (" + status + ")");
            return;
        }
    }

    /**
     * Implementation of the functionality related to canceling an advertisement
     * on an AllJoyn bus attachment.
     */
    private void doCancelAdvertise() {
        Log.i(TAG, "doCancelAdvertise()");

        /*
         * We depend on the user interface and model to work together to not
         * change the name out from under us while we are running.
         */
        String wellKnownName = NAME_PREFIX + "." + mTrainApplication.hostGetChannelName();
        Status status = mBus.cancelAdvertiseName(wellKnownName, SessionOpts.TRANSPORT_ANY);

        if (status != Status.OK) {
            mTrainApplication.alljoynError(TrainApplication.Module.HOST, "Incapaz de cancelar anuncio del id: (" + status + ")");
            return;
        }

        /*
         * There's not a lot we can do if the bus attachment refuses to cancel
         * our advertisement, so we don't bother to even get the status.
         */
        mHostChannelState = HostChannelState.BOUND;
        mTrainApplication.hostSetChannelState(mHostChannelState);
    }


    /**
     * Implementation of the functionality related to joining an existing
     * local or remote session.
     */
    private void doJoinSession() {
        Log.i(TAG, "doJoinSession()");

        /*
         * There is a relatively non-intuitive behavior of multipoint sessions
         * that one needs to grok in order to understand the code below.  The
         * important thing to understand is that there can be only one endpoint
         * for a multipoint session in a particular bus attachment.  This
         * endpoint can be created explicitly by a call to joinSession() or
         * implicitly by a call to bindSessionPort().  An attempt to call
         * joinSession() on a session port we have created with bindSessionPort()
         * will result in an error.
         *
         * When we call bindSessionPort(), we do an implicit joinSession() and
         * thus signals (which correspond to our chat messages) will begin to
         * flow from the hosted chat channel as soon as we begin to host a
         * corresponding session.
         *
         * To achieve sane user interface behavior, we need to block those
         * signals from the implicit join done by the bind until our user joins
         * the bound chat channel.  If we do not do this, the chat messages
         * from the chat channel hosted by the application will appear in the
         * chat channel joined by the application.
         *
         * Since the messages flow automatically, we can accomplish this by
         * turning a filter on and off in the chat signal handler.  So if we
         * detect that we are hosting a channel, and we find that we want to
         * join the hosted channel we turn the filter off.
         *
         * We also need to be able to send chat messages to the hosted channel.
         * This means we need to point the mTrainInterface at the session ID of
         * the hosted session.  There is another complexity here since the
         * hosted session doesn't exist until a remote session has joined.
         * This means that we don't have a session ID to chat to create a
         * SignalEmitter until a remote device does a joinSession on our
         * hosted session.  This, in turn, means that we have to create the
         * SignalEmitter after we get a sessionJoined() callback in the
         * SessionPortListener passed into bindSessionPort().  We chose to
         * create the signal emitter for this case in the sessionJoined()
         * callback itself.  Note that this hosted channel signal emitter
         * must be distinct from one constructed for the usual joinSession
         * since a hosted channel may have a remote device do a join at any
         * time, even when we are joined to another session.  If they were
         * not separated, a remote join on the hosted session could redirect
         * messages from the joined session unexpectedly.
         *
         * So, to summarize, these next few lines handle a relatively complex
         * case.  When we host a chat channel, we do a bindSessionPort which
         * *enables* the creation of a session.  As soon as a remote device
         * joins the hosted chat channel, a session is actually created, and
         * the SessionPortListener sessionJoined() callback is fired.  At that
         * point, we create a separate SignalEmitter using the hosted session's
         * sessionId that we can chat to send chat messages to the channel we
         * are hosting.  As soon as the session comes up, we begin receiving
         * chat messages from the session, so we need to filter them until the
         * user joins the hosted chat channel.  In a separate timeline, the
         * user can decide to join the chat channel she is hosting.  She can
         * do so either before or after the corresponding session has been
         * created as a result of a remote device joining the hosted session.
         * If she joins the hosted channel before the underlying session is
         * created, her chat messages will be discarded.  If she does so after
         * the underlying session is created, there will be a session emitter
         * waiting to chat to send chat messages.  In either case, the signal
         * filter will be turned off in order to listen to remote chat
         * messages.
         */
        if (mHostChannelState != HostChannelState.IDLE) {
            if (mTrainApplication.getMyGroup()) {
                mChatChannelState = ChatChannelState.JOINED;
                mTrainApplication.chatSetChannelState(mChatChannelState);
                mJoinedToSelf = true;
                return;
            }
        }
        /*
         * We depend on the user interface and model to work together to provide
         * a reasonable name.
         */
        String wellKnownName = NAME_PREFIX + "." + mTrainApplication.chatGetChannelName();

        /*
         * Since we can act as the host of a channel, we know what the other
         * side is expecting to see.
         */
        short contactPort = CONTACT_PORT;
        SessionOpts sessionOpts = new SessionOpts(SessionOpts.TRAFFIC_MESSAGES, true, SessionOpts.PROXIMITY_ANY, SessionOpts.TRANSPORT_ANY);
        Mutable.IntegerValue sessionId = new Mutable.IntegerValue();

        Status status = mBus.joinSession(wellKnownName, contactPort, sessionId, sessionOpts, new SessionListener() {
            /**
             * This method is called when the last remote participant in the
             * chat session leaves for some reason and we no longer have anyone
             * to chat with.
             *
             * In the class documentation for the BusListener note that it is a
             * requirement for this method to be multithread safe.  This is
             * accomplished by the chat of a monitor on the TrainApplication as
             * exemplified by the synchronized attribute of the removeFoundChannel
             * method there.
             */
            public void sessionLost(int sessionId, int reason) {
                Log.i(TAG, "BusListener.sessionLost(sessionId=" + sessionId + ",reason=" + reason + ")");
                mTrainApplication.alljoynError(TrainApplication.Module.USE, "Se ha perdido la sesión de chat");
                mChatChannelState = ChatChannelState.IDLE;
                mTrainApplication.chatSetChannelState(mChatChannelState);
            }
        });

        if (status == Status.OK) {
            Log.i(TAG, "doJoinSession(): chat sessionId is " + mChatSessionId);
            mChatSessionId = sessionId.value;
        } else {
            mTrainApplication.alljoynError(TrainApplication.Module.USE, "Imposible unirse a la sesión: (" + status + ")");
            return;
        }

        SignalEmitter emitter = new SignalEmitter(mTrainService, mChatSessionId, SignalEmitter.GlobalBroadcast.Off);
        mTrainInterface = emitter.getInterface(TrainInterface.class);

        mChatChannelState = ChatChannelState.JOINED;
        mTrainApplication.chatSetChannelState(mChatChannelState);
    }

    /**
     * This is the interface over which the chat messages will be preview_squat.
     */
    TrainInterface mTrainInterface = null;

    /**
     * Implementation of the functionality related to joining an existing
     * remote session.
     */
    private void doLeaveSession() {
        Log.i(TAG, "doLeaveSession()");
        if (mJoinedToSelf == false) {
            mBus.leaveSession(mChatSessionId);
        }
        mChatSessionId = -1;
        mJoinedToSelf = false;
        mChatChannelState = ChatChannelState.IDLE;
        mTrainApplication.chatSetChannelState(mChatChannelState);
    }

    /**
     * The session identifier of the "chat" session that the application
     * uses to talk to remote instances.  Set to -1 if not connectecd.
     */
    int mChatSessionId = -1;

    /**
     * Implementation of the functionality related to sending messages out over
     * an existing remote session.  Note that we always send all of the
     * messages on the outbound queue, so there may be instances where this
     * method is called and we find nothing to send depending on the races.
     */
    private void doSendMessages() {
        Log.i(TAG, "doSendMessages()");


        String message;
        while ((message = mTrainApplication.getOutboundItem()) != null) {
            Log.i(TAG, "doSendMessages(): sending message \"" + message + "\"");

            /*
             * If we are joined to a remote session, we send the message over
             * the mTrainInterface.  If we are implicityly joined to a session
             * we are hosting, we send the message over the mHostTrainInterface.
             * The mHostTrainInterface may or may not exist since it is created
             * when the sessionJoined() callback is fired in the
             * SessionPortListener, so we have to check for it.
             */
            try {
                if (mJoinedToSelf) {
                    if (mHostTrainInterface != null) {
                        mHostTrainInterface.Train(message);
                    }
                } else {
                    mTrainInterface.Train(message);
                }
            } catch (BusException ex) {
                mTrainApplication.alljoynError(TrainApplication.Module.USE, "Excepción de bus al enviar mensaje: (" + ex + ")");
            }
        }
    }


    /**
     * Our chat messages are going to be Bus Signals multicast out onto an
     * associated session.  In order to send signals, we need to define an
     * AllJoyn bus object that will allow us to instantiate a signal emmiter.
     */
    class TrainService implements TrainInterface, BusObject {
        /**
         * Intentionally empty implementation of Chat method.  Since this
         * method is only used as a signal emitter, it will never be called
         * directly.
         */
        public void Train(String str) throws BusException {
        }
    }

    /**
     * The TrainService is the instance of an AllJoyn interface that is exported
     * on the bus and allows us to send signals implementing messages
     */
    private TrainService mTrainService = new TrainService();

    /**
     * The signal handler for messages received from the AllJoyn bus.
     *
     * Since the messages preview_squat on a chat channel will be preview_squat using a bus
     * signal, we need to provide a signal handler to receive those signals.
     * This is it.  Note that the name of the signal handler has the first
     * letter capitalized to conform with the DBus convention for signal
     * handler names.
     */
    @BusSignalHandler(iface = "org.proyecto.ricardo.p2ptrain", signal = "Train")
    public void Train(String string) {

        /*
         * See the long comment in doJoinSession() for more explanation of
         * why this is needed.
         *
         * The only time we allow a signal from the hosted session ID to pass
         * through is if we are in mJoinedToSelf state.  If the source of the
         * signal is us, we also filter out the signal since we are going to
         * locally echo the signal.

         */
        String uniqueName = mBus.getUniqueName();
        MessageContext ctx = mBus.getMessageContext();
        Log.i(TAG, "Chat(): chat sessionId is " + mChatSessionId);
        Log.i(TAG, "Chat(): message sessionId is " + ctx.sessionId);

        /*
         * Always drop our own signals which may be echoed back from the system.
         */
        if (ctx.sender.equals(uniqueName)) {
            Log.i(TAG, "Chat(): dropped our own signal received on session " + ctx.sessionId);
            return;
        }

        /*
         * Drop signals on the hosted session unless we are joined-to-self.
         */
        if (mJoinedToSelf == false && ctx.sessionId == mHostSessionId) {
            Log.i(TAG, "Chat(): dropped signal received on hosted session " + ctx.sessionId + " when not joined-to-self");
            return;
        }

        /*
         * To keep the application simple, we didn't force users to choose a
         * nickname.  We want to identify the message source somehow, so we
         * just chat the unique name of the sender's bus attachment.
         */
        String nickname = ctx.sender;
        nickname = nickname.substring(nickname.length()-10, nickname.length());

        Log.i(TAG, "Chat(): signal " + string + " received from nickname " + nickname);
        mTrainApplication.newRemoteUserMessage(nickname, string);
    }

    /*
     * Load the native alljoyn_java library.  The actual AllJoyn code is
     * written in C++ and the alljoyn_java library provides the language
     * bindings from Java to C++ and vice versa.
     */
    static {
        Log.i(TAG, "System.loadLibrary(\"alljoyn_java\")");
        System.loadLibrary("alljoyn_java");
    }
}
