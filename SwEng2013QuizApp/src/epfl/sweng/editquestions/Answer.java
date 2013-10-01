package epfl.sweng.editquestions;

import java.util.ArrayList;

import epfl.sweng.R;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * An answer as represented in the GUI
 */
public class Answer {
	private Button mCorrectButton;
	private Button mRemoveButton;
	private EditText mContent;
	private boolean mCorrect;
	private LinearLayout linearLayout;

	public Answer(final EditQuestionActivity activity, ViewGroup view, boolean first) {
		// Create Layout
		linearLayout = new LinearLayout(activity);
		linearLayout.setLayoutParams(new LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		view.addView(linearLayout);

		// Create EditText
		mContent = new EditText(activity);
		mContent.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT));
		mContent.setHint(R.string.edit_answer_text);
		linearLayout.addView(mContent);

		// Create Button Correct
		mCorrectButton = new Button(activity);
		if (first) {
			mCorrectButton.setText(R.string.button_check);
			mCorrectButton.setEnabled(false);
			mCorrect = true;
		} else {
			mCorrectButton.setText(R.string.button_cross);
			mCorrect = false;
		}
		mCorrectButton.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT));
		linearLayout.addView(mCorrectButton);
		
		mCorrectButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				ArrayList<Answer> answers = activity.getAnswers();
				for (Answer a: answers) {
					if (a.isCorrect()) {
						a.setCorrect(false);
					}
				}
				setCorrect(true);
			}
		});

		// Create Button remove
		mRemoveButton = new Button(activity);
		mRemoveButton.setText(R.string.button_remove);
		linearLayout.addView(mRemoveButton);
		
		if (first) {
			mRemoveButton.setEnabled(false);
		} else {
			activity.getAnswers().get(0).mRemoveButton.setEnabled(true);
		}
		
		
		// TODO Make it cleaner
		final Answer mThis = this;
		
		mRemoveButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				ArrayList<Answer> answers = activity.getAnswers();
				answers.remove(mThis);
				
				if (mThis.isCorrect()) {
					answers.get(0).setCorrect(true);
				}
				
				remove();
				
				if (answers.size() == 1) { 
					answers.get(0).mRemoveButton.setEnabled(false);
				}
			}
		});
	}

	public String getContent() {
		return mContent.getText().toString();
	}

	public boolean isCorrect() {
		return mCorrect;
	}

	public void setCorrect(Boolean correct) {
		if (correct) {
			mCorrectButton.setText(R.string.button_check);
			mCorrectButton.setEnabled(false);
			mCorrect = true;
		} else {
			mCorrectButton.setText(R.string.button_cross);
			mCorrectButton.setEnabled(true);
			mCorrect = false;
		}
	}

	public Button getRemoveButton() {
		return mRemoveButton;
	}
	
	public void remove() {
		((ViewGroup) linearLayout.getParent()).removeView(linearLayout);
	}
}
