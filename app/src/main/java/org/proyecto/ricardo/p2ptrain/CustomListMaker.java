package org.proyecto.ricardo.p2ptrain;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Array;

public class CustomListMaker extends ArrayAdapter<String>{

    private final Activity context;
    private final String[] exercises;

    private String[] listaEjercicios ={
            "Abdominales", "Flexiones", "Sentadillas", "Burpees", "Gateadas"};

    private static  final String desAbs = "Ejercicio completo de abs de larga duración y múltiples variaciones.";
    private static  final String desFlex = "Ejercicio de pushups espartanas. Nivel avanzado.";
    private static  final String desSen = "Ejercicio básico y detallado de squats. Base para múltiples variaciones futuras.";
    private static  final String desBurps = "Ejercicio que combina pushups y salto. Requiere una elevada cantidad de esfuerzo.";
    private static  final String desGat = "Ejercicio de catwalk para desarrollar músculos abs, de brazos y de piernas.";

    private final String[] descriptions = {
            desAbs,
            desFlex,
            desSen,
            desBurps,
            desGat
    };
    private static final Integer[] icons ={
            R.drawable.preview_abs,
            R.drawable.preview_flex,
            R.drawable.preview_squat,
            R.drawable.preview_burps,
            R.drawable.preview_cat,
    };


    public CustomListMaker(Activity context, String[] exs) {
        super(context, R.layout.custom_list, exs);
        this.context = context;
        this.exercises = exs;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        LayoutInflater inflater = context.getLayoutInflater();
        View rowView= inflater.inflate(R.layout.custom_list, null, true);
        TextView txtTitle = (TextView) rowView.findViewById(R.id.title);
        TextView txtDes = (TextView) rowView.findViewById(R.id.description);
        ImageView imageView = (ImageView) rowView.findViewById(R.id.img);

        txtTitle.setText(exercises[position]);

        txtDes.setText(descriptions[position]);

        imageView.setImageResource(icons[position]);

        return rowView;
    }
}