package org.stepic.droid.util

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import io.reactivex.rxkotlin.zipWith
import java.util.concurrent.TimeUnit

enum class RxEmpty { INSTANCE }

data class RxOptional<out T>(val value: T?) {
    fun <R> map(f: (T) -> R?) =
            RxOptional(value?.let(f))
}

fun <T> Observable<RxOptional<T>>.unwrapOptional(): Observable<T> =
        this.filter { it.value != null }.map { it.value }

fun <T> Flowable<RxOptional<T>>.unwrapOptional(): Flowable<T> =
        this.filter { it.value != null }.map { it.value }

fun <T> Single<RxOptional<T>>.unwrapOptional(): Maybe<T> =
        this.filter { it.value != null }.map { it.value }

fun <T, R> Single<T>.mapNotNull(transform: (T) -> R?): Maybe<R> =
        this.map { RxOptional(transform(it)) }.unwrapOptional()

infix fun CompositeDisposable.addDisposable(d: Disposable) = this.add(d)

infix fun Completable.then(completable: Completable): Completable = this.andThen(completable)
infix fun <T> Completable.then(observable: Observable<T>): Observable<T> = this.andThen(observable)
infix fun <T> Completable.then(single: Single<T>): Single<T> = this.andThen(single)

infix fun <T> Observable<T>.merge(observable: Observable<T>): Observable<T> = Observable.merge(this, observable)
infix fun <T> Observable<T>.concat(observable: Observable<T>): Observable<T> = Observable.concat(this, observable)
operator fun <T> Observable<T>.plus(observable: Observable<T>): Observable<T> = merge(observable)

infix fun <X, Y> Observable<X>.zip(observable: Observable<Y>): Observable<Pair<X, Y>> = this.zipWith(observable)

class RetryWithDelay(private val retryDelayMillis: Int) : io.reactivex.functions.Function<Flowable<out Throwable>, Flowable<*>> {

    override fun apply(attempts: Flowable<out Throwable>): Flowable<*> =
            attempts.flatMap { Flowable.timer(retryDelayMillis.toLong(), TimeUnit.MILLISECONDS) }
}

class RetryExponential(private val maxAttempts: Int)
    : io.reactivex.functions.Function<Flowable<out Throwable>, Flowable<*>> {

    override fun apply(attempts: Flowable<out Throwable>): Flowable<*> =
            attempts.zipWith(Flowable.range(1, maxAttempts), BiFunction { t1: Throwable, t2: Int -> handleRetryAttempt(t1, t2) })
                    .flatMap { x -> x.toFlowable() }

    private fun handleRetryAttempt(throwable: Throwable, attempt: Int): Single<Long> =
            when (attempt) {
                1 -> Single.just(1L)
                maxAttempts -> Single.error<Long>(throwable)
                else -> {
                    val expDelay = Math.pow(2.toDouble(), (attempt - 2).toDouble()).toLong()
                    Single.timer(expDelay, TimeUnit.SECONDS)
                }
            }

}

inline fun <T> Maybe<T>.doCompletableOnSuccess(crossinline completableSource: (T) -> Completable): Maybe<T> =
        flatMap { completableSource(it).andThen(Maybe.just(it)) }

inline fun <T> Single<T>.doCompletableOnSuccess(crossinline completableSource: (T) -> Completable): Single<T> =
    flatMap { completableSource(it).andThen(Single.just(it)) }

fun <T> Single<List<T>>.requireSize(size: Int): Single<List<T>> =
    flatMap { list ->
        list.takeIf { it.size == size }
            ?.let { Single.just(it) }
            ?: Single.error(IllegalStateException("Expected list size = $size, actual = ${list.size}"))
    }

/**
 * Empty on error stub in order to suppress errors
 */
val emptyOnErrorStub: (Throwable) -> Unit = {}

/**
 * Filters observable according to [predicateSource] predicate
 */
fun <T> Observable<T>.filterSingle(predicateSource: (T) -> Single<Boolean>): Observable<T> =
    flatMap { item ->
        predicateSource(item)
            .toObservable()
            .filter { it }
            .map { item }
    }

/**
 * Wraps current object to Maybe
 */
fun <T : Any> T?.toMaybe(): Maybe<T> =
    if (this == null) {
        Maybe.empty()
    } else {
        Maybe.just(this)
    }

/**
 * Returns first element of list wrapped in Maybe or empty
 */
fun <T : Any> Single<List<T>>.maybeFirst(): Maybe<T> =
    flatMapMaybe { it.firstOrNull().toMaybe() }

/**
 * Returns first element of list
 */
fun <T : Any> Single<List<T>>.first(): Single<T> =
    map { it.first() }

fun <T : Any, R : Any> reduce(sources: List<Single<T>>, seed: R, transform: (R, T) -> Single<R>): Observable<R> =
    if (sources.isNotEmpty()) {
        sources
            .first()
            .flatMapObservable { item ->
                transform(seed, item)
                    .flatMapObservable { value ->
                        Observable.just(value) + reduce(sources.subList(1, sources.size), value, transform)
                    }
            }
    } else {
        Observable.empty<R>()
    }