package zk.rx.operators;

import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Executions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ZkObservable {

	public static <T> ObservableTransformer<T, T> activated() {
		return activated(Executions.getCurrent().getDesktop());
	}

	public static <T> ObservableTransformer<T, T> activated(Desktop desktop) {
		ZkDesktopOps desktopOps = new ZkDesktopOps(desktop);
		return upstream -> upstream
				.doOnNext(toConsumer(desktopOps.activate()))
				.doAfterNext(toConsumer(desktopOps.deactivate()))
				.doOnTerminate(desktopOps.deactivate());

	}

	public static <T> ObservableTransformer<T, T> activatedThrottle(int millis) {
		return upstream -> upstream
				.buffer(millis, TimeUnit.MILLISECONDS)
				.filter(items -> !items.isEmpty()) //avoid activation when buffer is empty
				.compose(activated())
				.concatMapIterable(items -> items); //concat... to preserve the original emission order
	}

	public static <T, K> ObservableTransformer<T, T> activatedThrottleUnique(int millis, java.util.function.Function<T, K> keySelector) {
		return upstream -> upstream
				.compose(bufferUnique(millis, keySelector))
				.filter(items -> !items.isEmpty()) //avoid activation when buffer is empty
				.compose(activated())
				.concatMapIterable(items -> items); //concat... to preserve the original emission order
	}
	public static <T, K> ObservableTransformer<T, Collection<T>> bufferUnique(int millis, java.util.function.Function<T, K> keySelector) {
		return upstream -> upstream.buffer(
				Observable.interval(millis, TimeUnit.MILLISECONDS),
				() -> new KeyedSet<K, T>(keySelector));
	}

	private static <T> Consumer<T> toConsumer(Action action) {
		return ignored -> action.run();
	}
	
	static class KeyedSet<K, V> extends AbstractSet<V> {
		private java.util.function.Function<V, K> keySelector;
		private Map<K, V> innerMap = new LinkedHashMap<>(16, 0.75f, true);
		
		public KeyedSet(java.util.function.Function<V, K> keySelector) {
			this.keySelector = keySelector;
		}
		
		@Override
		public boolean add(V e) {
			innerMap.put(keySelector.apply(e), e);
			return true;
		}

		@Override
		public Iterator<V> iterator() {
			return innerMap.values().iterator();
		}
		
		@Override
		public int size() {
			return innerMap.size();
		}
	}
}