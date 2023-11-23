package com.tangem.common

import com.tangem.common.CompletionResult.Success
import com.tangem.common.core.TangemError
import com.tangem.common.core.TangemSdkError

/**
 * Response class encapsulating successful and failed results.
 * @param T Type of data that is returned in [Success].
 */
sealed class CompletionResult<T> {
    class Success<T>(val data: T) : CompletionResult<T>()
    class Failure<T>(val error: TangemError) : CompletionResult<T>()
}

@Suppress("RemoveRedundantQualifierName")
inline fun <T> catching(block: () -> T): CompletionResult<T> {
    return try {
        CompletionResult.Success(block())
    } catch (e: TangemError) {
        CompletionResult.Failure(e)
    } catch (e: Throwable) {
        CompletionResult.Failure(TangemSdkError.ExceptionError(e))
    }
}

@Suppress("RemoveRedundantQualifierName")
inline fun <T, D> CompletionResult<T>.map(transform: (T) -> D): CompletionResult<D> {
    return when (this) {
        is CompletionResult.Failure -> CompletionResult.Failure(error)
        is CompletionResult.Success -> catching { transform(data) }
    }
}

@Suppress("RemoveRedundantQualifierName")
inline fun <T> CompletionResult<T>.mapFailure(transform: (TangemError) -> TangemError): CompletionResult<T> {
    return this.flatMapOnFailure { e -> CompletionResult.Failure(transform(e)) }
}

@Suppress("RemoveRedundantQualifierName")
inline fun <T, D> CompletionResult<T>.flatMap(transform: (T) -> CompletionResult<D>): CompletionResult<D> {
    return when (this) {
        is CompletionResult.Failure -> CompletionResult.Failure(error)
        is CompletionResult.Success -> try {
            transform(data)
        } catch (e: TangemError) {
            CompletionResult.Failure(e)
        } catch (e: Throwable) {
            CompletionResult.Failure(TangemSdkError.ExceptionError(e))
        }
    }
}

@Suppress("RemoveRedundantQualifierName")
inline fun <T> CompletionResult<T>.flatMapOnFailure(
    transform: (TangemError) -> CompletionResult<T>,
): CompletionResult<T> {
    return when (this) {
        is CompletionResult.Failure -> transform(error)
        is CompletionResult.Success -> this
    }
}

@Suppress("RemoveRedundantQualifierName")
inline fun <T> CompletionResult<T>.doOnSuccess(
    catchException: Boolean = true,
    block: (T) -> Unit,
): CompletionResult<T> {
    return when (this) {
        is CompletionResult.Failure -> this
        is CompletionResult.Success -> if (catchException) {
            catching { block(data); data }
        } else {
            block(data); this
        }
    }
}

@Suppress("RemoveRedundantQualifierName")
inline fun <T> CompletionResult<T>.doOnFailure(block: (TangemError) -> Unit): CompletionResult<T> {
    return when (this) {
        is CompletionResult.Failure -> {
            block(error); this
        }
        is CompletionResult.Success -> this
    }
}

@Suppress("RemoveRedundantQualifierName")
inline fun <T> CompletionResult<T>.doOnResult(block: () -> Unit): CompletionResult<T> {
    return this
        .doOnFailure { block() }
        .doOnSuccess { block() }
}

@Suppress("RedundantUnitExpression")
fun List<CompletionResult<Unit>>.fold(): CompletionResult<Unit> {
    return fold(Unit) { _, _ -> }
}

@Suppress("UNCHECKED_CAST", "RemoveRedundantQualifierName")
inline fun <reified D, reified R> List<CompletionResult<D>>.fold(
    initial: R,
    operation: (acc: R, data: D) -> R,
): CompletionResult<R> {
    var resultData = initial
    for (result in this) {
        when (result) {
            is CompletionResult.Success -> {
                resultData = operation(resultData, result.data)
            }
            is CompletionResult.Failure -> {
                return result as CompletionResult.Failure<R>
            }
        }
    }

    return CompletionResult.Success(resultData)
}

internal fun <T> CompletionResult<T>.successOrNull(): T? {
    return when (this) {
        is CompletionResult.Failure -> null
        is Success -> this.data
    }
}
