package org.proyecto.ricardo.p2ptrain;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;


public class HostActivity extends Activity implements Observer {
    private static final String TAG = "chat.HostActivity";

    private TextView mChannelName;
    private TextView mChannelStatus;
    private TextView mExName;
    private Button mSetNameButton;
    private Button mExButton;
    private Button mDesButton;
    private Button mStartButton;
    private Button mStopButton;
    private Button mQuitButton;
    private CheckBox mSetPrivate;

    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.host);

        mChannelName = (TextView)findViewById(R.id.hostChannelName);
        mChannelName.setText("");

        mExName = (TextView)findViewById(R.id.hostEjercicio);
        mExName.setText("");

        mChannelStatus = (TextView)findViewById(R.id.hostChannelStatus);
        mChannelStatus.setText("Ocioso");

        mSetNameButton = (Button)findViewById(R.id.hostSetName);
        mSetNameButton.setEnabled(true);
        mSetNameButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showDialog(DIALOG_SET_NAME_ID);
            }
        });

        mExButton = (Button)findViewById(R.id.hostSetExercise);
        mExButton.setEnabled(true);
        mExButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showDialog(DIALOG_SET_EX);
            }
        });

        mDesButton = (Button) findViewById(R.id.hostSetDescription);
        mDesButton.setEnabled(true);
        mDesButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showDialog(DIALOG_SET_DESC);
            }
        });

        mSetPrivate = (CheckBox) findViewById(R.id.hostSetPrivate);
        mSetPrivate.setEnabled(true);

        mStartButton = (Button)findViewById(R.id.hostStart);
        mStartButton.setEnabled(false);
        mStartButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showDialog(DIALOG_START_ID);
            }
        });

        mStopButton = (Button)findViewById(R.id.hostStop);
        mStopButton.setEnabled(false);
        mStopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showDialog(DIALOG_STOP_ID);
            }
        });

        /*
         * Keep a pointer to the Android Application class around.  We chat this
         * as the Model for our MVC-based application.  Whenever we are started
         * we need to "check in" with the application so it can ensure that our
         * required services are running.
         */
        mTrainApplication = (TrainApplication)getApplication();
        mTrainApplication.checkin();

        /*
         * Call down into the model to get its current state.  Since the model
         * outlives its Activities, this may actually be a lot of state and not
         * just empty.
         */
        updateChannelState();

        /*
         * Now that we're all ready to go, we are ready to accept notifications
         * from other components.
         */
        mTrainApplication.addObserver(this);


        mQuitButton = (Button)findViewById(R.id.hostQuit);
        mQuitButton.setEnabled(true);
        mQuitButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mTrainApplication.quit();
            }
        });
    }

    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        mTrainApplication = (TrainApplication)getApplication();
        mTrainApplication.deleteObserver(this);
        super.onDestroy();
    }

    private TrainApplication mTrainApplication = null;

    static final int DIALOG_SET_NAME_ID = 0;
    static final int DIALOG_START_ID = 1;
    static final int DIALOG_STOP_ID = 2;
    public static final int DIALOG_ALLJOYN_ERROR_ID = 3;
    static final int DIALOG_SET_EX = 4;
    static final int DIALOG_SET_DESC = 5;

    protected Dialog onCreateDialog(int id) {
        Log.i(TAG, "onCreateDialog()");
        Dialog result = null;
        switch(id) {
            case DIALOG_SET_NAME_ID:
            {
                DialogBuilder builder = new DialogBuilder();
                result = builder.createHostNameDialog(this, mTrainApplication);
            }
            break;
            case DIALOG_START_ID:
            {
                DialogBuilder builder = new DialogBuilder();
                result = builder.createHostStartDialog(this, mTrainApplication);
            }
            break;
            case DIALOG_STOP_ID:
            {
                DialogBuilder builder = new DialogBuilder();
                result = builder.createHostStopDialog(this, mTrainApplication);
            }
            break;
            case DIALOG_ALLJOYN_ERROR_ID:
            {
                DialogBuilder builder = new DialogBuilder();
                result = builder.createAllJoynErrorDialog(this, mTrainApplication);
            }
            break;
            case DIALOG_SET_EX:
            {
                DialogBuilder builder = new DialogBuilder();
                result = builder.createHostExerciseDialog(this, mTrainApplication);
            }
            break;
            case DIALOG_SET_DESC:
            {
                DialogBuilder builder = new DialogBuilder();
                result = builder.createHostDescriptionDialog(this, mTrainApplication);
            }
            break;
        }

        return result;
    }

    public synchronized void update(Observable o, Object arg) {
        Log.i(TAG, "update(" + arg + ")");
        String qualifier = (String)arg;

        if (qualifier.equals(TrainApplication.APPLICATION_QUIT_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_APPLICATION_QUIT_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(TrainApplication.HOST_CHANNEL_STATE_CHANGED_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_CHANNEL_STATE_CHANGED_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(TrainApplication.ALLJOYN_ERROR_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_ALLJOYN_ERROR_EVENT);
            mHandler.sendMessage(message);
        }
    }

    private void updateChannelState() {
        AllJoynService.HostChannelState channelState = mTrainApplication.hostGetChannelState();
        String name = mTrainApplication.hostGetChannelName();
        String ej = mTrainApplication.hostGetEj();
        boolean haveName = true;
        if (name == null) {
            haveName = false;
            name = "";
        }

        boolean haveEx = true;
        if (ej == null) {
            haveEx = false;
            ej = "";
        }

        mChannelName.setText(name);
        mExName.setText(ej);
        switch (channelState) {
        case IDLE:
            mChannelStatus.setText("Ocioso");
            break;
        case NAMED:
            mChannelStatus.setText("Nombre asignado");
            break;
        case BOUND:
            mChannelStatus.setText("Anclado");
            break;
        case ADVERTISED:
            mChannelStatus.setText("Anunciado");
            break;
        case CONNECTED:
            mChannelStatus.setText("Conectado");
            break;
        default:
            mChannelStatus.setText("Desconocido");
            break;
        }

        if (channelState == AllJoynService.HostChannelState.IDLE) {
            mSetNameButton.setEnabled(true);
            mDesButton.setEnabled(true);
            if (haveName && haveEx) {
                mStartButton.setEnabled(true);
            } else {
                mStartButton.setEnabled(false);
            }
            mStopButton.setEnabled(false);
        } else {
            mSetNameButton.setEnabled(false);
            mDesButton.setEnabled(false);
            mSetPrivate.setEnabled(false);
            mStartButton.setEnabled(false);
            mStopButton.setEnabled(true);
        }
    }

    private void alljoynError() {
        if (mTrainApplication.getErrorModule() == TrainApplication.Module.GENERAL ||
                mTrainApplication.getErrorModule() == TrainApplication.Module.USE) {
            showDialog(DIALOG_ALLJOYN_ERROR_ID);
        }
    }

    private static final int HANDLE_APPLICATION_QUIT_EVENT = 0;
    private static final int HANDLE_CHANNEL_STATE_CHANGED_EVENT = 1;
    private static final int HANDLE_ALLJOYN_ERROR_EVENT = 2;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case HANDLE_APPLICATION_QUIT_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): HANDLE_APPLICATION_QUIT_EVENT");
                    finish();
                }
                break;
            case HANDLE_CHANNEL_STATE_CHANGED_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): HANDLE_CHANNEL_STATE_CHANGED_EVENT");
                    updateChannelState();
                }
                break;
            case HANDLE_ALLJOYN_ERROR_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): HANDLE_ALLJOYN_ERROR_EVENT");
                    alljoynError();
                }
                break;
            default:
                break;
            }
        }
    };

}