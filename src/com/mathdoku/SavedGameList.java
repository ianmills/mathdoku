package com.mathdoku;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.util.Log;

public class SavedGameList extends ListActivity {
    private SavedGameListAdapter mAdapter;
    public boolean mCurrentSaved;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new SavedGameListAdapter(this);
        //this.getListView().setBackgroundColor(0xFFA0A0CC);
        setListAdapter(mAdapter);
    }

    public void DeleteGame(final String filename) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.save_game_screen_delete_game_confirmation_title)
                .setMessage(R.string.save_game_screen_delete_game_confirmation_message)
                .setNegativeButton(R.string.save_game_screen_delete_game_negative_button_label, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                }
        })
        .setPositiveButton(R.string.save_game_screen_delete_game_positive_button_label, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    new File(getFilesDir() + File.separator + filename).delete();
                    mAdapter.refreshFiles();
                    mAdapter.notifyDataSetChanged();
                }
        })
        .setIcon(android.R.drawable.ic_dialog_alert)
        .show();
    }

    public void LoadGame(String filename) {
        Intent i = new Intent().putExtra("filename", filename);
        setResult(Activity.RESULT_OK, i);
        finish();
    }

    void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    public void saveCurrent() {
        mCurrentSaved = true;
        int fileIndex;
        for (fileIndex = 0 ; ; fileIndex++) {
            if (! new File(getFilesDir() + File.separator + "savedgame_" + fileIndex).exists()) {
                Log.e(MathDoku.TAG, "Using fileindex " + fileIndex);
                break;
            }
        }
        String filename = getFilesDir() + File.separator + "savedgame_" + fileIndex;
        try {
            copy(new File(getFilesDir() + File.separator + MathDoku.savegamename), new File(filename));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mAdapter.refreshFiles();
        mAdapter.notifyDataSetChanged();
    }
}
