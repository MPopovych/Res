typealias CResThrow<C, O> = CRes<C, O, Throwable>

/**
 * Result pattern with a context object attached to both error and ok
 */
sealed interface CRes<C, O, E> {
	val context: C

	data class Ok<C, O, E>(override val context: C, val data: O) : CRes<C, O, E>
	data class Error<C, O, E>(override val context: C, val error: E) : CRes<C, O, E>

	companion object {
		fun <C, O, E> ok(context: C, data: O) = Ok<C, O, E>(context, data)
		fun <C, O, E> error(context: C, error: E) = Error<C, O, E>(context, error)
		inline fun <C, O> catch(context: C, block: () -> O): CResThrow<C, O> {
			return try {
				Ok(context, block())
			} catch (e: Exception) {
				Error(context, e)
			}
		}
	}
}

// region extension
fun <C, A, E> A.wrapOk(context: C) = CRes.Ok<C, A, E>(context, this)
fun <C, O, A> A.wrapError(context: C) = CRes.Error<C, O, A>(context, this)

fun CRes<*, *, *>.isOk() = this is CRes.Ok
fun CRes<*, *, *>.isError() = this is CRes.Error

fun <C, O, E> CRes<C, O, E>.asOk(): CRes.Ok<C, O, E>? = this as? CRes.Ok
fun <C, O, E> CRes<C, O, E>.asError(): CRes.Error<C, O, E>? = this as? CRes.Error

fun <O> CRes<*, O, *>.getOk() = this.asOk()?.data
fun <E> CRes<*, *, E>.getError() = this.asError()?.error
fun CRes<*, *, *>.get(): Any? = getOk() ?: getError()

inline fun <C, R, O, E> CRes<C, O, E>.mapContext(block: (C) -> R): CRes<R, O, E> {
	val newContext = block(this.context)
	return when (this) {
		is CRes.Error -> CRes.Error(newContext, this.error)
		is CRes.Ok -> CRes.Ok(newContext, this.data)
	}
}

inline fun <C, O, E, R> CRes<C, O, E>.mapOk(block: (C, O) -> R): CRes<C, R, E> {
	return when (this) {
		is CRes.Error -> CRes.Error(this.context, this.error)
		is CRes.Ok -> CRes.Ok(this.context, block(this.context, this.data))
	}
}

inline fun <C, O, E, R> CRes<C, O, E>.mapError(block: (C, E) -> R): CRes<C, O, R> {
	return when (this) {
		is CRes.Error -> CRes.Error(this.context, block(this.context, this.error))
		is CRes.Ok -> CRes.Ok(this.context, this.data)
	}
}

inline fun <C, O, E> CRes<C, O, E>.mapErrorToOk(block: (C, E) -> O): CRes<C, O, E> {
	return when (this) {
		is CRes.Error -> CRes.Ok(this.context, block(this.context, this.error))
		is CRes.Ok -> CRes.Ok(this.context, this.data)
	}
}

inline fun <C, O, E> CRes<C, O, E>.onOk(block: (C, O) -> Unit) = this.also {
	this.asOk()?.also { block(it.context, it.data) }
}

inline fun <C, O, E> CRes<C, O, E>.onError(block: (C, E) -> Unit) = this.also {
	this.asError()?.also { block(it.context, it.error) }
}

inline fun <C, O, E, R> CRes<C, O, E>.fold(ok: ((C, O) -> R), err: ((C, E) -> R)): R {
	return mapError(err).mapOk(ok).merge()
}

inline fun <C, A, R> CRes<C, A, A>.mapMerge(block: (C, A) -> R): R {
	return block(this.context, merge())
}

fun <C, R> CRes<C, R, R>.merge(): R {
	return when (this) {
		is CRes.Error -> this.error
		is CRes.Ok -> this.data
	}
}

inline fun <C, O, E> CRes<C, O, E>.okOrBlock(block: (C, E) -> Nothing): O {
	return when (this) {
		is CRes.Error -> block(this.context, this.error)
		is CRes.Ok -> this.data
	}
}

inline fun <C, O, E, R> CRes<C, O, E>.chainOk(block: (C, O) -> CRes<C, R, E>): CRes<C, R, E> {
	return when (this) {
		is CRes.Error -> CRes.Error(this.context, this.error)
		is CRes.Ok -> block(this.context, this.data)
	}
}

inline fun <C, O, E> CRes<C, O, E>.chainError(block: (C, E) -> CRes<C, O, E>): CRes<C, O, E> {
	return when (this) {
		is CRes.Error -> block(this.context, this.error)
		is CRes.Ok -> CRes.Ok(this.context, this.data)
	}
}
// endregion

// region compatibility

fun <C, O, E> CRes<C, O, E>.toRes(): Res<O, E> {
	return when (this) {
		is CRes.Error -> Res.Error(this.error)
		is CRes.Ok -> Res.Ok(this.data)
	}
}

// endregion