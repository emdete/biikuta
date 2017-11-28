package de.emdete.biikuta;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.RectF;
import android.renderscript.Double2;
import android.util.AttributeSet;
import android.view.View;
import android.content.res.Resources;
import android.graphics.Typeface;

public class Measure extends View {
	double percentage = 0;

	private final Paint black = new Paint();
	private final Paint red = new Paint();
	private final Paint yellow = new Paint();
	private final Paint green = new Paint();
	private final Paint gray = new Paint();

	public Measure(Context context, AttributeSet attrs) {
		super(context, attrs);
		final Resources res = getResources();
		black.setColor(res.getColor(R.color.black, null));
		red.setColor(res.getColor(R.color.red, null));
		yellow.setColor(res.getColor(R.color.yellow, null));
		yellow.setTypeface(Typeface. SANS_SERIF);
		yellow.setTextSize(80);
		green.setColor(res.getColor(R.color.green, null));
		gray.setColor(res.getColor(R.color.gray, null));
		setFault();
	}

	public Measure(Context context) {
		super(context);
	}

	@Override protected void onDraw(Canvas canvas) {
		int width = canvas.getWidth();
		int height = canvas.getHeight();
		canvas.save();
		if (!Double.isNaN(percentage)) {
			canvas.drawRect(
				0, // left
				0, // top
				width, // right
				height / 2, // bottom
				red // paint
				);
			canvas.drawRect(
				0, // left
				height / 2, // top
				width, // right
				height / 4 * 3, // bottom
				yellow // paint
				);
			canvas.drawRect(
				0, // left
				height / 4 * 3, // top
				width, // right
				height, // bottom
				green // paint
				);
			//if (false)
			canvas.drawRect( // black out from top
				0, // left
				0, // top
				width, // right
				(float)(height * (100 - percentage) / 100), // bottom
				black // paint
				);
		}
		else
			canvas.drawRect( // gray out
				0, // left
				0, // top
				width, // right
				height, // bottom
				gray // paint
				);
		String tag = getTag().toString();
		canvas.drawText (
			tag, // text
			0, // start
			1, // end
			width / 2 - 25, // x
			height / 6 * 5, // y
			yellow); // paint
		canvas.restore();
	}

	public void setPercentage(double percentage) {
		this.percentage = percentage;
		postInvalidate();
	}

	public double getPercentage() {
		return percentage;
	}

	public void setFault() {
		percentage = Double.NaN;
		postInvalidate();
	}
}
