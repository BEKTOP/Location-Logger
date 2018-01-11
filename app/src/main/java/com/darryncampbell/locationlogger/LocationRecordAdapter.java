package com.darryncampbell.locationlogger;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;

//  Allow LocationRecords to be stored in the UI ArrayList

public class LocationRecordAdapter extends ArrayAdapter<LocationRecord> {

    private Context mContext;
    private ArrayList<LocationRecord> mLocations;

    public LocationRecordAdapter(Context context, ArrayList<LocationRecord> locations)
    {
        super(context, 0, locations);
        this.mContext = context;
        this.mLocations = locations;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        ViewHolder holder = null;
        final int pos = position;
        if (convertView == null)
        {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.recorded_location, parent, false);
            holder = new ViewHolder();
            holder.mGpsLocation = (TextView) convertView.findViewById(R.id.txtGpsLocation);
            holder.mNetworkLocation = (TextView) convertView.findViewById(R.id.txtNetworkLocation);
            holder.mFusedLocation = (TextView) convertView.findViewById(R.id.txtFusedLocation);
            holder.mApBasedLocation = (TextView) convertView.findViewById(R.id.txtAPBasedLocation);
            holder.mTimestamp = (TextView) convertView.findViewById(R.id.txtTimestamp);
            holder.mWatcher = new MutableWatcher();
            holder.editNotes = (EditText) convertView.findViewById(R.id.txtNotes);
            holder.editNotes.addTextChangedListener(holder.mWatcher);
            holder.mDeleteButton = (ImageButton) convertView.findViewById(R.id.btnDelete);
            holder.mDeleteButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    new AlertDialog.Builder(getContext())
                            .setTitle("Delete?")
                            .setMessage("This will delete the specified record. Proceed?")
                            .setIconAttribute(android.R.attr.alertDialogIcon)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    {
                                        Intent updateIntent = new Intent(getContext(), LocationUpdateService.class);
                                        updateIntent.setAction(Constants.ACTION.DELETE_RECORD);
                                        updateIntent.putExtra("position", pos);
                                        mContext.startService(updateIntent);
                                    }
                                }})
                            .setNegativeButton(android.R.string.no, null).show();
                }
            });

            convertView.setTag(holder);
        }
        else
        {
            holder = (ViewHolder) convertView.getTag();
        }

        LocationRecord locationRecord = getItem(position);

        holder.mGpsLocation.setText(locationRecord.getGpsLocation());
        holder.mNetworkLocation.setText(locationRecord.getNetworkLocation());
        holder.mFusedLocation.setText(locationRecord.getFusedLocation());
        holder.mApBasedLocation.setText(locationRecord.getAPBasedLocation());
        holder.mTimestamp.setText(locationRecord.getTimestamp());
        holder.mWatcher.setActive(false);
        holder.mWatcher.setPosition(position);
        holder.editNotes.setText(locationRecord.getNote());
        holder.mWatcher.setActive(true);

        return convertView;
    }

    static class ViewHolder
    {
        public TextView mGpsLocation;
        public TextView mNetworkLocation;
        public TextView mFusedLocation;
        public TextView mApBasedLocation;
        public TextView mTimestamp;
        public EditText editNotes;
        public MutableWatcher mWatcher;
        public ImageButton mDeleteButton;
    }

    //  Credit to https://stackoverflow.com/questions/20958223/edittext-in-listview-is-updated-by-ontextchanged-when-scrolling
    class MutableWatcher implements TextWatcher {

        private int pos;
        private boolean mActive;

        void setPosition(int position) {
            pos = position;
        }

        void setActive(boolean active) {
            mActive = active;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
            if (mActive) {
                LocationRecord temp = mLocations.get(pos);
                temp.setNote(charSequence.toString());
                mLocations.set(pos, temp);
                Intent updateIntent = new Intent(getContext(), LocationUpdateService.class);
                updateIntent.setAction(Constants.ACTION.UPDATE_RECORD_NOTE);
                updateIntent.putExtra("position", pos);
                updateIntent.putExtra("note_data", charSequence.toString());
                mContext.startService(updateIntent);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {}
    }

}
