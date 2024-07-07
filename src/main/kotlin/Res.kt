typealias ResThrow<O> = Res<O, Throwable>
typealias ResIter<O, E> = Iterable<Res<O, E>>
typealias ResAny<O> = Res<O, Any>
typealias ResUnit<O> = Res<O, Unit>

sealed interface Res<O, E> {
	data class Ok<O, E>(val data: O) : Res<O, E>
	data class Error<O, E>(val error: E) : Res<O, E>

	companion object {
		fun <O, E> ok(data: O) = Ok<O, E>(data)
		fun <O, E> error(error: E) = Error<O, E>(error)
		inline fun <O> catch(block: () -> O): ResThrow<O> {
			return try {
				Ok(block())
			} catch (e: Exception) {
				Error(e)
			}
		}
	}
}

// region extension
fun <A, E> A.wrapOk() = Res.Ok<A, E>(this)
fun <O, A> A.wrapError() = Res.Error<O, A>(this)

fun Res<*, *>.isOk() = this is Res.Ok
fun Res<*, *>.isError() = this is Res.Error

fun <O, E> Res<O, E>.asOk(): Res.Ok<O, E>? = this as? Res.Ok
fun <O, E> Res<O, E>.asError(): Res.Error<O, E>? = this as? Res.Error

fun <O> Res<O, *>.getOk() = this.asOk()?.data
fun <E> Res<*, E>.getError() = this.asError()?.error
fun Res<*, *>.get(): Any? = getOk() ?: getError()

inline fun <O, E, R> Res<O, E>.mapOk(block: (O) -> R): Res<R, E> {
	return when (this) {
		is Res.Error -> Res.Error(this.error)
		is Res.Ok -> Res.Ok(block(this.data))
	}
}

inline fun <O, E, R> Res<O, E>.mapError(block: (E) -> R): Res<O, R> {
	return when (this) {
		is Res.Error -> Res.Error(block(this.error))
		is Res.Ok -> Res.Ok(this.data)
	}
}

inline fun <O, E> Res<O, E>.mapErrorToOk(block: (E) -> O): Res<O, E> {
	return when (this) {
		is Res.Error -> Res.Ok(block(this.error))
		is Res.Ok -> Res.Ok(this.data)
	}
}

inline fun <O, E> Res<O, E>.onOk(block: (O) -> Unit): Res<O, E> {
	this.asOk()?.also { block(it.data) }
	return this
}

inline fun <O, E> Res<O, E>.onError(block: (E) -> Unit): Res<O, E> {
	this.asError()?.also { block(it.error) }
	return this
}

inline fun <O, E, R> Res<O, E>.fold(ok: ((O) -> R), err: ((E) -> R)): R {
	return mapError(err).mapOk(ok).merge()
}

inline fun <A, R> Res<A, A>.mapMerge(block: (A) -> R): R {
	return merge().let(block)
}

fun <R> Res<R, R>.merge(): R {
	return when (this) {
		is Res.Error -> this.error
		is Res.Ok -> this.data
	}
}

inline fun <O, E> Res<O, E>.okOrBlock(block: (E) -> Nothing): O {
	return when (this) {
		is Res.Error -> block(this.error)
		is Res.Ok -> this.data
	}
}

inline fun <O, E, R> Res<O, E>.chainOk(block: (O) -> Res<R, E>): Res<R, E> {
	return when (this) {
		is Res.Error -> Res.Error(this.error)
		is Res.Ok -> block(this.data)
	}
}

inline fun <O, E> Res<O, E>.chainError(block: (E) -> Res<O, E>): Res<O, E> {
	return when (this) {
		is Res.Error -> block(this.error)
		is Res.Ok -> Res.Ok(this.data)
	}
}
// endregion

// region collections
inline fun <O, E, R> ResIter<O, E>.mapResOk(block: (O) -> R): List<Res<R, E>> {
	return this.map { it.mapOk(block) }
}

inline fun <O, E, R> ResIter<O, E>.mapResError(block: (E) -> R): List<Res<O, R>> {
	return this.map { it.mapError(block) }
}

inline fun <O, E> ResIter<O, E>.onEachResOk(block: (O) -> Unit) = this.also {
	this.onEach { it.onOk(block) }
}

inline fun <O, E> ResIter<O, E>.onEachResError(block: (E) -> Unit) = this.also {
	this.onEach { it.onError(block) }
}

fun <O, E> ResIter<O, E>.filterOk(): List<Res.Ok<O, E>> {
	return this.mapNotNull { it.asOk() }
}

fun <O, E> ResIter<O, E>.filterError(): List<Res.Error<O, E>> {
	return this.mapNotNull { it.asError() }
}
// endregion

// region others
fun <A> A?.wrapOkNotNull(msg: (() -> String) = { "Not found" }): ResThrow<A> {
	return this?.wrapOk() ?: NullPointerException(msg()).wrapError()
}

inline fun <O, E> Res<O?, E>.onOkNotNull(block: (O) -> Unit): Res<O?, E> {
	this.getOk()?.also(block)
	return this
}

inline fun <O, E> Res<O, E>.getOrDefault(block: (E) -> O): O {
	return when (this) {
		is Res.Error -> block(this.error)
		is Res.Ok -> this.data
	}
}

inline fun <O, E> Res<O?, E>.nullToError(block: () -> E): Res<O, E> {
	return when (this) {
		is Res.Error -> Res.Error(this.error)
		is Res.Ok -> this.data?.let { Res.Ok(it) } ?: Res.Error(block())
	}
}

fun <O, E : Throwable> Res<O, E>.okOrThrow(): O {
	return when (this) {
		is Res.Error -> throw this.error
		is Res.Ok -> this.data
	}
}

inline fun <O> Res<O, *>.okOrThrow(block: () -> Throwable): O {
	return when (this) {
		is Res.Error -> throw block()
		is Res.Ok -> this.data
	}
}

inline fun <O> Res<O, *>.okAnd(block: (O) -> Boolean): Boolean {
	if (this is Res.Ok) {
		return block(this.data)
	}
	return false
}

inline fun <E> Res<*, E>.errorAnd(block: (E) -> Boolean): Boolean {
	if (this is Res.Error) {
		return block(this.error)
	}
	return false
}


fun <O, E : Any> Res<O, E>.errorToAny(): ResAny<O> {
	return when (this) {
		is Res.Error -> Res.Error(this.error)
		is Res.Ok -> Res.Ok(this.data)
	}
}

fun <O, E : Any> Res<O, E>.errorToUnit(): ResUnit<O> {
	return when (this) {
		is Res.Error -> Res.Error(Unit)
		is Res.Ok -> Res.Ok(this.data)
	}
}

fun <C, O, E> Res<O, E>.withContext(context: C): CRes<C, O, E> {
	return when (this) {
		is Res.Error -> CRes.Error(context, this.error)
		is Res.Ok -> CRes.Ok(context, this.data)
	}
}
// endregion
