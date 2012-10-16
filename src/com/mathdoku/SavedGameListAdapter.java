package com.mathdoku;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;

public class SavedGameListAdapter extends BaseAdapter {

    public ArrayList<String> mGameFiles;
    private LayoutInflater inflater;
    private SavedGameList mContext;

    public SavedGameListAdapter(SavedGameList context) {
        inflater = LayoutInflater.from(context);
        mContext = context;
        mGameFiles = new ArrayList<String>();
        refreshFiles();

    }

    public class SortSavedGames implements Comparator<String> {
        long save1 = 0;
        long save2 = 0;
        public int compare(String object1, String object2) {
            try {
                save1 = GridView.ReadDate(mContext.getFilesDir() + File.separator + object1);
                save2 = GridView.ReadDate(mContext.getFilesDir() + File.separator + object2);
            }
            catch (Exception e) {
                //
            }
            return (int) ((save2 - save1)/1000);
        }

    }

    public void refreshFiles() {
        mGameFiles.clear();
        File dir = mContext.getFilesDir();
        String[] allFiles = dir.list();
        for (String entryName : allFiles) {
            if (entryName.startsWith("savedgame_")) {
                mGameFiles.add(entryName);
                Log.e(MathDoku.TAG, "adding saved game: " + entryName);
            }
        }

        Collections.sort((List<String>)mGameFiles, new SortSavedGames());

    }

    public int getCount() {
        return mGameFiles.size() + 1;
    }

    public Object getItem(int arg0) {
        if (arg0 == 0)
            return "";
        return mGameFiles.get(arg0-1);
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        if (position == 0) {
            convertView = inflater.inflate(R.layout.savedgamesaveitem, null);

            final Button saveCurrent = (Button)convertView.findViewById(R.id.saveCurrent);
            saveCurrent.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    saveCurrent.setEnabled(false);
                    mContext.saveCurrent();
                }
            });
            if (mContext.mCurrentSaved)
                saveCurrent.setEnabled(false);
            return convertView;
        }

        convertView = inflater.inflate(R.layout.savedgameitem, null);


        GridView grid = (GridView)convertView.findViewById(R.id.savedGridView);
        TextView label = (TextView)convertView.findViewById(R.id.savedGridText);

        final String saveFile = mGameFiles.get(position-1);
        Log.e(MathDoku.TAG, "getting miniview for " + saveFile);

        grid.mContext = mContext;
        grid.setActive(false);
        grid.mDupedigits = PreferenceManager.getDefaultSharedPreferences(convertView.getContext()).getBoolean("dupedigits", true);
        grid.mBadMaths = PreferenceManager.getDefaultSharedPreferences(convertView.getContext()).getBoolean("badmaths", true);

        try {
            grid.Restore(saveFile);
        } catch (Exception e) {
            Log.e(MathDoku.TAG, "Got exception " + e);
            e.printStackTrace();
            // Error, delete the file.
            new File(saveFile).delete();
            return convertView;
        }
        Calendar currentTime = Calendar.getInstance();
        Calendar gameTime = Calendar.getInstance();
        gameTime.setTimeInMillis(grid.mDate);
        if (System.currentTimeMillis() - grid.mDate < 86400000 &&
            gameTime.get(Calendar.DAY_OF_YEAR) != currentTime.get(Calendar.DAY_OF_YEAR))
            label.setText(gameTime.get(Calendar.HOUR) + ":" + gameTime.get(Calendar.MINUTE) +
                    ((gameTime.get(Calendar.AM_PM) == Calendar.AM) ? " AM" : " PM") + " yesterday");
        else if (System.currentTimeMillis() - grid.mDate < 86400000)
            label.setText("" + DateFormat.getTimeInstance(DateFormat.SHORT).format(grid.mDate));
        else
            label.setText("" + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(grid.mDate));

        grid.setBackgroundColor(0xFFFFFFFF);

        for (GridCell cell : grid.mCells)
            cell.mSelected = false;

        Button loadButton = (Button)convertView.findViewById(R.id.gameLoad);
        loadButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mContext.LoadGame(saveFile);
            }
        });

        Button deleteButton = (Button)convertView.findViewById(R.id.gameDelete);
        deleteButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mContext.DeleteGame(saveFile);
            }
        });

        return convertView;
    }

}
