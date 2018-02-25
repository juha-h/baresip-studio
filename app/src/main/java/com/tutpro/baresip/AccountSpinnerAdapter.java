package com.tutpro.baresip;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class AccountSpinnerAdapter extends ArrayAdapter<Integer> {

    private ArrayList<Integer> images;
    private ArrayList<String> aors;
    private Context context;

    public AccountSpinnerAdapter(Context context, ArrayList<String> aors, ArrayList<Integer> images) {
        super(context, android.R.layout.simple_spinner_item, images);
        this.images = images;
        this.aors = aors;
        this.context = context;
    }

    @Override
     public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getImageForPosition(position, convertView, parent);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getImageForPosition(position, convertView, parent);
    }

    private View getImageForPosition(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View row = inflater.inflate(R.layout.account_spinner, parent, false);
        TextView textView = (TextView) row.findViewById(R.id.spinnerText);
        textView.setText(aors.get(position));
        textView.setTextSize(17);
        ImageView imageView = (ImageView) row.findViewById(R.id.spinnerImage);
        imageView.setImageResource(images.get(position));
        return row;
    }
}

