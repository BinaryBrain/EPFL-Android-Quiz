package epfl.sweng.cache;

import java.util.Set;

import android.content.Context;
import android.util.LruCache;
import epfl.sweng.quizquestions.QuizQuestion;

/**
 * Non persistent Cache use for more performance.
 * Implements Proxy Pattern with QuestionCache
 * RAMCache is a proxy of QuestionCache
 * RAMCache has the set of HashQuestion => Question
 *
 */
public class RAMCache implements CacheInterface {
	//size of the cache : 50 MB;
	public static int MAX_CACHE_SIZE = 50 * 1024 * 1024;
	private static RAMCache instance;

	//private SparseArray<QuizQuestion> cacheMap ;
	
	//Cache where least recently is removed
	private LruCache<Integer, QuizQuestion> ramCache;
	private QuestionCache persistentCache;
	
	private RAMCache(Context context) {
		ramCache = new LruCache<Integer, QuizQuestion>(MAX_CACHE_SIZE){
			
			protected int sizeOf(Integer key, QuizQuestion value ){
				// TODO : override sizeOf
				return value.toByteCount();	
			}
		};
		//cacheMap = new SparseArray<QuizQuestion>();
		persistentCache = QuestionCache.getInstance(context);
	}

	public static synchronized RAMCache getInstance(Context context) {
		if (instance == null) {
			instance = new RAMCache(context);
		}
		return instance;
	}
	@Override
	public void cacheQuestion(QuizQuestion question) {
		Integer id = question.hashCode();
		// TODO : controler que la hashMap ne dépasse pas 50 MB
		ramCache.put(id, question);
	
		//cacheMap.append(id, question);
		persistentCache.cacheQuestion(question);
		
	}

	@Override
	public Set<Integer> getQuestionSetByTag(String tag) {
		return persistentCache.getQuestionSetByTag(tag);
	}

	@Override
	public QuizQuestion getQuestionById(Integer id) {
		QuizQuestion question = ramCache.get(id);
		//QuizQuestion question = cacheMap.get(id);
		if(question != null){
			return question;
		} else {
			return persistentCache.getQuestionById(id);
		}
	}
	
	public void clearCache(){
		ramCache.evictAll();
	}
	
}
