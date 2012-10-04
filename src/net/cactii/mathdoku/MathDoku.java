package net.cactii.mathdoku;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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

public class MathDoku extends Activity {
    public GridView kenKenGrid;
    private TextView solvedText;
    private TextView pressMenu;
    private ProgressDialog mProgressDialog;

    private LinearLayout topLayout;
    private LinearLayout controls;
    private Button digits[] = new Button[9];
    private Button clearDigit;
    private Button allDigit;
    private View[] sound_effect_views;
    private GridLayout numpad;
    private Animation outAnimation;
    private Animation solvedAnimation;

    public SharedPreferences preferences;

    final Handler mHandler = new Handler();
    private WakeLock wakeLock;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "athdoku");

        topLayout = (LinearLayout)findViewById(R.id.topLayout);
        kenKenGrid = (GridView)findViewById(R.id.gridView);
        kenKenGrid.mContext = this;
        solvedText = (TextView)findViewById(R.id.solvedText);
        kenKenGrid.animText = solvedText;
        pressMenu = (TextView)findViewById(R.id.pressMenu);
        controls = (LinearLayout)findViewById(R.id.controls);
        numpad = (GridLayout)findViewById(R.id.digits);
        for (int i=0;i<9;i++) {
            digits[i] = new Button(this);
            digits[i].setText(Integer.toString(i+1));
            numpad.addView(digits[i]);
        }
        kenKenGrid.digits = digits;
        clearDigit = (Button)findViewById(R.id.clearButton);
        allDigit = (Button)findViewById(R.id.allButton);

        sound_effect_views = new View[] { this.kenKenGrid, this.digits[0], this.digits[1],
            digits[2], this.digits[3], this.digits[4], this.digits[5], this.digits[6], this.digits[7], this.digits[8],
            clearDigit, this.allDigit
        };

        solvedAnimation = AnimationUtils.loadAnimation(this, R.anim.solvedanim);
        solvedAnimation.setAnimationListener(new AnimationListener() {
            public void onAnimationEnd(Animation animation) {
              solvedText.setVisibility(View.GONE);
            }
            public void onAnimationRepeat(Animation animation) {}
            public void onAnimationStart(Animation animation) {}
          });

        outAnimation = AnimationUtils.loadAnimation(this, R.anim.selectorzoomout);
        outAnimation.setAnimationListener(new AnimationListener() {
            public void onAnimationEnd(Animation animation) {
              controls.setVisibility(View.GONE);
            }
            public void onAnimationRepeat(Animation animation) {}
            public void onAnimationStart(Animation animation) {}
          });

        this.kenKenGrid.setOnGridTouchListener(this.kenKenGrid.new OnGridTouchListener() {
            @Override
            public void gridTouched(GridCell cell) {
                if (controls.getVisibility() == View.VISIBLE) {
                    // digitSelector.setVisibility(View.GONE);
                    if (preferences.getBoolean("hideselector", false)) {
                        controls.startAnimation(outAnimation);
                        //cell.mSelected = false;
                        kenKenGrid.mSelectorShown = false;
                    }
                    kenKenGrid.requestFocus();
                } else {
                    if (preferences.getBoolean("hideselector", false)) {
                        controls.setVisibility(View.VISIBLE);
                        Animation animation = AnimationUtils.loadAnimation(kenKenGrid.mContext, R.anim.selectorzoomin);
                        controls.startAnimation(animation);
                        kenKenGrid.mSelectorShown = true;
                    }
                    controls.requestFocus();
                }
            }
        });

        kenKenGrid.setSolvedHandler(kenKenGrid.new OnSolvedListener() {
                @Override
                public void puzzleSolved() {
                    // TODO Auto-generated method stub
                    if (kenKenGrid.mActive) {
                        Toast.makeText(kenKenGrid.mContext, R.string.main_ui_solved_messsage, Toast.LENGTH_SHORT).show();
                    }
                    controls.setVisibility(View.GONE);
                    pressMenu.setVisibility(View.VISIBLE);
                }
        });

        for (int i = 0; i<digits.length; i++)
            digits[i].setOnTouchListener(new OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    // Convert text of button (number) to Integer
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        int d = Integer.parseInt(((Button)v).getText().toString());
                        digitSelected(d);
                    }
                    return true;
                }
            });
        this.clearDigit.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                digitSelected(0);
            }
        });
        this.allDigit.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                digitSelected(-1);
            }
        });

        newVersionCheck();
        kenKenGrid.setFocusable(true);
        kenKenGrid.setFocusableInTouchMode(true);

        registerForContextMenu(kenKenGrid);
        SaveGame saver = new SaveGame();
        if (saver.Restore(kenKenGrid)) {
            setButtonVisibility(kenKenGrid.mGridSize);
            kenKenGrid.mActive = true;
        }
    }

    public void onPause() {
        if (kenKenGrid.mGridSize > 3) {
            SaveGame saver = new SaveGame();
            saver.Save(kenKenGrid);
        }
        if (wakeLock.isHeld())
            wakeLock.release();
        super.onPause();
    }

    public void onResume() {
        if (preferences.getBoolean("wakelock", true))
            wakeLock.acquire();
        if (preferences.getBoolean("alternatetheme", true)) {
            topLayout.setBackgroundDrawable(null);
            kenKenGrid.setTheme(GridView.THEME_NEWSPAPER);
        } else {
            topLayout.setBackgroundResource(R.drawable.background);
            kenKenGrid.setTheme(GridView.THEME_CARVED);
        }
        kenKenGrid.mDupedigits = preferences.getBoolean("dupedigits", true);
        kenKenGrid.mBadMaths = preferences.getBoolean("badmaths", true);
        if (kenKenGrid.mActive && !preferences.getBoolean("hideselector", false)) {
            controls.setVisibility(View.VISIBLE);
        }
        setSoundEffectsEnabled(preferences.getBoolean("soundeffects", true));
        if (kenKenGrid.mSelectedCell != null) {
            for (int i=1;i<=9;i++) {
                if (kenKenGrid.mSelectedCell.mPossibles.contains(i)) {
                    digits[i-1].setPressed(true);
                } else {
                    digits[i-1].setPressed(false);
                }
            }
        }

        super.onResume();
    }

    public void setSoundEffectsEnabled(boolean enabled) {
        for (View v : sound_effect_views)
            v.setSoundEffectsEnabled(enabled);
    }

    protected void onActivityResult(int requestCode, int resultCode,
              Intent data) {
        if (requestCode != 7 || resultCode != Activity.RESULT_OK)
          return;
        Bundle extras = data.getExtras();
        String filename = extras.getString("filename");
        Log.d("Mathdoku", "Loading game: " + filename);
        SaveGame saver = new SaveGame(filename);
        if (saver.Restore(kenKenGrid)) {
            setButtonVisibility(kenKenGrid.mGridSize);
            kenKenGrid.mActive = true;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        //Disable or enable solution option depending on whether grid is active
        menu.findItem(R.id.checkprogress).setEnabled(kenKenGrid.mActive);

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
        if (!kenKenGrid.mActive)
            return;

        menu.add(3, 105, 0, R.string.context_menu_show_solution);
        menu.add(2, 101, 0, R.string.context_menu_use_cage_maybes);
        menu.setGroupEnabled(2, false);
        menu.add(0, 102, 0,  R.string.context_menu_reveal_cell);
        menu.add(1, 103, 0,  R.string.context_menu_clear_cage_cells);
        menu.setGroupEnabled(1, false);
        menu.add(0, 104, 0,  R.string.context_menu_clear_grid);
        menu.setHeaderTitle(R.string.application_name);

        for (GridCell cell : kenKenGrid.mCages.get(kenKenGrid.mSelectedCell.mCageId).mCells) {
            if (cell.isUserValueSet() || cell.mPossibles.size() > 0)
                menu.setGroupEnabled(1, true);
            if (cell.mPossibles.size() == 1)
                menu.setGroupEnabled(2, true);
        }
    }

    public boolean onContextItemSelected(MenuItem item) {
        GridCell  selectedCell = kenKenGrid.mSelectedCell;
        switch (item.getItemId()) {
            case 103: // Clear cage
                if (selectedCell == null)
                    break;
                for (GridCell cell : kenKenGrid.mCages.get(selectedCell.mCageId).mCells) {
                    cell.clearUserValue();
                }
                kenKenGrid.invalidate();
                break;
            case 101: // Use maybes
                if (selectedCell == null)
                    break;
                for (GridCell cell : kenKenGrid.mCages.get(selectedCell.mCageId).mCells) {
                    if (cell.mPossibles.size() == 1) {
                        cell.setUserValue(cell.mPossibles.get(0));
                    }
                }
                kenKenGrid.invalidate();
                break;
            case 102: // Reveal cell
                if (selectedCell == null)
                    break;
                selectedCell.setUserValue(selectedCell.mValue);
                selectedCell.mCheated = true;
                Toast.makeText(this, R.string.main_ui_cheat_messsage, Toast.LENGTH_SHORT).show();
                kenKenGrid.invalidate();
                break;
            case 104: // Clear grid
                openClearDialog();
                break;
            case 105: // Show solution
                kenKenGrid.Solve();
                pressMenu.setVisibility(View.VISIBLE);
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
            startActivityForResult(i, 7);
            return true;
            case R.id.checkprogress:
            int textId;
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
            for (int i=1;i<=kenKenGrid.mGridSize; i++) {
                kenKenGrid.mSelectedCell.mPossibles.add(i);
            }
        } else {
            if (kenKenGrid.mSelectedCell.isUserValueSet())
                kenKenGrid.mSelectedCell.clearUserValue();
            kenKenGrid.mSelectedCell.togglePossible(value);
            if (kenKenGrid.mSelectedCell.mPossibles.size() == 1) {
                kenKenGrid.mSelectedCell.setUserValue(kenKenGrid.mSelectedCell.mPossibles.get(0));
            }
        }
        for (int i=1;i<=9;i++) {
            if (kenKenGrid.mSelectedCell.mPossibles.contains(i)) {
                digits[i-1].setPressed(true);
            } else {
                digits[i-1].setPressed(false);
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
            if (preferences.getBoolean("alternatetheme", true)) {
                topLayout.setBackgroundDrawable(null);
                kenKenGrid.setTheme(GridView.THEME_NEWSPAPER);
            } else {
                topLayout.setBackgroundResource(R.drawable.background);
                kenKenGrid.setTheme(GridView.THEME_CARVED);
            }
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
        pressMenu.setVisibility(View.GONE);
        if (!preferences.getBoolean("hideselector", false)) {
            controls.setVisibility(View.VISIBLE);
        }
    }

    private void animText(int textIdentifier, int color) {
        solvedText.setText(textIdentifier);
        solvedText.setTextColor(color);
        solvedText.setVisibility(View.VISIBLE);
        final float SCALE_FROM = (float) 0;
        final float SCALE_TO = (float) 1.0;
        ScaleAnimation anim = new ScaleAnimation(SCALE_FROM, SCALE_TO, SCALE_FROM, SCALE_TO,
                kenKenGrid.mCurrentWidth/2, kenKenGrid.mCurrentWidth/2);
        anim.setDuration(1000);
        //animText.setAnimation(anim);
        this.solvedText.startAnimation(this.solvedAnimation);
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
          //new File(SaveGame.saveFilename).delete();
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
