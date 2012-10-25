package com.mathdoku;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Random;
import java.util.TimerTask;
import java.util.Timer;

import com.mathdoku.DLX.SolveType;
import com.mathdoku.MathDokuDLX;
import com.mathdoku.GridCell;

import android.app.ActionBar;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.DiscretePathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.view.SoundEffectConstants;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation.AnimationListener;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.TextView;
import android.widget.LinearLayout;

public class GridView extends View implements OnTouchListener  {

    public Button digits[];
    public LinearLayout controls;
    private Animation outAnimation;
    private ActionBar mActionBar;

    public int mGridSize;
    public Random mRandom;
    public Context mContext;
    public MathDoku mMathDoku;

    public ArrayList<GridCage> mCages;
    public ArrayList<GridCell> mCells;

    private boolean mActive;
    public boolean mSelectorShown = false;
    public boolean markInvalidMaybes = false;
    public boolean maybe3x3 = false;
    public float mTrackPosX;
    public float mTrackPosY;
    public GridCell mSelectedCell;
    public int mCurrentWidth;
    public Paint mGridPaint;
    public Paint mBorderPaint;
    public int mBackgroundColor;

    public Typeface mFace;
    public boolean mDupedigits;
    public boolean mBadMaths;
    public boolean hideselector = false;

    // Date of current game (used for saved games)
    public long mDate;
    private Timer mTimer;
    private TimerTask mTimerTask;
    public int game_duration;

    private String mFilename;
    private BufferedWriter writer = null;

    public GridView(Context context) {
        super(context);
        mContext = context;
        initGridView();
    }

    public GridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        initGridView();
    }
    public GridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        initGridView();
    }

    private void initGridView() {
        mDupedigits = true;
        mBadMaths = true;
        game_duration = 0;
        mTimer = new Timer();

        outAnimation = AnimationUtils.loadAnimation(mContext, R.anim.selectorzoomout);
        outAnimation.setAnimationListener(new AnimationListener() {
            public void onAnimationEnd(Animation animation) {
              controls.setVisibility(View.GONE);
            }
            public void onAnimationRepeat(Animation animation) {}
            public void onAnimationStart(Animation animation) {}
          });

        mGridPaint = new Paint();
        mGridPaint.setColor(0x80000000);
        mGridPaint.setStrokeWidth(0);

        mBorderPaint = new Paint();
        mBorderPaint.setColor(0xFF000000);
        mBorderPaint.setStrokeWidth(3);
        mBorderPaint.setStyle(Style.STROKE);

        mCurrentWidth = 0;
        mGridSize = 0;
        setActive(false);
        setOnTouchListener((OnTouchListener) this);
    }

    public void setActive(boolean active) {
        mActive = active;
    }

    public boolean isActive() {
        return mActive;
    }

    public void onPause() {
        mTimerTask.cancel();
        mTimer.purge();
        mTimerTask = null;
    }

    public void onResume(ActionBar ab) {
        mActionBar = ab;
        mTimerTask = new MathDokuTimerTask();
        mTimer.scheduleAtFixedRate(mTimerTask, 1000, 1000);
    }

    public class MathDokuTimerTask extends TimerTask {
        public MathDokuTimerTask() {
        }

        @Override
        public void run() {
            if (mActive) {
                game_duration++;
                post(new Runnable() {
                    public void run() {
                        mActionBar.setSubtitle(DateUtils.formatElapsedTime(game_duration));
                    }
                });
            }
        }
    };

    public boolean clearInvalidPossibles() {
        boolean isChanged = false;
        for (GridCell cell : mCells) {
            for (int i = 1; i <= mGridSize;i++ ) {
                if (cell.mPossibles.contains(i) == false) {
                    continue;
                }
                int possible = i;
                if (getNumValueInRow(cell, possible) >= 1 ||
                        getNumValueInCol(cell, possible) >= 1) {
                    cell.togglePossible(possible);
                    if (cell.mPossibles.size() == 1) {
                        cell.setUserValue(cell.mPossibles.get(0));
                    }
                    isChanged = true;
                }
            }
        }
        return isChanged;
    }

    public synchronized void reCreate(boolean hideOperators) {
        int num_solns;
        int num_attempts = 0;
        setActive(false);
        game_duration = 0;
        mRandom = new Random();
        if (mGridSize < 4) return;
        do {
            mCells = new ArrayList<GridCell>();
            int cellnum = 0;
            for (int i = 0 ; i < mGridSize * mGridSize ; i++)
                mCells.add(new GridCell(this, cellnum++));
            randomiseGrid();
            mTrackPosX = mTrackPosY = 0;
            mCages = new ArrayList<GridCage>();
            CreateCages(hideOperators);
            num_attempts++;
            MathDokuDLX mdd = new MathDokuDLX(mGridSize, mCages);
            // Stop solving as soon as we find multiple solutions
            num_solns = mdd.Solve(SolveType.MULTIPLE);
            Log.d ("MathDoku", "Num Solns = " + num_solns);
        } while (num_solns > 1);
        Log.d ("MathDoku", "Num Attempts = " + num_attempts);
        setActive(true);
        mSelectorShown = false;
    }

  // Returns cage id of cell at row, column
  // Returns -1 if not a valid cell or cage
    public int CageIdAt(int row, int column) {
        if (row < 0 || row >= mGridSize || column < 0 || column >= mGridSize)
            return -1;
        return mCells.get(column + row * mGridSize).mCageId;
    }

    public int CreateSingleCages(boolean hideOperators) {
        int singles = mGridSize / 2;
        boolean RowUsed[] = new boolean[mGridSize];
        boolean ColUsed[] = new boolean[mGridSize];
        boolean ValUsed[] = new boolean[mGridSize];
        for (int i = 0 ; i < singles ; i++) {
            GridCell cell;
            while (true) {
                cell = mCells.get(mRandom.nextInt(mGridSize * mGridSize));
                if (!RowUsed[cell.mRow] && !ColUsed[cell.mColumn] && !ValUsed[cell.mValue-1])
                    break;
            }
            ColUsed[cell.mColumn] = true;
            RowUsed[cell.mRow] = true;
            ValUsed[cell.mValue-1] = true;
            GridCage cage = new GridCage(this, GridCage.CAGE_1, hideOperators);
            cage.mCells.add(cell);
            cage.setArithmetic();
            cage.setCageId(i);
            mCages.add(cage);
        }
        return singles;
    }

    /* Take a filled grid and randomly create cages */
    public void CreateCages(boolean hideOperators) {
        boolean restart;
        do {
            restart = false;
            int cageId = CreateSingleCages(hideOperators);
            for (int cellNum = 0 ; cellNum < mCells.size() ; cellNum++) {
                GridCell cell = mCells.get(cellNum);
                if (cell.CellInAnyCage())
                    continue; // Cell already in a cage, skip

                ArrayList<Integer> possible_cages = getvalidCages(cell);
                if (possible_cages.size() == 1) {	// Only possible cage is a single
                    ClearAllCages();
                    restart=true;
                    break;
                }

                // Choose a random cage type from one of the possible (not single cage)
                int cage_type = possible_cages.get(mRandom.nextInt(possible_cages.size()-1)+1);
                GridCage cage = new GridCage(this, cage_type, hideOperators);
                int [][]cage_coords = GridCage.CAGE_COORDS[cage_type];
                for (int coord_num = 0; coord_num < cage_coords.length; coord_num++) {
                    int col = cell.mColumn + cage_coords[coord_num][0];
                    int row = cell.mRow + cage_coords[coord_num][1];
                    cage.mCells.add(getCellAt(row, col));
                }

                cage.setArithmetic();  // Make the maths puzzle
                cage.setCageId(cageId++);  // Set cage's id
                mCages.add(cage);  // Add to the cage list
            }
        } while (restart);
        for (GridCage cage : mCages)
            cage.setBorders();
    }

    public ArrayList<Integer> getvalidCages(GridCell origin)
    {
        if (origin.CellInAnyCage())
            return null;

        boolean [] InvalidCages = new boolean[GridCage.CAGE_COORDS.length];

        // Don't need to check first cage type (single)
        for (int cage_num=1; cage_num < GridCage.CAGE_COORDS.length; cage_num++) {
            int [][]cage_coords = GridCage.CAGE_COORDS[cage_num];
            // Don't need to check first coordinate (0,0)
            for (int coord_num = 1; coord_num < cage_coords.length; coord_num++) {
                int col = origin.mColumn + cage_coords[coord_num][0];
                int row = origin.mRow + cage_coords[coord_num][1];
                GridCell c = getCellAt(row, col);
                if (c == null || c.CellInAnyCage()) {
                    InvalidCages[cage_num] = true;
                    break;
                }
            }
        }

        ArrayList<Integer> valid =  new ArrayList<Integer>();
        for (int i=0; i<GridCage.CAGE_COORDS.length; i++)
            if (!InvalidCages[i])
                valid.add(i);

        return valid;
    }

    public void ClearAllCages() {
        for (GridCell cell : mCells) {
            cell.mCageId = -1;
            cell.mCageText = "";
        }
        mCages = new ArrayList<GridCage>();
    }

    public void clearUserValues() {
        for (GridCell cell : mCells) {
            cell.clearUserValue();
        }
        invalidate();
    }

    /* Fetch the cell at the given row, column */
    public GridCell getCellAt(int row, int column) {
        if (row < 0 || row >= mGridSize)
            return null;
        if (column < 0 || column >= mGridSize)
            return null;

        return mCells.get(column + row * mGridSize);
    }

    /*
     * Fills the grid with random numbers, per the rules:
     *
     * - 1 to <rowsize> on every row and column
     * - No duplicates in any row or column.
     */
    public void randomiseGrid() {
        int attempts;
        for (int value = 1 ; value < mGridSize+1 ; value++) {
            for (int row = 0 ; row < mGridSize ; row++) {
                attempts = 20;
                GridCell cell;
                int column;
                while (true) {
                    column = mRandom.nextInt(mGridSize);
                    cell = getCellAt(row, column);
                    if (--attempts == 0)
                        break;
                    if (cell.mValue != 0)
                        continue;
                    if (valueInColumn(column, value))
                        continue;
                    break;
                }
                if (attempts == 0) {
                    clearValue(value--);
                    break;
                }
                cell.mValue = value;
                //Log.d("KenKen", "New cell: " + cell);
            }
        }
    }

    /* Clear any cells containing the given number. */
    public void clearValue(int value) {
        for (GridCell cell : mCells)
            if (cell.mValue == value)
                cell.mValue = 0;
    }

    /* Determine if the given value is in the given row */
    public boolean valueInRow(int row, int value) {
        for (int column=0; column< mGridSize; column++)
            if (mCells.get(column+row*mGridSize).mValue == value)
                return true;
        return false;
    }

    /* Determine if the given value is in the given column */
    public boolean valueInColumn(int column, int value) {
        for (int row=0; row< mGridSize; row++)
            if (mCells.get(column+row*mGridSize).mValue == value)
                return true;
        return false;
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Our target grid is a square, measuring 80% of the minimum dimension
        int measuredWidth = measure(widthMeasureSpec);
        int measuredHeight = measure(heightMeasureSpec);

        int dim = Math.min(measuredWidth, measuredHeight);

        setMeasuredDimension(dim, dim);
    }

    private int measure(int measureSpec) {

        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.UNSPECIFIED)
            return 180;
        else
            return specSize;
    }

    private void setPressedStates() {
        for (int i=0;i<mGridSize;i++) {
            digits[i].setPressed(false);
        }
        if (mSelectedCell.isUserValueSet()) {
            digits[mSelectedCell.getUserValue()-1].setPressed(true);
        } else {
            for (Integer i : mSelectedCell.mPossibles) {
                digits[i-1].setPressed(true);
            }
        }
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        if (mGridSize < 4 || mCages == null ) {
            mActive = false;
            return;
        }

        int width = getMeasuredWidth();

        if (width != mCurrentWidth)
            mCurrentWidth = width;

        // Fill canvas background
        canvas.drawColor(mBackgroundColor);

        // Check cage correctness
        for (GridCage cage : mCages)
            cage.userValuesCorrect();

        // Draw (dashed) grid
        for (int i = 1 ; i < mGridSize ; i++) {
            float pos = ((float)mCurrentWidth / (float)mGridSize) * i;
            canvas.drawLine(0, pos, mCurrentWidth, pos, mGridPaint);
            canvas.drawLine(pos, 0, pos, mCurrentWidth, mGridPaint);
        }
        if (mSelectedCell != null && digits != null) {
            setPressedStates();
        }

        // Draw cells
        for (GridCell cell : mCells) {
            if ((cell.isUserValueSet() && getNumValueInCol(cell) > 1) ||
                    (cell.isUserValueSet() && getNumValueInRow(cell) > 1))
                cell.mShowWarning = true;
            else
                cell.mShowWarning = false;
            cell.onDraw(canvas, false);
        }

        // Draw borders
        canvas.drawLine(0, 1, mCurrentWidth, 1, mBorderPaint);
        canvas.drawLine(1, 0, 1, mCurrentWidth, mBorderPaint);
        canvas.drawLine(0, mCurrentWidth-2, mCurrentWidth, mCurrentWidth-2, mBorderPaint);
        canvas.drawLine(mCurrentWidth-2, 0, mCurrentWidth-2, mCurrentWidth, mBorderPaint);

        // Draw cells
        for (GridCell cell : mCells) {
            cell.onDraw(canvas, true);
        }

        if (mActive && isSolved()) {
            mMathDoku.Solved();
            if (mSelectedCell != null)
                mSelectedCell.mSelected = false;
            setActive(false);
        }
    }

    // Given a cell number, returns origin x,y coordinates.
    private float[] CellToCoord(int cell) {
        float xOrd;
        float yOrd;
        int cellWidth = mCurrentWidth / mGridSize;
        xOrd = ((float)cell % mGridSize) * cellWidth;
        yOrd = ((int)(cell / mGridSize) * cellWidth);
        return new float[] {xOrd, yOrd};
    }

    // Opposite of above - given a coordinate, returns the cell number within.
    private GridCell CoordToCell(float x, float y) {
        int row = (int) ((y / (float)mCurrentWidth) * mGridSize);
        int col = (int) ((x / (float)mCurrentWidth) * mGridSize);
        // Log.d("KenKen", "Track x/y = " + col + " / " + row);
        return getCellAt(row, col);
    }

    public void setMarkInvalidMaybes(boolean value) {
        markInvalidMaybes = value;
    }

    public void setMaybe3x3(boolean value) {
        maybe3x3 = value;
    }

    public void gridTouched(GridCell cell) {
        if (controls.getVisibility() == View.VISIBLE) {
            // digitSelector.setVisibility(View.GONE);
            if (hideselector) {
                controls.startAnimation(outAnimation);
                //cell.mSelected = false;
                mSelectorShown = false;
            }
            requestFocus();
        } else {
            if (hideselector) {
                controls.setVisibility(View.VISIBLE);
                Animation animation = AnimationUtils.loadAnimation(mContext, R.anim.selectorzoomin);
                controls.startAnimation(animation);
                mSelectorShown = true;
            }
            controls.requestFocus();
        }
    }

    public boolean onTouch(View arg0, MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN)
            return false;
        if (!mActive)
            return false;

        // Find out where the grid was touched.
        float x = event.getX();
        float y = event.getY();
        int size = getMeasuredWidth();

        int row = (int)((size - (size-y))/(size/mGridSize));
        if (row > mGridSize-1) row = mGridSize-1;
        if (row < 0) row = 0;

        int col = (int)((size - (size-x))/(size/mGridSize));
        if (col > mGridSize-1) col = mGridSize-1;
        if (col < 0) col = 0;

        // We can now get the cell.
        GridCell cell = getCellAt(row, col);
        if (mSelectedCell != cell)
            playSoundEffect(SoundEffectConstants.CLICK);
        mSelectedCell = cell;

        float[] cellPos = CellToCoord(cell.mCellNumber);
        mTrackPosX = cellPos[0];
        mTrackPosY = cellPos[1];

        for (GridCell c : mCells) {
            c.mSelected = false;
            mCages.get(c.mCageId).mSelected = false;
        }
        mSelectedCell.mSelected = true;
        mCages.get(mSelectedCell.mCageId).mSelected = true;
        gridTouched(mSelectedCell);
        invalidate();
        return false;
    }

    // Handle trackball, both press down, and scrolling around to
    // select a cell.
    public boolean onTrackballEvent(MotionEvent event) {
        if (!mActive || mSelectorShown)
            return false;
        // On press event, take selected cell, call touched listener
        // which will popup the digit selector.
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mSelectedCell.mSelected = true;
            gridTouched(mSelectedCell);
            return true;
        }
        // A multiplier amplifies the trackball event values
        int trackMult = 70;
        switch (mGridSize) {
            case 5:
                trackMult = 60;
                break;
            case 6:
                trackMult = 50;
                break;
            case 7:
                trackMult = 40;
                break;
            case 8:
                trackMult = 40;
        }
        // Fetch the trackball position, work out the cell it's at
        float x = event.getX();
        float y = event.getY();
        mTrackPosX += x*trackMult;
        mTrackPosY += y*trackMult;
        GridCell cell = CoordToCell(mTrackPosX, mTrackPosY);
        if (cell == null) {
            mTrackPosX -= x*trackMult;
            mTrackPosY -= y*trackMult;
            return true;
        }
        // Set the cell as selected
        if (mSelectedCell != null) {
            mSelectedCell.mSelected = false;
            if (mSelectedCell != cell)
                gridTouched(cell);
        }
        for (GridCell c : mCells) {
            c.mSelected = false;
            mCages.get(c.mCageId).mSelected = false;
        }
        mSelectedCell = cell;
        cell.mSelected = true;
        mCages.get(mSelectedCell.mCageId).mSelected = true;
        invalidate();
        return true;
    }


    // Return the number of times a given user value is in a row
    public int getNumValueInRow(GridCell ocell) {
        int count = 0;
        for (GridCell cell : mCells) {
            if (cell.mRow == ocell.mRow && cell.getUserValue() == ocell.getUserValue())
                count++;
        }
        return count;
    }
    public int getNumValueInRow(GridCell ocell, int value) {
        int count = 0;
        for (GridCell cell : mCells) {
            if (cell.mRow == ocell.mRow && cell.getUserValue() == value)
                count++;
        }
        return count;
    }

    // Return the number of times a given user value is in a column
    public int getNumValueInCol(GridCell ocell) {
        int count = 0;
        for (GridCell cell : mCells) {
            if (cell.mColumn == ocell.mColumn && cell.getUserValue() == ocell.getUserValue())
                count++;
        }
        return count;
    }

    public int getNumValueInCol(GridCell ocell, int value) {
        int count = 0;
        for (GridCell cell : mCells) {
            if (cell.mColumn == ocell.mColumn && cell.getUserValue() == value)
                count++;
        }
        return count;
    }

    // Solve the puzzle by setting the Uservalue to the actual value
    public void Solve() {
        for (GridCell cell : mCells)
            cell.setUserValue(cell.mValue);
        invalidate();
    }

    // Returns whether the puzzle is solved.
    public boolean isSolved() {
        for (GridCell cell : mCells)
            if (!cell.isUserValueCorrect())
                return false;
        return true;
    }

    // Checks whether the user has made any mistakes
    public boolean isSolutionValidSoFar()
    {
        for (GridCell cell : mCells)
            if (cell.isUserValueSet())
                if (cell.getUserValue() != cell.mValue)
                    return false;

        return true;
    }

    // Highlight those cells where the user has made a mistake
    public void markInvalidChoices()
    {
        boolean isValid = true;
        for (GridCell cell : mCells)
            if (cell.isUserValueSet())
                if (cell.getUserValue() != cell.mValue) {
                    cell.setInvalidHighlight(true);
                    isValid = false;
                }

        if (!isValid)
            invalidate();

        return;
    }

    // Return the list of cells that are highlighted as invalid
    public ArrayList<GridCell> invalidsHighlighted()
    {
        ArrayList<GridCell> invalids = new ArrayList<GridCell>();
        for (GridCell cell : mCells)
            if (cell.getInvalidHighlight())
                invalids.add(cell);

        return invalids;
    }

    private void writeCell(GridCell cell) throws java.io.IOException {
        writer.write("CELL:");
        writer.write(cell.mCellNumber + ":");
        writer.write(cell.mRow + ":");
        writer.write(cell.mColumn + ":");
        writer.write(cell.mCageText + ":");
        writer.write(cell.mValue + ":");
        writer.write(cell.getUserValue() + ":");
        for (int possible : cell.mPossibles) {
            writer.write(possible + ",");
        }
        writer.write("\n");
    }

    private void writeCage(GridCage cage) throws java.io.IOException {
        writer.write("CAGE:");
        writer.write(cage.mId + ":");
        writer.write(cage.mAction + ":");
        writer.write(cage.mResult + ":");
        writer.write(cage.mType + ":");
        for (GridCell cell : cage.mCells)
            writer.write(cell.mCellNumber + ",");
        writer.write(":" + cage.isOperatorHidden());
        writer.write("\n");
    }

    public synchronized boolean Save(String filename) {// Avoid saving game at the same time as creating puzzle
        String mFilename = mContext.getFilesDir() + File.separator + filename;
        try {
            writer = new BufferedWriter(new FileWriter(mFilename));
            long now = System.currentTimeMillis();
            writer.write(now + "\n");
            writer.write(mGridSize + "\n");
            writer.write(isActive() + "\n");
            writer.write("Duration:");
            writer.write(Integer.toString(game_duration) + "\n");
            for (GridCell cell : mCells) {
                writeCell(cell);
            }
            if (mSelectedCell != null) {
                writer.write("SELECTED:" + mSelectedCell.mCellNumber + "\n");
            }
            ArrayList<GridCell> invalidchoices = invalidsHighlighted();
            if (invalidchoices.size() > 0) {
                writer.write("INVALID:");
                for (GridCell cell : invalidchoices) {
                    writer.write(cell.mCellNumber + ",");
                }
                writer.write("\n");
            }
            for (GridCage cage : mCages) {
                writeCage(cage);
            }
        } catch (IOException e) {
            Log.d("MathDoku", "Error saving game: "+e.getMessage());
            return false;
        } finally {
            try {
                if (writer != null)
                    writer.close();
                writer = null;
            } catch (IOException e) {
                //pass
                return false;
            }
        }
        Log.d("MathDoku", "Saved game.");
        return true;
    }


    public static long ReadDate(String filename) {
        String mFilename = filename;
        BufferedReader br = null;
        InputStream ins = null;
        try {
            ins = new FileInputStream(new File(mFilename));
            br = new BufferedReader(new InputStreamReader(ins), 8192);
            return Long.parseLong(br.readLine());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                ins.close();
                br.close();
            } catch (Exception e) {
                // Nothing.
                return 0;
            }
        }
        return 0;
    }

    GridCell readCell(String[] cellParts) {
        int cellNum = Integer.parseInt(cellParts[1]);
        GridCell cell = new GridCell(this, cellNum);
        cell.mRow = Integer.parseInt(cellParts[2]);
        cell.mColumn = Integer.parseInt(cellParts[3]);
        cell.mCageText = cellParts[4];
        cell.mValue = Integer.parseInt(cellParts[5]);
        cell.setUserValue(Integer.parseInt(cellParts[6]));
        if (cellParts.length == 8) {
            for (String possible : cellParts[7].split(",")) {
                cell.mPossibles.add(Integer.parseInt(possible));
            }
        }
        return cell;
    }

    GridCage readCage(String[] cageParts) {
        GridCage cage;
        if (cageParts.length >= 7) {
            cage = new GridCage(this,
                    Integer.parseInt(cageParts[4]),
                    Boolean.parseBoolean(cageParts[6]));
        } else {
            cage = new GridCage(this,
                    Integer.parseInt(cageParts[4]),
                    false);
        }
        cage.mId = Integer.parseInt(cageParts[1]);
        cage.mAction = Integer.parseInt(cageParts[2]);
        cage.mResult = Integer.parseInt(cageParts[3]);
        for (String cellId : cageParts[5].split(",")) {
            int cellNum = Integer.parseInt(cellId);
            GridCell c = mCells.get(cellNum);
            c.mCageId = cage.mId;
            cage.mCells.add(c);
        }
        return cage;
    }


    public boolean Restore(String filename) {
        String line = null;
        String mFilename = mContext.getFilesDir() + File.separator + filename;
        BufferedReader br = null;
        InputStream ins = null;
        String[] cellParts;
        String[] cageParts;
        File file = new File(mFilename);
        boolean isAutoSave =  filename.equals(MathDoku.savegamename) ? true : false;
        try {
            ins = new FileInputStream(file);
            br = new BufferedReader(new InputStreamReader(ins), 8192);
            mDate = Long.parseLong(br.readLine());
            mGridSize = Integer.parseInt(br.readLine());
            if (br.readLine().equals("true")) {
                setActive(true);
            } else {
                setActive(false);
            }
            mCells = new ArrayList<GridCell>();
            mCages = new ArrayList<GridCage>();
            mSelectedCell = null;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("Duration")) {
                    String parts[] = line.split(":");
                    game_duration = Integer.parseInt(parts[1]);
                    continue;
                }
                if (line.startsWith("CELL:")) {
                    cellParts = line.split(":");
                    GridCell cell = readCell(cellParts);
                    mCells.add(cell);
                }
                if (line.startsWith("SELECTED:")) {
                    int selected = Integer.parseInt(line.split(":")[1]);
                    mSelectedCell = mCells.get(selected);
                    mSelectedCell.mSelected = true;
                }
                if (line.startsWith("INVALID:")) {
                    String invalidlist = line.split(":")[1];
                    for (String cellId : invalidlist.split(",")) {
                        int cellNum = Integer.parseInt(cellId);
                        GridCell c = mCells.get(cellNum);
                        c.setInvalidHighlight(true);
                    }
                }
                if (line.startsWith("CAGE:")) {
                    cageParts = line.split(":");
                    GridCage cage = readCage(cageParts);
                    mCages.add(cage);
                }
            }

        } catch (FileNotFoundException e) {
            Log.d("Mathdoku", "FNF Error restoring game: " + e.getMessage());
            return false;
        } catch (IOException e) {
            Log.d("Mathdoku", "IO Error restoring game: " + e.getMessage());
            return false;
        } finally {
            try {
                ins.close();
                br.close();
                if (isAutoSave) {
                    file.delete();
                }
            } catch (Exception e) {
                // Nothing.
                return false;
            }
        }
        invalidate();
        return true;
    }
}
