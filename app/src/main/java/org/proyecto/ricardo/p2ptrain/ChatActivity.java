package org.proyecto.ricardo.p2ptrain;

import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.List;

public class ChatActivity extends FragmentActivity implements Observer {
    private static final String TAG = "chat.ChatActivity";

    //Acceso a los métodos de TrainApplication
    private TrainApplication mTrainApplication = null;

    //Array para contener los mensajes de chat
    private ArrayAdapter<String> mHistoryList;

    //Variables de los botones y vistas de texto
    private Button mJoinButton;
    private Button mLeaveButton;
    private TextView mChannelName;
    private TextView mChannelEj;
    private TextView mChannelStatus;

    //Variables para el vídeo del ejercicio
    private Uri uri;
    private VideoView videoView;

    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat);

        //Iniciamos el fragmento y lo mantenemos oculto mientras no estemos unidos a un grupo
        FragmentManager video = getFragmentManager();
        Fragment videoFrag = video.findFragmentById(R.id.videoFrag);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.hide(videoFrag).commitAllowingStateLoss();

        videoView = (VideoView) findViewById(R.id.vistaVideo);

        mHistoryList = new ArrayAdapter<String>(this, android.R.layout.test_list_item);
        ListView hlv = (ListView) findViewById(R.id.chatHistoryList);
        hlv.setAdapter(mHistoryList);

        //Preparamos el bloque de entrada de mensajes
        EditText messageBox = (EditText)findViewById(R.id.chatMessage);
        messageBox.setSingleLine();
        messageBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String message = view.getText().toString();
                    if(message.contains("//CONTROL")){
                        message = message.replace("//CONTROL", "//C0NTR0L");
                        //Evitamos que los usuarios generen manualmente señales de control
                    }
                    mTrainApplication.newLocalUserMessage(message);
                    view.setText("");
                }
                return true;
            }
        });

        //Preparamos los botones y textos de la actividad para su uso
        mJoinButton = (Button)findViewById(R.id.chatJoin);
        mJoinButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showDialog(DIALOG_JOIN_ID);
            }
        });

        mLeaveButton = (Button)findViewById(R.id.chatLeave);
        mLeaveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showDialog(DIALOG_LEAVE_ID);
            }
        });

        mChannelName = (TextView)findViewById(R.id.chatChannelName);
        mChannelStatus = (TextView)findViewById(R.id.chatChannelStatus);
        mChannelEj = (TextView)findViewById(R.id.chatChannelEjercicio);

        LinearLayout gr = (LinearLayout) findViewById(R.id.groupInfo);
        gr.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

            }
        });

        //Preparamos el puntero a TrainApplication y le indicamos que estamos listos
        mTrainApplication = (TrainApplication)getApplication();
        mTrainApplication.checkin();

        //Comprobamos el estado del canal
        updateChannelState();
        updateHistory();

        //Nos registramos como Observador de TrainApplication
        //Cuando esta reciba un evento se nos notificará
        mTrainApplication.addObserver(this);
    }

    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        mTrainApplication = (TrainApplication)getApplication();
        mTrainApplication.deleteObserver(this);
        super.onDestroy();
    }

    /*
     * Aquí se gestionan las llamadas a los distintos bloques de diálogo
     * mostrados antes diversos eventos como el boton Buscar Grupos
     */

    public static final int DIALOG_JOIN_ID = 0;
    public static final int DIALOG_LEAVE_ID = 1;
    public static final int DIALOG_ALLJOYN_ERROR_ID = 2;

    protected Dialog onCreateDialog(int id) {
        Log.i(TAG, "onCreateDialog()");
        Dialog result = null;
        switch(id) {
        case DIALOG_JOIN_ID:
            {
                DialogBuilder builder = new DialogBuilder();
                result = builder.createChatJoinDialog(this, mTrainApplication);
            }
            break;
        case DIALOG_LEAVE_ID:
            {
                DialogBuilder builder = new DialogBuilder();
                result = builder.createChatLeaveDialog(this, mTrainApplication);
            }
            break;
        case DIALOG_ALLJOYN_ERROR_ID:
            {
                DialogBuilder builder = new DialogBuilder();
                result = builder.createAllJoynErrorDialog(this, mTrainApplication);
            }
            break;
        }
        return result;
    }

    /*
     * Este es el método llamado por los objetos observados (TrainApplication)
     * cuando ocurre un evento. En función del evento indicado se responderá de
     * uno u otro modo.
     */
    public synchronized void update(Observable o, Object arg) {
        Log.i(TAG, "update(" + arg + ")");
        String qualifier = (String)arg;

        if (qualifier.equals(TrainApplication.APPLICATION_QUIT_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_APPLICATION_QUIT_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(TrainApplication.HISTORY_CHANGED_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_HISTORY_CHANGED_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(TrainApplication.CHAT_CHANNEL_STATE_CHANGED_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_CHANNEL_STATE_CHANGED_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(TrainApplication.ALLJOYN_ERROR_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_ALLJOYN_ERROR_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(TrainApplication.EJ_CHANGED_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_EJ_CHANGED_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(TrainApplication.CONTROL_CHANGED_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_CONTROL_CHANGED_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(TrainApplication.CHAT_CHANNEL_FOUND_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_CHANNEL_FOUND_EVENT);
            mHandler.sendMessage(message);
        }
    }

    /*
     * Métodos llamados cuando el usuario pausa o reanuda el vídeo.
     * Generan un mensaje con una señal de control enviada al bus AllJoyn.
     */
    public synchronized void msjPlay(){
        mTrainApplication.newLocalUserMessage("//CONTROL+PLAY");
    }

    public synchronized void msjPause(){
        mTrainApplication.newLocalUserMessage("//CONTROL+PAUSE");
    }

    //Método que actualiza la lista de mensajes de chat
    private void updateHistory() {
        Log.i(TAG, "updateHistory()");
        mHistoryList.clear();
        List<String> messages = mTrainApplication.getHistory();
        for (String message : messages) {
            if(message.startsWith("[")){
                mHistoryList.add(message);
            }
        }
        mHistoryList.notifyDataSetChanged();
    }

    private int secVideo;

    //Método encargado de gestionar las señales de control recibidas.
    private void manageControl(){
        String signal = mTrainApplication.getControl();

        if(signal != null){
            if(signal.contains("PLAY")){

                videoView.seekTo(secVideo);
                videoView.start();

            }else if(signal.contains("PAUSE")){

                secVideo = videoView.getCurrentPosition();
                videoView.pause();


            } else if(signal.contains("REQ.EJ")){
                if(mTrainApplication.getMyGroup()){
                    mTrainApplication.newLocalUserMessage("//CONTROL+EJIS:"+mTrainApplication.hostGetEj());
                }
            }else if(signal.contains("EJIS:")){
                String ejercicio = signal.substring(signal.indexOf(":")+1,signal.length());
                mTrainApplication.chatSetChannelEj(ejercicio);
                mChannelEj.setText(ejercicio);
                loadEx(ejercicio);
            }
        }
    }

    //Método para actualizar el estado del canal al unirnos o abandonarlo
    private void updateChannelState() {
        Log.i(TAG, "updateHistory()");
        AllJoynService.ChatChannelState channelState = mTrainApplication.chatGetChannelState();
        String name = mTrainApplication.chatGetChannelName();
        String ejercicio = mTrainApplication.chatGetChannelEj();

        if (name == null) {
            name = "";
        }
        mChannelName.setText(name);

        mChannelEj.setText(ejercicio);

        if(ejercicio != null) {
            if(!ejercicio.equals("")){
                loadEx(ejercicio);
            }
        }

        switch (channelState) {
        case IDLE:
            mChannelStatus.setText("Ocioso");
            mJoinButton.setEnabled(true);
            mLeaveButton.setEnabled(false);
            mChannelEj.setText("");
            controlFrag(0);
            break;
        case JOINED:
            mChannelStatus.setText("Unido");
            mJoinButton.setEnabled(false);
            mLeaveButton.setEnabled(true);

            if(!mTrainApplication.getMyGroup()){
                mTrainApplication.newLocalUserMessage("//CONTROL+REQ.EJ");
            }else{
                mTrainApplication.chatSetChannelEj(mTrainApplication.hostGetEj());
                loadEx(mTrainApplication.chatGetChannelEj());
            }
            controlFrag(1);
            break;
        }
    }

    private void updateEj(){

    }

    //Método encargado de actualizar el indicador numérico de grupos encontrados
    private void updateChannelsNumber(){
        int n = mTrainApplication.getFoundChannels().size();
        mJoinButton.setText("Buscar grupo (" + Integer.toString(n) + ")");
    }

    //Método encargado de actualizar el VideoView del ejercicio
    private void loadEx(String exercise){
        int ex;
        if(exercise.compareTo("Abdominales") == 0){
            ex = 1;
        }else if(exercise.compareTo("Flexiones") == 0){
            ex = 2;
        }else if(exercise.compareTo("Sentadillas") == 0){
            ex = 3;
        }else if(exercise.compareTo("Burpees") == 0){
            ex = 4;
        }else if(exercise.compareTo("Gateadas") == 0){
            ex = 5;
        } else{
            ex = 0;
        }

        switch (ex){
            case 1:
                uri = Uri.parse("android.resource://" + getPackageName() + "/"
                        + R.raw.abs);
                break;
            case 2:
                uri = Uri.parse("android.resource://" + getPackageName() + "/"
                        + R.raw.pushups);
                break;
            case 3:
                uri = Uri.parse("android.resource://" + getPackageName() + "/"
                        + R.raw.squats);
                break;
            case 4:
                uri = Uri.parse("android.resource://" + getPackageName() + "/"
                        + R.raw.burpees);
                break;
            case 5:
                uri = Uri.parse("android.resource://" + getPackageName() + "/"
                        + R.raw.catwalk);
                break;
            default:
                uri = Uri.parse("android.resource://" + getPackageName() + "/"
                        + R.raw.pushups);
                break;
        }

        videoView.setVideoURI(uri);
        videoView.start();

        mChannelEj.setText(exercise);
    }

    //Método encargado de ocultar y mostrar el fragmento de video
    private void controlFrag(int i){
        FragmentManager video = getFragmentManager();
        Fragment videoFrag = video.findFragmentById(R.id.videoFrag);
        FragmentTransaction ft = getFragmentManager().beginTransaction();

        if (i==1) {
            ft.show(videoFrag);
            videoView.requestFocus();
            videoView.resume();

        } else {
            videoView.pause();
            ft.hide(videoFrag);
        }
        ft.commitAllowingStateLoss();;
    }

    /**
     * Método llamado cuando Alljoyn encuentra un error. Al ser esta clase la primera
     * que se muestra se le ha asignado el manejo de errores generales.
     */
    private void alljoynError() {
        if (mTrainApplication.getErrorModule() == TrainApplication.Module.GENERAL ||
            mTrainApplication.getErrorModule() == TrainApplication.Module.USE) {
            showDialog(DIALOG_ALLJOYN_ERROR_ID);
        }
    }

    /**
     * El Handler se encarga de manejar los distintos eventos entrantes.
     * Primero se definen las constantes que representan dichos eventos.
    */

    private static final int HANDLE_APPLICATION_QUIT_EVENT = 0;
    private static final int HANDLE_HISTORY_CHANGED_EVENT = 1;
    private static final int HANDLE_CHANNEL_STATE_CHANGED_EVENT = 2;
    private static final int HANDLE_ALLJOYN_ERROR_EVENT = 3;
    private static final int HANDLE_EJ_CHANGED_EVENT = 4;
    private static final int HANDLE_CONTROL_CHANGED_EVENT = 5;
    private static final int HANDLE_CHANNEL_FOUND_EVENT = 6;


    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HANDLE_APPLICATION_QUIT_EVENT: {
                    Log.i(TAG, "mHandler.handleMessage(): HANDLE_APPLICATION_QUIT_EVENT");
                    finish();
                }
                break;
                case HANDLE_HISTORY_CHANGED_EVENT: {
                    Log.i(TAG, "mHandler.handleMessage(): HANDLE_HISTORY_CHANGED_EVENT");
                    updateHistory();
                    break;
                }
                case HANDLE_CHANNEL_STATE_CHANGED_EVENT: {
                    Log.i(TAG, "mHandler.handleMessage(): HANDLE_CHANNEL_STATE_CHANGED_EVENT");
                    updateChannelState();
                    break;
                }
                case HANDLE_ALLJOYN_ERROR_EVENT: {
                    Log.i(TAG, "mHandler.handleMessage(): HANDLE_ALLJOYN_ERROR_EVENT");
                    alljoynError();
                    break;
                }

                case HANDLE_EJ_CHANGED_EVENT: {
                    Log.i(TAG, "mHandler.handleMessage(): HANDLE_CONTROL_CHANGED_EVENT");
                    updateEj();
                    break;
                }

                case HANDLE_CONTROL_CHANGED_EVENT: {
                    Log.i(TAG, "mHandler.handleMessage(): HANDLE_CONTROL_CHANGED_EVENT");
                    manageControl();
                    break;
                }

                case HANDLE_CHANNEL_FOUND_EVENT: {
                    Log.i(TAG, "mHandler.handleMessage(): HANDLE_CONTROL_CHANGED_EVENT");
                    updateChannelsNumber();
                    break;
                }

            default:
                break;
            }
        }
    };

}
