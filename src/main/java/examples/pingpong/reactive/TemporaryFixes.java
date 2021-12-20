package examples.pingpong.reactive;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.functions.Function;
import io.reactivex.internal.operators.flowable.FlowableFromFuture;
import io.reactivex.internal.operators.flowable.FlowableSingleSingle;
import io.reactivex.internal.operators.single.SingleMap;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.reflect.Field;
import java.util.concurrent.Future;

@ParametersAreNonnullByDefault
@SuppressWarnings({"rawtypes", "unchecked"})
public class TemporaryFixes {
    public static <T> Single<T> fixSingleFromListenableFuture(Single<T> single) throws RuntimeException {
        try {
            return fixSingleFromListenableFuture((SingleSource) single);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Single fixSingleFromListenableFuture(SingleSource single) throws Exception {
        if (single instanceof FlowableSingleSingle) {
            FlowableSingleSingle flowableSingleSingle = (FlowableSingleSingle) single;

            Field sourceField = FlowableSingleSingle.class.getDeclaredField("source");
            sourceField.setAccessible(true);
            Flowable source = (Flowable) sourceField.get(flowableSingleSingle);

            FlowableFromFuture flowableFromFuture = (FlowableFromFuture) source;

            Field futureField = FlowableFromFuture.class.getDeclaredField("future");
            futureField.setAccessible(true);
            Future future = (Future) futureField.get(flowableFromFuture);

            ListenableFuture listenableFuture = (ListenableFuture) future;

            return Single.create(subscriber ->
                    Futures.addCallback(listenableFuture, new FutureCallback() {
                        @Override
                        public void onSuccess(@Nullable Object t) {
                            subscriber.onSuccess(t);
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            subscriber.onError(throwable);
                        }
                    }, Runnable::run));
        } else if (single instanceof SingleMap) {
            SingleMap singleMap = (SingleMap) single;

            Field sourceField = SingleMap.class.getDeclaredField("source");
            sourceField.setAccessible(true);
            SingleSource source = (SingleSource) sourceField.get(singleMap);

            Field mapperField = SingleMap.class.getDeclaredField("mapper");
            mapperField.setAccessible(true);
            Function mapper = (Function) mapperField.get(singleMap);

            return fixSingleFromListenableFuture(source).map(mapper);
        }

        throw new UnsupportedOperationException();
    }
}
