package org.proyecto.ricardo.p2ptrain;

import android.app.Activity;
import android.app.Dialog;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

public class DialogBuilder {

    private static final String TAG = "p2ptrain.Dialogs";

    private String[] listaEjercicios ={
            "Abdominales", "Flexiones", "Sentadillas", "Burpees", "Gateadas"};


    public Dialog createChatJoinDialog(final Activity activity, final TrainApplication application) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(dialog.getWindow().FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.chat_join_dialog);

        ArrayAdapter<String> channelListAdapter = new ArrayAdapter<String>(activity, android.R.layout.test_list_item);
        final ListView channelList = (ListView)dialog.findViewById(R.id.chatJoinChannelList);
        channelList.setAdapter(channelListAdapter);

        List<String> channels = application.getFoundChannels();
        for (String channel : channels) {
            int lastDot = channel.lastIndexOf('.');
            if (lastDot < 0) {
                continue;
            }
            channelListAdapter.add(channel.substring(lastDot + 1));
        }
        channelListAdapter.notifyDataSetChanged();

        channelList.setOnItemClickListener(new ListView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String name = channelList.getItemAtPosition(position).toString();
                application.chatSetChannelName(name);

                application.chatJoinChannel();

                /*
                 * Android likes to reuse dialogs for performance reasons.  If
                 * we reuse this one, the custom_list of channels will eventually be
                 * wrong since it can change.  We have to tell the Android
                 * application framework to forget about this dialog completely.
                 */
                activity.removeDialog(ChatActivity.DIALOG_JOIN_ID);
            }
        });

        Button cancel = (Button)dialog.findViewById(R.id.chatJoinCancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                /*
                 * Android likes to reuse dialogs for performance reasons.  If
                 * we reuse this one, the custom_list of channels will eventually be
                 * wrong since it can change.  We have to tell the Android
                 * application framework to forget about this dialog completely.
                 */
                activity.removeDialog(ChatActivity.DIALOG_JOIN_ID);
            }
        });

        return dialog;
    }

    public Dialog createChatLeaveDialog(Activity activity, final TrainApplication application) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(dialog.getWindow().FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.chat_leave_dialog);

        Button yes = (Button)dialog.findViewById(R.id.chatLeaveOk);
        yes.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                application.chatLeaveChannel();
                application.chatSetChannelName("");
                application.chatSetChannelEj("");
                dialog.cancel();
            }
        });

        Button no = (Button)dialog.findViewById(R.id.chatLeaveCancel);
        no.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.cancel();
            }
        });

        return dialog;
    }

    public Dialog createHostNameDialog(Activity activity, final TrainApplication application) {
        Log.i(TAG, "createHostNameDialog()");
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(dialog.getWindow().FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.host_name_dialog);

        final EditText channel = (EditText)dialog.findViewById(R.id.hostNameChannel);
        channel.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                    String name = view.getText().toString();
                    application.hostSetChannelName(name);
                    application.hostInitChannel();
                    dialog.cancel();
                }
                return true;
            }
        });

        Button okay = (Button)dialog.findViewById(R.id.hostNameOk);
        okay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String name = channel.getText().toString();
                application.hostSetChannelName(name);
                application.hostInitChannel();
                dialog.cancel();
            }
        });

        Button cancel = (Button)dialog.findViewById(R.id.hostNameCancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.cancel();
            }
        });

        return dialog;
    }

    public Dialog createHostExerciseDialog(final Activity activity, final TrainApplication application) {
        Log.i(TAG, "createHostExerciseDialog()");
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(dialog.getWindow().FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.host_exs_dialog);
        final ListView listAdapter = (ListView)  dialog.findViewById(R.id.botonesEjs);

        CustomListMaker adapter = new CustomListMaker(activity, listaEjercicios);
        listAdapter.setAdapter(adapter);

        listAdapter.setOnItemClickListener(new ListView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String ej = listAdapter.getItemAtPosition(position).toString();
                application.hostSetChannelExercise(ej);
                dialog.cancel();
            }
        });


        return dialog;
    }

    public Dialog createHostDescriptionDialog(Activity activity, final TrainApplication application) {
        Log.i(TAG, "createHostNameDialog()");
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(dialog.getWindow().FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.host_desc_dialog);

        final EditText description = (EditText)dialog.findViewById(R.id.hostDescChannel);
        description.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                    String desc = view.getText().toString();
                    application.hostSetChannelDescription(desc);
                    dialog.cancel();
                }
                return true;
            }
        });

        Button okay = (Button)dialog.findViewById(R.id.hostDescOk);
        okay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String desc = description.getText().toString();
                application.hostSetChannelDescription(desc);
                dialog.cancel();
            }
        });

        Button cancel = (Button)dialog.findViewById(R.id.hostDescCancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.cancel();
            }
        });

        return dialog;
    }

    public Dialog createHostStartDialog(Activity activity, final TrainApplication application) {
        Log.i(TAG, "createHostStartDialog()");
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(dialog.getWindow().FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.host_start_dialog);

        Button yes = (Button)dialog.findViewById(R.id.hostStartOk);
        yes.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                application.hostStartChannel();

                SystemClock.sleep(500);

                application.chatSetChannelName(application.hostGetChannelName());

                application.setMyGroupTrue();

                application.chatJoinChannel();

                dialog.cancel();
            }
        });

        Button no = (Button)dialog.findViewById(R.id.hostStartCancel);
        no.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.cancel();
            }
        });

        return dialog;
    }

    public Dialog createHostStopDialog(Activity activity, final TrainApplication application) {
        Log.i(TAG, "createHostStopDialog()");
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(dialog.getWindow().FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.host_stop_dialog);

        Button yes = (Button)dialog.findViewById(R.id.hostStopOk);
        yes.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                application.chatLeaveChannel();
                application.chatSetChannelName("");
                application.chatSetChannelEj("");
                application.hostStopChannel();
                dialog.cancel();
            }
        });

        Button no = (Button)dialog.findViewById(R.id.hostStopCancel);
        no.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.cancel();
            }
        });

        return dialog;
    }

    public Dialog createAllJoynErrorDialog(Activity activity, final TrainApplication application) {
        Log.i(TAG, "createAllJoynErrorDialog()");
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(dialog.getWindow().FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.alljoyn_error_dialog);

        TextView errorText = (TextView)dialog.findViewById(R.id.errorDescription);
        errorText.setText(application.getErrorString());

        Button yes = (Button)dialog.findViewById(R.id.errorOk);
        yes.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.cancel();
            }
        });

        return dialog;
    }
}
