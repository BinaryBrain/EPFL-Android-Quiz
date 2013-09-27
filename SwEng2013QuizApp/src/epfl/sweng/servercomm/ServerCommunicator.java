package epfl.sweng.servercomm;

import java.util.concurrent.ExecutionException;

import org.apache.http.client.methods.HttpGet;

import android.os.AsyncTask;
import epfl.sweng.QuizQuestion;

/**
 * 
 * Handles communication with the server.
 * 
 * @author  Jeremy Rabasco (jeremy.rabasco@epfl.ch),
 *          Philemon Favrod (philemon.favrod@epfl.ch)
 */
public final class ServerCommunicator {

	private static ServerCommunicator mInstance = null;
	
	private ServerCommunicator() {
		
	}
	
	public synchronized ServerCommunicator getInstance() {
		if (mInstance == null) {
			mInstance = new ServerCommunicator();
		}
		return mInstance;
	}
	
	public QuizQuestion getRandomQuestion() throws InterruptedException,
			ExecutionException {
		AsyncTask<Void, Void, QuizQuestion> fetchTask = new AsyncTask<Void, Void, QuizQuestion>() {

			@Override
			protected QuizQuestion doInBackground(Void... params) {
			    
			    
				// TODO Auto-generated method stub
				return null;
			}

		};
		return fetchTask.execute().get();
	}
	
	public void submitQuizQuestion(QuizQuestion question) throws InterruptedException, ExecutionException {
		
		AsyncTask<QuizQuestion, Void, Void> submitTask = new AsyncTask<QuizQuestion, Void, Void>() {

			@Override
			protected Void doInBackground(QuizQuestion... params) {
				// TODO Auto-generated method stub
				return null;
			}


		};
		submitTask.execute(question).get();
	}
}