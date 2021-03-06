package epfl.sweng.proxy;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Set;

import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;
import epfl.sweng.cache.SQLiteCache;
import epfl.sweng.context.AppContext;
import epfl.sweng.context.ConnectionEvent;
import epfl.sweng.context.ConnectionEvent.ConnectionEventType;
import epfl.sweng.context.connstates.ServerSyncConnectionState;
import epfl.sweng.editquestions.PostedQuestionEvent;
import epfl.sweng.entry.SwitchSuccessfulEvent;
import epfl.sweng.events.EventEmitter;
import epfl.sweng.events.EventListener;
import epfl.sweng.quizquestions.MalformedQuestionException;
import epfl.sweng.quizquestions.QuizQuestion;
import epfl.sweng.searchquestions.parser.QueryParser.QueryParserResult;
import epfl.sweng.servercomm.RequestContext;
import epfl.sweng.servercomm.ServerCommunicator;
import epfl.sweng.servercomm.ServerEvent;
import epfl.sweng.servercomm.ServerResponse;
import epfl.sweng.showquestions.ReceivedQuestionEvent;
import epfl.sweng.showquestions.ReceivedQuestionWithError;

/**
 * Proxy for ServerCommunicator class <br/>
 * Handle offline mode with questions caching <br/>
 * Implements Singleton Pattern <br/>
 * Listen to: ServerCommunicator to handle callbacks. <br/>
 * Emits events to AppContext to control state machine
 */
public final class Proxy extends EventEmitter implements IServer, EventListener {
    private static final int HTTP_ERROR_THRESHOLD = 500;
    private static final int HTTP_ERROR_INTERMEDIATE_THRESHOLD = 400;

    private static final String TAG = "Proxy";
    private static final String QUERY_KEY = "{ \"query\": \"";

    /**
     * Singleton Instance
     */
    private static Proxy sInstance = null;

    /**
     * ServerCommunicator for delegation
     */
    private final IServer serverComm;

    /**
     * Cache for questions to be submitted next time online
     */
    private ArrayList<QuestionToSubmit> postQuestion;

    /**
     * Cache for retrieving questions while offline
     */
    private SQLiteCache cache;

    /**
     * Temporary for a question we tried to submit online but IOException
     */
    private QuestionToSubmit questionToSubmit;

    private ProxyState state = ProxyState.NORMAL;
    private QueryParserResult query;

    private ArrayList<ServerResponse> results = new ArrayList<ServerResponse>();
    private String next;

    private static final String BACKUP_FILE_NAME = "question.backup";

    private Context mContext;

    private Proxy(Context context) {
        serverComm = ServerCommunicator.getInstance();
        postQuestion = new ArrayList<QuestionToSubmit>();
        serverComm.addListener(this);
        AppContext.getContext().addAsListener(this);
        cache = new SQLiteCache(context);
        mContext = context;
    }

    /**
     * Get the singleton instance of Proxy
     *
     * @return the singleton instance
     */
    public static synchronized Proxy getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new Proxy(context);
        }
        return sInstance;
    }

    @Override
    public void doHttpGet(RequestContext reqContext, ServerEvent event) {
        if (isOnline()) {
            /*
             * While online the different states have the following semantics:
             *    - SEARCH implies to start a search request with the server.
             *    - NEXT means that a search request has already been done and that the
             *      but the client has displayed all the question of the server answer.
             *      That is, the client ask the server for further quiz questions matching
             *      its search.
             *    - Otherwise, one simply gets a random question from the server.
             */

            switch (state) {
                case SEARCH:
                    searchOnServer(reqContext, event);
                    break;
                case NEXT:
                    continueSearchingOnServer(reqContext, event);
                    break;
                default:
                    retrieveQuestionFromServer(reqContext, event);
                    break;
            }
        } else {

            /*
             * While offline the states have almost the same meaning except one fetches questions from cache.
             * Note that the cache always returns all the questions matching a request. That is, the state
             * NEXT has a different semantics. A search occurs as follows:
             *   - Look if questions matches the search query in cache.
             *   - If there are more than one, display one, store the questions in a list and go to the NEXT state.
             *     While in NEXT state, if one fetches the next question, we just remove the head of the list until
             *     the list is empty and finally return to NORMAL mode.
             *   - If there are one, just display it and stay in NORMAL mode (random question mode)
             *   - If there are no, stay in normal mode
             * Then,
             */

            switch (state) {
                case SEARCH:
                    searchInCache();
                    break;
                case NEXT:
                    continueSearchingInCache();
                    break;
                default:
                    retrieveQuestionFromCache();
                    break;
            }
        }

    }

    @Override
    public void doHttpPost(RequestContext reqContext, ServerEvent event) {
        questionToSubmit = new QuestionToSubmit(reqContext, event);
        if (isOnline()) {
            this.emit(new ConnectionEvent(
                    ConnectionEventType.ADD_OR_RETRIEVE_QUESTION));
            reqContext.getEntity().toString();
            serverComm.doHttpPost(reqContext, event);
        } else {
            postQuestion.add(questionToSubmit);
            try {
                serializeQuestionToPostList(postQuestion);
            } catch (IOException e) {

                Log.d(TAG, e.getMessage(), e);
            }
            PostedQuestionEvent pqe = new PostedQuestionEvent();
            pqe.setResponse(new ServerResponse(questionToSubmit.getReqContext()
                    .getEntity().toString(), HttpStatus.SC_OK));
            this.emit(pqe);
        }

    }

    public void giveQuery(QueryParserResult newQuery) {
        state = ProxyState.SEARCH;
        this.query = newQuery;
    }

    public boolean isOnline() {
        return AppContext.getContext().isOnline();
    }

    public void on(OnlineEvent event) {
        try {
            postQuestion = readPendingQuizQuestion();
        } catch (ClassNotFoundException e) {

            Log.d(TAG, e.getMessage(), e);
        } catch (IOException e) {

            Log.d(TAG, e.getMessage(), e);
        }
        if (!postQuestion.isEmpty()) {
            RequestContext reqContext = postQuestion.get(0).getReqContext();
            ServerEvent postEvent = postQuestion.get(0).getEvent();
            postQuestion.remove(0);
            try {
                serializeQuestionToPostList(postQuestion);
            } catch (IOException e) {

                Log.d(TAG, e.getMessage(), e);
            }
            doHttpPost(reqContext, postEvent);
        } else {
            this.emit(new SwitchSuccessfulEvent());
            this.emit(new ConnectionEvent(
                    ConnectionEventType.COMMUNICATION_SUCCESS));

        }

    }

    /**
     * Occurrs when a question is receive from server or cache.
     * @param event
     */
    public void on(ReceivedQuestionEvent event) {
        // data cannot be null because server answered something
        ServerResponse data = event.getResponse();

        if (data.getStatusCode() < HTTP_ERROR_THRESHOLD) {
            if (data.getStatusCode() >= HTTP_ERROR_INTERMEDIATE_THRESHOLD) {

                event = new ReceivedQuestionEvent();
                event.setResponse(null);

                this.emit(new ConnectionEvent(
                        ConnectionEventType.COMMUNICATION_SUCCESS));
            } else {
                String json = "";
                if (state == ProxyState.SEARCH || state == ProxyState.NEXT) {
                    try {
                        JSONObject o = new JSONObject(data.getEntity()
                                .toString());
                        JSONArray array = o.getJSONArray("questions");
                        if (array.length() > 0) {
                            json = array.getJSONObject(0).toString();
                            for (int i = 1; i < array.length(); i++) {
                                String innerJson = array.getJSONObject(i)
                                        .toString();
                                results.add(new ServerResponse(innerJson,
                                        HttpStatus.SC_OK));

                                cache.cacheQuestion(innerJson);
                            }
                            next = o.getString("next");
                        } else {
                            json = null;
                        }
                    } catch (JSONException e) {
                        next = "";
                        Log.d(TAG, e.getMessage(), e);
                    }
                    if ((null == next || "".equals(next) || "null".equals(next))
                            && results.isEmpty()) {
                        state = ProxyState.NORMAL;
                    } else {
                        state = ProxyState.NEXT;
                    }
                } else {
                    json = data.getEntity().toString();
                }
                if (json != null) {
                    cache.cacheQuestion(json);
                    event.setResponse(new ServerResponse(json, HttpStatus.SC_OK));
                } else if (state == ProxyState.NEXT) {
                    RequestContext reqContext = new RequestContext();
                    reqContext.addHeader("Authorization", "Tequila "
                            + AppContext.getContext().getSessionID());
                    getNextResultFromServer(reqContext,
                            new ReceivedQuestionEvent());
                } else {
                    event.setResponse(new ServerResponse(null,
                            HttpStatus.SC_NOT_FOUND));
                }
                this.emit(new ConnectionEvent(
                        ConnectionEventType.COMMUNICATION_SUCCESS));
            }

            // Server answered >= 500 status, go to offline mode
        } else {
            this.on(new GetConnectionErrorEvent());
        }

        this.emit(event);
    }

    /**
     * Server unreachable or answered >= 500 status code
     *
     * @param event
     */
    public void on(GetConnectionErrorEvent event) {
        // transition for state machine
        this.emit(new ConnectionEvent(ConnectionEventType.COMMUNICATION_ERROR));
        ReceivedQuestionWithError receiveEvent = new ReceivedQuestionWithError();
        receiveEvent.setResponse(offlineRandomQuestion());
        this.emit(receiveEvent);
    }

    public void on(PostedQuestionEvent event) {
        ServerResponse data = event.getResponse();
        if (data.getStatusCode() < HTTP_ERROR_THRESHOLD) {
            if (data.getStatusCode() >= HTTP_ERROR_INTERMEDIATE_THRESHOLD) {
                event.setResponse(null);
            } else {
                QuizQuestion question = null;
                try {
                    question = new QuizQuestion(data.getEntity());
                } catch (JSONException e) {
                    Log.d(TAG, e.getMessage(), e);
                }

                if (null != question) {
                    cache.cacheQuestion(question);
                }
            }

            this.emit(event);
            // post other cached questions that wait to be posted
            if (AppContext.getContext().getCurrentConnectionState().getClass() == ServerSyncConnectionState.class) {
                on(new OnlineEvent());
            } else {
                this.emit(new ConnectionEvent(
                        ConnectionEventType.COMMUNICATION_SUCCESS));
            }

        } else {

            this.on(new PostConnectionErrorEvent());

        }

    }

    public void on(PostConnectionErrorEvent event) {
        postQuestion.add(0, questionToSubmit);
        try {
            serializeQuestionToPostList(postQuestion);
        } catch (IOException e) {

            Log.d(TAG, e.getMessage(), e);
        }
        this.emit(new ConnectionEvent(ConnectionEventType.COMMUNICATION_ERROR));
        this.emit(event);
    }

    public void resetState() {
        state = ProxyState.NORMAL;
        postQuestion = new ArrayList<QuestionToSubmit>();
        try {
            serializeQuestionToPostList(postQuestion);
        } catch (IOException e) {

            Log.d(TAG, e.getMessage(), e);
        }
    }

    private void retrieveQuestionFromCache() {
        ReceivedQuestionEvent receiveEvent = new ReceivedQuestionEvent();
        receiveEvent.setResponse(offlineRandomQuestion());
        this.emit(receiveEvent);
    }

    private void continueSearchingInCache() {
        ReceivedQuestionEvent receiveEvent = new ReceivedQuestionEvent();
        receiveEvent.setResponse(results.remove(0));
        if (results.isEmpty()) {
            state = ProxyState.NORMAL;
        } else {
            state = ProxyState.NEXT;
        }
        this.emit(receiveEvent);
    }

    private void searchInCache() {
        Set<QuizQuestion> quizQuestionsMatchingQuery = cache.getQuestionSetByTag(query.getAST());

        if (quizQuestionsMatchingQuery.isEmpty()) {

            ReceivedQuestionEvent receiveEvent = new ReceivedQuestionEvent();
            receiveEvent.setResponse(new ServerResponse(null, HttpStatus.SC_NOT_FOUND));
            state = ProxyState.NORMAL;

        } else {

            ReceivedQuestionEvent receiveEvent = new ReceivedQuestionEvent();
            for (QuizQuestion quizQuestion : quizQuestionsMatchingQuery) {
                try {
                    results.add(new ServerResponse(quizQuestion.toJSON(), HttpStatus.SC_OK));
                } catch (MalformedQuestionException e) {
                    Log.d(TAG, e.getMessage(), e);
                }
            }

            receiveEvent.setResponse(results.remove(0));
            if (results.isEmpty()) {
                state = ProxyState.NORMAL;
            } else {
                state = ProxyState.NEXT;
            }
            this.emit(receiveEvent);
        }
    }

    private void retrieveQuestionFromServer(RequestContext reqContext, ServerEvent event) {
        // state machine transition
        this.emit(new ConnectionEvent(
                ConnectionEventType.ADD_OR_RETRIEVE_QUESTION));
        // Continue in on(ReceivedQuestionEvent) if server reachable
        // else (IOException) continue in on(GetConnectionErrorEvent)
        serverComm.doHttpGet(reqContext, event);
    }

    private void continueSearchingOnServer(RequestContext reqContext, ServerEvent event) {
        if (results.isEmpty()) {
            getNextResultFromServer(reqContext, event);
        } else {
            ReceivedQuestionEvent receiveEvent = new ReceivedQuestionEvent();
            receiveEvent.setResponse(results.get(0));
            results.remove(0);
            if ((null == next || "".equals(next) || "null".equals(next))
                    && results.isEmpty()) {
                state = ProxyState.NORMAL;
            }
            this.emit(receiveEvent);
        }
    }

    private void searchOnServer(RequestContext reqContext, ServerEvent event) {
        reqContext
                .setServerURL("https://sweng-quiz.appspot.com/search");
        reqContext.addHeader("Content-type", "application/json");
        StringEntity queryEntity = null;
        try {
            queryEntity = new StringEntity(QUERY_KEY
                    + query.getQueryString() + "\" }");
        } catch (UnsupportedEncodingException e) {
            Log.d(TAG, e.getMessage(), e);
        }
        reqContext.setEntity(queryEntity);

        this.emit(new ConnectionEvent(
                ConnectionEventType.ADD_OR_RETRIEVE_QUESTION));
        serverComm.doHttpPost(reqContext, event);
    }



    private ServerResponse offlineRandomQuestion() {
        QuizQuestion question = cache.getRandomQuestion();

        if (question == null) {
            return new ServerResponse(null, HttpStatus.SC_NOT_FOUND);
        } else {
            ServerResponse response = null;
            try {
                response = new ServerResponse(question.toJSON(),
                        HttpStatus.SC_OK);
            } catch (MalformedQuestionException e) {
                Log.d(TAG, e.getMessage(), e);
            }

            return response;
        }
    }



    private enum ProxyState {
        NORMAL, SEARCH, NEXT
    }

    private void getNextResultFromServer(RequestContext reqContext,
                                         ServerEvent event) {
        reqContext.setServerURL("https://sweng-quiz.appspot.com/search");
        reqContext.addHeader("Content-type", "application/json");
        StringEntity queryEntity = null;
        try {
            queryEntity = new StringEntity(QUERY_KEY
                    + query.getQueryString() + "\", \"from\": \"" + next
                    + "\"}");
        } catch (UnsupportedEncodingException e) {
            Log.d(TAG, e.getMessage(), e);
        }
        reqContext.setEntity(queryEntity);
        this.emit(new ConnectionEvent(
                ConnectionEventType.ADD_OR_RETRIEVE_QUESTION));
        serverComm.doHttpPost(reqContext, event);
    }

    private void serializeQuestionToPostList(
            ArrayList<QuestionToSubmit> questions) throws IOException {
        FileOutputStream fos = mContext.openFileOutput(BACKUP_FILE_NAME,
                Context.MODE_PRIVATE);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(questions);
        oos.flush();
        oos.close();
    }

    @SuppressWarnings("unchecked")
    private ArrayList<QuestionToSubmit> readPendingQuizQuestion() throws ClassNotFoundException, IOException {
        FileInputStream fis = mContext.openFileInput(BACKUP_FILE_NAME);

        ObjectInputStream ois = new ObjectInputStream(fis);

        ArrayList<QuestionToSubmit> questions = (ArrayList<QuestionToSubmit>) ois
                .readObject();
        fis.close();
        ois.close();

        return questions;
    }
}
