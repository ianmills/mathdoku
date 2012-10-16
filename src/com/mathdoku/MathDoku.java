package com.mathdoku;

import android.app.Activity;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.view.animation.Animation.AnimationListener;
import android.widget.Button;
import android.widget.ToggleButton;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.R.style;

public class MathDoku extends Activity implements OnSharedPreferenceChangeListener,
                                                  OnTouchListener
{
    public static final String TAG = "MathDoku";
    public static final String savegamename = "savedgame";
    private static final int USE_MAYBES = 101;
    private static final int REVEAL_CELL = 102;
    private static final int CLEAR_CAGE = 103;
    private static final int CLEAR_GRID = 104;
    private static final int SHOW_SOLUTION = 105;
    private static final int POPULATE_MAYBES = 106;
    private static final int LOAD_GAME = 7;

    private GridView kenKenGrid;
    private TextView solvedText;
    private ProgressDialog mProgressDialog;

    private LinearLayout topLayout;
    private LinearLayout controls;
    private Button digits[] = new Button[9];
    private Button clearDigit;
    private Button allDigit;
    private View[] sound_effect_views;
    private GridLayout numpad;
    private Animation outAnimation;

    private SharedPreferences preferences;
    boolean clearInvalids = false;
    String invalidMaybePref = "I";

    private final Handler mHandler = new Handler();
    private WakeLock wakeLock;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "athdoku");
        ActionBar ab = getActionBar();
        ab.setDisplayShowTitleEnabled(true);
        ab.setTitle("MathDoku");
        topLayout = (LinearLayout)findViewById(R.id.topLayout);
        kenKenGrid = (GridView)findViewById(R.id.gridView);
        solvedText = (TextView)findViewById(R.id.solvedText);
        solvedText.setVisibility(View.GONE);
        controls = (LinearLayout)findViewById(R.id.controls);
        numpad = (GridLayout)findViewById(R.id.digits);
        for (int i=0;i<9;i++) {
            digits[i] = new Button(this);
            digits[i].setText(Integer.toString(i+1));
            numpad.addView(digits[i]);
        }
        kenKenGrid.mMathDoku = this;
        kenKenGrid.digits = digits;
        kenKenGrid.controls = controls;
        kenKenGrid.setMarkInvalidMaybes(preferences.getString("invalidmaybes", "I").equals("M"));
        kenKenGrid.setMaybe3x3(preferences.getBoolean("maybe3x3", true));
        kenKenGrid.preferences = preferences;
        clearDigit = (Button)findViewById(R.id.clearButton);
        allDigit = (Button)findViewById(R.id.allButton);

        sound_effect_views = new View[] { kenKenGrid, digits[0], digits[1],
            digits[2], digits[3], digits[4], digits[5], digits[6], digits[7], digits[8],
            clearDigit, allDigit
        };

        for (int i = 0; i<digits.length; i++) {
            digits[i].setOnTouchListener(this);
        }

        newVersionCheck();
        kenKenGrid.setFocusable(true);
        kenKenGrid.setFocusableInTouchMode(true);

        registerForContextMenu(kenKenGrid);
        if (kenKenGrid.Restore(savegamename)) {
            setButtonVisibility(kenKenGrid.mGridSize);
            kenKenGrid.setActive(true);
        }
    }

    public void Solved() {
        if (kenKenGrid.isActive()) {
            Toast.makeText(kenKenGrid.mContext, R.string.main_ui_solved_messsage, Toast.LENGTH_SHORT).show();
        }
        controls.setVisibility(View.GONE);
        solvedText.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        // Convert text of button (number) to Integer
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            int d = Integer.parseInt(((Button)v).getText().toString());
            digitSelected(d);
        }
        return true;
    }

    public void onClear(View view) {
        digitSelected(0);
    }

    public void onAll(View view) {
        digitSelected(-1);
    }

    @Override
    public void onSharedPreferenceChanged (SharedPreferences sharedPreferences, String key) {
        Log.e(TAG, "Pref changed : key: " + key);
        if (key.equals("maybe3x3")) {
            kenKenGrid.setMaybe3x3(preferences.getBoolean("maybe3x3", true));
        } else if (key.equals("invalidmaybes")) {
            String invalidmaybes = preferences.getString("invalidmaybes", "I");
            kenKenGrid.setMarkInvalidMaybes(invalidmaybes.equals("M"));
            clearInvalids = invalidmaybes.equals("C");
        }
        kenKenGrid.invalidate();
    }

    public void onPause() {
        if (kenKenGrid.mGridSize > 3) {
            kenKenGrid.Save(savegamename);
            kenKenGrid.onPause();
        }
        if (wakeLock.isHeld())
            wakeLock.release();
        super.onPause();
    }

    public void onResume() {
        if (preferences.getBoolean("wakelock", true))
            wakeLock.acquire();
        kenKenGrid.mDupedigits = preferences.getBoolean("dupedigits", true);
        kenKenGrid.mBadMaths = preferences.getBoolean("badmaths", true);
        if (kenKenGrid.isActive() && !preferences.getBoolean("hideselector", false)) {
            controls.setVisibility(View.VISIBLE);
        }
        setSoundEffectsEnabled(preferences.getBoolean("soundeffects", true));

        kenKenGrid.onResume(getActionBar());
        super.onResume();
    }

    public void setSoundEffectsEnabled(boolean enabled) {
        for (View v : sound_effect_views)
            v.setSoundEffectsEnabled(enabled);
    }

    protected void onActivityResult(int requestCode, int resultCode,
              Intent data) {
        if (requestCode != LOAD_GAME || resultCode != Activity.RESULT_OK)
          return;
        Bundle extras = data.getExtras();
        String filename = extras.getString("filename");
        Log.d("Mathdoku", "Loading game: " + filename);
        if (kenKenGrid.Restore(filename)) {
            setButtonVisibility(kenKenGrid.mGridSize);
            kenKenGrid.setActive(true);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        boolean showClearCageMaybes = false;
        boolean showUseMaybes = false;
        if (!kenKenGrid.isActive())
            return;
        for (GridCell cell : kenKenGrid.mCages.get(kenKenGrid.mSelectedCell.mCageId).mCells) {
            if (cell.isUserValueSet() || cell.mPossibles.size() > 0) {
                showClearCageMaybes = true;
            } else if (cell.mPossibles.size() == 1) {
                showUseMaybes = true;
            }
        }

        menu.add(3, SHOW_SOLUTION, 0, R.string.context_menu_show_solution);
        menu.add(0, REVEAL_CELL, 0,  R.string.context_menu_reveal_cell);
        if (showClearCageMaybes)
            menu.add(1, CLEAR_CAGE, 0,  R.string.context_menu_clear_cage_cells);
        if (showUseMaybes) {
            menu.add(2, USE_MAYBES, 0, R.string.context_menu_use_cage_maybes);
        }

        menu.add(0, CLEAR_GRID, 0,  R.string.context_menu_clear_grid);
        menu.add(0, POPULATE_MAYBES, 0, R.string.context_menu_populate_maybes);
        menu.setHeaderTitle(R.string.application_name);

    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        GridCell  selectedCell = kenKenGrid.mSelectedCell;
        switch (item.getItemId()) {
            case CLEAR_CAGE:
                if (selectedCell == null)
                    break;
                for (GridCell cell : kenKenGrid.mCages.get(selectedCell.mCageId).mCells) {
                    cell.clearUserValue();
                }
                kenKenGrid.invalidate();
                break;
            case USE_MAYBES:
                if (selectedCell == null)
                    break;
                for (GridCell cell : kenKenGrid.mCages.get(selectedCell.mCageId).mCells) {
                    if (cell.mPossibles.size() == 1) {
                        cell.setUserValue(cell.mPossibles.get(0));
                    }
                }
                kenKenGrid.invalidate();
                break;
            case REVEAL_CELL:
                if (selectedCell == null)
                    break;
                selectedCell.setUserValue(selectedCell.mValue);
                selectedCell.mCheated = true;
                Toast.makeText(this, R.string.main_ui_cheat_messsage, Toast.LENGTH_SHORT).show();
                kenKenGrid.invalidate();
                break;
            case CLEAR_GRID:
                openClearDialog();
                break;
            case SHOW_SOLUTION:
                kenKenGrid.Solve();
                solvedText.setVisibility(View.VISIBLE);
                break;
            case POPULATE_MAYBES:
                for (GridCell cell : kenKenGrid.mCells) {
                    if (cell.isUserValueSet()) {
                        continue;
                    }
                    cell.mPossibles.clear();
                    for (int i=1;i<=kenKenGrid.mGridSize; i++) {
                        cell.mPossibles.add(i);
                    }
                }
                if (invalidMaybePref != null && invalidMaybePref.equals("C")) {
                    while (kenKenGrid.clearInvalidPossibles() == true);
                }
                kenKenGrid.invalidate();
                break;

        }
        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {

        int menuId = menuItem.getItemId();
        if (menuId == R.id.size4 ||    menuId == R.id.size5 || 
                menuId == R.id.size6 ||    menuId == R.id.size7 || 
                menuId == R.id.size8 ||    menuId == R.id.size9) {
            final int gridSize;
            switch (menuId) {
                case R.id.size4: gridSize = 4; break;
                case R.id.size5: gridSize = 5; break;
                case R.id.size6: gridSize = 6; break;
                case R.id.size7: gridSize = 7; break;
                case R.id.size8: gridSize = 8; break;
                case R.id.size9: gridSize = 9; break;
                default: gridSize = 4; break;
            }
            String hideOperators = preferences.getString("hideoperatorsigns", "F");
            if (hideOperators.equals("T")) {
                startNewGame(gridSize, true);
                return true;
            }
            if (hideOperators.equals("F")) {
                startNewGame(gridSize, false);
                return true;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.hide_operators_dialog_message)
                .setCancelable(false)
                .setPositiveButton(R.string.Yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        startNewGame(gridSize, true);
                    }
                })
            .setNegativeButton(R.string.No, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    startNewGame(gridSize, false);
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
            return true;
                }

        switch (menuItem.getItemId()) {
        case R.id.saveload:
            Intent i = new Intent(this, SavedGameList.class);
            startActivityForResult(i, LOAD_GAME);
            return true;
            case R.id.checkprogress:
            int textId;
            if (kenKenGrid.isActive() == false) {
                return false;
            }
            if (kenKenGrid.isSolutionValidSoFar())
                textId = R.string.ProgressOK;
            else {
                textId = R.string.ProgressBad;
                kenKenGrid.markInvalidChoices();
            }
            Toast toast = Toast.makeText(getApplicationContext(),
                                             textId,
                                             Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER,0,0);
            toast.show();
            return true;
        case R.id.options:
            startActivityForResult(new Intent(
                    this, OptionsActivity.class), 0);
            return true;
        case R.id.help:
            openHelpDialog();
            return true;
           default:
            return super.onOptionsItemSelected(menuItem);
        }
    }


    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN &&
                keyCode == KeyEvent.KEYCODE_BACK &&
                kenKenGrid.mSelectorShown) {
            controls.setVisibility(View.GONE);
            kenKenGrid.requestFocus();
            kenKenGrid.mSelectorShown = false;
            kenKenGrid.invalidate();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    public void digitSelected(int value) {
        if (kenKenGrid.mSelectedCell == null)
            return;
        if (value == 0) {    // Clear Button
            kenKenGrid.mSelectedCell.mPossibles.clear();
            kenKenGrid.mSelectedCell.setUserValue(0);
        } else if (value == -1) { //all button
            kenKenGrid.mSelectedCell.clearUserValue();
            kenKenGrid.mSelectedCell.mPossibles.clear();
            for (int i=1;i<=kenKenGrid.mGridSize; i++) {
                kenKenGrid.mSelectedCell.mPossibles.add(i);
            }
        } else {
            if (kenKenGrid.mSelectedCell.isUserValueSet()) {
                int userVal = kenKenGrid.mSelectedCell.getUserValue();
                if (!kenKenGrid.mSelectedCell.mPossibles.contains(userVal)) {
                    kenKenGrid.mSelectedCell.togglePossible(userVal);
                }

                kenKenGrid.mSelectedCell.clearUserValue();
            }
            kenKenGrid.mSelectedCell.togglePossible(value);
            if (kenKenGrid.mSelectedCell.mPossibles.size() == 1) {
                kenKenGrid.mSelectedCell.setUserValue(kenKenGrid.mSelectedCell.mPossibles.get(0));
                invalidMaybePref = preferences.getString("invalidmaybes", "I");
                if (invalidMaybePref != null && invalidMaybePref.equals("C")) {
                    while (kenKenGrid.clearInvalidPossibles() == true);
                }
            }

        }

        if (preferences.getBoolean("hideselector", false))
            controls.setVisibility(View.GONE);
        // kenKenGrid.mSelectedCell.mSelected = false;
        kenKenGrid.requestFocus();
        kenKenGrid.mSelectorShown = false;
        kenKenGrid.invalidate();
    }


    // Create runnable for posting
    final Runnable newGameReady = new Runnable() {
        public void run() {
            dismissDialog(0);
            setButtonVisibility(kenKenGrid.mGridSize);
            kenKenGrid.invalidate();
        }
    };

    public void startNewGame(final int gridSize, final boolean hideOperators) {
        kenKenGrid.mGridSize = gridSize;
        showDialog(0);

        Thread t = new Thread() {
            public void run() {
                kenKenGrid.reCreate(hideOperators);
                mHandler.post(newGameReady);
            }
        };
        t.start();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle(R.string.main_ui_building_puzzle_title);
        mProgressDialog.setMessage(getResources().getString(R.string.main_ui_building_puzzle_message));
        mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
        mProgressDialog.setIndeterminate(false);
        mProgressDialog.setCancelable(false);
        return mProgressDialog;
    }

    public void setButtonVisibility(int gridSize) {

        for (int i=4; i<9; i++)
            if (i<gridSize)
                digits[i].setVisibility(View.VISIBLE);
            else
                digits[i].setVisibility(View.GONE);

        solvedText.setVisibility(View.GONE);
        if (!preferences.getBoolean("hideselector", false)) {
            controls.setVisibility(View.VISIBLE);
        }
    }

    private void openHelpDialog() {
        LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.aboutview, null);
        TextView tv = (TextView)view.findViewById(R.id.aboutVersionCode);
        tv.setText(getVersionName() + " (revision " + getVersionNumber() + ")");
        new AlertDialog.Builder(this)
        .setTitle(getResources().getString(R.string.application_name) + " " + getResources().getString(R.string.menu_help))
        .setIcon(R.drawable.about)
        .setView(view)
        .setNeutralButton(R.string.menu_changes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
              openChangesDialog();
          }
        })
        .setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {
          }
        })
        .show();
    }

    private void openChangesDialog() {
        LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.changeview, null);
        new AlertDialog.Builder(this)
            .setTitle(R.string.changelog_title)
            .setIcon(R.drawable.about)
            .setView(view)
            .setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    //
                }
            })
      .show();
    }

    private void openClearDialog() {
        new AlertDialog.Builder(this)
        .setTitle(R.string.context_menu_clear_grid_confirmation_title)
        .setMessage(R.string.context_menu_clear_grid_confirmation_message)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setNegativeButton(R.string.context_menu_clear_grid_negative_button_label, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {
              //
          }
        })
        .setPositiveButton(R.string.context_menu_clear_grid_positive_button_label, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                kenKenGrid.clearUserValues();
            }
        })
        .show();
      }

    public void newVersionCheck() {
        int pref_version = preferences.getInt("currentversion", -1);
        Editor prefeditor = preferences.edit();
        int current_version = getVersionNumber();
        if (pref_version == -1 || pref_version != current_version) {
          prefeditor.putInt("currentversion", current_version);
          prefeditor.commit();
          if (pref_version == -1)
              openHelpDialog();
          else
              openChangesDialog();
          return;
        }
    }

    public int getVersionNumber() {
        int version = -1;
          try {
              PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
              version = pi.versionCode;
          } catch (Exception e) {
              Log.e("Mathdoku", "Package name not found", e);
          }
          return version;
      }

    public String getVersionName() {
        String versionname = "";
          try {
              PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
              versionname = pi.versionName;
          } catch (Exception e) {
              Log.e("Mathdoku", "Package name not found", e);
          }
          return versionname;
      }
}
