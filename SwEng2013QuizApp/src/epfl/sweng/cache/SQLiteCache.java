package epfl.sweng.cache;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.json.JSONException;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import epfl.sweng.quizquestions.QuizQuestion;
import epfl.sweng.searchquestions.parser.SQLQueryCompiler;
import epfl.sweng.searchquestions.parser.tree.TreeNode;

public class SQLiteCache extends SQLiteOpenHelper implements CacheInterface {

    private static final String TAG = "SQLiteCache";
    
    // Database version
    private static final int DATABASE_VERSION = 1;
    // Database name
    private static final String DATABASE_NAME = "QuizQuestion_Cache";

    // Question table name
    private static final String TABLE_QUESTION = "table_Question";

    // Question table columns name
    public static final String COL_ID = "question_id";
    private static final String COL_QUESTION = "question_text";
    private static final String COL_SOLUTION = "question_solution_index";
    private static final String COL_OWNER = "question_owner";

    // Tab table name
    public static final String TABLE_TAG = "table_tag";
    public static final String COL_ID_TAG = "tag_question_id";
    public static final String COL_TAG = "tag_text";

    // Answer table name
    private static final String TABLE_ANSWER = "table_answer";

    private static final String COL_ID_ANSWER = "answer_question_id";
    private static final String COL_ANSWER = "answer_text";
    private static final String COL_INDEX = "answer_index";

    private static final String CREATE_QUESTION_TABLE = "CREATE TABLE "
            + TABLE_QUESTION + "(" + COL_ID + " INT PRIMARY KEY,"
            + COL_QUESTION + " TEXT," + COL_OWNER + " TEXT," + COL_SOLUTION
            + " INT" + ");";
    private static final String CREATE_TAG_TABLE = "CREATE TABLE " + TABLE_TAG
            + "(" + COL_TAG + " TEXT," + COL_ID_TAG + " INT"
            + ", PRIMARY KEY ("+COL_TAG+","+COL_ID_TAG+"));";
    private static final String CREATE_ANSWER_TABLE = "CREATE TABLE "
            + TABLE_ANSWER + "(" + COL_ID_ANSWER + " INT,"
            + COL_ANSWER + " TEXT," + COL_INDEX + " INT" + ","
            + "PRIMARY KEY ("+COL_ID_ANSWER+","+COL_INDEX+"));";

    private static final int MAX_SQL_CACHE_SIZE = 100;
    private static final long MAX_SQL_SIZE = 1024 * 1024 * 1024;

    public SQLiteCache(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        // To delete the database on disk, use reset method
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        // change de size of the cache of the sqlite
        db.setMaxSqlCacheSize(MAX_SQL_CACHE_SIZE);

        // change de size of the sqlite : 1 GB
        db.setMaximumSize(MAX_SQL_SIZE);

        db.execSQL(CREATE_QUESTION_TABLE);
        db.execSQL(CREATE_TAG_TABLE);
        db.execSQL(CREATE_ANSWER_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_QUESTION);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TAG);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ANSWER);

        onCreate(db);

    }

    @Override
    public void cacheQuestion(QuizQuestion question) {
        SQLiteDatabase db = this.getWritableDatabase();
        // Insert elements in the question table
        ContentValues valuesQuestion = new ContentValues();
        valuesQuestion.put(COL_ID, question.getId());
        valuesQuestion.put(COL_QUESTION, question.getQuestion());
        valuesQuestion.put(COL_OWNER, question.getOwner());
        valuesQuestion.put(COL_SOLUTION, question.getSolution());
        db.replace(TABLE_QUESTION, null, valuesQuestion);

        // Insert elements in the tags table
        Set<String> tagSet = question.getTags();
        for (String tag : tagSet) {
            ContentValues valuesTags = new ContentValues();
            valuesTags.put(COL_ID_TAG, question.getId());
            valuesTags.put(COL_TAG, tag);
            db.replace(TABLE_TAG, null, valuesTags);
        }


        // Insert elements in the answers table
        List<String> answerList = question.getAnswers();
        int index = 0;
        for (String answer : answerList) {
            ContentValues valuesAnswers = new ContentValues();
            valuesAnswers.put(COL_ID_ANSWER, question.getId());
            valuesAnswers.put(COL_ANSWER, answer);
            valuesAnswers.put(COL_INDEX, index);
            db.replace(TABLE_ANSWER, null, valuesAnswers);
            index++;
        }


        db.close();
    }
    
    public void cacheQuestion(String json) {
        QuizQuestion quizQuestion = null;
        try {
            quizQuestion = new QuizQuestion(json);
        } catch (JSONException e) {
            Log.d(TAG, "You are trying to cache an invalid question you fool ! ", e);
            throw new RuntimeException(
                    "You are trying to cache an invalid question you fool ! "
                            + json);
        }
        cacheQuestion(quizQuestion);
    }

    @Override
    public void clearCache() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_QUESTION, null, null);
        db.delete(TABLE_TAG, null, null);
        db.delete(TABLE_ANSWER, null, null);

    }


    @SuppressLint("UseSparseArrays")
    @Override
    public Set<QuizQuestion> getQuestionSetByTag(TreeNode ast) {
        Set<QuizQuestion> questions = new HashSet<QuizQuestion>();

        SQLiteDatabase db = this.getReadableDatabase();
        SQLQueryCompiler compiler = new SQLQueryCompiler();
        // | INT id | STR question | STR owner | INT solution | STR tag | STR answer | INT index |

        String questionQuery = "SELECT " + COL_ID + ", " + COL_QUESTION + ", " + COL_SOLUTION + ", " + COL_OWNER +
               " FROM " +TABLE_QUESTION +
            " INNER JOIN " + TABLE_TAG + " ON " + COL_ID_TAG +"="+COL_ID + " WHERE " + compiler.toSQL(ast);

        Cursor cursor = db.rawQuery(questionQuery, new String[0]);

        //if we get a question
        if (cursor.moveToFirst()) {
            do {
                QuizQuestion quizQuestion = constructQuizQuestion(db, cursor);

                questions.add(quizQuestion);

            } while (cursor.moveToNext());
        }

        return questions;
    }

    public QuizQuestion getRandomQuestion() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor quizQuestionCursor = db.query(TABLE_QUESTION + " ORDER BY RANDOM() LIMIT 1", 
            new String[] {"*"}, null, null, null, null, null);
        if (quizQuestionCursor.getCount() > 0) {
            quizQuestionCursor.moveToFirst();
            return constructQuizQuestion(db, quizQuestionCursor);
        } else {
            return null;
        }

    }

    private QuizQuestion constructQuizQuestion(SQLiteDatabase db, Cursor cursor) {
        //the id of the tag is the same as the one of the question
        long quizQuestionID = cursor.getLong(cursor.getColumnIndex(COL_ID));
        
        String quizQuestionBody = cursor.getString(cursor.getColumnIndex(COL_QUESTION));
        int quizQuestionSolutionIndex = cursor.getInt(cursor.getColumnIndex(COL_SOLUTION));
        String quizQuestionOwner = cursor.getString(cursor.getColumnIndex(COL_OWNER));

        List<String> quizQuestionAnswers = getAnswersForQuizQuestionWithID(db, quizQuestionID);

        Set<String> quizQuestionTags = getTagsForQuizQuestionWithID(db, quizQuestionID);

        return new QuizQuestion(quizQuestionBody, quizQuestionAnswers, quizQuestionSolutionIndex,
                quizQuestionTags, quizQuestionID, quizQuestionOwner);
    }

    private List<String> getAnswersForQuizQuestionWithID(SQLiteDatabase db, long quizQuestionID) {
        List<String> answers = new LinkedList<String>();

        String answersQuery = "SELECT " + COL_ANSWER + " FROM " + TABLE_ANSWER + " WHERE " +
                COL_ID_ANSWER + "=" + quizQuestionID + " ORDER BY " + COL_INDEX;
        Cursor answersCursor = db.rawQuery(answersQuery, new String[0]);

        if (answersCursor.moveToFirst()) {
            do {
                answers.add(answersCursor.getString(answersCursor.getColumnIndex(COL_ANSWER)));
            } while (answersCursor.moveToNext());
        }

        return answers;
    }

    private Set<String> getTagsForQuizQuestionWithID(SQLiteDatabase db, long quizQuestionID) {
        Set<String> tags = new HashSet<String>();

        String tagsQuery = "SELECT " + COL_TAG + " FROM " + TABLE_TAG + " WHERE " + COL_ID_TAG + "=" + quizQuestionID;
        Cursor tagsCursor = db.rawQuery(tagsQuery, new String[0]);

        if (tagsCursor.moveToFirst()) {
            do {
                tags.add(tagsCursor.getString(tagsCursor.getColumnIndex(COL_TAG)));
            } while (tagsCursor.moveToNext());
        }

        return tags;
    }

    public void reset() {
        onUpgrade(getWritableDatabase(), 0, 0);
    }

}
