import kotlin.test.Test
import kotlin.test.assertEquals

class ResTest {
	private sealed class TestError(msg: String) : Throwable(message = msg) {
		data object FatalFail : TestError("Fatal test case")
		data class ValueFail(val value: String) : TestError("Will recover?")
	}

	@Test
	fun checkContextMapping() {
		fun step1(): Res<Int, TestError> {
			return 20.wrapOk()
		}

		fun step2(value: Int): Res<String, TestError> {
			return value.toString().wrapOk()
		}

		fun step3Error(value: String): Res<String, TestError> {
			return TestError.ValueFail(value).wrapError()
		}

		fun recoverFromError(error: TestError): Res<String, TestError> {
			if (error is TestError.ValueFail) return error.value.wrapOk()
			return TestError.FatalFail.wrapError()
		}

		val result = step1()
			.chainOk(::step2)
			.onOk { i -> println(i) }
			.chainOk(::step3Error)
			.onError { testError -> println(testError) }
			.chainError(::recoverFromError)
			.get()
		assertEquals("20", result)
	}
}