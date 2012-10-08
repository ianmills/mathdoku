package com.mathdoku;

import java.util.ArrayList;
import java.util.Random;

import com.mathdoku.DLX.SolveType;
import com.mathdoku.MathDokuDLX;
import com.mathdoku.GridCell;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.DiscretePathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.TextView;

public class GridView extends View implements OnTouchListener  {

	public static final int THEME_CARVED = 0;
	public static final int THEME_NEWSPAPER = 1;

    private OnSolvedListener mSolvedListener;
    private OnGridTouchListener mTouchedListener;
    public Button digits[];

    public int mGridSize;
    public Random mRandom;
    public Activity mContext;

    public ArrayList<GridCage> mCages;
    public ArrayList<GridCell> mCells;

    public boolean mActive;
    public boolean mSelectorShown = false;
    public float mTrackPosX;
    public float mTrackPosY;
    public GridCell mSelectedCell;
    public TextView animText;
    public int mCurrentWidth;
    public Paint mGridPaint;
    public Paint mBorderPaint;
    public int mBackgroundColor;

    public Typeface mFace;
    public boolean mDupedigits;
    public boolean mBadMaths;

	// Date of current game (used for saved games)
	public long mDate;
	public int mTheme;

    public GridView(Context context) {
        super(context);
        initGridView();
    }

    public GridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initGridView();
    }
    public GridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initGridView();
    }


    public void initGridView() {

        mSolvedListener = null;
        mDupedigits = true;
        mBadMaths = true;

        mGridPaint = new Paint();
        mGridPaint.setColor(0x80000000);
        mGridPaint.setStrokeWidth(0);
        //mGridPaint.setPathEffect(new DashPathEffect(new float[] {2, 2}, 0));

        mBorderPaint = new Paint();
        mBorderPaint.setColor(0xFF000000);
        mBorderPaint.setStrokeWidth(3);
        mBorderPaint.setStyle(Style.STROKE);

        mCurrentWidth = 0;
        mGridSize = 0;
        mActive = false;
        setOnTouchListener((OnTouchListener) this);
    }

    public void setTheme(int theme) {
        if (theme == THEME_CARVED) {
            mGridPaint.setAntiAlias(true);
            mGridPaint.setPathEffect(new DiscretePathEffect(20, 1));
            mGridPaint.setColor(0xbf906050);
            mBorderPaint.setAntiAlias(true);
            mBorderPaint.setPathEffect(new DiscretePathEffect(30, 1));
            mBackgroundColor = 0x7ff0d090;
        } else if (theme == THEME_NEWSPAPER) {
            mGridPaint.setPathEffect(new DashPathEffect(new float[] {2, 2}, 0));
            mBorderPaint.setAntiAlias(false);
            mBorderPaint.setPathEffect(null);
            mBackgroundColor = 0xffffffff;
        }
        if (getMeasuredHeight() < 150)
            mBorderPaint.setStrokeWidth(1);
        else
            mBorderPaint.setStrokeWidth(3);

        if (mCells != null)
            for (GridCell cell : mCells)
                cell.setTheme(theme);
    }

    public synchronized void reCreate(boolean hideOperators) {
        int num_solns;
        int num_attempts = 0;
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
        mActive = true;
        mSelectorShown = false;
        setTheme(mTheme);
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
        for (GridCell cell : mCells)
            if (cell.mRow == row && cell.mValue == value)
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

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        if (mGridSize < 4) return;
        if (mCages == null) return;

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
            if (mSolvedListener != null)
                mSolvedListener.puzzleSolved();
            if (mSelectedCell != null)
                mSelectedCell.mSelected = false;
            mActive = false;
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
        for (int i=1;i<=9;i++) {
            if (mSelectedCell.mPossibles.contains(i)) {
                digits[i-1].setPressed(true);
            } else {
                digits[i-1].setPressed(false);
            }
        }

        float[] cellPos = CellToCoord(cell.mCellNumber);
        mTrackPosX = cellPos[0];
        mTrackPosY = cellPos[1];

        for (GridCell c : mCells) {
            c.mSelected = false;
            mCages.get(c.mCageId).mSelected = false;
        }
        if (mTouchedListener != null) {
            mSelectedCell.mSelected = true;
            mCages.get(mSelectedCell.mCageId).mSelected = true;
            mTouchedListener.gridTouched(mSelectedCell);
        }
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
            if (mTouchedListener != null) {
                mSelectedCell.mSelected = true;
                mTouchedListener.gridTouched(mSelectedCell);
            }
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
                mTouchedListener.gridTouched(cell);
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

    public void setSolvedHandler(OnSolvedListener listener) {
        mSolvedListener = listener;
    }
    public abstract class OnSolvedListener {
        public abstract void puzzleSolved();
    }

    public void setOnGridTouchListener(OnGridTouchListener listener) {
        mTouchedListener = listener;
    }
    public abstract class OnGridTouchListener {
        public abstract void gridTouched(GridCell cell);
    }
}
