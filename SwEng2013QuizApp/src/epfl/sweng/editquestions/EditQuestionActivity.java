package epfl.sweng.editquestions;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import epfl.sweng.QuizQuestion;
import epfl.sweng.R;
import epfl.sweng.servercomm.ServerCommunicator;
import epfl.sweng.showquestions.ShowQuestionsActivity;
import epfl.sweng.testing.TestingTransactions;
import epfl.sweng.testing.TestingTransactions.TTChecks;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

/**
 * 
 * Activity to edit questions
 * 
 */
public class EditQuestionActivity extends Activity {

	private ArrayList<Answer> answers;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_edit_question);

		answers = new ArrayList<Answer>();
		answers.add(new Answer(this,
				(ViewGroup) findViewById(R.id.linearLayoutAnswers), true));

		findViewById(R.id.editQuestionText).requestFocus();

		// let the testing infrastructure know that edit question has been
		// initialized
		TestingTransactions.check(TTChecks.EDIT_QUESTIONS_SHOWN);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.edit_question, menu);
		return true;
	}

	public void addAnswer(View view) {
		ViewGroup linearLayout = (ViewGroup) findViewById(R.id.linearLayoutAnswers);
		answers.add(new Answer(this, linearLayout, false));
	}

	public ArrayList<Answer> getAnswers() {
		return answers;
	}

	public void submitQuestion(View view) {
		InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

		inputManager.hideSoftInputFromWindow(
				getCurrentFocus().getWindowToken(),
				InputMethodManager.HIDE_NOT_ALWAYS);

		String question = ((EditText) findViewById(R.id.editQuestionText))
				.getText().toString();

		ArrayList<String> answersText = new ArrayList<String>();
		int solutionIndex = 0;

		for (Answer answer : answers) {
			answersText.add(answer.getContent());
			if (answer.isCorrect()) {
				solutionIndex = answers.indexOf(answer);
			}
		}

		String tagsText = ((EditText) findViewById(R.id.editTags)).getText()
				.toString();
		String[] tags = tagsText.split("\\W+");

		QuizQuestion quizQuestion = new QuizQuestion(null, question,
				answersText.toArray(new String[answers.size()]), solutionIndex,
				tags, null);

		try {
			System.out.println(ServerCommunicator.getInstance()
					.submitQuizQuestion(quizQuestion));
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (AssertionError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Toast.makeText(this, "Successful Submit", 1000).show();
		Intent displayActivityIntent = new Intent(this,
				ShowQuestionsActivity.class);
		startActivity(displayActivityIntent);
	}

}