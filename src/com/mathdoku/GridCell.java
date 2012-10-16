package com.mathdoku;

import java.util.ArrayList;
import java.util.Collections;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.DiscretePathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.util.Log;

public class GridCell {
    // Index of the cell (left to right, top to bottom, zero-indexed)
    public int mCellNumber;
    // X grid position, zero indexed
    public int mColumn;
    // Y grid position, zero indexed
    public int mRow;
    // X pixel position
    public float mPosX;
    // Y pixel position
    public float mPosY;
    // Value of the digit in the cell
    public int mValue;
    // User's entered value
    private int mUserValue;
    // Id of the enclosing cage
    public int mCageId;
    // String of the cage
    public String mCageText;
    // View context
    public GridView mGridView;
    // User's candidate digits
    public ArrayList<Integer> mPossibles;
    // Whether to show warning background (duplicate value in row/col)
    public boolean mShowWarning;
    // Whether to show cell as selected
    public boolean mSelected;
    // Player cheated (revealed this cell)
    public boolean mCheated;
    // Highlight user input isn't correct value
    private boolean mInvalidHighlight;

    public static final int BORDER_NONE = 0;
    public static final int BORDER_SOLID = 1;
    public static final int BORDER_WARN = 3;
    public static final int BORDER_CAGE_SELECTED = 4;

    public int[] mBorderTypes;

    private Paint mValuePaint;
    private Paint mBorderPaint;
    private Paint mCageSelectedPaint;

    private Paint mWrongBorderPaint;
    private Paint mCageTextPaint;
    private Paint mPossiblesPaint;
    private Paint mWarningPaint;
    private Paint mCheatedPaint;
    private Paint mSelectedPaint;

    public GridCell(GridView gridView, int cell) {
        mGridView = gridView;
        int gridSize = mGridView.mGridSize;
        mCellNumber = cell;
        mColumn = cell % gridSize;
        mRow = (int)(cell / gridSize);
        mCageText = "";
        mCageId = -1;
        mValue = 0;
        mUserValue = 0;
        mShowWarning = false;
        mCheated = false;
        mInvalidHighlight = false;

        mPosX = 0;
        mPosY = 0;

        mValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mValuePaint.setColor(0xFF000000);
        // mValuePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        mBorderPaint = new Paint();
        mBorderPaint.setColor(0xFF000000);
        mBorderPaint.setStrokeWidth(2);


        mWrongBorderPaint = new Paint();
        mWrongBorderPaint.setColor(0xFFBB0000);
        mWrongBorderPaint.setStrokeWidth(2);

        mCageSelectedPaint = new Paint();
        mCageSelectedPaint.setColor(0xFF9BCF00);
        mCageSelectedPaint.setStrokeWidth(2);

        mWarningPaint = new Paint();
        mWarningPaint.setColor(0x50FF0000);
        mWarningPaint.setStyle(Paint.Style.FILL);

        mCheatedPaint = new Paint();
        mCheatedPaint.setColor(0x90ffcea0);
        mCheatedPaint.setStyle(Paint.Style.FILL);

        mSelectedPaint = new Paint();
        mSelectedPaint.setColor(0xD0F0D042);
        mSelectedPaint.setStyle(Paint.Style.FILL);

        mCageTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCageTextPaint.setColor(0xFF0000A0);
        mCageTextPaint.setTextSize(14);
        //mCageTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        mPossiblesPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPossiblesPaint.setColor(0xFF000000);
        mPossiblesPaint.setTextSize(10);
        mPossiblesPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        mPossibles = new ArrayList<Integer>();
        //mPossibles.add(1);
        //mPossibles.add(2);
        //mPossibles.add(3);
        //mPossibles.add(4);

        //mPossibles.add(5);

        setBorders(BORDER_NONE, BORDER_NONE, BORDER_NONE, BORDER_NONE);
    }

    public String toString() {
        String str = "<cell:" + mCellNumber + " col:" + mColumn +
            " row:" + mRow + " posX:" + mPosX + " posY:" +
            mPosY + " val:" + mValue + ", userval: " + mUserValue + ">";
        return str;
    }

    /* Sets the cells border type to the given values.
     *
     * Border is BORDER_NONE, BORDER_SOLID, BORDER_WARN or BORDER_CAGE_SELECTED.
     */
    public void setBorders(int north, int east, int south, int west) {
        int[] borders = new int[4];
        borders[0] = north;
        borders[1] = east;
        borders[2] = south;
        borders[3] = west;
        mBorderTypes = borders;
    }

    /* Returns the Paint object for the given border of this cell. */
    private Paint getBorderPaint(int border) {
        switch (mBorderTypes[border]) {
            case BORDER_NONE:
                return null;
            case BORDER_SOLID :
                return mBorderPaint;
            case BORDER_WARN :
                return mWrongBorderPaint;
            case BORDER_CAGE_SELECTED :
                return mCageSelectedPaint;
        }
        return null;
    }

    public void togglePossible(int digit) {
        if (mPossibles.indexOf(new Integer(digit)) == -1)
            mPossibles.add(digit);
        else
            mPossibles.remove(new Integer(digit));
        Collections.sort(mPossibles);
    }

    public int getUserValue() {
        return mUserValue;
    }

    public boolean isUserValueSet() {
        return mUserValue != 0;
    }

    public void setUserValue(int digit) {
        mUserValue = digit;
        mInvalidHighlight = false;
    }

    public void clearUserValue() {
        setUserValue(0);
    }

    public boolean isUserValueCorrect()
    {
        return mUserValue == mValue;
    }

    /* Returns whether the cell is a member of any cage */
    public boolean CellInAnyCage()
    {
        return mCageId != -1;
    }

    public void setInvalidHighlight(boolean value) {
        mInvalidHighlight = value;
    }
    public boolean getInvalidHighlight() {
        return mInvalidHighlight;
    }

    /* Draw the cell. Border and text is drawn. */
    public void onDraw(Canvas canvas, boolean onlyBorders) {

        // Calculate x and y for the cell origin (topleft)
        float cellSize = (float)mGridView.getMeasuredWidth() / (float)mGridView.mGridSize;
        mPosX = cellSize * mColumn;
        mPosY = cellSize * mRow;

        float north = mPosY;
        float south = mPosY + cellSize;
        float east = mPosX + cellSize;
        float west = mPosX;
        GridCell cellAbove = mGridView.getCellAt(mRow-1, mColumn);
        GridCell cellLeft = mGridView.getCellAt(mRow, mColumn-1);
        GridCell cellRight = mGridView.getCellAt(mRow, mColumn+1);
        GridCell cellBelow = mGridView.getCellAt(mRow+1, mColumn);

        if (!onlyBorders) {
            if ((mShowWarning && mGridView.mDupedigits) || mInvalidHighlight)
                canvas.drawRect(west + 1, north+1, east-1, south-1, mWarningPaint);
            if (mSelected)
                canvas.drawRect(west+1, north+1, east-1, south-1, mSelectedPaint);
            if (mCheated)
                canvas.drawRect(west+1, north+1, east-1, south-1, mCheatedPaint);
        } else {
            if (mBorderTypes[0] > 2)
                if (cellAbove == null)
                    north += 2;
                else
                    north += 1;
            if (mBorderTypes[3] > 2)
                if (cellLeft == null)
                    west += 2;
                else
                    west += 1;
            if (mBorderTypes[1] > 2)
                if (cellRight == null)
                    east -= 3;
                else
                    east -= 2;
            if (mBorderTypes[2] > 2)
                if (cellBelow == null)
                    south -= 3;
                else
                    south -= 2;
        }
        // North
        Paint borderPaint = getBorderPaint(0);
        if (!onlyBorders && mBorderTypes[0] > 2)
            borderPaint = mBorderPaint;
        if (borderPaint != null) {
            canvas.drawLine(west, north, east, north, borderPaint);
        }

        // East
        borderPaint = getBorderPaint(1);
        if (!onlyBorders && mBorderTypes[1] > 2)
            borderPaint = mBorderPaint;
        if (borderPaint != null)
            canvas.drawLine(east, north, east, south, borderPaint);

        // South
        borderPaint = getBorderPaint(2);
        if (!onlyBorders && mBorderTypes[2] > 2)
            borderPaint = mBorderPaint;
        if (borderPaint != null)
            canvas.drawLine(west, south, east, south, borderPaint);

        // West
        borderPaint = getBorderPaint(3);
        if (!onlyBorders && mBorderTypes[3] > 2)
            borderPaint = mBorderPaint;
        if (borderPaint != null) {
            canvas.drawLine(west, north, west, south, borderPaint);
        }

        if (onlyBorders)
            return;

        // Cell value
        if (isUserValueSet()) {
            int textSize = (int)(cellSize*3/4);
            mValuePaint.setTextSize(textSize);
            float leftOffset = cellSize/2 - textSize/4;
            float topOffset;
            topOffset = cellSize/2 + textSize/3;
            canvas.drawText("" + mUserValue, mPosX + leftOffset, mPosY + topOffset, mValuePaint);
        }

        int cageTextSize = (int)(cellSize/3);
        mCageTextPaint.setTextSize(cageTextSize);
        // Cage text
        if (!mCageText.equals("")) {
            canvas.drawText(mCageText, mPosX + 2, mPosY + cageTextSize, mCageTextPaint);

            // canvas.drawText(mCageText, mPosX + 2, mPosY + 13, mCageTextPaint);
        }

        if (mPossibles.size()>1) {
            if (mGridView.maybe3x3) {
                mPossiblesPaint.setFakeBoldText(true);
                mPossiblesPaint.setTextSize((int)(cellSize/4.5));
                int xOffset = (int) (cellSize/3);
                int yOffset = (int) (cellSize/2) + 1;
                float xScale = (float) 0.21 * cellSize;
                float yScale = (float) 0.21 * cellSize;
                for (int i = 0 ; i < mPossibles.size() ; i++) {
                    int possible = mPossibles.get(i);
                    mPossiblesPaint.setColor(0xFF000000);
                    if (mGridView.markInvalidMaybes && (mGridView.getNumValueInRow(this, possible) >= 1 ||
                                mGridView.getNumValueInCol(this, possible) >= 1)) {
                        mPossiblesPaint.setColor(0x50FF0000);
                                }
                    float xPos = mPosX + xOffset + ((possible-1)%3)*xScale;
                    float yPos = mPosY + yOffset + ((int)(possible-1)/3)*yScale;
                    canvas.drawText(Integer.toString(possible), xPos, yPos, mPossiblesPaint);
                }
            }
            else {
                mPossiblesPaint.setFakeBoldText(false);
                mPossiblesPaint.setTextSize((int)((cellSize*1.5)/mPossibles.size()));
                int offset = 0;
                for (int i = 0 ; i < mPossibles.size() ; i++) {
                    int possible = mPossibles.get(i);
                    if (mGridView.markInvalidMaybes && (mGridView.getNumValueInRow(this, possible) >= 1 ||
                                mGridView.getNumValueInCol(this, possible) >= 1)) {
                        mPossiblesPaint.setColor(0x50FF0000);
                    } else {
                        mPossiblesPaint.setColor(0xFF000000);
                    }
                    canvas.drawText(Integer.toString(possible), mPosX+3+offset, mPosY + cellSize-5, mPossiblesPaint);
                    offset += mPossiblesPaint.measureText(Integer.toString(possible));
                }
            }
        }
    }

}
