package org.proyecto.ricardo.p2ptrain;

import android.app.Fragment;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.VideoView;

/**
 * Created by Riky on 14/07/2016.
 */
public class VideoFragment extends Fragment {
    private VideoView videoView;
    private ImageButton botonPlay;
    private ImageButton botonStop;
    public MediaPlayer mediaPlayer;

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState){
        View fragmentView = inflater.inflate(R.layout.video_layout, container, false);

        videoView = (VideoView) fragmentView.findViewById(R.id.vistaVideo);

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mediaPlayer = mp;
            }
        });

        botonPlay = (ImageButton) fragmentView.findViewById(R.id.botonPlay);
        botonPlay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                if (mediaPlayer != null){
                    mediaPlayer.start();
                    ((ChatActivity) getActivity()).msjPlay();
                }
            }
        });

        botonStop = (ImageButton) fragmentView.findViewById(R.id.botonStop);
        botonStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                if (mediaPlayer != null){
                    mediaPlayer.pause();
                    ((ChatActivity) getActivity()).msjPause();
                }
            }
        });
        return fragmentView;
    }
}

